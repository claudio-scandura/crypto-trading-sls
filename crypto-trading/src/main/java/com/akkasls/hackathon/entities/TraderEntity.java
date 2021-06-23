package com.akkasls.hackathon.entities;

import com.akkasls.hackathon.Hello;
import com.akkaserverless.javasdk.EntityId;
import com.akkaserverless.javasdk.eventsourcedentity.CommandHandler;
import com.akkaserverless.javasdk.eventsourcedentity.EventSourcedEntity;

@EventSourcedEntity(entityType = "trader")
public class TraderEntity {

    private final String entityId;

    public TraderEntity(@EntityId String entityId) {
        this.entityId = entityId;
    }

    @CommandHandler
    public Hello sayHello() {
        return Hello.newBuilder().setMessage("Hello!").build();
    }


}
