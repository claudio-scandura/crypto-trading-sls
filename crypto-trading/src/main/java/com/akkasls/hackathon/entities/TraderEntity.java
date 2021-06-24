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
import com.akkasls.hackathon.TraderAdded;
import com.akkasls.hackathon.TraderState;
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

    private Optional<TraderState> traderState = Optional.empty();

    // auxiliary stateful deps – not part of the state
    private MovingAverage shortMa;
    private MovingAverage longMa;

    public TraderEntity(@EntityId String entityId) {
        this.entityId = entityId;
    }

    @CommandHandler
    public TraderState newTrader(NewTraderCommand command, CommandContext ctx) {
        ctx.emit(TraderAdded.newBuilder().setTrader(command.getTrader()).build());
        return command.getTrader();

    }

    @CommandHandler
    public Empty addCandle(AddCandleCommand command, CommandContext ctx) {
        var maybeShortMa = updatedMovingAverage(shortMa, command.getCandle());
        var maybeLongMa = updatedMovingAverage(longMa, command.getCandle());

        var maybeOrderPlaced = maybeShortMa.flatMap(shortMa ->
                maybeLongMa.flatMap(longMa ->
                        placeOrder(command.getCandle(), shortMa.getValue(), longMa.getValue())
                ));

        Stream.of(maybeShortMa, maybeLongMa, maybeOrderPlaced).flatMap(Optional::stream)
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

    @EventHandler
    public void orderPlaced(OrderPlaced event) {
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

    private Optional<MovingAverageUpdated> updatedMovingAverage(MovingAverage currentMa, CandleStick candle) {
        return currentMa.updateWith(BigDecimal.valueOf(candle.getClosingPrice()))
                .value()
                .map(toMovingAverageUpdated(currentMa.period, candle.getTime()));
    }

    public static TraderState buy(TraderState state, double quantity, double exchangeRate) {
        if (state.getQuoteBalance() >= exchangeRate * quantity) {
            return state.toBuilder()
                    .setBaseBalance(quantity + state.getBaseBalance())
                    .setQuoteBalance(state.getQuoteBalance() - exchangeRate * quantity)
                    .build();
        } else {
            log.warn("not enough {} funds for entity!", state.getQuoteAsset());
            return state;
        }
    }

    public static TraderState sell(TraderState state, double quantity, double exchangeRate) {
        if (state.getBaseBalance() >= quantity) {
            return state.toBuilder()
                    .setBaseBalance(state.getBaseBalance() - quantity)
                    .setQuoteBalance(state.getQuoteBalance() + exchangeRate * quantity)
                    .build();
        } else {
            log.warn("not enough {} funds for entity!", state.getBaseAsset());
            return state;
        }
    }

    private Optional<TraderState> buy(double quantity, double exchangeRate) {
        return traderState.map(state -> buy(state, quantity, exchangeRate));
    }

    private Optional<TraderState> sell(double quantity, double exchangeRate) {
        return traderState.map(state -> sell(state, quantity, exchangeRate));
    }

    private Optional<OrderPlaced> placeOrder(CandleStick candle, double updatedShortMa, double updatedLongMa) {
        // if a new order should be placed then return it.
        return traderState.flatMap(state -> {
            var currentDiff = diff(state.getShortMaValue(), state.getLongMaValue());
            var updatedDiff = diff(updatedShortMa, updatedLongMa);
            var orderType = updatedDiff > 0 ? "BUY" : "SELL";
            var slope = Math.abs(diff(updatedDiff, currentDiff));
            double quantity = 10 * slope;
            if (slope > state.getThreshold() && haveEnoughFunds(orderType, quantity)) {
                return Optional.of(OrderPlaced.newBuilder()
                        .setTime(candle.getTime())
                        .setExchangeRate(candle.getClosingPrice())
                        .setQuantity(quantity)
                        .setType(orderType)
                        .build());
            }
            return Optional.empty();
        });

    }

    private boolean haveEnoughFunds(String orderType, double orderQuantity) {

        Function<TraderState, Double> getBalance =
                orderType.equals("BUY") ? TraderState::getQuoteBalance : TraderState::getBaseBalance;
        return traderState.map(getBalance).stream().anyMatch(balance -> balance > orderQuantity);
    }

    private double diff(double a, double b) {
        return (a - b) / a;
    }

    private static Function<BigDecimal, MovingAverageUpdated> toMovingAverageUpdated(int period, long time) {
        return value -> MovingAverageUpdated.newBuilder()
                .setPeriod(period)
                .setValue(value.doubleValue())
                .setTime(time)
                .build();
    }

}
