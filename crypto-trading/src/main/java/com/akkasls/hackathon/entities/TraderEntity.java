package com.akkasls.hackathon.entities;

import com.akkaserverless.javasdk.EntityId;
import com.akkaserverless.javasdk.eventsourcedentity.CommandContext;
import com.akkaserverless.javasdk.eventsourcedentity.CommandHandler;
import com.akkaserverless.javasdk.eventsourcedentity.EventHandler;
import com.akkaserverless.javasdk.eventsourcedentity.EventSourcedEntity;
import com.akkasls.hackathon.AddCandleCommand;
import com.akkasls.hackathon.NewTraderCommand;
import com.akkasls.hackathon.Trader;
import com.akkasls.hackathon.TraderAdded;

import java.util.Optional;

@EventSourcedEntity(entityType = "traders")
public class TraderEntity {

    private final String entityId;
    private Optional<Trader> traderState = Optional.empty();

    public TraderEntity(@EntityId String entityId) {
        this.entityId = entityId;
    }

    @CommandHandler
    public Trader newTrader(NewTraderCommand command, CommandContext ctx) {
        ctx.emit(TraderAdded.newBuilder().setTrader(command.getTrader()).build());
        return command.getTrader();

    }

    @CommandHandler
    public void addCandle(AddCandleCommand command) {
    }

    @EventHandler
    public void traderAdded(TraderAdded event) {
        traderState.ifPresentOrElse(nu -> {
        }, () -> traderState = Optional.of(event.getTrader()));
    }


}
