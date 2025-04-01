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

import com.example.smart.entities.Door;
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
        // Tìm ID của door tương ứng với session bị đóng
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

            // Cập nhật doorIp và doorStatus thành null khi mất kết nối
            // doorService.updateDoorStatus(disconnectedDoorId, null, null, null,);
            System.out.println(
                    "Connection closed for Door ID: " + disconnectedDoorId + ". Door status and IP set to null.");
        }
    }
    // chuỗi gửi lên có dạng : doorId:doorStatus:doorLockDown:doorIp:ownerId:token

    @Override
    public void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        String payload = message.getPayload();
        // Kiểm tra nếu đây là phản hồi từ ESP32 về yêu cầu thay đổi owner
        if (payload.startsWith("response:")) {
            handleEspResponse(payload);
            return;
        }
        String[] data = payload.split(":");
        Long doorId = Long.parseLong(data[0]);
        Integer doorStatus = Integer.parseInt(data[1]);
        Integer doorLockDown = Integer.parseInt(data[2]);
        String doorIp = data[3];
        Long ownerId = Long.parseLong(data[4]);
        String token = data[5];
        arduinoSessions.put(doorId, session);
        if (handleArduinoMessage(session, doorId, doorStatus, doorLockDown, doorIp, ownerId, token)) {

            session.sendMessage(new TextMessage("Da nhan duoc thong tin tu: " + doorId));
        }

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

    private boolean handleArduinoMessage(WebSocketSession session, Long doorId, Integer doorStatus,
            Integer doorLockDown,
            String doorIp,
            Long ownerId, String token) {
        String secretKey = "12344321";
        String authToken = secretKey + doorId.toString();
        System.out.println(
                "Received message from Door ID: " + doorId + ", Status: " + doorStatus + " , LockDown:" + doorLockDown
                        + ", IP: " + doorIp + " , Token: " + token);
        if (doorService.idIsExist(doorId) && token.equals(token)) {
            System.out.println("Chap nhan ket noi tu: " + doorId);
            arduinoSessions.put(doorId, session);

            Door thisDoor = doorService.findByDoorId(doorId);
            // Kiểm tra user mới đã được thêm vào ESP chưa, nếu chưa thì cập nhật trước.
            if (thisDoor.getUser() != null && thisDoor.getUser().getUserId() != ownerId) {
                try {
                    sendControlSignal(doorId, "ownerId:" + thisDoor.getUser().getUserId());

                } catch (IOException err) {
                    System.err.println("error while sending to door device");
                }
                doorService.updateDoorStatus(doorId, doorStatus, doorLockDown, doorIp, thisDoor.getUser().getUserId());
            } else if (thisDoor.getUser() == null && ownerId != -1) {
                try {
                    sendControlSignal(doorId, "ownerId:" + -1);

                } catch (Exception e) {
                    System.err.println("error while sending to door device");
                }
                // doorService.updatedoorStatus(doorId, doorStatus, doorIp, null);
            } else {

                doorService.updateDoorStatus(doorId, doorStatus, doorLockDown, doorIp, ownerId);
            }
            return true;
        } else {
            // Nếu ID không tồn tại hoặc token không đúng thì đóng kết nối websocket
            System.err.println("Can't Update - Invalid ID or Token");
            try {
                session.close(CloseStatus.BAD_DATA);

            } catch (Exception e) {
                System.err.println("error while Close");
            }
            return false;
        }
    }

    // Gửi lệnh điều khiển đến ESP32 và đợi phản hồi
    public CompletableFuture<Boolean> sendControlSignalWithResponse(Long doorId, String controlMessage, String command)
            throws IOException {
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
                    Map<String, CompletableFuture<Boolean>> doorPromises = pendingResponses.get(doorId);
                    if (doorPromises != null) {
                        doorPromises.remove(command);
                        if (doorPromises.isEmpty()) {
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
            doorService.updateDoorStatus(doorId, null, null, null,
                    doorService.findByDoorId(doorId).getUser().getUserId());
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
}