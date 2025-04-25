package com.example.smart.services;

import org.springframework.security.core.userdetails.UserDetailsService;

import com.example.smart.DTO.AuthRequest;
import com.example.smart.DTO.AuthResponse;
import com.example.smart.DTO.ChangePasswordRequest;
import com.example.smart.DTO.RegisterRequest;
import com.example.smart.DTO.UpdateProfileRequest;
import com.example.smart.entities.User;

public interface UserService extends UserDetailsService {
    AuthResponse register(RegisterRequest request);

    AuthResponse authenticate(AuthRequest request);

    User getCurrentUser();
    
    boolean changePassword(ChangePasswordRequest request);
    
    boolean resetPassword(String email);
    
    User updateProfile(UpdateProfileRequest request);
}