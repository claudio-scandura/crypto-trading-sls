package com.akkasls.hackathon.views;

import com.akkaserverless.javasdk.view.UpdateHandler;
import com.akkaserverless.javasdk.view.View;
import com.akkasls.hackathon.MovingAverageUpdated;
import com.akkasls.hackathon.OrderPlaced;
import com.akkasls.hackathon.TraderAdded;
import com.akkasls.hackathon.TraderState;
import com.akkasls.hackathon.entities.TraderEntity;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@View
public class TradersByTestRunView {

    @UpdateHandler
    public TraderState processTraderAdded(TraderAdded event) {
        return event.getTrader();
    }

    @UpdateHandler
    public TraderState processMovingAverageUpdated(MovingAverageUpdated event, TraderState state) {
        var isLongMa = event.getPeriod() == state.getLongMaPeriod();
        return isLongMa
                ? state.toBuilder().setLongMaValue(event.getValue()).build()
                : state.toBuilder().setShortMaValue(event.getValue()).build();
    }

    @UpdateHandler
    public TraderState processOrderPlaced(OrderPlaced event, TraderState state) {
        switch (event.getType()) {
            case "BUY":
                return TraderEntity.buy(state, event.getQuantity(), event.getExchangeRate());
            case "SELL":
                return TraderEntity.sell(state, event.getQuantity(), event.getExchangeRate());
            default:
                log.warn("Unknown order type '{}'", event.getType());
                return state;
        }
    }
}
