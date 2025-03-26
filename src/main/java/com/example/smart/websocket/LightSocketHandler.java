package com.example.smart.websocket;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
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

    // Map để lưu trữ các promise đang chờ phản hồi từ ESP32
    private Map<Long, Map<String, CompletableFuture<Boolean>>> pendingResponses = new ConcurrentHashMap<>();

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
            lightService.updateLightStatus(disconnectedLightId, null, null,
                    lightService.findByLightId(disconnectedLightId).getUser().getUserId());
            System.out.println(
                    "Connection closed for Light ID: " + disconnectedLightId + ". Light status and IP set to null.");
        }
    }

    @Override
    public void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        String payload = message.getPayload();
        System.out.println("payload: " + payload);

        // Kiểm tra nếu đây là phản hồi từ ESP32 về yêu cầu thay đổi owner
        if (payload.startsWith("response:")) {
            handleEspResponse(payload);
            return;
        }

        String[] data = payload.split(":");
        Long lightId = Long.parseLong(data[0]);
        Integer lightStatus = Integer.parseInt(data[1]);
        String lightIp = data[2];
        Long ownerId = Long.parseLong(data[3]);
        String arduinoToken = data[4];

        if (handleArduinoMessage(session, lightId, lightStatus, lightIp, ownerId, arduinoToken)) {
            // arduinoSessions.put(lightId, session);
            session.sendMessage(new TextMessage("Da nhan duoc thong tin tu: " + lightId));
        }
    }

    // Xử lý phản hồi từ ESP32
    private void handleEspResponse(String payload) {
        // Format: response:lightId:command:result
        // Ví dụ: response:123:ownerId:accept
        String[] parts = payload.split(":");
        if (parts.length >= 4) {
            Long lightId = Long.parseLong(parts[1]);
            String command = parts[2];
            String result = parts[3];

            Map<String, CompletableFuture<Boolean>> lightPromises = pendingResponses.get(lightId);
            if (lightPromises != null && lightPromises.containsKey(command)) {
                CompletableFuture<Boolean> promise = lightPromises.get(command);
                if ("accept".equals(result)) {
                    promise.complete(true);
                } else {
                    promise.complete(false);
                }

                // Xóa promise đã hoàn thành
                lightPromises.remove(command);
                if (lightPromises.isEmpty()) {
                    pendingResponses.remove(lightId);
                }
            }
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
            arduinoSessions.put(lightId, session);

            Light thisLight = lightService.getLightById(lightId);
            // Kiểm tra user mới đã được thêm vào ESP chưa, nếu chưa thì cập nhật trước.
            if (thisLight.getUser() != null && thisLight.getUser().getUserId() != ownerId) {
                sendControlSignal(lightId, "ownerId:" + thisLight.getUser().getUserId());
                lightService.updateLightStatus(lightId, lightStatus, lightIp, thisLight.getUser().getUserId());
            } else if (thisLight.getUser() == null && ownerId != -1) {
                sendControlSignal(lightId, "ownerId:" + -1);
                // lightService.updateLightStatus(lightId, lightStatus, lightIp, null);
            } else {

                lightService.updateLightStatus(lightId, lightStatus, lightIp, ownerId);
            }
            return true;
        } else {
            // Nếu ID không tồn tại hoặc token không đúng thì đóng kết nối websocket
            System.err.println("Can't Update - Invalid ID or Token");
            session.close(CloseStatus.BAD_DATA);
            return false;
        }
    }

    // Gửi lệnh điều khiển đến ESP32 và đợi phản hồi
    public CompletableFuture<Boolean> sendControlSignalWithResponse(Long lightId, String controlMessage, String command)
            throws IOException {
        // Tạo promise để đợi phản hồi
        CompletableFuture<Boolean> responsePromise = new CompletableFuture<>();

        // Lưu promise vào map để xử lý khi nhận được phản hồi
        pendingResponses.computeIfAbsent(lightId, k -> new ConcurrentHashMap<>())
                .put(command, responsePromise);

        // Gửi lệnh điều khiển
        WebSocketSession session = arduinoSessions.get(lightId);
        if (session != null && session.isOpen()) {
            session.sendMessage(new TextMessage(controlMessage));

            // Đặt timeout 10 giây cho việc đợi phản hồi
            CompletableFuture.delayedExecutor(10, TimeUnit.SECONDS).execute(() -> {
                if (!responsePromise.isDone()) {
                    responsePromise.complete(false);
                    // Xóa promise đã timeout
                    Map<String, CompletableFuture<Boolean>> lightPromises = pendingResponses.get(lightId);
                    if (lightPromises != null) {
                        lightPromises.remove(command);
                        if (lightPromises.isEmpty()) {
                            pendingResponses.remove(lightId);
                        }
                    }
                }
            });

            return responsePromise;
        } else {
            System.err.println("No active session found for Arduino ID: " + lightId);
            responsePromise.complete(false);
            return responsePromise;
        }
    }

    public void sendControlSignal(Long lightId, String controlMessage) throws IOException {
        // Get the session associated with the Arduino ID
        WebSocketSession session = arduinoSessions.get(lightId);

        if (session != null && session.isOpen()) {
            session.sendMessage(new TextMessage(controlMessage));
        } else {
            System.err.println("No active session found for Arduino ID: " + lightId);
            // lightService.updateLightStatus(lightId, null, null,
            // lightService.findByLightId(lightId).getUser().getUserId());
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