package com.example.smart.services;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.example.smart.entities.Camera;
import com.example.smart.entities.CameraRecording;
import com.example.smart.repositories.CameraRecordingRepository;
import com.example.smart.repositories.CameraRepositories;
import com.example.smart.repositories.UserRepository;
import com.example.smart.websocket.CameraSocketHandler;
import com.example.smart.websocket.ClientWebSocketHandler;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;

@Service
public class CameraService {
    @Autowired
    CameraRepositories cameraRepo;
    @Autowired
    UserRepository userRepo;
    @Autowired
    ClientWebSocketHandler clientWebSocketHandler;
    @Autowired
    CameraSocketHandler cameraSocketHandler;
    @Autowired
    CameraRecordingRepository recordingRepository;
    @Autowired
    DeviceActivityService deviceActivityService;

    public void userRemoveCamera(Long cameraId, Long userId) {
        Camera selectedCamera = cameraRepo.findById(cameraId).get();
        // tránh trường hợp một api từ user khác xóa Camera của user khác
        if (selectedCamera.getUser().getUserId() == userId) {
            selectedCamera.setUser(null);
        }
        // xóa log của camera
        deviceActivityService.deleteDeviceActivities("CAMERA", cameraId);
        cameraRepo.save(selectedCamera);
        try {
            cameraSocketHandler.sendControlSignal(cameraId, "ownerId:" + -1);
        } catch (Exception e) {
            throw new IllegalArgumentException("Camera not found");
        }
    }

    public Camera findById(Long cameraId) {
        return cameraRepo.findById(cameraId).get();
    }

    public List<Camera> getAllCamera() {
        return cameraRepo.findAll();
    }

    public List<Camera> getCameraByUserId(Long userId) {
        return cameraRepo.findByUser_UserId(userId);
    }

    public Camera getCameraById(Long cameraId) {
        return cameraRepo.findByCameraId(cameraId);
    }

    public void userAddCamera(Long cameraId, Long userId, String cameraName) {
        // Kiểm tra đèn tồn tại và chưa có chủ sở hữu
        if (cameraRepo.existsById(cameraId) && cameraRepo.findById(cameraId).get().getUser() == null) {
            Camera thisCamera = cameraRepo.findById(cameraId).get();

            try {
                // Gửi yêu cầu thay đổi chủ sở hữu đến ESP32 và đợi phản hồi
                CompletableFuture<Boolean> response = cameraSocketHandler.sendControlSignalWithResponse(cameraId,
                        "ownerId:" + userId, "ownerId");

                // Đợi kết quả từ ESP32 (với timeout 10 giây đã được xử lý trong phương thức)
                boolean accepted = response.get(); // Sẽ chờ tối đa 10 giây

                if (accepted) {
                    // Nếu ESP32 chấp nhận, lưu thông tin vào database
                    thisCamera.setUser(userRepo.findById(userId).get());
                    thisCamera.setCameraName(cameraName);
                    cameraRepo.save(thisCamera);

                    // Thông báo đến client
                    if (clientWebSocketHandler != null) {
                        clientWebSocketHandler.notifyCameraUpdate(thisCamera);
                    }

                    System.out.println("ESP32 accepted ownership change for camera: " + cameraId);
                } else {
                    // Nếu ESP32 từ chối hoặc timeout
                    throw new IllegalStateException("ESP32 rejected ownership change or did not respond");
                }
            } catch (Exception e) {
                // Xử lý ngoại lệ (có thể do mất kết nối, timeout, vv)
                throw new IllegalStateException("Failed to communicate with ESP32: " + e.getMessage(), e);
            }
        } else {
            throw new IllegalArgumentException("camera not found or already owned");
        }
    }

    public void updateCameraStatus(Long cameraId, Integer cameraStatus, String cameraIp, Long ownerId) {
        Camera selectedCamera = cameraRepo.findById(cameraId)
                .orElseThrow(() -> new IllegalArgumentException("camera not found with ID: " + cameraId));

        selectedCamera.setCameraStatus(cameraStatus);
        selectedCamera.setCameraIp(cameraIp);
        if (ownerId == -1) {
            selectedCamera.setUser(null);
        } else {
            selectedCamera.setUser(userRepo.findById(ownerId).get());
        }

        // Lưu thay đổi vào database
        cameraRepo.save(selectedCamera);

        // Gửi thông báo đến client qua WebSocket nếu có ClientWebSocketHandler
        if (clientWebSocketHandler != null && selectedCamera.getUser() != null) {
            clientWebSocketHandler.notifyCameraUpdate(selectedCamera);
        } else {

        }
    }

    // người dùng xóa camera
    public void userDeleteCamera(Long cameraId, Long userId) {
        Camera selectedLight = cameraRepo.findById(cameraId).get();
        // tránh trường hợp một api từ user khác xóa light của user khác
        if (selectedLight.getUser().getUserId() == userId) {
            selectedLight.setUser(null);
        }
        // xóa toàn bộ log hoạt động của thiết bị đèn đó
        deviceActivityService.deleteDeviceActivities("CAMERA", cameraId);
        cameraRepo.save(selectedLight);
        try {
            cameraSocketHandler.sendControlSignal(cameraId, "ownerId:" + -1);
        } catch (Exception e) {
            throw new IllegalArgumentException("camera not found");
        }
    }

    public void adminAddNewCamera(Long CameraId) {
        try {
            Camera newCamera = new Camera();
            newCamera.setCameraId(CameraId);
            newCamera.setCameraName(null);
            newCamera.setCameraStatus(null);
            newCamera.setCameraIp(null);
            newCamera.setUser(null);
            cameraRepo.save(newCamera);

        } catch (Exception e) {
            throw new IllegalStateException("Failed to create new Camera ");
        }
    }

    public void adminDeletecamera(Long cameraId, Long userId) {
        Camera thisCamera = cameraRepo.findById(cameraId).get();
        // xóa toàn bộ log
        deviceActivityService.deleteDeviceActivities(
                "CAMERA", cameraId);
        if (thisCamera != null) {
            cameraRepo.delete(thisCamera);
        }
    }

    public void userAddcamera(Long cameraId, Long userId, String cameraName) {
        // Kiểm tra cameaa tồn tại và chưa có chủ sở hữu
        if (cameraRepo.existsById(cameraId) && cameraRepo.findById(cameraId).get().getUser() == null) {
            Camera thiscamera = cameraRepo.findById(cameraId).get();

            try {
                // Gửi yêu cầu thay đổi chủ sở hữu đến ESP32 và đợi phản hồi
                CompletableFuture<Boolean> response = cameraSocketHandler.sendControlSignalWithResponse(cameraId,
                        "ownerId:" + userId, "ownerId");

                // Đợi kết quả từ ESP32 (với timeout 10 giây đã được xử lý trong phương thức)
                boolean accepted = response.get(); // Sẽ chờ tối đa 10 giây

                if (accepted) {
                    // Nếu ESP32 chấp nhận, lưu thông tin vào database
                    thiscamera.setUser(userRepo.findById(userId).get());
                    thiscamera.setCameraName(cameraName);
                    cameraRepo.save(thiscamera);

                    // Thông báo đến client
                    if (clientWebSocketHandler != null) {
                        clientWebSocketHandler.notifyCameraUpdate(thiscamera);
                    }

                    System.out.println("ESP32 accepted ownership change for camera: " + cameraId);
                } else {
                    // Nếu ESP32 từ chối hoặc timeout
                    throw new IllegalStateException("ESP32 rejected ownership change or did not respond");
                }
            } catch (Exception e) {
                // Xử lý ngoại lệ (có thể do mất kết nối, timeout, vv)
                throw new IllegalStateException("Failed to communicate with ESP32: " + e.getMessage(), e);
            }
            // nếu đèn đã có chủ và chủ đúng với người gửi về thì cập nhật tên camera thôi
        } else if (cameraRepo.existsById(cameraId)
                && cameraRepo.findById(cameraId).get().getUser().getUserId() == userId) {
            Camera thiscamera = cameraRepo.findById(cameraId).get();
            thiscamera.setCameraName(cameraName);
            cameraRepo.save(thiscamera);
            clientWebSocketHandler.notifyCameraUpdate(thiscamera);
        } else {
            throw new IllegalStateException("camera already been Used");
        }
    }

    // trả về list video đã quay
    public List<CameraRecording> getRecordingsByCameraId(Long cameraId) {
        return recordingRepository.findByCamera_CameraIdOrderByStartTimeDesc(cameraId);
    }

    // trả về video file để xem trực tiếp trên frontend
    public Resource getVideoFile(Long recordingId) throws IOException {
        CameraRecording recording = recordingRepository.findById(recordingId)
                .orElseThrow(() -> new RuntimeException("Recording not found"));
        Path path = Paths.get(recording.getFilePath());
        return new UrlResource(path.toUri());
    }

    // xóa video đã quay
    public void deleteRecording(Long recordingId) {
        CameraRecording recording = recordingRepository.findById(recordingId)
                .orElseThrow(() -> new RuntimeException("Recording not found"));
        try {
            Files.deleteIfExists(Paths.get(recording.getFilePath()));
        } catch (IOException e) {
            throw new RuntimeException("Failed to delete file", e);
        }
        recordingRepository.delete(recording);
    }
}
