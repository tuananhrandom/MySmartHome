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

import com.example.smart.entities.Light;
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
            // lightService.updateLightStatus(disconnectedLightId, null, null);
            System.out.println(
                    "Connection closed for Light ID: " + disconnectedLightId + ". Light status and IP set to null.");
        }
    }

    @Override
    public void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        String payload = message.getPayload();
        System.out.println("payload: " + payload);
        String[] data = payload.split(":");
        Long lightId = Long.parseLong(data[0]);
        Integer lightStatus = Integer.parseInt(data[1]);
        String lightIp = data[2];
        Long ownerId = Long.parseLong(data[3]);
        String arduinoToken = data[4];

        if (handleArduinoMessage(session, lightId, lightStatus, lightIp, ownerId, arduinoToken)) {
            arduinoSessions.put(lightId, session);
            session.sendMessage(new TextMessage("Da nhan duoc thong tin tu: " + lightId));
        }
    }

    private boolean handleArduinoMessage(WebSocketSession session, Long lightId, Integer lightStatus, String lightIp,
            Long ownerId, String arduinoToken) throws IOException {
        String secretKey = "12341234";
        String token = secretKey + lightId.toString();

        System.out.println(
                "Received message from Arduino ID: " + lightId + ", Status: " + lightStatus + ", IP: " + lightIp
                        + ", Owner ID: " + ownerId);

        if (lightService.idIsExist(lightId) && token.equals(arduinoToken)) {
            System.out.println("Chap nhan ket noi tu: " + lightId);
            Light thisLight = lightService.getLightById(lightId);
            // Kiểm tra user mới đã được thêm vào ESP chưa, nếu chưa thì cập nhật trước.
            if (thisLight.getUser() != null && thisLight.getUser().getUserId() != ownerId) {
                sendControlSignal(lightId, "ownerId:" + thisLight.getUser().getUserId());
            } else if (thisLight.getUser() == null) {
                sendControlSignal(lightId, "ownerId:" + -1);
            }

            lightService.updateLightStatus(lightId, lightStatus, lightIp, ownerId);
            return true;
        } else {
            // Nếu ID không tồn tại hoặc token không đúng thì đóng kết nối websocket
            System.err.println("Can't Update - Invalid ID or Token");
            session.close(CloseStatus.BAD_DATA);
            return false;
        }
    }

    public void sendControlSignal(Long lightId, String controlMessage) throws IOException {
        // Get the session associated with the Arduino ID
        WebSocketSession session = arduinoSessions.get(lightId);

        if (session != null && session.isOpen()) {
            session.sendMessage(new TextMessage(controlMessage));
        } else {
            System.err.println("No active session found for Arduino ID: " + lightId);
            // lightService.updateLightStatus(lightId, null, null);
        }
    }

    public boolean isConnected(Long lightId) {
        WebSocketSession session = arduinoSessions.get(lightId);
        if (session != null && session.isOpen()) {
            return true;
        }
        return false;
    }
}