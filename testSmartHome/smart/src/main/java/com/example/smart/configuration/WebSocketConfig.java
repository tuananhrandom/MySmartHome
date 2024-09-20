package com.example.smart.configuration;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

import com.example.smart.websocket.DoorSocketHandler;
import com.example.smart.websocket.LightSocketHandler;

@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    // private final LightSocketHandler lightSocketHandler;

    // public WebSocketConfig(LightSocketHandler lightSocketHandler) {
    // this.lightSocketHandler = lightSocketHandler;
    // }
    @Autowired
    LightSocketHandler lightSocketHandler;

    @Autowired
    DoorSocketHandler doorSocketHandler;

    public WebSocketConfig(LightSocketHandler lightSocketHandler, DoorSocketHandler doorSocketHandler) {
        this.lightSocketHandler = lightSocketHandler;
        this.doorSocketHandler = doorSocketHandler;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(lightSocketHandler, "/ws/light").setAllowedOrigins("*");
        registry.addHandler(doorSocketHandler, "/ws/door").setAllowedOrigins("*");
    }
}
