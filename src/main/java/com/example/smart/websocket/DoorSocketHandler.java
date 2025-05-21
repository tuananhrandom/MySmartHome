package com.example.smart.websocket;

import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.Objects;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import com.example.smart.entities.Camera;
import com.example.smart.entities.DeviceActivity;
import com.example.smart.entities.Door;
import com.example.smart.entities.User;
import com.example.smart.services.CameraService;
import com.example.smart.services.DeviceActivityService;
import com.example.smart.services.DoorServicesImp;
import com.example.smart.services.EmailService;
import com.example.smart.services.NotificationService;
import com.example.smart.services.UserService;

@Component
public class DoorSocketHandler extends TextWebSocketHandler {
    @Autowired
    EmailService emailService;
    @Autowired
    DoorServicesImp doorService;
    @Autowired
    DeviceActivityService deviceActivityService;
    @Autowired
    NotificationService notificationService;
    @Autowired
    CameraService cameraService;

    // Map để theo dõi trạng thái kết nối của từng cửa
    private Map<Long, Boolean> doorConnectionStatus = new ConcurrentHashMap<>();

    private Map<Long, Map<String, CompletableFuture<Boolean>>> pendingResponses = new ConcurrentHashMap<>();

    // Map to store WebSocket sessions with Arduino IDs as keys
    private Map<Long, WebSocketSession> arduinoSessions = new ConcurrentHashMap<>();

    // Thêm map để theo dõi thời gian hoạt động cuối cùng của cửa
    private final Map<WebSocketSession, Instant> lastActivityTime = new ConcurrentHashMap<>();
    // Thời gian tối đa (giây) cửa được phép không có hoạt động
    private static final long MAX_INACTIVE_TIME_SECONDS = 60; // 1 phút

    private Map<Long, Instant> lastUpdateTime = new ConcurrentHashMap<>();
    private static final long UPDATE_THRESHOLD_MS = 100; // 100ms

    private Map<Long, DoorState> lastDoorState = new ConcurrentHashMap<>();

    private static class DoorState {
        Integer doorStatus;
        Integer doorLockDown;
        String doorIp;

        public DoorState(Integer doorStatus, Integer doorLockDown, String doorIp) {
            this.doorStatus = doorStatus;
            this.doorLockDown = doorLockDown;
            this.doorIp = doorIp;
        }

        public boolean equals(DoorState other) {
            if (other == null)
                return false;
            return Objects.equals(this.doorStatus, other.doorStatus) &&
                    Objects.equals(this.doorLockDown, other.doorLockDown) &&
                    Objects.equals(this.doorIp, other.doorIp);
        }
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        System.out.println("a Door Connected");
        // Ghi nhận thời gian kết nối ban đầu
        lastActivityTime.put(session, Instant.now());
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

            try {
                // Lấy thông tin user trước khi cập nhật
                Door door = doorService.findByDoorId(disconnectedDoorId);
                if (door != null && door.getUser() != null) {
                    Long userId = door.getUser().getUserId();

                    // Cập nhật doorIp và doorStatus thành null khi mất kết nối
                    doorService.updateDoorStatus(disconnectedDoorId, null, null, null, userId);

                    // Ghi log thiết bị đã bị mất kết nối
                    // deviceActivityService.logDoorActivity(disconnectedDoorId, "DISCONNECT", null,
                    // null, null, null,
                    // null, null, null, userId);

                    System.out.println(
                            "Connection closed for Door ID: " + disconnectedDoorId
                                    + ". Door status and IP set to null.");

                    // Cập nhật trạng thái kết nối của cửa này thành false
                    doorConnectionStatus.put(disconnectedDoorId, false);
                }
            } catch (Exception e) {
                System.err.println("Error during connection closure: " + e.getMessage());
            }
        }

        // Xóa thời gian hoạt động
        lastActivityTime.remove(session);
    }
    // chuỗi gửi lên có dạng : doorId:doorStatus:doorLockDown:doorIp:ownerId:token

    @Override
    public void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        String payload = message.getPayload();

        // Cập nhật thời gian hoạt động khi nhận được tin nhắn
        lastActivityTime.put(session, Instant.now());

        // Kiểm tra nếu đây là phản hồi từ ESP32 về yêu cầu thay đổi owner
        if (payload.startsWith("response:")) {
            handleEspResponse(payload);
            return;
        }

        // Kiểm tra nếu đây là phản hồi "pong" từ ESP32
        if (payload.equals("pong")) {
            System.out.println("Received pong from door session: " + session.getId());
            return;
        }

        if (payload.startsWith("alerted:")) {
            handleAlert(payload);
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

        // Kiểm tra thời gian từ lần cập nhật cuối
        Instant now = Instant.now();
        Instant lastUpdate = lastUpdateTime.get(doorId);
        if (lastUpdate != null &&
                ChronoUnit.MILLIS.between(lastUpdate, now) < UPDATE_THRESHOLD_MS) {
            System.out.println("Bỏ qua cập nhật cho cửa " + doorId + " - Quá sớm sau lần cập nhật trước");
            return true; // Bỏ qua cập nhật nếu quá sớm
        }

        lastUpdateTime.put(doorId, now);

        System.out.println(
                "Received message from Door ID: " + doorId + ", Status: " + doorStatus + " , LockDown:" + doorLockDown
                        + ", IP: " + doorIp + " , Token: " + authToken);
        if (doorService.idIsExist(doorId) && token.equals(token)) {
            System.out.println("Chap nhan ket noi tu: " + doorId);
            arduinoSessions.put(doorId, session);

            Door thisDoor = doorService.findByDoorId(doorId);
            // Kiểm tra user mới đã được thêm vào ESP chưa, nếu chưa thì cập nhật trước.
            if (thisDoor.getUser() != null && !thisDoor.getUser().getUserId().equals(ownerId)) {
                try {
                    sendControlSignal(doorId, "ownerId:" + thisDoor.getUser().getUserId());
                } catch (IOException err) {
                    System.err.println("error while sending to door device");
                }
                doorService.updateDoorStatus(doorId, doorStatus, doorLockDown, doorIp, thisDoor.getUser().getUserId());
            } else if (thisDoor.getUser() == null && !Long.valueOf(-1).equals(ownerId)) {
                try {
                    sendControlSignal(doorId, "ownerId:" + -1);
                } catch (Exception e) {
                    System.err.println("error while sending to door device");
                }
            } else {
                doorService.updateDoorStatus(doorId, doorStatus, doorLockDown, doorIp, ownerId);
            }

            // Kiểm tra xem cửa này đã được đánh dấu là kết nối chưa
            Boolean isConnected = doorConnectionStatus.getOrDefault(doorId, false);
            if (!isConnected) {
                // Ghi log kết nối thành công
                deviceActivityService.logDoorActivity(doorId, "CONNECT", null, null, null, null, null, null, doorIp,
                        ownerId);

                // Cập nhật trạng thái kết nối
                doorConnectionStatus.put(doorId, true);
                System.out.println("Door " + doorId + " connected successfully.");
            }

            // Kiểm tra trạng thái có thay đổi không
            DoorState currentState = new DoorState(doorStatus, doorLockDown, doorIp);
            DoorState lastState = lastDoorState.get(doorId);

            if (lastState != null && lastState.equals(currentState)) {
                System.out.println("Bỏ qua cập nhật cho cửa " + doorId + " - Trạng thái không thay đổi");
                return true; // Bỏ qua nếu trạng thái không thay đổi
            }

            lastDoorState.put(doorId, currentState);

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
            // Đảm bảo format lệnh doorLockDown đúng
            if (controlMessage.startsWith("doorLockDown:")) {
                System.out.println("Sending doorLockDown command to door " + doorId + ": " + controlMessage);
            }
            session.sendMessage(new TextMessage(controlMessage));
        } else {
            System.err.println("No active session found for Arduino ID: " + doorId);
            try {
                Door door = doorService.findByDoorId(doorId);
                if (door != null && door.getUser() != null) {
                    Long userId = door.getUser().getUserId();
                    doorService.updateDoorStatus(doorId, null, null, null, userId);

                    // Ghi log thiết bị đã bị mất kết nối
                    deviceActivityService.logDoorActivity(doorId, "DISCONNECT", null, null, null, null, null, null,
                            null, userId);

                    // Cập nhật trạng thái kết nối
                    doorConnectionStatus.put(doorId, false);
                }
            } catch (Exception e) {
                System.err.println("Error updating door status: " + e.getMessage());
            }
        }
    }

    // Xử lý phản hồi từ ESP32
    private void handleEspResponse(String payload) {
        // Format: response:doorId:command:result
        // Ví dụ: response:123:ownerId:accept
        String[] parts = payload.split(":");
        if (parts.length >= 4) {
            Long doorId = Long.parseLong(parts[1]);
            String command = parts[2];
            String result = parts[3];

            Map<String, CompletableFuture<Boolean>> doorPromises = pendingResponses.get(doorId);
            if (doorPromises != null && doorPromises.containsKey(command)) {
                CompletableFuture<Boolean> promise = doorPromises.get(command);
                if ("accept".equals(result)) {
                    promise.complete(true);
                } else {
                    promise.complete(false);
                }

                // Xóa promise đã hoàn thành
                doorPromises.remove(command);
                if (doorPromises.isEmpty()) {
                    pendingResponses.remove(doorId);
                }
            }
        }
    }

    private void handleAlert(String payload) {
        LocalDateTime now = LocalDateTime.now(); // thời điểm hiện tại
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("HH:mm:ss dd/MM/yyyy"); // đúng format
        String formattedDateTime = now.format(formatter);
        String[] parts = payload.split(":");
        System.out.println("doorSaid:" + payload);
        if (parts.length >= 1) {
            Long doorId = Long.parseLong(parts[1]);

            // Cập nhật trạng thái cảnh báo ngay lập tức
            doorService.updateDoorAlert(doorId, 1);
            // khởi động chế độ ghi hình của các camera ngay lập tức.
            Long ownerId = doorService.findByDoorId(doorId).getUser().getUserId();
            cameraService.startRecordingAllCameraByDoor(ownerId);
            ;

            try {
                // Lấy thông tin cửa và người dùng
                Door thisDoor = doorService.findByDoorId(doorId);
                if (thisDoor != null && thisDoor.getUser() != null) {
                    User thisUser = thisDoor.getUser();

                    // Ghi log hoạt động
                    deviceActivityService.logDoorActivity(doorId, "WARNING", null, null, null, null, null, null, null,
                            thisDoor.getOwnerId());

                    // Tạo thông báo
                    notificationService.createNotification(
                            "DOOR",
                            "Cảnh báo đột nhập",
                            "Cửa " + thisDoor.getDoorName() + " đã bị mở vào lúc " + formattedDateTime,
                            thisUser.getUserId());

                    System.out.println("Cửa " + doorId + " đã kích hoạt cảnh báo!");

                    // Chuẩn bị thông tin email
                    String email = thisUser.getEmail();
                    String subject = "Cảnh báo đột nhập qua cửa thông minh: " + thisDoor.getDoorName();
                    String body = "Xin chào " + thisUser.getFullName() + ",\n\n"
                            + "Cửa thông minh " + thisDoor.getDoorName() + " đã bị mở vào lúc " + formattedDateTime
                            + ".\n"
                            + "Vui lòng kiểm tra ngay lập tức để đảm bảo an toàn.\n\n"
                            + "Trân trọng,\n"
                            + "Đội ngũ Smart Home";

                    // Gửi email bất đồng bộ - không chặn luồng xử lý chính
                    emailService.sendEmailAsync(email, subject, body)
                            .exceptionally(ex -> {
                                System.err.println(
                                        "Lỗi khi gửi email cảnh báo cho cửa " + doorId + ": " + ex.getMessage());
                                return null;
                            });

                    System.out.println("Sent Email for Door " + doorId);
                }
            } catch (Exception e) {
                System.err.println("Error while notify Door " + doorId + ": " + e.getMessage());
            }
        }
    }

    // Scheduled task chạy mỗi 30 giây để kiểm tra kết nối cửa không hoạt động
    @Scheduled(fixedRate = 30000)
    public void checkInactiveDoorSessions() {
        System.out.println("Checking inactive door...");
        Instant now = Instant.now();

        // Tập hợp các session cần đóng
        Set<WebSocketSession> sessionsToClose = ConcurrentHashMap.newKeySet();

        lastActivityTime.forEach((session, lastActive) -> {
            long inactiveSeconds = ChronoUnit.SECONDS.between(lastActive, now);

            if (inactiveSeconds > MAX_INACTIVE_TIME_SECONDS) {
                // Tìm ID của cửa tương ứng với session
                Long doorId = null;
                for (Map.Entry<Long, WebSocketSession> entry : arduinoSessions.entrySet()) {
                    if (entry.getValue().equals(session)) {
                        doorId = entry.getKey();
                        break;
                    }
                }

                if (doorId != null) {
                    System.out.println("Door " + doorId + " isnt active for " + inactiveSeconds
                            + " second. Close connection.");
                    sessionsToClose.add(session);
                }
            }
        });

        // Đóng các session không hoạt động
        for (WebSocketSession session : sessionsToClose) {
            try {
                // Tìm ID của cửa tương ứng với session
                Long doorId = null;
                for (Map.Entry<Long, WebSocketSession> entry : arduinoSessions.entrySet()) {
                    if (entry.getValue().equals(session)) {
                        doorId = entry.getKey();
                        break;
                    }
                }

                if (doorId != null) {
                    try {
                        // Cập nhật trạng thái cửa thành offline
                        Door door = doorService.findByDoorId(doorId);
                        if (door != null && door.getUser() != null) {
                            Long userId = door.getUser().getUserId();
                            doorService.updateDoorStatus(doorId, null, null, null, userId);

                            // Ghi log thiết bị đã bị mất kết nối
                            deviceActivityService.logDoorActivity(doorId, "DISCONNECT", null, null, null, null, null,
                                    null, null, userId);

                            System.out.println("Heartbeat: updated door status " + doorId + " to offline");

                            // Cập nhật trạng thái kết nối
                            doorConnectionStatus.put(doorId, false);
                        }
                    } catch (Exception e) {
                        System.err.println("Error while notify door: " + e.getMessage());
                    }
                }

                // Đóng session
                session.close(CloseStatus.GOING_AWAY.withReason("Inactive door connection"));

                // Xóa khỏi các map
                lastActivityTime.remove(session);

                System.out.println("Closed inactive session for door ID: " + doorId);
            } catch (IOException e) {
                System.err.println("Error while close session " + e.getMessage());
            }
        }

        if (sessionsToClose.isEmpty()) {
            System.out.println("No inactive door.");
        } else {
            System.out.println("Closed " + sessionsToClose.size() + " inactive door session(s).");
        }
    }

    // Phương thức để ping tất cả cửa để kiểm tra trạng thái
    @Scheduled(fixedRate = 20000) // 20 giây ping một lần
    public void pingAllDoors() {
        System.out.println("Ping all door...");
        arduinoSessions.forEach((doorId, session) -> {
            if (session.isOpen()) {
                try {
                    // Gửi lệnh ping đơn giản để kiểm tra kết nối
                    session.sendMessage(new TextMessage("ping"));
                    System.out.println("Ping door " + doorId);
                } catch (IOException e) {
                    System.err.println("Cant ping door " + doorId + ": " + e.getMessage());
                    // Nếu không gửi được ping, đánh dấu là không hoạt động
                    lastActivityTime.put(session,
                            Instant.now().minus(MAX_INACTIVE_TIME_SECONDS + 1, ChronoUnit.SECONDS));
                }
            }
        });
    }

    // Phương thức để kiểm tra kết nối cửa
    public boolean isDoorConnected(Long doorId) {
        // Kiểm tra trong bản đồ trạng thái kết nối trước
        Boolean status = doorConnectionStatus.get(doorId);
        if (status != null && status) {
            // Kiểm tra thêm xem phiên làm việc có tồn tại và đang mở không
            WebSocketSession session = arduinoSessions.get(doorId);
            return (session != null && session.isOpen());
        }
        return false;
    }
}