package com.akkasls.hackathon.entities;

import com.akkaserverless.javasdk.EntityId;
import com.akkaserverless.javasdk.eventsourcedentity.CommandContext;
import com.akkaserverless.javasdk.eventsourcedentity.CommandHandler;
import com.akkaserverless.javasdk.eventsourcedentity.EventHandler;
import com.akkaserverless.javasdk.eventsourcedentity.EventSourcedEntity;
import com.akkasls.hackathon.AddCandleCommand;
import com.akkasls.hackathon.CandleStick;
import com.akkasls.hackathon.MovingAverageUpdated;
import com.akkasls.hackathon.NewTraderCommand;
import com.akkasls.hackathon.OrderPlaced;
import com.akkasls.hackathon.Trader;
import com.akkasls.hackathon.TraderAdded;
import com.akkasls.hackathon.indicators.MovingAverages;
import com.akkasls.hackathon.indicators.MovingAverages.MovingAverage;
import com.google.protobuf.Empty;
import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Stream;

@EventSourcedEntity(entityType = "traders")
@Slf4j
public class TraderEntity {

    private final String entityId;

    private Optional<Trader> traderState = Optional.empty();

    // auxiliary stateful deps – not part of the state
    private MovingAverage shortMa;
    private MovingAverage longMa;

    public TraderEntity(@EntityId String entityId) {
        this.entityId = entityId;
    }

    @CommandHandler
    public Trader newTrader(NewTraderCommand command, CommandContext ctx) {
        ctx.emit(TraderAdded.newBuilder().setTrader(command.getTrader()).build());
        return command.getTrader();

    }

    @CommandHandler
    public Empty addCandle(AddCandleCommand command, CommandContext ctx) {
        updateMovingAverages(command.getCandle())
                .forEach(ctx::emit);
        return Empty.getDefaultInstance();
    }

    @EventHandler
    public void traderAdded(TraderAdded event) {
        traderState.ifPresentOrElse(nu -> {
        }, () -> {
            var trader = event.getTrader();
            var maType = trader.getMaType();
            traderState = Optional.of(trader);
            shortMa = movingAverageFor(maType).apply(trader.getShortMaPeriod());
            longMa = movingAverageFor(maType).apply(trader.getLongMaPeriod());
        });
    }

    @EventHandler
    public void movingAverageUpdated(MovingAverageUpdated event) {
        traderState = traderState.map(state -> {
            var stateBuilder = state.toBuilder();
            if (event.getPeriod() == shortMa.period) {
                stateBuilder.setShortMaValue(event.getValue());
            } else {
                stateBuilder.setLongMaValue(event.getValue());
            }
            return stateBuilder.build();
        });
    }


    private Function<Integer, MovingAverage> movingAverageFor(String maType) {
        switch (maType) {
            case "simple":
                return MovingAverages::simple;
            case "exponential":
                return MovingAverages::exponential;
            default:
                throw new IllegalArgumentException("Unsupported Moving Average type: " + maType);
        }
    }

    private Stream<MovingAverageUpdated> updateMovingAverages(CandleStick candle) {
        var maybeShortMa = shortMa.updateWith(BigDecimal.valueOf(candle.getClosingPrice())).value()
                .map(toMovingAverageUpdated(shortMa.period, candle.getTime()));

        var maybeLongMa = longMa.updateWith(BigDecimal.valueOf(candle.getClosingPrice())).value()
                .map(toMovingAverageUpdated(longMa.period, candle.getTime()));

        return Stream.concat(maybeShortMa.stream(), maybeLongMa.stream());
    }

    @EventHandler
    public void OrderPlaced(OrderPlaced event) {
        traderState.ifPresent(state -> {
            switch (event.getType()) {
                case "BUY":
                    traderState = buy(event.getQuantity(), event.getExchangeRate());
                    break;
                case "SELL":
                    traderState = sell(event.getQuantity(), event.getExchangeRate());
                    break;
                default:
                    log.info("nothing to do here! Just spending some serverless credits :)");
            }
        });
    }

    private Optional<Trader> buy(double quantity, double exchangeRate) {
        return traderState.flatMap(trader -> {
            if (trader.getQuoteBalance() >= exchangeRate * quantity) {
                return Optional.of(trader.toBuilder()
                        .setBaseBalance(quantity + trader.getBaseBalance())
                        .setQuoteBalance(trader.getQuoteBalance() - exchangeRate * quantity)
                        .build());
            } else {
                log.warn("not enough {} funds for entity {}!", trader.getQuoteAsset(), entityId);
                return Optional.empty();
            }
        });
    }

    private Optional<Trader> sell(double quantity, double exchangeRate) {
        return traderState.flatMap(trader -> {
            if (trader.getBaseBalance() >= quantity) {
                return Optional.of(trader.toBuilder()
                        .setBaseBalance(trader.getBaseBalance() - quantity)
                        .setQuoteBalance(trader.getQuoteBalance() + exchangeRate * quantity)
                        .build());
            } else {
                log.warn("not enough {} funds for entity {}!", trader.getBaseAsset(), entityId);
                return Optional.empty();
            }
        });
    }


    private static Function<BigDecimal, MovingAverageUpdated> toMovingAverageUpdated(int period, long time) {
        return value -> MovingAverageUpdated.newBuilder()
                .setPeriod(period)
                .setValue(value.doubleValue())
                .setTime(time)
                .build();
    }

}
