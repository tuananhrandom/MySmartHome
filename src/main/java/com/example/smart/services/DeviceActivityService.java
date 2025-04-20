package com.example.smart.services;
import java.time.LocalDateTime;
import java.util.List;

import com.example.smart.entities.DeviceActivity;

public interface DeviceActivityService {
    // Phương thức chung
    DeviceActivity saveActivity(DeviceActivity activity);
    
    List<DeviceActivity> getAllActivities();
    
    List<DeviceActivity> getActivitiesByUserId(Long userId);
    
    List<DeviceActivity> getActivitiesByDeviceTypeAndId(String deviceType, Long deviceId);
    
    List<DeviceActivity> getActivitiesByTimeRange(LocalDateTime startTime, LocalDateTime endTime);
    
    // Phương thức cho đèn
    void logLightActivity(Long lightId, String activityType, Integer previousStatus, Integer currentStatus, String lightIp, Long userId);
    
    // Phương thức cho cửa
    void logDoorActivity(Long doorId, String activityType, Integer previousStatus, Integer currentStatus, 
                        Integer previousLockDown, Integer currentLockDown,
                        Integer previousAlert, Integer currentAlert, 
                        String doorIp, Long userId);
    
    // Phương thức cho camera
    void logCameraActivity(Long cameraId, String activityType, Integer previousStatus, Integer currentStatus, 
                           String cameraIp, Long userId);
    
    // Xóa lịch sử hoạt động theo thời gian
    void deleteActivitiesOlderThan(LocalDateTime dateTime);
}