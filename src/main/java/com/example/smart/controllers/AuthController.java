package com.example.smart.controllers;

import java.util.HashMap;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.smart.DTO.AuthRequest;
import com.example.smart.DTO.AuthResponse;
import com.example.smart.DTO.RegisterRequest;
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
    
}