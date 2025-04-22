package com.example.smart.utils;

import org.bytedeco.javacv.*;
import org.bytedeco.ffmpeg.global.avcodec;
import org.bytedeco.ffmpeg.global.avutil;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.time.LocalDateTime;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;

public class FFmpegVideoRecorder {
    private final String outputPath;
    private FFmpegFrameRecorder recorder;
    private int frameCount = 0;
    private boolean active = false;
    private LocalDateTime startTime;
    private Long recordingId;

    public FFmpegVideoRecorder(String outputPath) {
        this.outputPath = outputPath;
    }

    public void start() throws IOException {
        // Tạo recorder với kích thước frame mặc định 640x480
        // Kích thước sẽ được điều chỉnh sau khi nhận được frame đầu tiên
        recorder = new FFmpegFrameRecorder(outputPath, 640, 480);
        configureRecorder();
        recorder.start();

        startTime = LocalDateTime.now();
        active = true;
        frameCount = 0;
    }

    private void configureRecorder() {
        recorder.setVideoCodec(avcodec.AV_CODEC_ID_H264);
        recorder.setFormat("mp4");
        recorder.setPixelFormat(avutil.AV_PIX_FMT_YUV420P);
        recorder.setFrameRate(25); // Tốc độ khung hình
        recorder.setVideoQuality(0); // Chất lượng tốt nhất
        recorder.setVideoOption("preset", "ultrafast");
        recorder.setVideoOption("tune", "zerolatency");
    }

    public void addFrame(byte[] jpegData) throws IOException {
        if (!active || recorder == null) {
            throw new IllegalStateException("Recorder chưa được khởi tạo");
        }

        // Chuyển đổi JPEG byte array thành BufferedImage
        BufferedImage image = ImageIO.read(new ByteArrayInputStream(jpegData));
        if (image == null) {
            throw new IOException("Không thể đọc dữ liệu JPEG");
        }

        // Nếu là frame đầu tiên, điều chỉnh kích thước recorder
        if (frameCount == 0) {
            recorder.stop();
            recorder.release();

            recorder = new FFmpegFrameRecorder(outputPath, image.getWidth(), image.getHeight());
            configureRecorder();
            recorder.start();
        }

        // Chuyển đổi BufferedImage thành Frame
        Java2DFrameConverter converter = new Java2DFrameConverter();
        Frame frame = converter.convert(image);

        // Ghi frame
        recorder.record(frame);
        frameCount++;
    }

    public void stop() throws IOException {
        if (active && recorder != null) {
            recorder.stop();
            recorder.release();
            active = false;
        }
    }

    public boolean isActive() {
        return active;
    }

    public int getDurationInSeconds() {
        if (startTime == null)
            return 0;
        return (int) java.time.Duration.between(startTime, LocalDateTime.now()).getSeconds();
    }

    public int getFrameCount() {
        return frameCount;
    }

    public Long getRecordingId() {
        return recordingId;
    }

    public void setRecordingId(Long recordingId) {
        this.recordingId = recordingId;
    }
}