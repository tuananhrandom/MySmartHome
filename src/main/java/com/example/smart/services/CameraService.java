package com.example.smart.services;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.HashSet;
import java.util.Set;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.smart.entities.Camera;
import com.example.smart.entities.CameraRecording;
import com.example.smart.entities.User;
import com.example.smart.repositories.CameraRecordingRepository;
import com.example.smart.repositories.CameraRepositories;
import com.example.smart.repositories.UserRepository;
import com.example.smart.websocket.CameraSocketHandler;
import com.example.smart.websocket.ClientWebSocketHandler;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;

import java.io.File;

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
    @Autowired
    NotificationService notificationService;

    public void userRemoveCamera(Long cameraId, Long userId) {
        Camera selectedCamera = cameraRepo.findById(cameraId).get();
        // tránh trường hợp một api từ user khác xóa Camera của user khác
        if (selectedCamera.getUser().getUserId().equals(userId)) {
            // Lấy danh sách các bản ghi recording
            List<CameraRecording> recordings = recordingRepository.findByCamera(selectedCamera);

            // Tạo danh sách các CompletableFuture để xóa file bất đồng bộ
            List<CompletableFuture<Void>> deleteFutures = recordings.stream()
                    .map(recording -> CompletableFuture.runAsync(() -> {
                        File recordingFile = new File(recording.getFilePath());
                        if (recordingFile.exists()) {
                            recordingFile.delete();
                        }
                    }))
                    .toList();

            // Đợi tất cả các file được xóa xong
            CompletableFuture.allOf(deleteFutures.toArray(new CompletableFuture[0])).join();

            // Xóa các bản ghi recording từ database
            recordingRepository.deleteAll(recordings);

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

    @Transactional(readOnly = true)
    public Camera getCameraById(Long id) {
        return cameraRepo.findById(id)
                .map(camera -> {
                    // Force load sharedUsers
                    camera.getSharedUsers().size();
                    return camera;
                })
                .orElse(null);
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
        }
        // nếu đèn đã có chủ và chủ đúng với người gửi về thì cập nhật tên camera thôi
        else if (cameraRepo.existsById(cameraId)
                && cameraRepo.findById(cameraId).get().getUser().getUserId().equals(userId)) {
            Camera thisCamera = cameraRepo.findById(cameraId).get();
            thisCamera.setCameraName(cameraName);
            cameraRepo.save(thisCamera);
            clientWebSocketHandler.notifyCameraUpdate(thisCamera);
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
        }

        // Tạo thông báo khi camera bị ngắt kết nối
        if (cameraStatus == 0 && selectedCamera.getUser() != null) {
            notificationService.createNotification(
                    "CAMERA",
                    "Mất kết nối thiết bị",
                    "Camera " + selectedCamera.getCameraName() + " đã mất kết nối với hệ thống",
                    selectedCamera.getUser().getUserId());
        }
    }

    // người dùng xóa camera
    public void userDeleteCamera(Long cameraId, Long userId) {
        Camera selectedLight = cameraRepo.findById(cameraId).get();
        // tránh trường hợp một api từ user khác xóa light của user khác
        if (selectedLight.getUser().getUserId().equals(userId)) {
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
            newCamera.setCameraStatus(0);
            newCamera.setCameraIp(null);
            newCamera.setUser(null);
            newCamera.setIsRecord(false);
            newCamera.setCreatedTime(LocalDateTime.now(ZoneId.of("Asia/Ho_Chi_Minh")));
            cameraRepo.save(newCamera);

        } catch (Exception e) {
            throw new IllegalStateException("Failed to create new Camera ");
        }
    }

    public void adminDeletecamera(Long cameraId, Long userId) {
        // xóa toàn bộ log
        deviceActivityService.deleteDeviceActivities(
                "CAMERA", cameraId);
        // Sử dụng phương thức deleteCamera để xóa camera và các bản ghi liên quan
        deleteCamera(cameraId);
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
                && cameraRepo.findById(cameraId).get().getUser().getUserId().equals(userId)) {
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

    public List<Camera> getCamerasByRange(Long start, Long end) {
        return cameraRepo.findByCameraIdBetween(start, end);
    }

    public void updateCamera(Camera camera) {
        cameraRepo.save(camera);
        if (clientWebSocketHandler != null && camera.getUser() != null) {
            clientWebSocketHandler.notifyCameraUpdate(camera);
        }
    }

    public void toggleRecordCamera(Long cameraId) {
        Camera selectedCamera = cameraRepo.findById(cameraId)
                .orElseThrow(() -> new IllegalArgumentException("camera not found with ID: " + cameraId));
        selectedCamera.setIsRecord(!selectedCamera.getIsRecord());
        cameraRepo.save(selectedCamera);
        clientWebSocketHandler.notifyCameraUpdate(selectedCamera);
        cameraSocketHandler.checkCameraRecordStatus(cameraId, selectedCamera.getIsRecord());
    }

    public void startRecordingAllCameraByDoor(Long ownerId) {
        List<Camera> cameras = cameraRepo.findByUser_UserId(ownerId);
        List<CompletableFuture<Void>> futures = cameras.stream().map(camera -> CompletableFuture.runAsync(() -> {
            try {
                if (camera.getIsRecord()) {
                    return; // Nếu camera đã đang ghi hình thì bỏ qua
                } else if (!camera.getIsRecord()) {
                    camera.setIsRecord(true);
                    cameraRepo.save(camera);
                }
                // Gửi yêu cầu bắt đầu ghi hình đến camera
                cameraSocketHandler.checkCameraRecordStatus(camera.getCameraId(), true);
                // tạo thông báo đến người dùng
                notificationService.createNotification(
                        "CAMERA",
                        "Bắt đầu ghi hình",
                        "Camera " + camera.getCameraName() + " đã bắt đầu ghi hình khẩn cấp do cửa mở trái phép",
                        ownerId);
                // thay đổi trạng thái camera trên giao diện
                clientWebSocketHandler.notifyCameraUpdate(camera);
            } catch (Exception e) {
                throw new RuntimeException("Failed to start recording for camera: " + camera.getCameraId(), e);
            }
        })).toList();
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
    }

    @Transactional
    public void deleteCamera(Long cameraId) {
        Camera camera = cameraRepo.findById(cameraId)
                .orElseThrow(() -> new RuntimeException("Camera not found"));

        // Lấy danh sách các bản ghi recording
        List<CameraRecording> recordings = recordingRepository.findByCamera(camera);

        // Xóa các file recording
        for (CameraRecording recording : recordings) {
            File recordingFile = new File(recording.getFilePath());
            if (recordingFile.exists()) {
                recordingFile.delete();
            }
        }

        // Xóa camera (các bản ghi recording sẽ tự động bị xóa nhờ @OnDelete)
        cameraRepo.delete(camera);
    }

    public List<Camera> getCamerasByDateRange(LocalDateTime start, LocalDateTime end) {
        return cameraRepo.findByCreatedTimeBetween(start, end);
    }

    public void shareCamera(Long cameraId, Long currentUserId, String targetUsername, String targetEmail) {
        // Kiểm tra camera tồn tại
        Camera camera = cameraRepo.findById(cameraId)
                .orElseThrow(() -> new IllegalArgumentException("Camera not found"));

        // Kiểm tra người dùng hiện tại là chủ sở hữu
        if (camera.getUser() == null || !camera.getUser().getUserId().equals(currentUserId)) {
            throw new IllegalArgumentException("You don't have permission to share this camera");
        }

        // Tìm target user
        User targetUser = userRepo.findByUsername(targetUsername)
                .orElseThrow(() -> new IllegalArgumentException("Target user not found"));

        // Kiểm tra email có khớp không
        if (!targetUser.getEmail().equals(targetEmail)) {
            throw new IllegalArgumentException("Email does not match with the username");
        }

        // Kiểm tra không phải là chủ sở hữu
        if (targetUser.getUserId().equals(currentUserId)) {
            throw new IllegalArgumentException("Cannot share camera with yourself");
        }

        // Khởi tạo sharedUsers nếu chưa có
        if (camera.getSharedUsers() == null) {
            camera.setSharedUsers(new HashSet<>());
        }

        // Kiểm tra camera đã được chia sẻ cho user này chưa
        if (camera.getSharedUsers().contains(targetUser)) {
            throw new IllegalArgumentException("Camera already shared with this user");
        }

        // Thêm user vào danh sách shared
        camera.getSharedUsers().add(targetUser);
        cameraRepo.save(camera);

        // Tạo thông báo cho người dùng được chia sẻ
        notificationService.createNotification(
                "CAMERA",
                "Camera được chia sẻ",
                "Camera " + camera.getCameraName() + " đã được chia sẻ với bạn",
                targetUser.getUserId());
    }

    public void unshareCamera(Long cameraId, Long currentUserId, Long targetUserId) {
        // Kiểm tra camera tồn tại
        Camera camera = cameraRepo.findById(cameraId)
                .orElseThrow(() -> new IllegalArgumentException("Camera not found"));

        // Kiểm tra người dùng hiện tại là chủ sở hữu
        if (camera.getUser() == null || !camera.getUser().getUserId().equals(currentUserId)) {
            throw new IllegalArgumentException("You don't have permission to unshare this camera");
        }

        // Tìm target user
        User targetUser = userRepo.findById(targetUserId)
                .orElseThrow(() -> new IllegalArgumentException("Target user not found"));

        // Kiểm tra camera đã được chia sẻ cho user này chưa
        if (camera.getSharedUsers() == null ||
                !camera.getSharedUsers().contains(targetUser)) {
            throw new IllegalArgumentException("Camera is not shared with this user");
        }

        // Xóa user khỏi danh sách shared
        camera.getSharedUsers().remove(targetUser);
        cameraRepo.save(camera);

        // Tạo thông báo cho người dùng bị hủy chia sẻ
        notificationService.createNotification(
                "CAMERA",
                "Camera bị hủy chia sẻ",
                "Camera " + camera.getCameraName() + " đã bị hủy chia sẻ với bạn",
                targetUser.getUserId());
    }

    public Set<User> getSharedUsers(Long cameraId, Long currentUserId) {
        // Kiểm tra camera tồn tại
        Camera camera = cameraRepo.findById(cameraId)
                .orElseThrow(() -> new IllegalArgumentException("Camera not found"));

        // Kiểm tra người dùng hiện tại là chủ sở hữu
        if (camera.getUser() == null ||
                !camera.getUser().getUserId().equals(currentUserId)) {
            throw new IllegalArgumentException("You don't have permission to view shared users");
        }

        return camera.getSharedUsers() != null ? camera.getSharedUsers() : new HashSet<>();
    }

    public void adminAddUserToCamera(Long cameraId, Long userId) {
        Camera camera = cameraRepo.findById(cameraId)
                .orElseThrow(() -> new IllegalArgumentException("Camera not found"));
        camera.setUser(userRepo.findById(userId).get());
        camera.setCameraName("");
        cameraRepo.save(camera);
    }
}
