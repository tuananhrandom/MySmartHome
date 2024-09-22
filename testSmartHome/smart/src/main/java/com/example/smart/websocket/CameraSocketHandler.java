package com.example.smart.websocket;

import org.bytedeco.javacv.FFmpegFrameRecorder;
import org.bytedeco.javacv.Frame;
import org.bytedeco.javacv.Java2DFrameConverter;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.BinaryMessage;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.BinaryWebSocketHandler;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.util.concurrent.*;
import java.util.logging.Level;
import java.util.logging.Logger;

@Component
public class CameraSocketHandler extends BinaryWebSocketHandler {

    private final CopyOnWriteArrayList<WebSocketSession> sessions = new CopyOnWriteArrayList<>();
    private final BlockingQueue<BufferedImage> frameQueue = new LinkedBlockingQueue<>(750); // Lưu tối đa 750 khung hình
    private final Logger logger = Logger.getLogger(CameraSocketHandler.class.getName());

    // Cài đặt về tốc độ khung hình và độ phân giải
    private static final int FRAME_RATE = 15;
    private static final int WIDTH = 1280;
    private static final int HEIGHT = 720;
    private static final int VIDEO_DURATION_FRAMES = FRAME_RATE * 30; // Số lượng khung hình cần thiết cho video 30 giây

    // Executor cho việc lưu video
    private final ExecutorService videoSavingExecutor = Executors.newSingleThreadExecutor();

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        sessions.add(session);
        logger.info("WebSocket connected: " + session.getId());
    }

    @Override
    protected void handleBinaryMessage(WebSocketSession session, BinaryMessage message) throws Exception {
        byte[] payload = message.getPayload().array();

        // Chuyển byte[] JPEG thành BufferedImage
        BufferedImage frame = ImageIO.read(new ByteArrayInputStream(payload));

        if (frame != null) {
            // Nếu đã đủ 750 khung hình thì bắt đầu lưu video
            if (frameQueue.size() >= VIDEO_DURATION_FRAMES) {
                logger.info("Đã đủ khung hình, bắt đầu lưu video...");
                saveVideoAsync(); // Gọi hàm lưu video không đồng bộ
            }

            // Thêm khung hình mới vào hàng đợi
            if (!frameQueue.offer(frame)) {
                logger.warning("Frame queue đầy, khung hình bị bỏ qua.");
            }
        }

        broadcast(payload); // Phát khung hình cho tất cả các WebSocket client kết nối
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        sessions.remove(session);
        logger.info("WebSocket disconnected: " + session.getId());
    }

    private void broadcast(byte[] data) {
        sessions.forEach(session -> {
            if (session.isOpen()) {
                try {
                    session.sendMessage(new BinaryMessage(data));
                } catch (IOException e) {
                    logger.log(Level.SEVERE, "Lỗi khi gửi khung hình đến WebSocket session", e);
                }
            }
        });
    }

    private void saveVideoAsync() {
        videoSavingExecutor.submit(this::saveVideo); // Gửi tác vụ lưu video vào luồng riêng
    }

    private void saveVideo() {
        System.out.println("luu nhe !!!!!!!!!!!!!!!!!");
        try {
            File file = new File("./CameraStream/" + System.currentTimeMillis() + ".mp4");
            try (FFmpegFrameRecorder recorder = new FFmpegFrameRecorder(file, WIDTH, HEIGHT)) {
                recorder.setFrameRate(FRAME_RATE);
                recorder.setVideoCodec(org.bytedeco.ffmpeg.global.avcodec.AV_CODEC_ID_H264);
                recorder.setFormat("mp4");
                recorder.setPixelFormat(org.bytedeco.ffmpeg.global.avutil.AV_PIX_FMT_YUV420P);

                recorder.start();
                Java2DFrameConverter converter = new Java2DFrameConverter();

                // Lưu chính xác 30 giây khung hình (450 khung hình với 15 FPS)
                for (int i = 0; i < VIDEO_DURATION_FRAMES; i++) {
                    BufferedImage bufferedImage = frameQueue.poll();
                    if (bufferedImage != null) {
                        Frame frame = converter.convert(bufferedImage);
                        recorder.record(frame);
                    }
                }

                recorder.stop();
                logger.info("Video 30 giây đã được lưu tại: " + file.getAbsolutePath());
            } catch (Exception e) {
                logger.log(Level.SEVERE, "Lỗi khi lưu video", e);
            }
        } catch (Exception exception) {
            System.out.println("Loi tao file");
        }

        // Sau khi lưu video, hàng đợi được làm trống để tiếp tục thu thập khung hình
        // mới
        frameQueue.clear();
    }
}
