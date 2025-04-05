package com.example.smart.websocket;

import org.bytedeco.javacv.FFmpegFrameRecorder;
import org.bytedeco.javacv.Frame;
import org.bytedeco.javacv.Java2DFrameConverter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.BinaryMessage;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.BinaryWebSocketHandler;

import com.example.smart.entities.Camera;
import com.example.smart.services.CameraService;

import org.springframework.web.socket.TextMessage;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.*;
import java.util.logging.Level;
import java.util.logging.Logger;

@Component
public class CameraSocketHandler extends BinaryWebSocketHandler {
    @Autowired
    CameraService cameraService;

    private final CopyOnWriteArrayList<WebSocketSession> sessions = new CopyOnWriteArrayList<>();
    private final BlockingQueue<BufferedImage> frameQueue = new LinkedBlockingQueue<>(750); // Lưu tối đa 750 khung hình
    private final Logger logger = Logger.getLogger(CameraSocketHandler.class.getName());

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        sessions.add(session);
        logger.info("WebSocket connected: " + session.getId());
    }

    @Override
    protected void handleBinaryMessage(WebSocketSession session, BinaryMessage message) throws Exception {
        byte[] payload = message.getPayload().array();

        // Chuyển byte[] JPEG thành BufferedImage
        broadcast(payload); // Phát khung hình cho tất cả các WebSocket client kết nối
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        sessions.remove(session);
        logger.info("WebSocket disconnected: " + session.getId());
    }

    private void broadcast(byte[] data) {
        sessions.forEach(session -> {
            if (session.isOpen()) {
                try {
                    session.sendMessage(new BinaryMessage(data));
                } catch (Exception e) {
                    logger.log(Level.SEVERE, "Lỗi khi gửi khung hình đến WebSocket session", e);
                }
            }
        });
    }

    private Map<Long, WebSocketSession> arduinoSessions = new ConcurrentHashMap<>();

    // Map để lưu trữ các promise đang chờ phản hồi từ ESP32
    private Map<Long, Map<String, CompletableFuture<Boolean>>> pendingResponses = new ConcurrentHashMap<>();

    // @Override
    // public void handleTextMessage(WebSocketSession session, TextMessage message)
    // {
    // try {
    // String payload = message.getPayload();
    // System.out.println("payload: " + payload);

    // // Kiểm tra nếu đây là phản hồi từ ESP32 về yêu cầu thay đổi owner
    // if (payload.startsWith("response:")) {
    // handleEspResponse(payload);
    // return;
    // }

    // String[] data = payload.split(":");
    // if (data.length < 1) {
    // System.err.println("Dữ liệu không hợp lệ: " + payload);
    // return;
    // }

    // Long cameraId = Long.parseLong(data[0]);
    // Integer cameraStatus = Integer.parseInt(data[1]);
    // String lightIp = data[2];
    // Long ownerId = Long.parseLong(data[3]);
    // String arduinoToken = data[4];

    // // if (handleArduinoMessage(session, lightId, lightStatus, lightIp, ownerId,
    // arduinoToken)) {
    // // session.sendMessage(new TextMessage("Đã nhận được thông tin từ: " +
    // lightId));
    // }
    // } catch (NumberFormatException e) {
    // System.err.println("Lỗi chuyển đổi kiểu số: " + e.getMessage());
    // } catch (IOException e) {
    // System.err.println("Lỗi khi gửi tin nhắn WebSocket: " + e.getMessage());
    // } catch (Exception e) {
    // System.err.println("Lỗi không xác định: " + e.getMessage());
    // e.printStackTrace();
    // }
    // }

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

    private boolean handleArduinoMessage(WebSocketSession session, Long cameraId, Integer cameraStatus, String cameraIp,
            Long ownerId, String arduinoToken) throws IOException {
        String secretKey = "12341234";
        String token = secretKey + cameraId.toString();

        System.out.println(
                "Received message from Arduino ID: " + cameraId + ", Status: " + cameraStatus + ", IP: " + cameraIp
                        + ", Owner ID: " + ownerId);

        if (cameraService.getCameraById(cameraId) != null && token.equals(arduinoToken)) {
            System.out.println("Chap nhan ket noi tu: " + cameraId);
            arduinoSessions.put(cameraId, session);

            Camera thisCamera = cameraService.getCameraById(cameraId);
            // Kiểm tra user mới đã được thêm vào ESP chưa, nếu chưa thì cập nhật trước.
            if (thisCamera.getUser() != null && thisCamera.getUser().getUserId() != ownerId) {
                sendControlSignal(cameraId, "ownerId:" + thisCamera.getUser().getUserId());
                cameraService.updateCameraStatus(cameraId, cameraStatus, cameraIp, thisCamera.getUser().getUserId());
            } else if (thisCamera.getUser() == null && ownerId != -1) {
                sendControlSignal(cameraId, "ownerId:" + -1);
                // lightService.updateLightStatus(lightId, lightStatus, lightIp, null);
            } else {

                cameraService.updateCameraStatus(cameraId, cameraStatus, cameraIp, ownerId);
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
    public CompletableFuture<Boolean> sendControlSignalWithResponse(Long cameraId, String controlMessage,
            String command)
            throws IOException {
        // Tạo promise để đợi phản hồi
        CompletableFuture<Boolean> responsePromise = new CompletableFuture<>();

        // Lưu promise vào map để xử lý khi nhận được phản hồi
        pendingResponses.computeIfAbsent(cameraId, k -> new ConcurrentHashMap<>())
                .put(command, responsePromise);

        // Gửi lệnh điều khiển
        WebSocketSession session = arduinoSessions.get(cameraId);
        if (session != null && session.isOpen()) {
            session.sendMessage(new TextMessage(controlMessage));

            // Đặt timeout 10 giây cho việc đợi phản hồi
            CompletableFuture.delayedExecutor(10, TimeUnit.SECONDS).execute(() -> {
                if (!responsePromise.isDone()) {
                    responsePromise.complete(false);
                    // Xóa promise đã timeout
                    Map<String, CompletableFuture<Boolean>> cameraPromises = pendingResponses.get(cameraId);
                    if (cameraPromises != null) {
                        cameraPromises.remove(command);
                        if (cameraPromises.isEmpty()) {
                            pendingResponses.remove(cameraId);
                        }
                    }
                }
            });

            return responsePromise;
        } else {
            System.err.println("No active session found for Arduino ID: " + cameraId);
            responsePromise.complete(false);
            return responsePromise;
        }
    }

    public void sendControlSignal(Long cameraId, String controlMessage) throws IOException {
        // Get the session associated with the Arduino ID
        WebSocketSession session = arduinoSessions.get(cameraId);

        if (session != null && session.isOpen()) {
            session.sendMessage(new TextMessage(controlMessage));
        } else {
            System.err.println("No active session found for Arduino ID: " + cameraId);
            // lightService.updateLightStatus(lightId, null, null,
            // lightService.findByLightId(lightId).getUser().getUserId());
            // lightService.updateLightStatus(lightId, null, null);
        }
    }

    public boolean isConnected(Long cameraId) {
        WebSocketSession session = arduinoSessions.get(cameraId);
        if (session != null && session.isOpen()) {
            return true;
        }
        return false;
    }
}