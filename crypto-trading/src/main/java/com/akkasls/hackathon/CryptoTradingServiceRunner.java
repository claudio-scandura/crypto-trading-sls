package com.akkasls.hackathon;

import com.akkaserverless.javasdk.AkkaServerless;
import com.akkasls.hackathon.entities.TraderEntity;
import com.akkasls.hackathon.views.BalanceByTestRunView;
import com.akkasls.hackathon.views.MovingAveragesByPeriodView;
import com.akkasls.hackathon.views.TradersByTestRunView;
import lombok.SneakyThrows;

public class CryptoTradingServiceRunner {

    @SneakyThrows
    public static void main(String[] args) {
        new AkkaServerless()
                .registerEventSourcedEntity(
                        TraderEntity.class,
                        Trading.getDescriptor().findServiceByName("CryptoTradingService"),
                        Trading.getDescriptor()
                        )
                .registerView(
                        TradersByTestRunView.class,
                        Trading.getDescriptor().findServiceByName("TradersView"),
                        "tradersByBaseAsset",
                        Trading.getDescriptor())
                .registerView(
                        BalanceByTestRunView.class,
                        Trading.getDescriptor().findServiceByName("BalanceView"),
                        "balanceByAssetPair",
                        Trading.getDescriptor())
                .registerView(
                        MovingAveragesByPeriodView.class,
                        Trading.getDescriptor().findServiceByName("MovingAverageView"),
                        "movingAveragesByPeriod",
                        Trading.getDescriptor())
                .start().toCompletableFuture().get();
    }
}
