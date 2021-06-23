package com.akkasls.hackathon.entities;

import com.akkaserverless.javasdk.EntityId;
import com.akkaserverless.javasdk.eventsourcedentity.CommandContext;
import com.akkaserverless.javasdk.eventsourcedentity.CommandHandler;
import com.akkaserverless.javasdk.eventsourcedentity.EventHandler;
import com.akkaserverless.javasdk.eventsourcedentity.EventSourcedEntity;
import com.akkasls.hackathon.AddCandleCommand;
import com.akkasls.hackathon.NewTraderCommand;
import com.akkasls.hackathon.OrderPlaced;
import com.akkasls.hackathon.Trader;
import com.akkasls.hackathon.TraderAdded;
import lombok.extern.slf4j.Slf4j;

import javax.swing.text.html.Option;
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

    @EventHandler
    public void OrderPlaced(OrderPlaced event) {
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

    private Optional<Trader> buy(double quantity, double exchangeRate) {
        return traderState.flatMap(trader -> {
            if (trader.getQuoteBalance() >= exchangeRate * quantity) {
                return Optional.of(trader.toBuilder()
                        .setBaseBalance(quantity + trader.getBaseBalance())
                        .setQuoteBalance(trader.getQuoteBalance() - exchangeRate * quantity)
                        .build());
            } else {
                log.warn("not enough {} funds for entity {}!", trader.getQuoteAsset(), entityId);
                return Optional.empty();
            }
        });
    }

    private Optional<Trader> sell(double quantity, double exchangeRate) {
        return traderState.flatMap(trader -> {
            if (trader.getBaseBalance() >= quantity) {
                return Optional.of(trader.toBuilder()
                        .setBaseBalance(trader.getBaseBalance() - quantity)
                        .setQuoteBalance(trader.getQuoteBalance() + exchangeRate * quantity)
                        .build());
            } else {
                log.warn("not enough {} funds for entity {}!", trader.getBaseAsset(), entityId);
                return Optional.empty();
            }
        });
    }

}
