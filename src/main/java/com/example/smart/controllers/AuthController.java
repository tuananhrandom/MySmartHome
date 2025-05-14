package com.example.smart.controllers;

import java.util.HashMap;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.example.smart.DTO.AuthRequest;
import com.example.smart.DTO.AuthResponse;
import com.example.smart.DTO.ChangePasswordRequest;
import com.example.smart.DTO.RegisterRequest;
import com.example.smart.DTO.UpdateProfileRequest;
import com.example.smart.entities.User;
import com.example.smart.services.UserService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/auth")
@CrossOrigin(origins = "*")
@RequiredArgsConstructor
public class AuthController {

    private final UserService userService;

    // đăng ký
    // @PostMapping("/register")
    // public ResponseEntity<AuthResponse> register(@Valid @RequestBody
    // RegisterRequest request) {
    // return ResponseEntity.ok(userService.register(request));
    // }
    @PostMapping("/register")
    public ResponseEntity<?> register(@Valid @RequestBody RegisterRequest request, BindingResult result) {
        // Nếu có lỗi validation, trả về danh sách lỗi
        if (result.hasErrors()) {
            // Tạo một Map để lưu trữ các lỗi validation
            Map<String, String> errors = new HashMap<>();
            // Duyệt qua từng lỗi validation và thêm vào Map
            // Với key là tên trường bị lỗi (error.getField())
            // Và value là thông báo lỗi tương ứng (error.getDefaultMessage())
            result.getFieldErrors().forEach(error -> errors.put(error.getField(), error.getDefaultMessage()));
            return ResponseEntity.badRequest().body(errors);
        }

        // Nếu không có lỗi, thực hiện đăng ký
        return ResponseEntity.ok(userService.register(request));
    }

    // đăng nhập
    @PostMapping("/login")
    public ResponseEntity<AuthResponse> authenticate(@Valid @RequestBody AuthRequest request) {
        return ResponseEntity.ok(userService.authenticate(request));
    }

    // để lấy thông tin user hiện tại
    @GetMapping("/me")
    public ResponseEntity<User> getCurrentUser() {
        return ResponseEntity.ok(userService.getCurrentUser());
    }

    // đổi mật khẩu
    @PostMapping("/change-password")
    public ResponseEntity<?> changePassword(@Valid @RequestBody ChangePasswordRequest request, BindingResult result) {
        // Nếu có lỗi validation, trả về danh sách lỗi
        if (result.hasErrors()) {
            Map<String, String> errors = new HashMap<>();
            result.getFieldErrors().forEach(error -> errors.put(error.getField(), error.getDefaultMessage()));
            return ResponseEntity.badRequest().body(errors);
        }

        try {
            boolean success = userService.changePassword(request);
            if (success) {
                Map<String, String> response = new HashMap<>();
                response.put("message", "Đổi mật khẩu thành công");
                return ResponseEntity.ok(response);
            } else {
                return ResponseEntity.badRequest().body("Đổi mật khẩu thất bại");
            }
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @PostMapping("/forget")
    public ResponseEntity<?> handleForgetPassword(@RequestParam String email) {
        try {
            boolean success = userService.resetPassword(email);
            if (success) {
                Map<String, String> response = new HashMap<>();
                response.put("message", "Mật khẩu mới đã được gửi tới email của bạn");
                return ResponseEntity.ok(response);
            } else {
                return ResponseEntity.badRequest().body("Không thể đặt lại mật khẩu");
            }
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    // cập nhật thông tin tài khoản
    @PutMapping("/profile")
    public ResponseEntity<?> updateProfile(@Valid @RequestBody UpdateProfileRequest request, BindingResult result) {
        // Nếu có lỗi validation, trả về danh sách lỗi
        if (result.hasErrors()) {
            Map<String, String> errors = new HashMap<>();
            result.getFieldErrors().forEach(error -> errors.put(error.getField(), error.getDefaultMessage()));
            return ResponseEntity.badRequest().body(errors);
        }

        try {
            User updatedUser = userService.updateProfile(request);
            return ResponseEntity.ok(updatedUser);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }
}