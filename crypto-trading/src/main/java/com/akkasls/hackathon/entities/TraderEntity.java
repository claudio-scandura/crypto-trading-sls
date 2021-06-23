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
import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;
import java.util.Optional;

@EventSourcedEntity(entityType = "traders")
@Slf4j
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

    private void buy(BigDecimal quantity, BigDecimal exchangeRate) {
        traderState.ifPresent(trader -> {
            if (trader.getQuoteBalance() >= exchangeRate.multiply(quantity).doubleValue()) {
                traderState = Optional.of(trader.toBuilder()
                        .setBaseBalance(quantity.add(BigDecimal.valueOf(trader.getBaseBalance())).doubleValue())
                        .setQuoteBalance(trader.getQuoteBalance() - exchangeRate.multiply(quantity).doubleValue())
                        .build());
            } else {
                log.warn("not enough {} funds for entity {}!", trader.getQuoteAsset(), entityId);
            }
        });
    }

    private void sell(BigDecimal quantity, BigDecimal exchangeRate) {
        traderState.ifPresent(trader -> {
            if (trader.getBaseBalance() >= quantity.doubleValue()) {
                traderState = Optional.of(trader.toBuilder()
                        .setBaseBalance(trader.getBaseBalance() - quantity.doubleValue())
                        .setQuoteBalance(trader.getQuoteBalance() + exchangeRate.multiply(quantity).doubleValue())
                        .build());
            } else {
                log.warn("not enough {} funds for entity {}!", trader.getBaseAsset(), entityId);
            }
        });
    }

}
