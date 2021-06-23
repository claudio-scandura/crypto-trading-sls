package com.akkasls.hackathon.views;

import com.akkaserverless.javasdk.view.UpdateHandler;
import com.akkaserverless.javasdk.view.View;
import com.akkasls.hackathon.Trader;
import com.akkasls.hackathon.TraderAdded;

import java.util.Optional;

@View
public class TradersByBaseAssetView {

    @UpdateHandler
    public Trader processTraderAdded(TraderAdded event, Optional<Trader> maybeState) {
        return maybeState.orElse(event.getTrader());
    }
}
