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

import com.example.smart.services.DoorServicesImp;

@Component
public class DoorSocketHandler extends TextWebSocketHandler {
    @Autowired
    DoorServicesImp doorService;

    private Map<Long, Map<String, CompletableFuture<Boolean>>> pendingResponses = new ConcurrentHashMap<>();

    // Map to store WebSocket sessions with Arduino IDs as keys
    private Map<Long, WebSocketSession> arduinoSessions = new ConcurrentHashMap<>();

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        System.out.println("a Door Connected");
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        // Tìm ID của light tương ứng với session bị đóng
        Long disconnectedDoorId = null;
        for (Map.Entry<Long, WebSocketSession> entry : arduinoSessions.entrySet()) {
            if (entry.getValue().equals(session)) {
                disconnectedDoorId = entry.getKey();
                break;
            }
        }

        // Xóa session bị đóng
        if (disconnectedDoorId != null) {
            arduinoSessions.remove(disconnectedDoorId);

            // Cập nhật lightIp và lightStatus thành null khi mất kết nối
            doorService.updateDoorStatus(disconnectedDoorId, null, null, null);
            System.out.println(
                    "Connection closed for Door ID: " + disconnectedDoorId + ". Door status and IP set to null.");
        }
    }

    @Override
    public void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        String payload = message.getPayload();
        String[] data = payload.split(":");
        Long doorId = Long.parseLong(data[0]);
        Integer doorStatus = Integer.parseInt(data[1]);
        Integer doorLockDown = Integer.parseInt(data[2]);
        String doorIp = data[3];
        arduinoSessions.put(doorId, session);
        handleArduinoMessage(doorId, doorStatus, doorLockDown, doorIp);
        session.sendMessage(new TextMessage("Da nhan duoc thong tin tu: " + doorId));

        // Store the session in the map with Arduino ID as the key
        // if (doorService.idIsExist(doorId)) {
        // arduinoSessions.put(doorId, session);
        // handleArduinoMessage(doorId, doorStatus, doorLockDown, doorIp);
        // session.sendMessage(new TextMessage("Da nhan duoc thong tin tu: " + doorId));
        // } else {
        // System.out.println("Door ID trying to connect: " + doorId);
        // System.err.println("illegal ID");
        // session.sendMessage(new TextMessage("Refuse connect"));
        // session.close(CloseStatus.BAD_DATA);
        // }
    }

    private void handleArduinoMessage(Long doorId, Integer doorStatus, Integer doorLockDown, String doorIp) {
        // Implement your logic to handle messages from Arduino devices
        System.out.println(
                "Received message from Door ID: " + doorId + ", Status: " + doorStatus + " , LockDown:" + doorLockDown
                        + ", IP: " + doorIp);
        if (doorService.idIsExist(doorId)) {
            doorService.updateDoorStatus(doorId, doorStatus, doorLockDown, doorIp);
        } else {
            System.err.println("Can't Update");
        }
    }
     // Gửi lệnh điều khiển đến ESP32 và đợi phản hồi
     public CompletableFuture<Boolean> sendControlSignalWithResponse(Long doorId, String controlMessage, String command)throws IOException {
        // Tạo promise để đợi phản hồi
        CompletableFuture<Boolean> responsePromise = new CompletableFuture<>();

        // Lưu promise vào map để xử lý khi nhận được phản hồi
        pendingResponses.computeIfAbsent(doorId, k -> new ConcurrentHashMap<>())
                .put(command, responsePromise);

        // Gửi lệnh điều khiển
        WebSocketSession session = arduinoSessions.get(doorId);
        if (session != null && session.isOpen()) {
            session.sendMessage(new TextMessage(controlMessage));

            // Đặt timeout 10 giây cho việc đợi phản hồi
            CompletableFuture.delayedExecutor(10, TimeUnit.SECONDS).execute(() -> {
                if (!responsePromise.isDone()) {
                    responsePromise.complete(false);
                    // Xóa promise đã timeout
                    Map<String, CompletableFuture<Boolean>> lightPromises = pendingResponses.get(doorId);
                    if (lightPromises != null) {
                        lightPromises.remove(command);
                        if (lightPromises.isEmpty()) {
                            pendingResponses.remove(doorId);
                        }
                    }
                }
            });

            return responsePromise;
        } else {
            System.err.println("No active session found for Arduino ID: " + doorId);
            responsePromise.complete(false);
            return responsePromise;
        }
}

    public void sendControlSignal(Long doorId, String controlMessage) throws IOException {
        // Get the session associated with the Arduino ID
        WebSocketSession session = arduinoSessions.get(doorId);

        if (session != null && session.isOpen()) {
            session.sendMessage(new TextMessage(controlMessage));
        } else {
            System.err.println("No active session found for Arduino ID: " + doorId);
            doorService.updateDoorStatus(doorId, null, null, null);
        }
    }
}