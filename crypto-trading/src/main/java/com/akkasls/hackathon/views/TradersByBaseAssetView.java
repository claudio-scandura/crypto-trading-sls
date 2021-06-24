package com.akkasls.hackathon.views;

import com.akkaserverless.javasdk.view.UpdateHandler;
import com.akkaserverless.javasdk.view.View;
import com.akkasls.hackathon.MovingAverageUpdated;
import com.akkasls.hackathon.TraderState;
import com.akkasls.hackathon.TraderAdded;

import java.util.Optional;

@View
public class TradersByBaseAssetView {

    @UpdateHandler
    public TraderState processTraderAdded(TraderAdded event, Optional<TraderState> maybeState) {
        return maybeState.orElse(event.getTrader());
    }

    @UpdateHandler
    public TraderState processMovingAverageUpdated(MovingAverageUpdated event, TraderState state) {
        var isLongMa = event.getPeriod() == state.getLongMaPeriod();
        return isLongMa
                ? state.toBuilder().setLongMaValue(event.getValue()).build()
                : state.toBuilder().setShortMaValue(event.getValue()).build();
    }
}
