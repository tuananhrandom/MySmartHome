package com.example.smart.services;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import org.springframework.beans.factory.annotation.Autowired;

import org.springframework.stereotype.Service;

import com.example.smart.entities.Light;
import com.example.smart.repositories.LightRepositories;
import com.example.smart.repositories.UserRepository;

import com.example.smart.websocket.LightSocketHandler;

@Service
public class LightServicesImp implements LightServices {
    @Autowired
    DeviceActivityService deviceActivityService;
    @Autowired
    LightSocketHandler lightSocketHandler;
    @Autowired
    LightRepositories lightRepo;

    @Autowired
    UserRepository userRepo;

    @Autowired(required = false)
    private com.example.smart.websocket.ClientWebSocketHandler clientWebSocketHandler;
    @Autowired
    NotificationService notificationService;

    @Override
    public List<Light> getAllLight() {
        return lightRepo.findAll();
    }

    @Override
    public List<Light> getLightByUserId(Long userId) {
        return lightRepo.findByUser_UserId(userId);
    }

    @Override
    public Light getLightById(Long lightId) {
        return lightRepo.findById(lightId).orElseThrow(() -> new IllegalArgumentException("Light not found"));
    }

    // cập nhật thông tin đèn từ ESP32
    @Override
    public void updateLightStatus(Long lightId, Integer lightStatus, String lightIp, Long ownerId) {
        Light selectedLight = lightRepo.findById(lightId)
                .orElseThrow(() -> new IllegalArgumentException("Light not found with ID: " + lightId));
        Integer previousStatus = selectedLight.getLightStatus();
        selectedLight.setLightStatus(lightStatus);
        selectedLight.setLightIp(lightIp);
        if (ownerId == -1) {
            selectedLight.setUser(null);
        } else {
            selectedLight.setUser(userRepo.findById(ownerId).get());
        }

        // Lưu thay đổi vào database
        lightRepo.save(selectedLight);

        // Chỉ ghi log và xử lý thông báo nếu lightStatus không phải null
        if (lightStatus != null) {
            // lưu log thiết bị
            // Ghi nhận hoạt động
            String activityType = lightStatus == 1 ? "ON" : "OFF";
            deviceActivityService.logLightActivity(lightId, activityType, previousStatus, lightStatus, lightIp,
                    ownerId);

            // Gửi thông báo đến client qua WebSocket nếu có ClientWebSocketHandler
            if (clientWebSocketHandler != null && selectedLight.getUser() != null) {
                clientWebSocketHandler.notifyLightUpdate(selectedLight);
            }
        } else {
            // Trường hợp lightStatus là null (thiết bị offline)
            deviceActivityService.logLightActivity(lightId, "DISCONNECT", previousStatus, null, lightIp, ownerId);

            // Tạo thông báo khi đèn bị ngắt kết nối
            if (selectedLight.getUser() != null) {
                notificationService.createNotification(
                        "LIGHT",
                        "Mất kết nối thiết bị",
                        "Đèn " + selectedLight.getLightName() + " đã mất kết nối với hệ thống",
                        selectedLight.getUser().getUserId());
            }

            // Vẫn thông báo đến client về việc thiết bị offline nếu cần
            if (clientWebSocketHandler != null && selectedLight.getUser() != null) {
                clientWebSocketHandler.notifyLightUpdate(selectedLight);
            }
        }
    }

    // admin thêm một đèn mới được tạo ra vào database, đèn mới được tạo ra chỉ để
    // xác thực Id này đã được tạo mới tất cả các trương khác đều là rỗng chờ đơi
    // ESP32 và user thêm dữ liệu
    @Override
    public void adminAddNewLight(Long lightId) {
        Light newLight = new Light();
        newLight.setLightId(lightId);
        newLight.setLightName(null);
        newLight.setLightStatus(null);
        newLight.setLightIp(null);
        newLight.setUser(null);
        newLight.setCreatedTime(LocalDateTime.now(ZoneId.of("Asia/Ho_Chi_Minh")));
        lightRepo.save(newLight);
    }

    // user thêm một đèn mới vào quyền sở hữu của họ(cập nhật ownerId cho đèn, và
    // đồng thời người dùng đặt tên cho đèn)
    @Override
    public void userAddLight(Long lightId, Long userId, String lightName) {
        // Kiểm tra đèn tồn tại và chưa có chủ sở hữu
        if (lightRepo.existsById(lightId) && lightRepo.findById(lightId).get().getUser() == null) {
            Light thisLight = lightRepo.findById(lightId).get();

            try {
                // Gửi yêu cầu thay đổi chủ sở hữu đến ESP32 và đợi phản hồi
                CompletableFuture<Boolean> response = lightSocketHandler.sendControlSignalWithResponse(lightId,
                        "ownerId:" + userId, "ownerId");

                // Đợi kết quả từ ESP32 (với timeout 10 giây đã được xử lý trong phương thức)
                boolean accepted = response.get(); // Sẽ chờ tối đa 10 giây

                if (accepted) {
                    // Nếu ESP32 chấp nhận, lưu thông tin vào database
                    thisLight.setUser(userRepo.findById(userId).get());
                    thisLight.setLightName(lightName);
                    lightRepo.save(thisLight);

                    // Thông báo đến client
                    if (clientWebSocketHandler != null) {
                        clientWebSocketHandler.notifyLightUpdate(thisLight);
                    }

                    System.out.println("ESP32 accepted ownership change for light: " + lightId);
                } else {
                    // Nếu ESP32 từ chối hoặc timeout
                    throw new IllegalStateException("ESP32 rejected ownership change or did not respond");
                }
            } catch (Exception e) {
                // Xử lý ngoại lệ (có thể do mất kết nối, timeout, vv)
                throw new IllegalStateException("Failed to communicate with ESP32: " + e.getMessage(), e);
            }
            // nếu đèn đã có chủ và chủ đúng với người gửi về thì cập nhật tên đèn thôi
        } else if (lightRepo.existsById(lightId)
                && lightRepo.findById(lightId).get().getUser().getUserId().equals(userId)) {
            Light thisLight = lightRepo.findById(lightId).get();
            thisLight.setLightName(lightName);
            lightRepo.save(thisLight);
            clientWebSocketHandler.notifyLightUpdate(thisLight);
        } else {
            throw new IllegalStateException("Light already been Used");
        }
    }

    @Override
    public Light findByLightName(String lightName) {
        return lightRepo.findByLightName(lightName);
    }

    @Override
    public Light findByLightId(Long lightId) {
        return lightRepo.findById(lightId).orElseThrow(() -> new IllegalArgumentException("Light not found"));
    }

    @Override
    public boolean nameIsExist(String lightName) {
        return lightRepo.findByLightName(lightName) != null;
    }

    @Override
    public boolean idIsExist(Long lightId) {
        return lightRepo.findById(lightId).isPresent();
    }

    @Override
    public boolean ipIsExist(String lightIp) {
        return lightRepo.findByLightIp(lightIp) != null;
    }

    @Override
    public Light findByLightIp(String lightIp) {
        return lightRepo.findByLightIp(lightIp);
    }

    @Override
    public void deleteLight(Long lightId) {
        Light selected = lightRepo.findById(lightId).get();
        deviceActivityService.deleteDeviceActivities("LIGHT", lightId);
        lightRepo.delete(selected);

    }

    @Override
    public void userDeleteLight(Long lightId, Long userId) {
        Light selectedLight = lightRepo.findById(lightId).get();
        // tránh trường hợp một api từ user khác xóa light của user khác
        if (selectedLight.getUser().getUserId().equals(userId)) {
            selectedLight.setUser(null);
        }
        // xóa toàn bộ log hoạt động của thiết bị đèn đó
        deviceActivityService.deleteDeviceActivities("LIGHT", lightId);
        lightRepo.save(selectedLight);
        try {
            lightSocketHandler.sendControlSignal(lightId, "ownerId:" + -1);
        } catch (Exception e) {
            throw new IllegalArgumentException("Light not found");
        }
    }

    @Override
    public void toggleLight(Long lightId, Long ownerId) {
        Light selectedLight = lightRepo.findById(lightId).get();
        if (selectedLight != null && selectedLight.getUser().getUserId().equals(ownerId)) {
            Integer previousStatus = selectedLight.getLightStatus();
            Integer lightStatus = 1 - previousStatus;
            selectedLight.setLightStatus(lightStatus);
            lightRepo.save(selectedLight);
            clientWebSocketHandler.notifyLightUpdate(selectedLight);
            // gửi lệnh điều khiển về light ESP32
            try {
                lightSocketHandler.sendControlSignal(lightId, "lightStatus:" + selectedLight.getLightStatus());
            } catch (Exception e) {
                throw new IllegalArgumentException("Light not found");
            }
            // tạo log
            // Ghi nhận hoạt động
            // String activityType = lightStatus == 1 ? "ON" : "OFF";
            // String lightIp = selectedLight.getLightIp();
            // deviceActivityService.logLightActivity(lightId, activityType, previousStatus,
            // lightStatus, lightIp,
            // ownerId);
        }
    }

    @Override
    public List<Light> getLightsByRange(Long start, Long end) {
        return lightRepo.findByLightIdBetween(start, end);
    }

    @Override
    public void updateLight(Light light) {
        lightRepo.save(light);
        if (clientWebSocketHandler != null && light.getUser() != null) {
            clientWebSocketHandler.notifyLightUpdate(light);
        }
    }

    @Override
    public List<Light> getLightsByDateRange(LocalDateTime start, LocalDateTime end) {
        return lightRepo.findByCreatedTimeBetween(start, end);
    }

    @Override
    public void adminAddUserToLight(Long lightId, Long userId) {
        Light light = lightRepo.findById(lightId).orElseThrow(() -> new IllegalArgumentException("Light not found"));
        light.setUser(userRepo.findById(userId).get());
        light.setLightName("");
        lightRepo.save(light);
    }
}