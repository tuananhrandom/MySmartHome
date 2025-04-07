package com.example.smart.services;

import java.util.List;
import java.util.concurrent.CompletableFuture;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.example.smart.entities.Camera;
import com.example.smart.repositories.CameraRepositories;
import com.example.smart.repositories.UserRepository;
import com.example.smart.websocket.CameraSocketHandler;
import com.example.smart.websocket.ClientWebSocketHandler;

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

    public void userRemoveCamera(Long CameraId, Long userId) {
        Camera selectedCamera = cameraRepo.findById(userId).get();
        // tránh trường hợp một api từ user khác xóa Camera của user khác
        if (selectedCamera.getUser().getUserId() == userId) {
            selectedCamera.setUser(null);
        }
        cameraRepo.save(selectedCamera);
        try {
            cameraSocketHandler.sendControlSignal(CameraId, "ownerId:" + -1);
        } catch (Exception e) {
            throw new IllegalArgumentException("Camera not found");
        }
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

    public void userDeletecamera(Long cameraId, Long userId) {
        Camera thisCamera = cameraRepo.findById(userId).get();
        if (thisCamera != null) {

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
            // nếu đèn đã có chủ và chủ đúng với người gửi về thì cập nhật tên đèn thôi
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
}
