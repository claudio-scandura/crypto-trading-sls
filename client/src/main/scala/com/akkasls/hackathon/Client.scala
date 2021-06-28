package com.akkasls.hackathon

import akka.actor.typed.ActorSystem
import akka.actor.typed.scaladsl.Behaviors
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.ws.{Message, TextMessage, WebSocketRequest}
import akka.stream.RestartSettings
import akka.stream.alpakka.csv.scaladsl.CsvFormatting
import akka.stream.scaladsl.{FileIO, Flow, RestartSource, Sink, Source}
import akka.{Done, NotUsed}
import com.typesafe.config.{Config, ConfigFactory}
import io.grpc.netty.shaded.io.grpc.netty.{GrpcSslContexts, NettyChannelBuilder}
import org.slf4j.LoggerFactory
import play.api.libs.json._

import java.net.URI
import java.net.http.HttpResponse.BodyHandlers
import java.net.http.{HttpClient, HttpRequest}
import java.nio.file.Path
import java.time.Instant
import java.util.concurrent.Executors
import scala.compat.java8.FutureConverters
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}
import scala.jdk.CollectionConverters._
import scala.math.BigDecimal.RoundingMode
import scala.util.{Failure, Random, Success}

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

  val config = ConfigFactory.load().getConfig("crypto-trading")
  implicit val system = ActorSystem(Behaviors.empty, "trading-platform-client")

  val HttpExecutor = Executors.newCachedThreadPool()
  val GrpcExecutor = Executors.newFixedThreadPool(8)

  val akkaServerlessHost = "tight-waterfall-9695.us-east1.apps.akkaserverless.io"


//    var channel = NettyChannelBuilder.forAddress(akkaServerlessHost, 443)
//      .sslContext(GrpcSslContexts.forClient().build())
//      .build()

  var channel = NettyChannelBuilder.forAddress("localhost", 9000)
    .usePlaintext()
    .build()

  var tradingServiceClient = CryptoTradingServiceGrpc.newBlockingStub(channel)
  val tradingViewsClient = TradersViewGrpc.newBlockingStub(channel)
  val balanceViewsClient = BalanceViewGrpc.newBlockingStub(channel)

  val testRun = config.getString("test-run-id")
  val candleSize = config.getDuration("candlestick-size").toMinutes.minutes

  val traders = Future.sequence {
    List(
      createTraders(testRun, 1, candleSize, AssetPairs.`BTC/EUR`).map(AssetPairs.`BTC/EUR` -> _),
      createTraders(testRun, 1, candleSize, AssetPairs.`BTC/USDT`).map(AssetPairs.`BTC/USDT` -> _),
      createTraders(testRun, 1, candleSize, AssetPairs.`ETH/USDT`).map(AssetPairs.`ETH/USDT` -> _),
      createTraders(testRun, 1, candleSize, AssetPairs.`ETH/EUR`).map(AssetPairs.`ETH/EUR` -> _)
    )
  }.map(_.groupBy(_._1).view.mapValues(_.flatMap(_._2)))

  val backTest = traders.flatMap { assetPairsToTraders =>
    Future.traverse(assetPairsToTraders.toList) {
      case (assetPair, traders) => sendHistoricalCandles(assetPair, traders, config)
        .flatMap { _ =>
          if (config.getBoolean("connect-to-live-feed")) {
            sendLiveCandles(assetPair, traders, config.getDuration("candlestick-size").toMinutes)._1.map(_ => Done)
          } else Future.successful(Done)
        }
    }
  }

  traders.onComplete {
    case Success(_) =>
      logger.info("Starting query balances for {}", testRun)
      queryBalances(testRun)
    case Failure(e) =>
      logger.error("Could not created traders", e)
      sys.exit(-1)
  }

  backTest.onComplete {
    case any =>
      logger.info("Backtest {} completed with {}", testRun, any)
      sys.exit(0)
  }

  def queryBalances(testRun: String) = {

    Source.tick(Duration.Zero, 5.second, Done)
      .map { _ =>
        balanceViewsClient.getTradersBalance(ByTestRun.newBuilder().setTestRunId(testRun).build())
      }.mapConcat(_.asScala)
      .map { state =>
        List[String](
          state.getTraderId,
          Instant.ofEpochMilli(state.getLastUpdatedAt).toString,
          state.getBaseAsset,
          fixDigits(state.getBaseBalance),
          fixDigits(state.getExchangeRate),
          state.getQuoteAsset,
          fixDigits(state.getQuoteBalance),
          state.getBuyOrders.toString,
          state.getSellOrders.toString,
        )
      }
      .via(CsvFormatting.format(delimiter = CsvFormatting.Tab))
      .runWith(FileIO.toPath(Path.of(s"data/balances.tsv")))

  }

  def createTraders(testRun: String, n: Int, candleSize: Duration, assetPair: AssetPair): Future[Seq[String]] = {
    Source(List.fill(n) {

      val movingAverageType = if (Math.random() > 0.5) "simple" else "exponential"
      val shortMaPeriod = 5 + Random.nextInt(15)
      val longMaPeriod = shortMaPeriod + Random.nextInt(90)
      val threshold = BigDecimal(Math.random()).setScale(2, RoundingMode.HALF_UP).min(0.20)
      val traderId = s"${assetPair._1}${assetPair._2}_${candleSize.toMinutes}m_${movingAverageType}_${shortMaPeriod}_${longMaPeriod}_$threshold"
      NewTraderCommand.newBuilder()
        .setTraderId(traderId)
        .setTrader(TraderState.newBuilder()
          .setTraderId(traderId)
          .setTestRunId(testRun)
          .setBaseAsset(assetPair._1)
          .setQuoteAsset(assetPair._2)
          .setMaType(movingAverageType)
          .setShortMaPeriod(shortMaPeriod)
          .setLongMaPeriod(longMaPeriod)
          .setBaseBalance(1)
          .setQuoteBalance(1000)
          .setThreshold(threshold.toDouble)
        ).build()
    }).mapAsyncUnordered(8) { command =>
      logger.info("Sending command {}", command)
      grpcCall {
        tradingServiceClient.newTrader(command)
      }.map(_ => command.getTraderId())
    }.runWith(Sink.seq[String])
  }

  def sendLiveCandles(assetPair: AssetPair, traders: List[String], candleSizeInMinutes: Long) = {
    val symbol = s"${assetPair._1.toLowerCase}${assetPair._2.toLowerCase}"
    val request = WebSocketRequest(s"wss://stream.binance.com:9443/ws/$symbol@kline_${candleSizeInMinutes}m")

    val webSocketFlow: Flow[Message, Message, NotUsed] =
      Flow[Message].mapConcat {
        case message: TextMessage.Strict =>
          val json = Json.parse(message.text)
          if ((json \ "k" \ "x").as[Boolean]) List(json.as[CandleStick]) else List.empty
      }.mapConcat { candle =>
        traders.map(traderId => AddCandleCommand.newBuilder().setCandle(candle).setTraderId(traderId).build())
      }.mapAsync(8) { command =>
        grpcCall {
          tradingServiceClient.addCandle(command)
        }
      }.mapConcat(_ => List.empty[Message])

    Http().singleWebSocketRequest(request, webSocketFlow)

  }

  def sendHistoricalCandles(assetPair: AssetPair, traders: List[String], config: Config) = {
    val from = Instant.now().minusMillis(config.getDuration("historical-load").toMillis)
    val candleSize = config.getDuration("candlestick-size").toMinutes.minutes
    getHistoricalCandles(from, candleSize, assetPair)
      .mapConcat { candle =>
        traders.map(traderId => AddCandleCommand.newBuilder().setCandle(candle).setTraderId(traderId).build())
      }
      .mapAsync(8) { command =>
        grpcCall(tradingServiceClient.addCandle(command))
      }
      .run()
  }

  def getHistoricalCandles(from: Instant, interval: Duration, assetPair: AssetPair): Source[CandleStick, NotUsed] = {
    val symbol = s"${assetPair._1.toUpperCase}${assetPair._2.toUpperCase}"
    val baseUrl = s"https://api.binance.com/api/v3/klines?symbol=$symbol&interval=${interval.toMinutes}m&limit=1000"
    val stepInMillis = interval.toMillis * 1000
    implicit val client = HttpClient.newBuilder().executor(HttpExecutor).build()
    val requestBuilder = HttpRequest.newBuilder().GET()

    Source.unfold(from) {
      case s if s.isBefore(Instant.now()) => Some(s.plusMillis(stepInMillis), s)
      case _ => None
    }.map { startTime: Instant =>
      val now = Instant.now()
      val from = startTime
      val to = startTime.plusMillis(stepInMillis)
      val cappedTo = if (to.isAfter(now)) now else to
      logger.info("Pulling {} candlesticks between {} and {} with interval {} minutes", symbol, from, cappedTo, interval.toMinutes)
      requestBuilder.GET().uri(URI.create(s"$baseUrl&startTime=${from.toEpochMilli}&endTime=${cappedTo.toEpochMilli}")).build()
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

  private def grpcCall[T](f: => T): Future[T] = Future(f)(ExecutionContext.fromExecutor(GrpcExecutor))
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
        }(ExecutionContext.fromExecutor(client.executor().get()))
      }
    }.runWith(Sink.head)
  }
}