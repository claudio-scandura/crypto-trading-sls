package com.akkasls.hackathon.views;

import akka.japi.Pair;
import com.akkaserverless.javasdk.view.UpdateHandler;
import com.akkaserverless.javasdk.view.View;
import com.akkasls.hackathon.MovingAverageUpdated;
import com.akkasls.hackathon.OrderPlaced;
import com.akkasls.hackathon.TraderAdded;
import com.akkasls.hackathon.TraderBalance;
import com.akkasls.hackathon.TraderState;
import com.akkasls.hackathon.entities.TraderEntity;
import lombok.extern.slf4j.Slf4j;

import java.util.Optional;

@Slf4j
@View
public class BalanceByAssetPairView {

    @UpdateHandler
    public TraderBalance processTraderAdded(TraderAdded event, Optional<TraderBalance> maybeState) {
        var trader = event.getTrader();
        return maybeState.orElse(TraderBalance.newBuilder()
                .setTraderId(trader.getTraderId())
                .setBaseBalance(trader.getBaseBalance())
                .setQuoteBalance(trader.getQuoteBalance())
                .build());
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
                var res = buy(event.getQuantity(), event.getExchangeRate(), state.getQuoteBalance(),
                        state.getBaseBalance());
                if (res.isPresent()) {
                    var balances = res.get();
                    return builder.setBaseBalance(balances.first()).setQuoteBalance(balances.second()).build();
                } else {
                    return state;
                }
            case "SELL":
                var sellResult = sell(event.getQuantity(), event.getExchangeRate(), state.getQuoteBalance(),
                        state.getBaseBalance());
                if (sellResult.isPresent()) {
                    var balances = sellResult.get();
                    return builder.setBaseBalance(balances.first()).setQuoteBalance(balances.second()).build();
                } else {
                    return state;
                }
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
