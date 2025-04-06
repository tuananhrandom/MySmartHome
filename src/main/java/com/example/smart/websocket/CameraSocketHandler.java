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
import java.util.Set;
import java.util.concurrent.*;
import java.util.logging.Level;
import java.util.logging.Logger;

@Component
public class CameraSocketHandler extends BinaryWebSocketHandler {
    private Map<Long, Map<String, CompletableFuture<Boolean>>> pendingResponses = new ConcurrentHashMap<>();

    @Autowired
    private CameraService cameraService;

    private final Logger logger = Logger.getLogger(CameraSocketHandler.class.getName());

    private final Map<Long, WebSocketSession> arduinoSessions = new ConcurrentHashMap<>();
    private final Map<WebSocketSession, Boolean> authenticatedCameras = new ConcurrentHashMap<>();
    private final Map<Long, Set<WebSocketSession>> viewerSessions = new ConcurrentHashMap<>();
    private final Map<WebSocketSession, Long> sessionToCameraId = new ConcurrentHashMap<>();

    // Người dùng phải xác thực trước khi nhận video
    private final Set<WebSocketSession> authenticatedViewers = ConcurrentHashMap.newKeySet();

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        logger.info("WebSocket connected: " + session.getId());
    }

    @Override
    protected void handleBinaryMessage(WebSocketSession session, BinaryMessage message) throws Exception {
        if (!authenticatedCameras.getOrDefault(session, false)) {
            logger.warning("Camera chưa xác thực nhưng gửi binary: " + session.getId());
            session.close(CloseStatus.POLICY_VIOLATION);
            return;
        }

        Long cameraId = sessionToCameraId.get(session);
        if (cameraId != null) {
            broadcastToViewers(cameraId, message.getPayload().array());
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        logger.info("WebSocket disconnected: " + session.getId());
        authenticatedCameras.remove(session);
        authenticatedViewers.remove(session);
        sessionToCameraId.remove(session);

        // Xóa khỏi danh sách viewers nếu có
        viewerSessions.values().forEach(set -> set.remove(session));
        arduinoSessions.values().removeIf(s -> s.equals(session));
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        String payload = message.getPayload();
        System.out.println("Text message: " + payload);

        try {
            if (payload.startsWith("camera:")) {
                handleCameraAuth(session, payload);
            } else if (payload.startsWith("user:")) {
                handleViewerAuth(session, payload);
            } else {
                session.sendMessage(new TextMessage("invalid_format"));
                session.close(CloseStatus.BAD_DATA);
            }
        } catch (Exception e) {
            try {
                session.sendMessage(new TextMessage("internal_error"));
                session.close(CloseStatus.SERVER_ERROR);
            } catch (IOException ioException) {
                logger.warning("Không thể gửi lỗi tới client: " + ioException.getMessage());
            }
            logger.warning("Lỗi trong handleTextMessage: " + e.getMessage());
        }
    }
// gửi yêu cầu cấp mới user và đợi phản hồi từ ESP32
    private void handleCameraAuth(WebSocketSession session, String payload) throws Exception {
        try {
            if (payload.startsWith("response")) {
                handleEspResponse(payload);
            }
            String[] parts = payload.split(":");
            if (parts.length <= 4)
                throw new IllegalArgumentException("Invalid camera auth format");

            Long cameraId = Long.parseLong(parts[0]);
            Integer status = Integer.parseInt(parts[1]);
            String ip = parts[2];
            Long ownerId = Long.parseLong(parts[3]);
            String token = parts[4];

            String secretKey = "12341234";
            String expectedToken = secretKey + cameraId;

            if (!expectedToken.equals(token)) {
                session.sendMessage(new TextMessage("unauthorized_token"));
                session.close(CloseStatus.POLICY_VIOLATION);
                return;
            }

            Camera camera = cameraService.getCameraById(cameraId);
            if (camera == null) {
                session.sendMessage(new TextMessage("camera_not_found"));
                session.close(CloseStatus.BAD_DATA);
                return;
            }

            cameraService.updateCameraStatus(cameraId, status, ip, ownerId);
            arduinoSessions.put(cameraId, session);
            authenticatedCameras.put(session, true);
            sessionToCameraId.put(session, cameraId);

            session.sendMessage(new TextMessage("accepted"));
            logger.info("Camera authenticated: " + cameraId);

        } catch (Exception e) {
            session.sendMessage(new TextMessage("camera_auth_error"));
            session.close(CloseStatus.BAD_DATA);
        }
    }

    private void handleViewerAuth(WebSocketSession session, String payload) throws Exception {
        try {
            String[] parts = payload.split(":");
            if (parts.length != 3)
                throw new IllegalArgumentException("Invalid user auth format");

            Long userId = Long.parseLong(parts[1]);
            Long cameraId = Long.parseLong(parts[2]);

            Camera camera = cameraService.getCameraById(cameraId);
            if (camera == null || camera.getUser() == null || !camera.getUser().getUserId().equals(userId)) {
                session.sendMessage(new TextMessage("unauthorized_access"));
                session.close(CloseStatus.POLICY_VIOLATION);
                return;
            }

            authenticatedViewers.add(session);
            sessionToCameraId.put(session, cameraId);
            viewerSessions.computeIfAbsent(cameraId, k -> ConcurrentHashMap.newKeySet()).add(session);

            session.sendMessage(new TextMessage("viewer_accepted"));
            logger.info("User " + userId + " authorized to view camera " + cameraId);

        } catch (Exception e) {
            session.sendMessage(new TextMessage("viewer_auth_error"));
            session.close(CloseStatus.BAD_DATA);
        }
    }

    private void broadcastToViewers(Long cameraId, byte[] data) {
        Set<WebSocketSession> viewers = viewerSessions.get(cameraId);
        if (viewers != null) {
            viewers.forEach(session -> {
                if (session.isOpen() && authenticatedViewers.contains(session)) {
                    try {
                        session.sendMessage(new BinaryMessage(data));
                    } catch (IOException e) {
                        logger.warning("Gửi video thất bại tới session " + session.getId());
                    }
                }
            });
        }
    }

    public void sendControlSignal(Long cameraId, String controlMessage) throws IOException {
        // Get the session associated with the Arduino ID
        WebSocketSession session = arduinoSessions.get(cameraId);

        if (session != null && session.isOpen()) {
            session.sendMessage(new TextMessage(controlMessage));
        } else {
            System.err.println("No active session found for Arduino ID: " + cameraId);
            // cameraService.updateCameraStatus(cameraId, null, null, null,
            //         cameraService.findBycameraId(cameraId).getUser().getUserId());
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

     // Xử lý phản hồi thay đổi user từ ESP32
     private void handleEspResponse(String payload) {
        // Format: response:cameraId:command:result
        // Ví dụ: response:123:ownerId:accept
        String[] parts = payload.split(":");
        if (parts.length >= 4) {
            Long cameraId = Long.parseLong(parts[1]);
            String command = parts[2];
            String result = parts[3];

            Map<String, CompletableFuture<Boolean>> cameraPromises = pendingResponses.get(cameraId);
            if (cameraPromises != null && cameraPromises.containsKey(command)) {
                CompletableFuture<Boolean> promise = cameraPromises.get(command);
                if ("accept".equals(result)) {
                    promise.complete(true);
                } else {
                    promise.complete(false);
                }

                // Xóa promise đã hoàn thành
                cameraPromises.remove(command);
                if (cameraPromises.isEmpty()) {
                    pendingResponses.remove(cameraId);
                }
            }
        }
    }
}
