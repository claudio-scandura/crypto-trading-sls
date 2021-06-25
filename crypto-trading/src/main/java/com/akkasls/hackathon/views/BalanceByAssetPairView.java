package com.akkasls.hackathon.views;

import akka.japi.Pair;
import com.akkaserverless.javasdk.view.UpdateHandler;
import com.akkaserverless.javasdk.view.View;
import com.akkasls.hackathon.MovingAverageUpdated;
import com.akkasls.hackathon.OrderPlaced;
import com.akkasls.hackathon.TraderAdded;
import com.akkasls.hackathon.TraderBalance;
import lombok.extern.slf4j.Slf4j;

import java.time.Instant;
import java.util.Optional;

@Slf4j
@View
public class BalanceByAssetPairView {

    @UpdateHandler
    public TraderBalance processTraderAdded(TraderAdded event) {
        var trader = event.getTrader();
        return TraderBalance.newBuilder()
                .setTraderId(trader.getTraderId())
                .setBaseAsset(trader.getBaseAsset())
                .setQuoteAsset(trader.getQuoteAsset())
                .setBaseBalance(trader.getBaseBalance())
                .setQuoteBalance(trader.getQuoteBalance())
                .setLastUpdatedAt(Instant.EPOCH.toEpochMilli())
                .setBuyOrders(0)
                .setSellOrders(0)
                .build();
    }

    @UpdateHandler
    public TraderBalance processMovingAverageUpdated(MovingAverageUpdated event, Optional<TraderBalance> state) {
        return state.orElse(TraderBalance.newBuilder().build());
    }

    @UpdateHandler
    public TraderBalance processOrderPlaced(OrderPlaced event, Optional<TraderBalance> maybeState) {
        var state = maybeState.orElse(TraderBalance.newBuilder().build());
        var builder = state.toBuilder().setTraderId(event.getTraderId())
                .setExchangeRate(event.getExchangeRate());
        switch (event.getType()) {
            case "BUY":
                return buy(event.getQuantity(), event.getExchangeRate(), state.getQuoteBalance(),
                        state.getBaseBalance()).map(baseAndQuote ->
                        builder.setBaseBalance(baseAndQuote.first())
                                .setQuoteBalance(baseAndQuote.second())
                                .setLastUpdatedAt(event.getTime())
                                .setBuyOrders(state.getBuyOrders() + 1)
                                .build()
                ).orElse(state);

            case "SELL":
                return sell(event.getQuantity(), event.getExchangeRate(), state.getQuoteBalance(),
                        state.getBaseBalance()).map(baseAndQuote ->
                        builder.setBaseBalance(baseAndQuote.first())
                                .setQuoteBalance(baseAndQuote.second())
                                .setLastUpdatedAt(event.getTime())
                                .setSellOrders(state.getSellOrders() + 1)
                                .build()
                ).orElse(state);
            default:
                log.warn("Unknown order type '{}'", event.getType());
                return state;
        }
    }

    private Optional<Pair<Double, Double>> buy(double quantity, double exchangeRate, double quoteBalance,
                                               double baseBalance) {
        if (quoteBalance >= exchangeRate * quantity) {
            return Optional.of(Pair.create(quantity + baseBalance, quoteBalance - exchangeRate * quantity));
        } else {
            return Optional.empty();
        }
    }

    private Optional<Pair<Double, Double>> sell(double quantity, double exchangeRate, double quoteBalance,
                                                double baseBalance) {
        if (baseBalance >= quantity) {
            return Optional.of(Pair.create(baseBalance - quantity, quoteBalance + exchangeRate * quantity));
        } else {
            return Optional.empty();
        }
    }
}
