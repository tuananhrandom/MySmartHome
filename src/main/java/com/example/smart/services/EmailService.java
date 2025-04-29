package com.example.smart.services;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class EmailService {

    private final JavaMailSender mailSender;

    // Tạo một executor riêng để xử lý email không chặn luồng chính
    private final Executor emailExecutor = Executors.newFixedThreadPool(3);

    /**
     * Gửi email đồng bộ - sẽ chặn luồng xử lý cho đến khi email được gửi xong
     * Chỉ nên sử dụng cho các tình huống nhất định cần đảm bảo email được gửi đi
     * trước khi tiếp tục
     */
    public void sendEmail(String to, String subject, String body) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setTo(to);
        message.setSubject(subject);
        message.setText(body);

        mailSender.send(message);
    }

    /**
     * Gửi email bất đồng bộ - không chặn luồng xử lý chính
     * Nên sử dụng cho hầu hết các trường hợp, đặc biệt là trong các webhook hoặc xử
     * lý thời gian thực
     */
    public CompletableFuture<Void> sendEmailAsync(String to, String subject, String body) {
        return CompletableFuture.runAsync(() -> {
            try {
                SimpleMailMessage message = new SimpleMailMessage();
                message.setTo(to);
                message.setSubject(subject);
                message.setText(body);

                System.out.println("Đang gửi email bất đồng bộ đến: " + to);
                mailSender.send(message);
                System.out.println("Đã gửi email bất đồng bộ thành công đến: " + to);
            } catch (Exception e) {
                System.err.println("Lỗi khi gửi email bất đồng bộ: " + e.getMessage());
                e.printStackTrace();
            }
        }, emailExecutor);
    }
}