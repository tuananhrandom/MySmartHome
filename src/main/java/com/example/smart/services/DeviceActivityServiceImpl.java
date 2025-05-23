package com.example.smart.services;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.stereotype.Service;

import com.example.smart.entities.Camera;
import com.example.smart.entities.DeviceActivity;
import com.example.smart.entities.Door;
import com.example.smart.entities.Light;
import com.example.smart.entities.User;
import com.example.smart.repositories.CameraRepositories;
import com.example.smart.repositories.DeviceActivityRepository;
import com.example.smart.repositories.DoorRepositories;
import com.example.smart.repositories.LightRepositories;
import com.example.smart.repositories.UserRepository;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class DeviceActivityServiceImpl implements DeviceActivityService {

    private final DeviceActivityRepository deviceActivityRepository;
    private final LightRepositories lightRepositories;
    private final DoorRepositories doorRepositories;
    private final CameraRepositories cameraRepositories;
    private final UserRepository userRepository;

    @Override
    public DeviceActivity saveActivity(DeviceActivity activity) {
        return deviceActivityRepository.save(activity);
    }

    @Override
    public List<DeviceActivity> getAllActivities() {
        return deviceActivityRepository.findAll();
    }

    @Override
    public List<DeviceActivity> getActivitiesByUserId(Long userId) {
        return deviceActivityRepository.findByUser_UserId(userId);
    }

    @Override
    public List<DeviceActivity> getActivitiesByDeviceTypeAndId(String deviceType, Long deviceId) {
        return deviceActivityRepository.findByDeviceTypeAndDeviceIdOrderByActivityTimeDesc(deviceType, deviceId);
    }

    @Override
    public List<DeviceActivity> getActivitiesByTimeRange(String deviceType, Long deviceId, LocalDateTime startTime,
            LocalDateTime endTime) {
        return deviceActivityRepository.findByDeviceTypeAndDeviceIdAndActivityTimeBetweenOrderByActivityTimeDesc(
                deviceType, deviceId, startTime, endTime);
    }

    @Override
    public void logLightActivity(Long lightId, String activityType, Integer previousStatus, Integer currentStatus,
            String lightIp, Long userId) {

        DeviceActivity activity = new DeviceActivity();
        activity.setDeviceType("LIGHT");
        activity.setDeviceId(lightId);
        activity.setActivityType(activityType);
        activity.setPreviousState(previousStatus != null ? previousStatus.toString() : null);
        activity.setCurrentState(currentStatus != null ? currentStatus.toString() : null);
        activity.setIpAddress(lightIp != null ? lightIp : null);
        activity.setActivityTime(LocalDateTime.now());

        // Thêm thông tin mô tả dựa trên hoạt động
        String description = "";
        switch (activityType) {
            case "ON":
                description = "Đèn đã được bật";
                break;
            case "OFF":
                description = "Đèn đã được tắt";
                break;
            case "CONNECT":
                description = "Đèn đã kết nối";
                break;
            case "DISCONNECT":
                description = "Đèn đã mất kết nối";
                break;
            default:
                description = "Hoạt động khác của đèn";
        }
        activity.setDescription(description);

        // Lấy tên thiết bị và người dùng
        if (lightId != null) {
            Light light = lightRepositories.findById(lightId).orElse(null);
            if (light != null) {
                activity.setDeviceName(light.getLightName());
            }
        }

        if (userId != null) {
            User user = userRepository.findById(userId).orElse(null);
            activity.setUser(user);
        }

        deviceActivityRepository.save(activity);
    }

    @Override
    public void logDoorActivity(Long doorId, String activityType, Integer previousStatus, Integer currentStatus,
            Integer previousLockDown, Integer currentLockDown, Integer previousAlert, Integer currentAlert,
            String doorIp, Long userId) {

        DeviceActivity activity = new DeviceActivity();
        activity.setDeviceType("DOOR");
        activity.setDeviceId(doorId);
        activity.setActivityType(activityType);

        // Tạo trạng thái dưới dạng chuỗi JSON để lưu nhiều thông tin
        String prevState = String.format("{\"status\":%s, \"lockDown\":%s, \"alert\":%s}",
                previousStatus, previousLockDown, previousAlert);
        String currState = String.format("{\"status\":%s, \"lockDown\":%s, \"alert\":%s}",
                currentStatus, currentLockDown, currentAlert);

        activity.setPreviousState(prevState);
        activity.setCurrentState(currState);
        activity.setIpAddress(doorIp);
        activity.setActivityTime(LocalDateTime.now());

        // Thêm thông tin mô tả dựa trên hoạt động
        String description = "";
        switch (activityType) {
            case "OPEN":
                description = "Cửa đã được mở";
                break;
            case "CLOSE":
                description = "Cửa đã được đóng";
                break;
            case "ALARM_ON":
                description = "Cảnh báo cửa đã được bật";
                break;
            case "ALARM_OFF":
                description = "Cảnh báo cửa đã được tắt";
                break;
            case "CONNECT":
                description = "Cửa đã kết nối";
                break;
            case "DISCONNECT":
                description = "Cửa đã mất kết nối";
                break;
            case "WARNING":
                description = "Phát hiện đột nhập";
                break;
            default:
                description = "Hoạt động khác của cửa";
        }
        activity.setDescription(description);

        // Lấy tên thiết bị và người dùng
        if (doorId != null) {
            Door door = doorRepositories.findById(doorId).orElse(null);
            if (door != null) {
                activity.setDeviceName(door.getDoorName());
            }
        }

        if (userId != null) {
            User user = userRepository.findById(userId).orElse(null);
            activity.setUser(user);
        }

        deviceActivityRepository.save(activity);
    }

    @Override
    public void logCameraActivity(Long cameraId, String activityType, Integer previousStatus, Integer currentStatus,
            String cameraIp, Long userId) {

        DeviceActivity activity = new DeviceActivity();
        activity.setDeviceType("CAMERA");
        activity.setDeviceId(cameraId);
        activity.setActivityType(activityType);
        activity.setPreviousState(previousStatus != null ? previousStatus.toString() : null);
        activity.setCurrentState(currentStatus != null ? currentStatus.toString() : null);
        activity.setIpAddress(cameraIp);
        activity.setActivityTime(LocalDateTime.now());

        // Thêm thông tin mô tả dựa trên hoạt động
        String description = "";
        switch (activityType) {
            case "START_RECORDING":
                description = "Camera bắt đầu truyền dữ liệu";
                break;
            case "STOP_RECORDING":
                description = "Camera ngừng truyền dữ liệu";
                break;
            case "CONNECT":
                description = "Camera đã kết nối";
                break;
            case "DISCONNECT":
                description = "Camera đã mất kết nối";
                break;
            default:
                description = "Hoạt động khác của camera";
        }
        activity.setDescription(description);

        // Lấy tên thiết bị và người dùng
        if (cameraId != null) {
            Camera camera = cameraRepositories.findById(cameraId).orElse(null);
            if (camera != null) {
                activity.setDeviceName(camera.getCameraName());
            }
        }

        if (userId != null) {
            User user = userRepository.findById(userId).orElse(null);
            activity.setUser(user);
        }

        deviceActivityRepository.save(activity);
    }

    @Override
    public void deleteActivitiesOlderThan(LocalDateTime dateTime) {
        List<DeviceActivity> oldActivities = deviceActivityRepository.findByActivityTimeBetween(
                LocalDateTime.MIN, dateTime);
        deviceActivityRepository.deleteAll(oldActivities);

    }

    @Override
    @Transactional
    public void deleteDeviceActivities(String deviceType, Long deviceId) {
        try {
            deviceActivityRepository.deleteByDeviceTypeAndDeviceId(deviceType, deviceId);
        } catch (Exception e) {
            System.err.println(e);
        }
    }
}
