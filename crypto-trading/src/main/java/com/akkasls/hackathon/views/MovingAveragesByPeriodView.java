package com.akkasls.hackathon.views;

import com.akkaserverless.javasdk.view.UpdateHandler;
import com.akkaserverless.javasdk.view.View;
import com.akkasls.hackathon.MovingAverage;
import com.akkasls.hackathon.MovingAverageUpdated;
import com.akkasls.hackathon.OrderPlaced;
import com.akkasls.hackathon.TraderAdded;
import lombok.extern.slf4j.Slf4j;

import java.util.Optional;

@Slf4j
@View
public class MovingAveragesByPeriodView {

    @UpdateHandler
    public MovingAverage processTraderAdded(TraderAdded event) {
        var trader = event.getTrader();
        return MovingAverage.newBuilder()
                .setLongMaValue(trader.getLongMaValue())
                .setShortMaValue(trader.getShortMaValue())
                .setShortMaPeriod(trader.getShortMaPeriod())
                .setLongMaPeriod(trader.getLongMaPeriod())
                .setTime(-1)
                .setType(trader.getMaType())
                .setSymbol(trader.getBaseAsset() + "/" + trader.getQuoteAsset())
                .build();
    }

    @UpdateHandler
    public MovingAverage processMovingAverageUpdated(MovingAverageUpdated event, Optional<MovingAverage> maybeState) {
        var stateBuilder = maybeState.orElse(MovingAverage.newBuilder().build()).toBuilder();

        if (!event.getType().equals(stateBuilder.getType()))
            return stateBuilder.build();

        if (event.getPeriod() == stateBuilder.getShortMaPeriod()) {
            stateBuilder.setShortMaValue(event.getValue()).setTime(event.getTime()).build();
        }
        if (event.getPeriod() == stateBuilder.getLongMaPeriod()) {
            return stateBuilder.setLongMaValue(event.getValue()).setTime(event.getTime()).build();
        }

        return stateBuilder.build();
    }

    @UpdateHandler
    public MovingAverage processOrderPlaced(OrderPlaced event, Optional<MovingAverage> maybeState) {
        return maybeState.orElse(MovingAverage.newBuilder().build());
    }

}
