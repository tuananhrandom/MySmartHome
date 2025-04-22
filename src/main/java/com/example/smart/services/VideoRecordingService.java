package com.example.smart.services;

import com.example.smart.entities.Camera;
import com.example.smart.entities.CameraRecording;
import com.example.smart.repositories.CameraRecordingRepository;

import org.bytedeco.ffmpeg.global.avcodec;
import org.bytedeco.javacv.FFmpegFrameRecorder;
// import org.bytedeco.javacv.FFmpegFrameRecorder;
import org.bytedeco.javacv.Frame;
import org.bytedeco.javacv.Java2DFrameConverter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import java.awt.image.*;
import java.io.ByteArrayInputStream;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import javax.imageio.ImageIO;

import jakarta.annotation.PostConstruct;

import com.example.smart.utils.FFmpegVideoRecorder;

@Service
public class VideoRecordingService {
    private static class RecordingWorker implements Runnable {
        private final Long cameraId;
        private final BlockingQueue<byte[]> queue;
        private final FFmpegFrameRecorder recorder;
        private final Java2DFrameConverter converter = new Java2DFrameConverter();
        private volatile boolean running = true;

        public RecordingWorker(Long cameraId, String outputFile) throws Exception {
            this.cameraId = cameraId;
            this.queue = new LinkedBlockingQueue<>();
            this.recorder = new FFmpegFrameRecorder(outputFile, 640, 480);
            recorder.setVideoCodec(avcodec.AV_CODEC_ID_H264);
            recorder.setFormat("mp4");
            recorder.setFrameRate(25);
            recorder.start();
        }

        public void addFrame(byte[] jpegData) {
            queue.offer(jpegData);
        }

        public void stop() throws Exception {
            running = false;
            recorder.stop();
            recorder.release();
        }

        @Override
        public void run() {
            try {
                while (running || !queue.isEmpty()) {
                    byte[] data = queue.poll(1, TimeUnit.SECONDS);
                    if (data != null) {
                        BufferedImage image = ImageIO.read(new ByteArrayInputStream(data));
                        Frame frame = converter.convert(image);
                        recorder.record(frame);
                    }
                }
            } catch (Exception e) {
                e.printStackTrace(); // Có thể log ra file sau
            }
        }
    }

    private final Map<Long, RecordingWorker> workers = new ConcurrentHashMap<>();
    private final Map<Long, Thread> threads = new ConcurrentHashMap<>();

    public void handleFrame(Long cameraId, byte[] jpegData) throws Exception {
        workers.computeIfAbsent(cameraId, id -> {
            try {
                String outputFile = "videos/camera_" + id + "_" + System.currentTimeMillis() + ".mp4";
                RecordingWorker worker = new RecordingWorker(id, outputFile);
                Thread t = new Thread(worker);
                t.start();
                threads.put(id, t);
                return worker;
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }).addFrame(jpegData);
    }

    public void stopRecording(Long cameraId) throws Exception {
        RecordingWorker worker = workers.remove(cameraId);
        Thread thread = threads.remove(cameraId);
        if (worker != null) {
            worker.stop();
        }
        if (thread != null) {
            thread.join(); // Đợi thread kết thúc
        }
    }
}