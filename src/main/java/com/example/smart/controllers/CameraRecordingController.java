package com.example.smart.controllers;

import com.example.smart.entities.Camera;
import com.example.smart.entities.CameraRecording;
import com.example.smart.services.CameraService;
import com.example.smart.services.VideoRecordingService;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/recordings")
public class CameraRecordingController {

    @Autowired
    private VideoRecordingService videoRecordingService;

    @Autowired
    private CameraService cameraService;

    // Lấy danh sách tất cả video của một camera
    // @GetMapping("/camera/{cameraId}")
    // public ResponseEntity<?> getRecordings(@PathVariable Long cameraId) {
    // Authentication auth = SecurityContextHolder.getContext().getAuthentication();
    // Long userId = Long.parseLong(auth.getName());

    // Camera camera = cameraService.getCameraById(cameraId);
    // if (camera == null) {
    // return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Camera không tồn
    // tại");
    // }

    // // Kiểm tra quyền truy cập
    // if (camera.getUser() == null || !camera.getUser().getUserId().equals(userId))
    // {
    // return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Không có quyền truy
    // cập");
    // }

    // // Lấy danh sách bản ghi
    // List<CameraRecording> recordings =
    // videoRecordingService.getRecordingsForCamera(cameraId);

    // // Chuyển đổi thành DTO để trả về
    // List<RecordingDTO> recordingDTOs = recordings.stream()
    // .map(this::convertToDTO)
    // .collect(Collectors.toList());

    // return ResponseEntity.ok(recordingDTOs);
    // }

    // // Xem video
    // @GetMapping("/stream/{recordingId}")
    // public ResponseEntity<Resource> streamVideo(@PathVariable Long recordingId) {
    // Authentication auth = SecurityContextHolder.getContext().getAuthentication();
    // Long userId = Long.parseLong(auth.getName());

    // CameraRecording recording = videoRecordingService.getRecording(recordingId);
    // if (recording == null) {
    // return ResponseEntity.notFound().build();
    // }

    // // Kiểm tra quyền truy cập
    // Camera camera = recording.getCamera();
    // if (camera.getUser() == null || !camera.getUser().getUserId().equals(userId))
    // {
    // return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
    // }

    // // Kiểm tra file có tồn tại không
    // File videoFile = new File(recording.getFilePath());
    // if (!videoFile.exists()) {
    // return ResponseEntity.notFound().build();
    // }

    // // Trả về file dưới dạng stream
    // Resource resource = new FileSystemResource(videoFile);
    // return ResponseEntity.ok()
    // .contentType(MediaType.parseMediaType("video/mp4"))
    // .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" +
    // videoFile.getName() + "\"")
    // .body(resource);
    // }

    // // Xóa video
    // @DeleteMapping("/{recordingId}")
    // public ResponseEntity<?> deleteRecording(@PathVariable Long recordingId) {
    // Authentication auth = SecurityContextHolder.getContext().getAuthentication();
    // Long userId = Long.parseLong(auth.getName());

    // CameraRecording recording = videoRecordingService.getRecording(recordingId);
    // if (recording == null) {
    // return ResponseEntity.notFound().build();
    // }

    // // Kiểm tra quyền truy cập
    // Camera camera = recording.getCamera();
    // if (camera.getUser() == null || !camera.getUser().getUserId().equals(userId))
    // {
    // return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Không có quyền xóa
    // video này");
    // }

    // try {
    // // Xóa recording qua service
    // videoRecordingService.deleteRecording(recordingId);
    // return ResponseEntity.ok("Đã xóa video thành công");
    // } catch (Exception e) {
    // return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
    // .body("Lỗi khi xóa video: " + e.getMessage());
    // }
    // }

    // DTO để trả về thông tin recording
    private static class RecordingDTO {
        private Long id;
        private Long cameraId;
        private String cameraName;
        private String startTime;
        private String endTime;
        private Integer durationSeconds;
        private Long fileSize;

        // Getters và setters
        public Long getId() {
            return id;
        }

        public void setId(Long id) {
            this.id = id;
        }

        public Long getCameraId() {
            return cameraId;
        }

        public void setCameraId(Long cameraId) {
            this.cameraId = cameraId;
        }

        public String getCameraName() {
            return cameraName;
        }

        public void setCameraName(String cameraName) {
            this.cameraName = cameraName;
        }

        public String getStartTime() {
            return startTime;
        }

        public void setStartTime(String startTime) {
            this.startTime = startTime;
        }

        public String getEndTime() {
            return endTime;
        }

        public void setEndTime(String endTime) {
            this.endTime = endTime;
        }

        public Integer getDurationSeconds() {
            return durationSeconds;
        }

        public void setDurationSeconds(Integer durationSeconds) {
            this.durationSeconds = durationSeconds;
        }

        public Long getFileSize() {
            return fileSize;
        }

        public void setFileSize(Long fileSize) {
            this.fileSize = fileSize;
        }
    }

    // Chuyển đổi từ Entity sang DTO
    private RecordingDTO convertToDTO(CameraRecording recording) {
        RecordingDTO dto = new RecordingDTO();
        dto.setId(recording.getId());
        dto.setCameraId(recording.getCamera().getCameraId());
        dto.setCameraName(recording.getCamera().getCameraName());
        dto.setStartTime(recording.getStartTime().toString());
        dto.setEndTime(recording.getEndTime().toString());
        dto.setDurationSeconds(recording.getDurationSeconds());
        dto.setFileSize(recording.getFileSize());
        return dto;
    }
}