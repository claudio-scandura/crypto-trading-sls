package com.akkasls.hackathon;

import com.akkasls.hackathon.entities.TraderEntity;
import com.akkaserverless.javasdk.AkkaServerless;
import lombok.SneakyThrows;

public class CryptoTradingServiceRunner {

    @SneakyThrows
    public static void main(String[] args) {
         new AkkaServerless().registerEventSourcedEntity(
                TraderEntity.class,
                Trading.getDescriptor().findServiceByName("CryptoTradingService")
        ).start().toCompletableFuture().get();
    }
}
