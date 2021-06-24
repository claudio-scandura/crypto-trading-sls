package com.akkasls.hackathon;

import com.akkaserverless.javasdk.AkkaServerless;
import com.akkasls.hackathon.entities.TraderEntity;
import com.akkasls.hackathon.views.BalanceByAssetPairView;
import com.akkasls.hackathon.views.TradersByBaseAssetView;
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
                        TradersByBaseAssetView.class,
                        Trading.getDescriptor().findServiceByName("TradersView"),
                        "tradersByBaseAsset",
                        Trading.getDescriptor())
                .registerView(
                        BalanceByAssetPairView.class,
                        Trading.getDescriptor().findServiceByName("BalanceView"),
                        "balanceByAssetPair",
                        Trading.getDescriptor())
                .start().toCompletableFuture().get();
    }
}
