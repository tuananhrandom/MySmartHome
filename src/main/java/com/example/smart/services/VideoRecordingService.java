package com.example.smart.services;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.example.smart.entities.Camera;
import com.example.smart.entities.CameraRecording;
import com.example.smart.repositories.CameraRecordingRepository;
import com.example.smart.repositories.CameraRepositories;

@Service
public class VideoRecordingService {
    @Autowired
    private CameraRecordingRepository cameraRecordingRepository;
    @Autowired
    private CameraRepositories cameraRepository;

    private final Map<Long, LocalDateTime> videoStartTimes = new ConcurrentHashMap<>();
    private final Path baseDir = Paths.get("video_frames");
    private final Map<Long, Integer> frameCounters = new ConcurrentHashMap<>();

    public VideoRecordingService() {
        try {
            Files.createDirectories(baseDir);
        } catch (IOException e) {
            throw new RuntimeException("Không thể tạo thư mục lưu ảnh video", e);
        }
    }

    public void handleFrame(Long cameraId, byte[] data) {
        try {
            Path cameraDir = baseDir.resolve("camera_" + cameraId);
            Files.createDirectories(cameraDir);

            // if (frameNumber == 0) {
            // videoStartTimes.put(cameraId, LocalDateTime.now());
            // }
            // Kiểm tra nếu file frame_00000.jpg không tồn tại => reset
            Path firstFramePath = cameraDir.resolve("frame_00000.jpg");
            if (!Files.exists(firstFramePath)) {
                frameCounters.put(cameraId, 0);
                videoStartTimes.put(cameraId, LocalDateTime.now());
            }
            int frameNumber = frameCounters.getOrDefault(cameraId, 0);
            String filename = String.format("frame_%05d.jpg", frameNumber);
            Path framePath = cameraDir.resolve(filename);

            Files.write(framePath, data);
            frameCounters.put(cameraId, frameNumber + 1);

            // khoảng 2 phút sẽ lưu một cái video
            if (frameNumber > 0 && frameNumber % 1200 == 0) {
                LocalDateTime startTime = videoStartTimes.getOrDefault(cameraId, LocalDateTime.now());
                createVideoFromFramesAsync(cameraId, cameraDir, frameNumber, startTime);
            }

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // public void createVideoFromFrames(Long cameraId, Path cameraDir, int
    // upToFrameNumber) {
    // try {
    // String ffmpegPath =
    // "E:\\Download\\ffmpeg-2025-04-21-git-9e1162bdf1-full_build\\ffmpeg-2025-04-21-git-9e1162bdf1-full_build\\bin\\ffmpeg.exe";
    // Path videosDir = cameraDir.resolve("videos");
    // Files.createDirectories(videosDir);
    // String timestamp =
    // LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));

    // Path outputPath = videosDir.resolve("video_" + timestamp + ".mp4");

    // ProcessBuilder pb = new ProcessBuilder(
    // ffmpegPath,
    // "-y",
    // "-framerate", "10",
    // "-i", cameraDir.resolve("frame_%05d.jpg").toString(),
    // "-frames:v", String.valueOf(upToFrameNumber),
    // "-c:v", "libx264",
    // "-pix_fmt", "yuv420p",
    // outputPath.toString());

    // pb.redirectErrorStream(true); // Gộp stdout + stderr
    // Process process = pb.start();

    // // In log đầu ra
    // try (BufferedReader reader = new BufferedReader(new
    // InputStreamReader(process.getInputStream()))) {
    // String line;
    // while ((line = reader.readLine()) != null) {
    // System.out.println("[FFmpeg] " + line);
    // }
    // }

    // int exitCode = process.waitFor();
    // if (exitCode != 0) {
    // System.err.println("FFmpeg exited with error code: " + exitCode);
    // return; // Không xóa frame nếu lỗi
    // }

    // // 🔥 XÓA FRAME đã dùng
    // for (int i = 0; i < upToFrameNumber; i++) {
    // String frameName = String.format("frame_%05d.jpg", i);
    // Path framePath = cameraDir.resolve(frameName);
    // try {
    // Files.deleteIfExists(framePath);
    // } catch (IOException e) {
    // System.err.println("Không thể xóa frame: " + framePath);
    // e.printStackTrace();
    // }
    // }
    // // Reset bộ đếm
    // frameCounters.put(cameraId, 0);

    // } catch (Exception e) {
    // e.printStackTrace();
    // }
    // }
    public void createVideoFromFramesAsync(Long cameraId, Path cameraDir, int upToFrameNumber,
            LocalDateTime startTime) {
        CompletableFuture.runAsync(() -> {
            try {
                Path videosDir = cameraDir.resolve("videos");
                Files.createDirectories(videosDir);
                String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
                Path outputPath = videosDir.resolve("video_" + timestamp + ".mp4");

                ProcessBuilder pb = new ProcessBuilder(
                        "ffmpeg",
                        "-y",
                        "-framerate", "10",
                        "-i", cameraDir.resolve("frame_%05d.jpg").toString(),
                        "-frames:v", String.valueOf(upToFrameNumber),
                        "-c:v", "libx264",
                        "-pix_fmt", "yuv420p",
                        outputPath.toString());

                pb.redirectErrorStream(true);
                Process process = pb.start();

                try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        System.out.println("[FFmpeg] " + line);
                    }
                }

                int exitCode = process.waitFor();
                if (exitCode != 0) {
                    System.err.println("FFmpeg exited with error code: " + exitCode);
                    return;
                }

                // XÓA FRAME đã dùng
                for (int i = 0; i < upToFrameNumber; i++) {
                    String frameName = String.format("frame_%05d.jpg", i);
                    Path framePath = cameraDir.resolve(frameName);
                    try {
                        Files.deleteIfExists(framePath);
                    } catch (IOException e) {
                        System.err.println("Không thể xóa frame: " + framePath);
                        e.printStackTrace();
                    }
                }

                frameCounters.put(cameraId, 0);
                System.out.println("[FFmpeg] Video created: " + outputPath);

                // tạo entity CameraRecorder
                Camera camera = cameraRepository.findById(cameraId)
                        .orElseThrow(() -> new RuntimeException("Không tìm thấy camera với ID: " + cameraId));

                File videoFile = outputPath.toFile();
                long fileSize = videoFile.length(); // bytes

                CameraRecording recording = new CameraRecording();
                recording.setCamera(camera);
                recording.setFilePath(outputPath.toString());
                recording.setStartTime(startTime); // Cần truyền startTime lúc gọi hàm
                recording.setEndTime(LocalDateTime.now()); // Tạm thời dùng hiện tại
                recording.setFileSize(fileSize);
                recording.setDurationSeconds(upToFrameNumber / 10); // Vì framerate là 10 fps

                cameraRecordingRepository.save(recording);

            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }

    public void saveVideoEmergency(Long cameraId) {
        try {
            System.out.println("====> Bắt đầu lưu video khẩn cấp cho camera " + cameraId);

            int frameNumber = frameCounters.getOrDefault(cameraId, 0);
            System.out.println("Camera " + cameraId + " có " + frameNumber + " khung hình cần lưu");

            if (frameNumber > 0) {
                Path cameraDir = baseDir.resolve("camera_" + cameraId);

                // Kiểm tra thư mục có tồn tại không
                if (!Files.exists(cameraDir)) {
                    System.out.println("Thư mục camera " + cameraId + " không tồn tại, tạo mới...");
                    Files.createDirectories(cameraDir);
                }

                // Kiểm tra xem có frames nào tồn tại không
                boolean framesExist = false;
                for (int i = 0; i < frameNumber; i++) {
                    String frameName = String.format("frame_%05d.jpg", i);
                    Path framePath = cameraDir.resolve(frameName);
                    if (Files.exists(framePath)) {
                        framesExist = true;
                        System.out.println("Tìm thấy frame " + framePath);
                        break;
                    }
                }

                if (!framesExist) {
                    System.out.println("Không tìm thấy khung hình nào để tạo video cho camera " + cameraId);
                    return;
                }

                LocalDateTime startTime = videoStartTimes.getOrDefault(cameraId, LocalDateTime.now());
                System.out.println("Chuẩn bị tạo video từ các khung hình bắt đầu từ " + startTime);

                // Tạo video ngay (không bất đồng bộ) để đảm bảo video được lưu trước khi tiến
                // trình kết thúc
                createVideoEmergencySync(cameraId, cameraDir, frameNumber, startTime);
            } else {
                System.out.println("Không có khung hình nào để lưu cho camera " + cameraId);
            }
        } catch (Exception e) {
            System.err.println("====> Lỗi khi lưu video khẩn cấp cho camera " + cameraId + ": " + e.getMessage());
            e.printStackTrace();
        }
    }

    // Phương thức đồng bộ để đảm bảo video được lưu trong trường hợp khẩn cấp
    private void createVideoEmergencySync(Long cameraId, Path cameraDir, int upToFrameNumber, LocalDateTime startTime) {
        try {
            System.out.println("Bắt đầu tạo video khẩn cấp (đồng bộ) cho camera " + cameraId);

            Path videosDir = cameraDir.resolve("videos");
            Files.createDirectories(videosDir);

            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
            Path outputPath = videosDir.resolve("emergency_" + timestamp + ".mp4");

            // Sử dụng ffmpeg từ PATH hoặc đường dẫn cụ thể
            String ffmpegPath = "ffmpeg";

            ProcessBuilder pb = new ProcessBuilder(
                    ffmpegPath,
                    "-y",
                    "-framerate", "10",
                    "-i", cameraDir.resolve("frame_%05d.jpg").toString(),
                    "-frames:v", String.valueOf(upToFrameNumber),
                    "-c:v", "libx264",
                    "-pix_fmt", "yuv420p",
                    outputPath.toString());

            System.out.println("Lệnh FFmpeg: " + String.join(" ", pb.command()));

            pb.redirectErrorStream(true);
            Process process = pb.start();

            // Đọc và in log từ FFmpeg
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    System.out.println("[FFmpeg] " + line);
                }
            }

            int exitCode = process.waitFor();
            if (exitCode != 0) {
                System.err.println("FFmpeg exited with error code: " + exitCode);
                return;
            }

            System.out.println("Video khẩn cấp đã được tạo thành công: " + outputPath);

            // Xóa frames đã sử dụng
            int deletedFrames = 0;
            for (int i = 0; i < upToFrameNumber; i++) {
                String frameName = String.format("frame_%05d.jpg", i);
                Path framePath = cameraDir.resolve(frameName);
                try {
                    if (Files.deleteIfExists(framePath)) {
                        deletedFrames++;
                    }
                } catch (IOException e) {
                    System.err.println("Không thể xóa frame: " + framePath + ": " + e.getMessage());
                }
            }

            System.out.println(
                    "Đã xóa " + deletedFrames + "/" + upToFrameNumber + " khung hình sau khi tạo video khẩn cấp");

            // Reset counter
            frameCounters.put(cameraId, 0);

            try {
                // Lưu thông tin vào database
                Camera camera = cameraRepository.findById(cameraId)
                        .orElseThrow(() -> new RuntimeException("Không tìm thấy camera với ID: " + cameraId));

                File videoFile = outputPath.toFile();
                long fileSize = videoFile.length();

                CameraRecording recording = new CameraRecording();
                recording.setCamera(camera);
                recording.setFilePath(outputPath.toString());
                recording.setStartTime(startTime);
                recording.setEndTime(LocalDateTime.now());
                recording.setFileSize(fileSize);
                recording.setDurationSeconds(upToFrameNumber / 10);

                cameraRecordingRepository.save(recording);
                System.out.println("Đã lưu thông tin recording vào database cho camera " + cameraId);
            } catch (Exception e) {
                System.err.println("Lỗi khi lưu thông tin recording vào database: " + e.getMessage());
                e.printStackTrace();
            }

        } catch (Exception e) {
            System.err.println("Lỗi trong createVideoEmergencySync cho camera " + cameraId + ": " + e.getMessage());
            e.printStackTrace();
        }
    }
}