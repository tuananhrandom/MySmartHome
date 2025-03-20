package com.example.smart.services;

import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.context.annotation.Lazy;
import org.springframework.beans.factory.annotation.Autowired;

import com.example.smart.DTO.AuthRequest;
import com.example.smart.DTO.AuthResponse;
import com.example.smart.DTO.RegisterRequest;
import com.example.smart.entities.Role;
import com.example.smart.entities.User;
import com.example.smart.repositories.UserRepository;
import com.example.smart.security.JwtService;

@Service
public class UserServiceImpl implements UserService {

        private final UserRepository userRepository;
        private final PasswordEncoder passwordEncoder;
        private final JwtService jwtService;
        private AuthenticationManager authenticationManager;

        public UserServiceImpl(UserRepository userRepository,
                        PasswordEncoder passwordEncoder,
                        JwtService jwtService) {
                this.userRepository = userRepository;
                this.passwordEncoder = passwordEncoder;
                this.jwtService = jwtService;
        }

        @Autowired
        public void setAuthenticationManager(@Lazy AuthenticationManager authenticationManager) {
                this.authenticationManager = authenticationManager;
        }

        @Override
        public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
                return userRepository.findByUsername(username)
                                .orElseThrow(
                                                () -> new UsernameNotFoundException(
                                                                "Không tìm thấy người dùng với username: " + username));
        }

        @Override
        public AuthResponse register(RegisterRequest request) {
                // Kiểm tra username và email đã tồn tại chưa
                if (userRepository.existsByUsername(request.getUsername())) {
                        throw new RuntimeException("Username đã tồn tại");
                }

                if (userRepository.existsByEmail(request.getEmail())) {
                        throw new RuntimeException("Email đã tồn tại");
                }

                var user = User.builder()
                                .username(request.getUsername())
                                .password(passwordEncoder.encode(request.getPassword()))
                                .email(request.getEmail())
                                .fullName(request.getFullName())
                                .role(Role.USER)
                                .build();

                userRepository.save(user);
                // tạo token mặc định cho user mới đăng ký
                var jwtToken = jwtService.generateToken(user);
                // trả về thông tin user và token
                return AuthResponse.builder()
                                .token(jwtToken)
                                .username(user.getUsername())
                                .email(user.getEmail())
                                .role(user.getRole().name())
                                .build();
        }

        @Override
        public AuthResponse authenticate(AuthRequest request) {
                authenticationManager.authenticate(
                                new UsernamePasswordAuthenticationToken(
                                                request.getUsername(),
                                                request.getPassword()));

                var user = userRepository.findByUsername(request.getUsername())
                                .orElseThrow(() -> new UsernameNotFoundException("Không tìm thấy người dùng"));

                var jwtToken = jwtService.generateToken(user);

                return AuthResponse.builder()
                                .token(jwtToken)
                                .username(user.getUsername())
                                .email(user.getEmail())
                                .role(user.getRole().name())
                                .build();
        }

        @Override
        public User getCurrentUser() {
                Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
                String username = authentication.getName();
                return userRepository.findByUsername(username)
                                .orElseThrow(() -> new UsernameNotFoundException("Không tìm thấy người dùng hiện tại"));
        }
}