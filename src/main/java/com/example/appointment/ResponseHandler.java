package com.example.appointment;


import com.cajunsystems.ActorContext;
import com.cajunsystems.handler.Handler;

public class ResponseHandler implements Handler<AgentResponse> {
    @Override
    public void receive(AgentResponse message, ActorContext context) {
        System.out.println("Agent: " + message.message());
    }
}

