package com.example.smart.configuration;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;
import org.springframework.web.socket.server.support.DefaultHandshakeHandler;
import org.springframework.web.socket.server.standard.ServletServerContainerFactoryBean;

import com.example.smart.websocket.CameraSocketHandler;
import com.example.smart.websocket.DoorSocketHandler;
import com.example.smart.websocket.LightSocketHandler;

@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    @Autowired
    LightSocketHandler lightSocketHandler;

    @Autowired
    DoorSocketHandler doorSocketHandler;

    @Autowired
    CameraSocketHandler cameraSocketHandler;

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(lightSocketHandler, "/ws/light").setAllowedOrigins("*");
        registry.addHandler(doorSocketHandler, "/ws/door").setAllowedOrigins("*");
        registry.addHandler(cameraSocketHandler, "/ws/camera/livecamera").setAllowedOrigins("*");
    }

    // Tạo bean để cấu hình giới hạn kích thước tin nhắn
    @Bean
    public ServletServerContainerFactoryBean createWebSocketContainer() {
        ServletServerContainerFactoryBean container = new ServletServerContainerFactoryBean();
        container.setMaxTextMessageBufferSize(10 * 1024 * 1024); // 10MB
        container.setMaxBinaryMessageBufferSize(10 * 1024 * 1024); // 10MB
        return container;
    }
}
