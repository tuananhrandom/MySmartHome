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
import com.example.smart.services.LightServicesImp;

@Component
public class LightSocketHandler extends TextWebSocketHandler {
    @Autowired
    LightServicesImp lightService;

    // Map to store WebSocket sessions with Arduino IDs as keys
    private Map<Long, WebSocketSession> arduinoSessions = new ConcurrentHashMap<>();

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        System.out.println("a Light Connected");
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        // Tìm ID của light tương ứng với session bị đóng
        Long disconnectedLightId = null;
        for (Map.Entry<Long, WebSocketSession> entry : arduinoSessions.entrySet()) {
            if (entry.getValue().equals(session)) {
                disconnectedLightId = entry.getKey();
                break;
            }
        }

        // Xóa session bị đóng
        if (disconnectedLightId != null) {
            arduinoSessions.remove(disconnectedLightId);

            // Cập nhật lightIp và lightStatus thành null khi mất kết nối
            lightService.updateLightStatus(disconnectedLightId, null, null);
            System.out.println(
                    "Connection closed for Light ID: " + disconnectedLightId + ". Light status and IP set to null.");
        }
    }

    @Override
    public void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        String payload = message.getPayload();
        String[] data = payload.split(":");
        Long lightId = Long.parseLong(data[0]);
        Integer lightStatus = Integer.parseInt(data[1]);
        String lightIp = data[2];

        // Store the session in the map with Arduino ID as the key
        if (lightService.idIsExist(lightId)) {
            arduinoSessions.put(lightId, session);
            handleArduinoMessage(lightId, lightStatus, lightIp);
            session.sendMessage(new TextMessage("Da nhan duoc thong tin tu: " + lightId));
        } else {
            System.err.println("illegal ID");
            session.sendMessage(new TextMessage("Refuse connect"));
            session.close(CloseStatus.BAD_DATA);
        }
    }

    private void handleArduinoMessage(Long lightId, Integer lightStatus, String lightIp) {
        // Implement your logic to handle messages from Arduino devices
        System.out.println(
                "Received message from Arduino ID: " + lightId + ", Status: " + lightStatus + ", IP: " + lightIp);
        if (lightService.idIsExist(lightId)) {
            lightService.updateLightStatus(lightId, lightStatus, lightIp);
        } else {
            System.err.println("Can't Update");
        }
    }

    public void sendControlSignal(Long lightId, String controlMessage) throws IOException {
        // Get the session associated with the Arduino ID
        WebSocketSession session = arduinoSessions.get(lightId);

        if (session != null && session.isOpen()) {
            session.sendMessage(new TextMessage(controlMessage));
        } else {
            System.err.println("No active session found for Arduino ID: " + lightId);
            lightService.updateLightStatus(lightId, null, null);
        }
    }

    public boolean isConnected(Long lightId) {
        WebSocketSession session = arduinoSessions.get(lightId);
        if (session != null && session.isOpen()) {
            return true;
        } else {
            return false;

        }
    }
}