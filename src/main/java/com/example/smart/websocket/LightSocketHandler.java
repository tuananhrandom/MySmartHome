package com.example.smart.websocket;

import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import com.example.smart.entities.Light;
import com.example.smart.services.DeviceActivityService;
import com.example.smart.services.LightServicesImp;

@Component
public class LightSocketHandler extends TextWebSocketHandler {
    // Map để theo dõi trạng thái kết nối của từng đèn
    private Map<Long, Boolean> lightConnectionStatus = new ConcurrentHashMap<>();

    @Autowired
    LightServicesImp lightService;
    @Autowired
    DeviceActivityService deviceActivityService;

    // Map to store WebSocket sessions with Arduino IDs as keys
    private Map<Long, WebSocketSession> arduinoSessions = new ConcurrentHashMap<>();

    // Map để lưu trữ các promise đang chờ phản hồi từ ESP32
    private Map<Long, Map<String, CompletableFuture<Boolean>>> pendingResponses = new ConcurrentHashMap<>();

    // Thêm map để theo dõi thời gian hoạt động cuối cùng của đèn
    private final Map<WebSocketSession, Instant> lastActivityTime = new ConcurrentHashMap<>();
    // Thời gian tối đa (giây) đèn được phép không có hoạt động
    private static final long MAX_INACTIVE_TIME_SECONDS = 60; // 1 phút

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        System.out.println("a Light Connected");
        // Ghi nhận thời gian kết nối ban đầu
        lastActivityTime.put(session, Instant.now());
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

            try {
                // Lấy thông tin user trước khi cập nhật
                Light light = lightService.findByLightId(disconnectedLightId);
                if (light != null && light.getUser() != null) {
                    Long userId = light.getUser().getUserId();

                    // Cập nhật lightIp và lightStatus thành null khi mất kết nối
                    lightService.updateLightStatus(disconnectedLightId, null, null, userId);

                    // ghi log thiết bị đã bị mất kết nối
                    deviceActivityService.logLightActivity(disconnectedLightId, "DISCONNECT", null, null, null, userId);

                    System.out.println(
                            "Connection closed for Light ID: " + disconnectedLightId
                                    + ". Light status and IP set to null.");

                    // Cập nhật trạng thái kết nối của đèn này thành false
                    lightConnectionStatus.put(disconnectedLightId, false);
                }
            } catch (Exception e) {
                System.err.println("Error during connection closure: " + e.getMessage());
            }
        }

        // Xóa thời gian hoạt động
        lastActivityTime.remove(session);
    }

    @Override
    public void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        String payload = message.getPayload();
        System.out.println("payload: " + payload);

        // Cập nhật thời gian hoạt động khi nhận được tin nhắn
        lastActivityTime.put(session, Instant.now());

        // Kiểm tra nếu đây là phản hồi từ ESP32 về yêu cầu thay đổi owner
        if (payload.startsWith("response:")) {
            handleEspResponse(payload);
            return;
        }

        // Kiểm tra nếu đây là phản hồi "pong" từ ESP32
        if (payload.equals("pong")) {
            System.out.println("Received pong from light session: " + session.getId());
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

            // Kiểm tra xem đèn này đã kết nối chưa
            if (lightConnectionStatus.getOrDefault(lightId, false) == false) {
                // cập nhật Log trước khi chuyển trạng thái
                deviceActivityService.logLightActivity(lightId, "CONNECT", null, null, lightIp, ownerId);

                // Cập nhật trạng thái kết nối
                lightConnectionStatus.put(lightId, true);
                System.out.println("Đèn " + lightId + " đã kết nối thành công");
            }
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
            try {
                Light light = lightService.getLightById(lightId);
                if (light != null && light.getUser() != null) {
                    Long userId = light.getUser().getUserId();
                    lightService.updateLightStatus(lightId, null, null, userId);

                    // ghi log thiết bị đã bị mất kết nối
                    deviceActivityService.logLightActivity(lightId, "DISCONNECT", null, null, null, userId);

                    // Cập nhật trạng thái kết nối
                    lightConnectionStatus.put(lightId, false);
                }
            } catch (Exception e) {
                System.err.println("Error updating light status: " + e.getMessage());
            }
        }
    }

    public boolean isConnected(Long lightId) {
        // Kiểm tra trong bản đồ trạng thái kết nối trước
        Boolean status = lightConnectionStatus.get(lightId);
        if (status != null && status) {
            // Kiểm tra thêm xem phiên làm việc có tồn tại và đang mở không
            WebSocketSession session = arduinoSessions.get(lightId);
            return (session != null && session.isOpen());
        }
        return false;
    }

    // Scheduled task chạy mỗi 30 giây để kiểm tra kết nối đèn không hoạt động
    @Scheduled(fixedRate = 30000)
    public void checkInactiveLightSessions() {
        System.out.println("Đang kiểm tra các kết nối đèn không hoạt động...");
        Instant now = Instant.now();

        // Tập hợp các session cần đóng
        Set<WebSocketSession> sessionsToClose = ConcurrentHashMap.newKeySet();

        lastActivityTime.forEach((session, lastActive) -> {
            long inactiveSeconds = ChronoUnit.SECONDS.between(lastActive, now);

            if (inactiveSeconds > MAX_INACTIVE_TIME_SECONDS) {
                // Tìm ID của đèn tương ứng với session
                Long lightId = null;
                for (Map.Entry<Long, WebSocketSession> entry : arduinoSessions.entrySet()) {
                    if (entry.getValue().equals(session)) {
                        lightId = entry.getKey();
                        break;
                    }
                }

                if (lightId != null) {
                    System.out.println("Đèn " + lightId + " không hoạt động trong " + inactiveSeconds
                            + " giây. Đóng kết nối.");
                    sessionsToClose.add(session);
                }
            }
        });

        // Đóng các session không hoạt động
        for (WebSocketSession session : sessionsToClose) {
            try {
                // Tìm ID của đèn tương ứng với session
                Long lightId = null;
                for (Map.Entry<Long, WebSocketSession> entry : arduinoSessions.entrySet()) {
                    if (entry.getValue().equals(session)) {
                        lightId = entry.getKey();
                        break;
                    }
                }

                if (lightId != null) {
                    try {
                        // Cập nhật trạng thái đèn thành offline
                        Light light = lightService.getLightById(lightId);
                        if (light != null && light.getUser() != null) {
                            Long userId = light.getUser().getUserId();
                            lightService.updateLightStatus(lightId, null, null, userId);
                            deviceActivityService.logLightActivity(lightId, "DISCONNECT", null, null, null, userId);
                            System.out.println("Heartbeat: Đã cập nhật trạng thái đèn " + lightId + " thành offline");

                            // Cập nhật trạng thái kết nối
                            lightConnectionStatus.put(lightId, false);
                        }
                    } catch (Exception e) {
                        System.err.println("Lỗi khi cập nhật trạng thái đèn: " + e.getMessage());
                    }
                }

                // Đóng session
                session.close(CloseStatus.GOING_AWAY.withReason("Inactive light connection"));

                // Xóa khỏi các map
                lastActivityTime.remove(session);

                System.out.println("Đã đóng session đèn không hoạt động");
            } catch (IOException e) {
                System.err.println("Lỗi khi đóng session không hoạt động: " + e.getMessage());
            }
        }

        if (sessionsToClose.isEmpty()) {
            System.out.println("Không có đèn nào bị ngắt kết nối do không hoạt động");
        } else {
            System.out.println("Đã đóng " + sessionsToClose.size() + " kết nối đèn không hoạt động");
        }
    }

    // Phương thức để ping tất cả đèn để kiểm tra trạng thái
    @Scheduled(fixedRate = 20000) // 20 giây ping một lần
    public void pingAllLights() {
        System.out.println("Đang ping tất cả đèn...");
        arduinoSessions.forEach((lightId, session) -> {
            if (session.isOpen()) {
                try {
                    // Gửi lệnh ping đơn giản để kiểm tra kết nối
                    session.sendMessage(new TextMessage("ping"));
                    System.out.println("Đã ping đèn " + lightId);
                } catch (IOException e) {
                    System.err.println("Không thể ping đèn " + lightId + ": " + e.getMessage());
                    // Nếu không gửi được ping, đánh dấu là không hoạt động
                    lastActivityTime.put(session,
                            Instant.now().minus(MAX_INACTIVE_TIME_SECONDS + 1, ChronoUnit.SECONDS));
                }
            }
        });
    }
}