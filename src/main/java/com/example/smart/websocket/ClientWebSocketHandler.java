package com.example.smart.websocket;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import com.example.smart.entities.Camera;
import com.example.smart.entities.Door;
import com.example.smart.entities.Light;
import com.example.smart.security.JwtService;
import com.example.smart.services.LightServicesImp;
import com.fasterxml.jackson.databind.ObjectMapper;

@Component
public class ClientWebSocketHandler extends TextWebSocketHandler {

    @Autowired
    private JwtService jwtService;

    @Autowired
    private LightServicesImp lightService;

    // Map để lưu trữ session theo userId - mỗi user có thể có nhiều session (nhiều
    // tab)
    private Map<Long, Map<String, WebSocketSession>> userSessions = new ConcurrentHashMap<>();

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        // Lấy JWT token từ URL query param
        String token = extractTokenFromSession(session);
        if (token != null && jwtService.isTokenValid(token)) {
            Long userId = jwtService.extractUserId(token);
            storeUserSession(userId, session);
            // session.sendMessage(new TextMessage("Kết nối WebSocket thành công"));
            System.out.println("Client connected: " + userId + " - " + session.getId());
        } else {
            session.close(CloseStatus.NOT_ACCEPTABLE.withReason("Invalid token"));
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        String token = extractTokenFromSession(session);
        if (token != null) {
            Long userId = jwtService.extractUserId(token);
            removeUserSession(userId, session);
            System.out.println("Client disconnected: " + userId + " - " + session.getId());
        }
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        // Xử lý tin nhắn từ client - trong trường hợp này có thể là yêu cầu subscribe
        String payload = message.getPayload();
        // Có thể thêm logic xử lý thêm nếu cần
    }

    // Gửi cập nhật thiết bị đến tất cả session của một userId cụ thể
    public void sendDeviceUpdateToUser(Long userId, Object deviceData, String eventType) {
        Map<String, WebSocketSession> sessions = userSessions.get(userId);
        if (sessions != null) {
            ObjectMapper mapper = new ObjectMapper();
            try {
                // Tạo JSON với type và data
                String json = mapper.writeValueAsString(Map.of(
                        "type", eventType,
                        "data", deviceData));

                // Gửi đến tất cả session của user này
                for (WebSocketSession session : sessions.values()) {
                    if (session.isOpen()) {
                        session.sendMessage(new TextMessage(json));
                        System.out.println("Send to user: " + userId + " - " + json);
                    }
                }
            } catch (IOException e) {
                System.err.println("Lỗi khi gửi cập nhật đến user " + userId + ": " + e.getMessage());
            }
        }
    }

    // Phương thức này được gọi từ LightServiceImp khi có cập nhật từ ESP32
    public void notifyLightUpdate(Light light) {
        System.out.println("đang gửi thông báo đến frontend");
        if (light.getUser() != null) {
            Long ownerId = light.getUser().getUserId();
            sendDeviceUpdateToUser(ownerId, light, "light-update");
        }
    }

    public void notifyDoorUpdate(Door door) {
        if (door.getUser() != null) {
            Long ownerId = door.getUser().getUserId();
            sendDeviceUpdateToUser(ownerId, door, "door-update");
        }
    }

    public void notifyCameraUpdate(Camera camera) {
        System.out.println("đang gửi thông báo đến frontend");
        if (camera.getUser() != null) {
            Long ownerId = camera.getUser().getUserId();
            sendDeviceUpdateToUser(ownerId, camera, "camera-update");
        }
    }

    private String extractTokenFromSession(WebSocketSession session) {
        String query = session.getUri().getQuery();
        if (query != null && query.startsWith("token=")) {
            return query.substring(6);
        }
        return null;
    }

    private void storeUserSession(Long userId, WebSocketSession session) {
        userSessions.computeIfAbsent(userId, k -> new ConcurrentHashMap<>())
                .put(session.getId(), session);
    }
    
    private void removeUserSession(Long userId, WebSocketSession session) {
        Map<String, WebSocketSession> sessions = userSessions.get(userId);
        if (sessions != null) {
            sessions.remove(session.getId());
            if (sessions.isEmpty()) {
                userSessions.remove(userId);
            }
        }
    }
}