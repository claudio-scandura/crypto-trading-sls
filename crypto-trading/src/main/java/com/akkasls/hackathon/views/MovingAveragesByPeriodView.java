package com.akkasls.hackathon.views;

import akka.japi.Pair;
import com.akkaserverless.javasdk.view.UpdateHandler;
import com.akkaserverless.javasdk.view.View;
import com.akkasls.hackathon.MovingAverage;
import com.akkasls.hackathon.MovingAverageUpdated;
import com.akkasls.hackathon.OrderPlaced;
import com.akkasls.hackathon.TraderAdded;
import com.akkasls.hackathon.TraderBalance;
import lombok.extern.slf4j.Slf4j;

import java.util.Optional;

@Slf4j
@View
public class MovingAveragesByPeriodView {

    @UpdateHandler
    public MovingAverage processTraderAdded(TraderAdded event, Optional<MovingAverage> maybeState) {
        var trader = event.getTrader();
        return maybeState.orElse(MovingAverage.newBuilder()
                .setLongMaValue(trader.getLongMaValue())
                .setShortMaValue(trader.getShortMaValue())
                .setShortMaPeriod(trader.getShortMaPeriod())
                .setLongMaPeriod(trader.getLongMaPeriod())
                .build());
    }

    @UpdateHandler
    public MovingAverage processMovingAverageUpdated(MovingAverageUpdated event, Optional<MovingAverage> maybeState) {
        var state = maybeState.orElse(MovingAverage.newBuilder().build());

        if (event.getPeriod() == state.getShortMaPeriod()) {
            return state.toBuilder().setShortMaValue(event.getValue()).setTime(event.getTime()).build();
        }
        if (event.getPeriod() == state.getLongMaPeriod()) {
            return state.toBuilder().setLongMaValue(event.getValue()).setTime(event.getTime()).build();
        }
        return state;
    }

    @UpdateHandler
    public MovingAverage processOrderPlaced(OrderPlaced event, Optional<MovingAverage> maybeState) {
        return maybeState.orElse(MovingAverage.newBuilder().build());
    }

}
