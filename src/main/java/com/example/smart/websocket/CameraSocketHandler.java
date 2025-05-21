package com.example.smart.websocket;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.BinaryMessage;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.BinaryWebSocketHandler;

import com.example.smart.entities.Camera;
import com.example.smart.entities.User;
import com.example.smart.services.CameraService;
import com.example.smart.services.DeviceActivityService;
import com.example.smart.services.VideoRecordingService;

import org.springframework.web.socket.TextMessage;
import org.springframework.scheduling.annotation.Scheduled;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.*;

import java.util.logging.Logger;

@Component
public class CameraSocketHandler extends BinaryWebSocketHandler {
    private Map<Long, Map<String, CompletableFuture<Boolean>>> pendingResponses = new ConcurrentHashMap<>();
    @Autowired
    DeviceActivityService deviceActivityService;

    @Autowired
    private CameraService cameraService;

    @Autowired
    private VideoRecordingService videoRecordingService;

    private final Logger logger = Logger.getLogger(CameraSocketHandler.class.getName());

    private final Map<Long, WebSocketSession> arduinoSessions = new ConcurrentHashMap<>();
    private final Map<WebSocketSession, Boolean> authenticatedCameras = new ConcurrentHashMap<>();
    private final Map<Long, Set<WebSocketSession>> viewerSessions = new ConcurrentHashMap<>();
    private final Map<WebSocketSession, Long> sessionToCameraId = new ConcurrentHashMap<>();

    // thêm map theo dõi trạng thái có ghi video hay không của camear
    private final Map<Long, Boolean> isRecording = new ConcurrentHashMap<>();

    // Thêm map để theo dõi thời gian hoạt động cuối cùng của camera
    private final Map<WebSocketSession, Instant> lastActivityTime = new ConcurrentHashMap<>();
    // Thời gian tối đa (giây) camera được phép không có hoạt động
    private static final long MAX_INACTIVE_TIME_SECONDS = 60; // 1 phút

    // Người dùng phải xác thực trước khi nhận video
    private final Set<WebSocketSession> authenticatedViewers = ConcurrentHashMap.newKeySet();

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        logger.info("WebSocket connected: " + session.getId());
        // Ghi nhận thời gian kết nối ban đầu
        lastActivityTime.put(session, Instant.now());
    }

    @Override
    protected void handleBinaryMessage(WebSocketSession session, BinaryMessage message) throws Exception {
        if (!authenticatedCameras.getOrDefault(session, false)) {
            logger.warning("Invalid camera send binary: " + session.getId());
            session.close(CloseStatus.POLICY_VIOLATION);
            return;
        }

        // Cập nhật thời gian hoạt động cuối cùng của camera
        lastActivityTime.put(session, Instant.now());

        Long cameraId = sessionToCameraId.get(session);
        if (cameraId != null) {
            try {
                byte[] data = message.getPayload().array();

                // Gửi dữ liệu đến người xem nếu như trạng thái đang là ghi video
                // và cameraId không phải là null
                broadcastToViewers(cameraId, data);
                if (isRecording.get(cameraId) != null && isRecording.get(cameraId)) {
                    videoRecordingService.handleFrame(cameraId, data);
                }

            } catch (Exception e) {
                // Ghi log lỗi nhưng không đóng session camera
                logger.warning("Error binary processing " + cameraId + ": " + e.getMessage());
                // KHÔNG đóng session camera ở đây để duy trì kết nối
            }
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        logger.info("WebSocket disconnected: " + session.getId() + " status: " + status);

        // Lấy camera ID trước khi xóa mapping
        Long cameraId = sessionToCameraId.get(session);

        // Kiểm tra xem đây là session của camera hay người xem
        boolean isCamera = authenticatedCameras.containsKey(session);
        boolean isViewer = authenticatedViewers.contains(session);

        if (isCamera) {
            logger.info("Camera session đã đóng: " + session.getId() + " cho camera: " + cameraId);

            // Chỉ cập nhật trạng thái camera nếu cameraId tồn tại
            if (cameraId != null) {
                try {
                    Camera camera = cameraService.findById(cameraId);
                    if (camera != null && camera.getUser() != null) {
                        cameraService.updateCameraStatus(cameraId, 0, null, camera.getUser().getUserId());
                        logger.info("Updated camera " + cameraId + " to 0 (offline)");

                        // gọi ermergency save video
                        try {
                            if (isRecording.get(cameraId) != null && isRecording.get(cameraId)) {
                                videoRecordingService.saveVideoEmergency(cameraId);
                            }
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                        // ghi log mất kết nối camera
                        deviceActivityService.logCameraActivity(cameraId, "DISCONNECT", null, null, null,
                                camera.getOwnerId());
                    } else {
                        logger.warning("Camera is not working: " + cameraId);
                    }
                } catch (Exception e) {
                    logger.warning("Error : " + e.getMessage());
                }
            }

            // Xóa session camera khỏi các map
            authenticatedCameras.remove(session);
            arduinoSessions.values().removeIf(s -> s.equals(session));

        } else if (isViewer) {
            logger.info("Viewer close session " + session.getId() + " for camera: " + cameraId);

            // Đối với người xem, chỉ cần xóa khỏi danh sách người xem
            authenticatedViewers.remove(session);

            // Xóa khỏi danh sách viewers nếu có
            if (cameraId != null && viewerSessions.containsKey(cameraId)) {
                viewerSessions.get(cameraId).remove(session);
                logger.info("Delete viewer from watching " + cameraId);
            }
        }

        // Xóa mapping session-to-cameraId
        sessionToCameraId.remove(session);

        // Xóa thời gian hoạt động
        lastActivityTime.remove(session);
    }

    // Scheduled task chạy mỗi 30 giây để kiểm tra kết nối camera không hoạt động
    @Scheduled(fixedRate = 30000)
    public void checkInactiveCameraSessions() {
        logger.info("Checking all inactive camera...");
        Instant now = Instant.now();

        // Tập hợp các session cần đóng
        Set<WebSocketSession> sessionsToClose = ConcurrentHashMap.newKeySet();

        lastActivityTime.forEach((session, lastActive) -> {
            // Kiểm tra nếu đây là session camera và không hoạt động quá thời gian quy định
            if (authenticatedCameras.containsKey(session)) {
                long inactiveSeconds = ChronoUnit.SECONDS.between(lastActive, now);

                if (inactiveSeconds > MAX_INACTIVE_TIME_SECONDS) {
                    Long cameraId = sessionToCameraId.get(session);
                    logger.warning("Camera " + cameraId + " is not working in " + inactiveSeconds
                            + " seconds. Close connection.");
                    sessionsToClose.add(session);
                }
            }
        });

        // Đóng các session không hoạt động
        for (WebSocketSession session : sessionsToClose) {
            try {
                Long cameraId = sessionToCameraId.get(session);
                if (cameraId != null) {
                    // Lưu video khẩn cấp TRƯỚC KHI cập nhật trạng thái camera
                    try {
                        logger.info("====> saveVideoEmergency camera " + cameraId);
                        if (isRecording.get(cameraId) != null && isRecording.get(cameraId)) {
                            videoRecordingService.saveVideoEmergency(cameraId);
                        }

                    } catch (Exception e) {
                        logger.severe("Error saveVideoEmergency cho camera " + cameraId + ": " + e.getMessage());
                        e.printStackTrace();
                    }

                    // Cập nhật trạng thái camera thành offline
                    try {
                        Camera camera = cameraService.findById(cameraId);
                        if (camera != null && camera.getUser() != null) {
                            cameraService.updateCameraStatus(cameraId, 0, null, camera.getUser().getUserId());
                            logger.info("Heartbeat: camera status update " + cameraId + " thành 0 (offline)");
                        }
                    } catch (Exception e) {
                        logger.warning("Error heartbeat: " + e.getMessage());
                    }
                }

                // Đóng session và gọi hàm afterConnectionClosed
                session.close(CloseStatus.GOING_AWAY.withReason("Inactive camera connection"));

                // Xóa thủ công khỏi các map để đảm bảo dọn dẹp
                sessionToCameraId.remove(session);
                authenticatedCameras.remove(session);
                lastActivityTime.remove(session);
                arduinoSessions.values().removeIf(s -> s.equals(session));

                logger.info("Close camera session: " + session.getId());
            } catch (IOException e) {
                logger.warning("error while closing camera session: " + e.getMessage());
            }
        }

        if (sessionsToClose.isEmpty()) {
            logger.info("no inactive camera session found");
        } else {
            logger.info("Closed " + sessionsToClose.size() + " inactive camera sessions");
        }
    }

    // Phương thức gọi từ các service khác để kiểm tra trạng thái camera
    public boolean isCameraConnected(Long cameraId) {
        return arduinoSessions.containsKey(cameraId) &&
                arduinoSessions.get(cameraId).isOpen();
    }

    // Phương thức để ping tất cả camera để kiểm tra trạng thái
    @Scheduled(fixedRate = 20000) // 20 giây ping một lần
    public void pingAllCameras() {
        logger.info("ping all cameras...");
        arduinoSessions.forEach((cameraId, session) -> {
            if (session.isOpen()) {
                try {
                    // Gửi lệnh ping đơn giản để kiểm tra kết nối
                    session.sendMessage(new TextMessage("ping"));
                    logger.fine("Đã ping camera " + cameraId);
                } catch (IOException e) {
                    logger.warning("cant ping camera " + cameraId + ": " + e.getMessage());
                    // Nếu không gửi được ping, đánh dấu là không hoạt động
                    lastActivityTime.put(session,
                            Instant.now().minus(MAX_INACTIVE_TIME_SECONDS + 1, ChronoUnit.SECONDS));
                }
            }
        });
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        String payload = message.getPayload();
        logger.info("Text message: " + payload);

        // Cập nhật thời gian hoạt động khi nhận được tin nhắn text
        lastActivityTime.put(session, Instant.now());

        try {
            if (payload.startsWith("camera:")) {
                handleCameraAuth(session, payload);
            } else if (payload.startsWith("user:")) {
                handleViewerAuth(session, payload);
            } else if (payload.startsWith("response:")) {
                // xử lý cấp mới người dùng trong ESP32
                handleEspResponse(payload);
            } else if (payload.equals("pong")) {
                // Camera trả về pong sau khi nhận ping
                logger.fine("receive pong from " + session.getId());
            } else if (payload.startsWith("userControl:") && payload.contains(":control")
                    && payload.contains(":Rotate:")) {
                handleRotateCamera(payload);
            } else {
                try {
                    synchronized (session) {
                        session.sendMessage(new TextMessage("invalid_format"));
                        session.close(CloseStatus.BAD_DATA);
                    }
                } catch (IOException e) {
                    logger.warning("Error sending invalid_format message: " + e.getMessage());
                }
            }
        } catch (Exception e) {
            try {
                synchronized (session) {
                    session.sendMessage(new TextMessage("internal_error"));
                    session.close(CloseStatus.SERVER_ERROR);
                }
            } catch (IOException ioException) {
                logger.warning("Cant send error: " + ioException.getMessage());
            }
            logger.warning("error handleTextMessage: " + e.getMessage());
        }
    }

    private void handleCameraAuth(WebSocketSession session, String payload) throws Exception {
        // camera:1:0:192.168.1.26:123412341
        try {
            if (payload.startsWith("response")) {
                handleEspResponse(payload);
                return;
            }
            String[] parts = payload.split(":");
            if (parts.length <= 4)
                throw new IllegalArgumentException("Invalid camera auth format");

            Long cameraId = Long.parseLong(parts[1]);
            Integer status = Integer.parseInt(parts[2]);
            String ip = parts[3];
            Long ownerId = Long.parseLong(parts[4]);
            String token = parts[5];

            String secretKey = "12341234";
            String expectedToken = secretKey + cameraId.toString();

            if (!expectedToken.equals(token)) {
                synchronized (session) {
                    session.sendMessage(new TextMessage("unauthorized_token"));
                    session.close(CloseStatus.POLICY_VIOLATION);
                }
                return;
            }

            Camera camera = cameraService.getCameraById(cameraId);
            if (camera == null) {
                synchronized (session) {
                    session.sendMessage(new TextMessage("camera_not_found"));
                    session.close(CloseStatus.BAD_DATA);
                }
                return;
            }

            // Lưu thông tin session trước
            arduinoSessions.put(cameraId, session);
            authenticatedCameras.put(session, true);
            sessionToCameraId.put(session, cameraId);

            // Kiểm tra user mới đã được thêm vào ESP chưa, nếu chưa thì cập nhật trước.
            Camera thisCamera = cameraService.getCameraById(cameraId);
            if (thisCamera == null) {
                synchronized (session) {
                    session.sendMessage(new TextMessage("camera_not_found"));
                    session.close(CloseStatus.BAD_DATA);
                }
                return;
            }

            // Nếu camera đã có chủ sở hữu và ownerId khác với chủ sở hữu hiện tại
            if (thisCamera.getUser() != null && !thisCamera.getUser().getUserId().equals(ownerId)) {
                // Gửi lệnh cập nhật ownerId mới cho ESP32
                sendControlSignal(cameraId, "ownerId:" + thisCamera.getUser().getUserId());
                // Cập nhật trạng thái camera với chủ sở hữu hiện tại
                cameraService.updateCameraStatus(cameraId, status, ip, thisCamera.getUser().getUserId());
            }
            // Nếu camera chưa có chủ sở hữu và ownerId khác -1
            else if (thisCamera.getUser() == null && ownerId != -1) {
                // Gửi lệnh reset ownerId về -1 cho ESP32
                sendControlSignal(cameraId, "ownerId:" + -1);
                // Cập nhật trạng thái camera với ownerId null
                cameraService.updateCameraStatus(cameraId, status, ip, null);
            }
            // Trường hợp còn lại: camera chưa có chủ và ownerId là -1, hoặc ownerId khớp
            // với chủ hiện tại
            else {
                cameraService.updateCameraStatus(cameraId, status, ip, ownerId);
            }

            // Cập nhật trạng thái ghi hình
            Boolean isThisCameraRecord = thisCamera.getIsRecord();
            isRecording.put(cameraId, isThisCameraRecord);

            // Cập nhật trạng thái camera thành online
            cameraService.updateCameraStatus(cameraId, 1, ip, ownerId);

            // Ghi log kết nối thành công
            deviceActivityService.logCameraActivity(cameraId, "CONNECT", null, 1, ip, ownerId);

            synchronized (session) {
                session.sendMessage(new TextMessage("accepted"));
            }
            logger.info("Camera authenticated: " + cameraId);

        } catch (Exception e) {
            logger.warning("Camera auth error: " + e.getMessage());
            synchronized (session) {
                session.sendMessage(new TextMessage("camera_auth_error"));
                session.close(CloseStatus.BAD_DATA);
            }
        }
    }

    private void handleViewerAuth(WebSocketSession session, String payload) throws Exception {
        try {
            String[] parts = payload.split(":");
            if (parts.length != 3)
                throw new IllegalArgumentException("Invalid user auth format");

            Long userId = Long.parseLong(parts[1]);
            Long cameraId = Long.parseLong(parts[2]);
            if (cameraId != null) {
                Camera camera = cameraService.getCameraById(cameraId);
                if (camera == null) {
                    synchronized (session) {
                        session.sendMessage(new TextMessage("camera_not_found"));
                        session.close(CloseStatus.POLICY_VIOLATION);
                    }
                    return;
                }

                // Kiểm tra quyền xem camera
                boolean hasPermission = false;

                // Kiểm tra nếu là chủ sở hữu
                if (camera.getUser() != null && camera.getUser().getUserId().equals(userId)) {
                    hasPermission = true;
                }

                // Kiểm tra nếu là người dùng được chia sẻ
                if (!hasPermission) {
                    Set<User> sharedUsers = camera.getSharedUsers();
                    if (sharedUsers != null) {
                        hasPermission = sharedUsers.stream()
                                .anyMatch(user -> user.getUserId().equals(userId));
                    }
                }

                if (!hasPermission) {
                    synchronized (session) {
                        session.sendMessage(new TextMessage("unauthorized_access"));
                        session.close(CloseStatus.POLICY_VIOLATION);
                    }
                    return;
                }

                // Kiểm tra và xóa session cũ của người dùng này nếu có
                if (sessionToCameraId.containsKey(session)) {
                    Long oldCameraId = sessionToCameraId.get(session);
                    if (oldCameraId != null && viewerSessions.containsKey(oldCameraId)) {
                        viewerSessions.get(oldCameraId).remove(session);
                        logger.info("Removed user " + userId + " from old camera " + oldCameraId);
                    }
                }

                authenticatedViewers.add(session);
                sessionToCameraId.put(session, cameraId);
                viewerSessions.computeIfAbsent(cameraId, k -> ConcurrentHashMap.newKeySet()).add(session);

                synchronized (session) {
                    session.sendMessage(new TextMessage("viewer_accepted"));
                }
                logger.info("User " + userId + " authorized to view camera " + cameraId);
            }
        } catch (Exception e) {
            logger.warning("Lỗi xác thực người xem: " + e.getMessage());
            try {
                synchronized (session) {
                    session.sendMessage(new TextMessage("viewer_auth_error"));
                    session.close(CloseStatus.BAD_DATA);
                }
            } catch (IOException ex) {
                logger.warning("Không thể đóng session lỗi: " + ex.getMessage());
            }
        }
    }

    private void broadcastToViewers(Long cameraId, byte[] data) {
        Set<WebSocketSession> viewers = viewerSessions.get(cameraId);
        if (viewers != null) {
            Set<WebSocketSession> toRemove = ConcurrentHashMap.newKeySet();

            viewers.forEach(session -> {
                if (session.isOpen() && authenticatedViewers.contains(session)) {
                    try {
                        // Tạo binary message mới cho mỗi session để tránh vấn đề đồng thời
                        BinaryMessage binaryMessage = new BinaryMessage(data);
                        session.sendMessage(binaryMessage);
                    } catch (IOException e) {
                        logger.warning("Lỗi gửi video đến session " + session.getId() + ": " + e.getMessage());
                        // Đánh dấu session để xóa sau này
                        toRemove.add(session);
                    }
                } else if (!session.isOpen()) {
                    // Đánh dấu session đã đóng để xóa
                    toRemove.add(session);
                    logger.info("Đánh dấu session đã đóng để xóa: " + session.getId());
                }
            });

            // Xóa các session đã đánh dấu
            if (!toRemove.isEmpty()) {
                viewers.removeAll(toRemove);
                for (WebSocketSession session : toRemove) {
                    logger.info("Xóa viewer session đã đóng: " + session.getId());
                    authenticatedViewers.remove(session);
                    sessionToCameraId.remove(session);
                }
            }
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
            // cameraService.findBycameraId(cameraId).getUser().getUserId());
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
        System.out.println(payload);
        // Format: response:cameraId:command:result:token
        // Ví dụ: response:123:ownerId:accept:123412341
        String[] parts = payload.split(":");
        if (parts.length >= 4) {
            Long cameraId = Long.parseLong(parts[1]);
            String command = parts[2];
            String result = parts[3];
            String token = parts[4];
            String secretKey = "12341234";
            String authToken = secretKey + cameraId.toString();
            if (token.equals(authToken)) {
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
            } else {
                System.err.println("wrong token");
            }
        }
    }

    // Xử lý lệnh xoay camera
    private void handleRotateCamera(String payload) throws IOException {
        try {
            // Format: user:{userId}:control{cameraId}:Rotate:{direction}
            String[] parts = payload.split(":");
            if (parts.length < 5) {
                logger.warning("Định dạng lệnh xoay camera không hợp lệ: " + payload);
                return;
            }

            Long userId = Long.parseLong(parts[1]);
            String controlPart = parts[2];
            Long cameraId = null;

            // Trích xuất cameraId từ chuỗi "control{cameraId}"
            if (controlPart.startsWith("control")) {
                String cameraIdStr = controlPart.substring(7); // Lấy phần sau "control"
                cameraId = Long.parseLong(cameraIdStr);
            } else {
                logger.warning("Định dạng phần control không hợp lệ: " + controlPart);
                return;
            }

            String direction = parts[4].toLowerCase(); // Chuyển direction thành chữ thường

            // Kiểm tra quyền sở hữu camera
            Camera camera = cameraService.getCameraById(cameraId);
            if (camera == null || camera.getUser() == null || !camera.getUser().getUserId().equals(userId)) {
                logger.warning("Người dùng " + userId + " không có quyền điều khiển camera " + cameraId);
                return;
            }

            // Kiểm tra camera có đang kết nối không
            if (!arduinoSessions.containsKey(cameraId) || !arduinoSessions.get(cameraId).isOpen()) {
                logger.warning("Camera " + cameraId + " không trực tuyến");
                return;
            }

            // Gửi lệnh xoay đến ESP32
            sendControlSignal(cameraId, "Rotate:" + direction);
            logger.info("Đã gửi lệnh xoay " + direction + " đến camera " + cameraId);
        } catch (Exception e) {
            logger.warning("Lỗi xử lý lệnh xoay camera: " + e.getMessage());
        }
    }

    public void checkCameraRecordStatus(Long cameraId, Boolean status) {
        Boolean oldStatus = isRecording.get(cameraId);
        if (!isRecording.containsKey(cameraId)) {
            isRecording.put(cameraId, false);
            return;
        }
        isRecording.put(cameraId, status);
        // Sử dụng Boolean.TRUE.equals để tránh NullPointerException
        if (Boolean.TRUE.equals(oldStatus) && Boolean.FALSE.equals(status)) {
            try {
                videoRecordingService.saveVideoEmergency(cameraId);
                // ghi log
                deviceActivityService.logCameraActivity(cameraId, "STOP_RECORDING", null, null, null,
                        cameraService.getCameraById(cameraId).getUser().getUserId());
            } catch (Exception e) {
                logger.warning("Lỗi khi lưu video khẩn cấp: " + e.getMessage());
            }
        } else if (Boolean.FALSE.equals(oldStatus) && Boolean.TRUE.equals(status)) {
            // ghi log bật camera
            deviceActivityService.logCameraActivity(cameraId, "START_RECORDING", null, null, null,
                    cameraService.getCameraById(cameraId).getUser().getUserId());
        }
    }
}
