package com.akkasls.hackathon

import akka.actor.typed.ActorSystem
import akka.actor.typed.scaladsl.Behaviors
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.ws.{Message, TextMessage, WebSocketRequest}
import akka.stream.RestartSettings
import akka.stream.alpakka.csv.scaladsl.CsvFormatting
import akka.stream.scaladsl.{FileIO, Flow, RestartSource, Sink, Source}
import akka.{Done, NotUsed}
import io.grpc.netty.shaded.io.grpc.netty.{GrpcSslContexts, NettyChannelBuilder}
import org.slf4j.LoggerFactory
import play.api.libs.json._

import java.net.URI
import java.net.http.HttpResponse.BodyHandlers
import java.net.http.{HttpClient, HttpRequest}
import java.nio.file.Path
import java.time.Instant
import java.time.temporal.ChronoUnit
import scala.compat.java8.FutureConverters
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.concurrent.duration._
import scala.jdk.CollectionConverters.IteratorHasAsScala
import scala.math.BigDecimal.RoundingMode
import scala.util.{Random, Success}

object Client extends App {

  type AssetPair = (String, String)

  object AssetPairs {
    val `BTC/EUR` = "BTC" -> "EUR"
    val `BTC/USDT` = "BTC" -> "USDT"
    val `ETH/USDT` = "ETH" -> "USDT"
    val `ETH/EUR` = "ETH" -> "EUR"
    val all = List(`BTC/USDT`, `BTC/EUR`, `ETH/USDT`, `ETH/EUR`)
  }

  val logger = LoggerFactory.getLogger(classOf[Client.type])

  import CandleStickFormats._

  implicit val system = ActorSystem(Behaviors.empty, "trading-platform-client")

  val akkaServerlessHost = "localhost"


  var channel = NettyChannelBuilder.forAddress(akkaServerlessHost, 443)
    .sslContext(GrpcSslContexts.forClient().build())
    .build()
  var tradingServiceClient = CryptoTradingServiceGrpc.newBlockingStub(channel)
  val tradingViewsClient = TradersViewGrpc.newBlockingStub(channel)
  val balanceViewsClient = BalanceViewGrpc.newBlockingStub(channel)


  val traders = Future.sequence {
    List(
      createTraders(2, AssetPairs.`BTC/EUR`, "exponential").map(AssetPairs.`BTC/EUR` -> _),
      createTraders(2, AssetPairs.`BTC/USDT`, "simple").map(AssetPairs.`BTC/USDT` -> _),
      createTraders(2, AssetPairs.`ETH/USDT`, "simple").map(AssetPairs.`ETH/USDT` -> _),
      createTraders(2, AssetPairs.`ETH/EUR`, "simple").map(AssetPairs.`ETH/EUR` -> _)
    )
  }.map(_.groupBy(_._1).view.mapValues(_.flatMap(_._2)).toMap)

  traders.flatMap { assetPairsToTraders =>
    Future.sequence(assetPairsToTraders.map {
      case (assetPair, traders) =>
        sendHistoricalCandles(assetPair, traders)
          .flatMap { _ =>
            sendLiveCandles(assetPair, traders)._1
          }
    })
  }

  traders.andThen {
    case Success(_) => queryBalances()
  }

  def queryBalances() = {
    val queryCommands = AssetPairs.all.map {
      case (bAsset, qAsset) => ByAssetPair.newBuilder().setBaseAsset(bAsset).setQuoteAsset(qAsset).build()
    }
    Source.tick(1.second, 5.second, Done)
      .mapConcat(_ => queryCommands)
      .mapAsyncUnordered(8) { byAssets =>
        Future(balanceViewsClient.getTradersBalance(byAssets))
      }.mapConcat(_.asScala)
      .map { state =>
        List[String](
          state.getTraderId,
          Instant.ofEpochMilli(state.getLastUpdatedAt).toString,
          state.getBaseAsset,
          fixDigits(state.getBaseBalance),
          fixDigits(state.getExchangeRate),
          state.getQuoteAsset, fixDigits(state.getQuoteBalance)
        )
      }
      .via(CsvFormatting.format(delimiter = CsvFormatting.Tab))
      .runWith(FileIO.toPath(Path.of(s"data/balances.tsv")))

  }

  def createTraders(n: Int, assetPair: AssetPair, movingAverageType: String): Future[Seq[String]] = {
    Source((1 to n).map { idx =>

      val shortMaPeriod = 5 + Random.nextInt(15)
      val longMaPeriod = 10 + Random.nextInt(90)
      val threshold = Math.random()
      val traderId = s"${assetPair._1}${assetPair._2}_1m_${movingAverageType}_${shortMaPeriod}_${longMaPeriod}_${threshold}_$idx"
      NewTraderCommand.newBuilder()
        .setTraderId(traderId)
        .setTrader(TraderState.newBuilder()
          .setTraderId(traderId)
          .setBaseAsset(assetPair._1)
          .setQuoteAsset(assetPair._2)
          .setMaType(movingAverageType)
          .setShortMaPeriod(shortMaPeriod)
          .setLongMaPeriod(longMaPeriod)
          .setBaseBalance(10)
          .setQuoteBalance(1000)
          .setThreshold(threshold)
        ).build()
    }).mapAsyncUnordered(8) { command =>
      logger.info("Sending command {}", command)
      Future(tradingServiceClient.newTrader(command)).map(_ => command.getTraderId())
    }.runWith(Sink.seq[String])
  }

  def sendLiveCandles(assetPair: AssetPair, traders: List[String]) = {
    val symbol = s"${assetPair._1.toLowerCase}${assetPair._2.toLowerCase}"
    val request = WebSocketRequest(s"wss://stream.binance.com:9443/ws/$symbol@kline_1m")

    val webSocketFlow: Flow[Message, Message, NotUsed] =
      Flow[Message].mapConcat {
        case message: TextMessage.Strict =>
          val json = Json.parse(message.text)
          //        List(json.as[CandleStick])
          if ((json \ "k" \ "x").as[Boolean]) List(json.as[CandleStick]) else List.empty
      }.mapConcat { candle =>
        traders.map(traderId => AddCandleCommand.newBuilder().setCandle(candle).setTraderId(traderId).build())
      }.mapAsync(8) { command =>
        Future(tradingServiceClient.addCandle(command))
      }.mapConcat(_ => List.empty[Message])

    Http().singleWebSocketRequest(request, webSocketFlow)

  }

  def sendHistoricalCandles(assetPair: AssetPair, traders: List[String]) = {

    getHistoricalCandles(Instant.now().minus(2, ChronoUnit.HOURS), 1.minutes, assetPair)
      .mapConcat { candle =>
        traders.map(traderId => AddCandleCommand.newBuilder().setCandle(candle).setTraderId(traderId).build())
      }
      .mapAsyncUnordered(8) { command =>
        Future(tradingServiceClient.addCandle(command))
      }.run()
  }

  def getHistoricalCandles(from: Instant, interval: Duration, assetPair: AssetPair): Source[CandleStick, NotUsed] = {
    val symbol = s"${assetPair._1.toUpperCase}${assetPair._2.toUpperCase}"
    val baseUrl = s"https://api.binance.com/api/v3/klines?symbol=$symbol&interval=${interval.toMinutes}m&limit=1000"
    implicit val client = HttpClient.newHttpClient()
    val requestBuilder = HttpRequest.newBuilder().GET()

    Source.unfold(from) {
      case s if s.isBefore(Instant.now()) => Some(s.plusMillis(1000 * interval.toMillis), s)
      case _ => None
    }.map { startTime: Instant =>
      val from = startTime
      val to = startTime.plusMillis(1000 * interval.toMillis)
      logger.info("Pulling candlesticks between {} and {} with interval {} minutes", from, to, interval.toMinutes)
      requestBuilder.GET().uri(URI.create(s"$baseUrl&startTime=${from.toEpochMilli}&endTime=${to.toEpochMilli}")).build()
    }.throttle(50, 1.second) // What's the binance rate limit?
      .mapAsync(8) {
        Retriable.callApiWithRetries
      }
      .map(response => Json.parse(response.body()))
      .mapConcat {
        case xs: JsArray => xs.value.map {
          case JsArray(entries) =>
            CandleStick.newBuilder()
              .setTime(entries(6).as[Long])
              .setClosingPrice(entries(4).as[String].toDouble)
              .build()
        }
      }
  }

  private def fixDigits(value: Double): String = BigDecimal(value).setScale(6, RoundingMode.HALF_EVEN).toString()

}

object CandleStickFormats {
  implicit val reads: Reads[CandleStick] = (json: JsValue) => JsSuccess(CandleStick.newBuilder()
    .setTime((json \ "E").as[Long])
    .setClosingPrice((json \ "k" \ "c").as[String].toDouble)
    .build())
}

object Retriable {

  import scala.concurrent.duration._

  val logger = LoggerFactory.getLogger(classOf[Retriable.type])

  def callApiWithRetries(req: HttpRequest)
                        (implicit client: HttpClient, system: ActorSystem[_]) = {
    RestartSource.onFailuresWithBackoff(RestartSettings(1.second, 10.seconds, 0.10)) { () =>
      Source.future {
        FutureConverters.toScala(client.sendAsync(req, BodyHandlers.ofByteArray())).map {
          case resp if resp.statusCode() == 200 => resp
          case error =>
            logger.warn("Rate limit exceeded - will retry. {}", new String(error.body()))
            throw new RuntimeException("Rate limit exceeded")
        }
      }
    }.runWith(Sink.head)
  }
}