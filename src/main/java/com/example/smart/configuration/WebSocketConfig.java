package com.example.smart.configuration;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;
import org.springframework.web.socket.server.standard.ServletServerContainerFactoryBean;
import com.example.smart.websocket.CameraSocketHandler;
import com.example.smart.websocket.ClientWebSocketHandler;
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

    @Autowired
    ClientWebSocketHandler clientWebSocketHandler;

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(lightSocketHandler, "/ws/light")
                .setAllowedOrigins("*");
        registry.addHandler(doorSocketHandler, "/ws/door")
                .setAllowedOrigins("*");
        registry.addHandler(cameraSocketHandler, "/ws/camera/livecamera")
                .setAllowedOrigins("*");
        registry.addHandler(clientWebSocketHandler, "/ws/client")
                .setAllowedOrigins("*");
    }

    // Tạo bean để cấu hình giới hạn kích thước tin nhắn
    @Bean
    public ServletServerContainerFactoryBean createWebSocketContainer() {
        ServletServerContainerFactoryBean container = new ServletServerContainerFactoryBean();
        container.setMaxTextMessageBufferSize(20 * 1024 * 1024); // 20MB
        container.setMaxBinaryMessageBufferSize(20 * 1024 * 1024); // 20MB
        container.setAsyncSendTimeout(30000L); // 30 giây timeout cho gửi bất đồng bộ
        container.setMaxSessionIdleTimeout(600000L); // 10 phút timeout cho session không hoạt động
        return container;
    }
}
