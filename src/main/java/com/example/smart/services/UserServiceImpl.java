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
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import org.springframework.beans.factory.annotation.Autowired;

import com.example.smart.DTO.AuthRequest;
import com.example.smart.DTO.AuthResponse;
import com.example.smart.DTO.ChangePasswordRequest;
import com.example.smart.DTO.RegisterRequest;
import com.example.smart.DTO.UpdateProfileRequest;
import com.example.smart.entities.Role;
import com.example.smart.entities.User;
import com.example.smart.repositories.UserRepository;
import com.example.smart.security.JwtService;
import com.example.smart.utils.PasswordGenerator;
import com.example.smart.repositories.DeviceActivityRepository;
import com.example.smart.repositories.ScheduleRepository;
import com.example.smart.repositories.NotificationRepository;
import com.example.smart.repositories.PushSubscriptionRepository;
import com.example.smart.repositories.CameraRecordingRepository;

@Service
public class UserServiceImpl implements UserService {

        private final UserRepository userRepository;
        private final PasswordEncoder passwordEncoder;
        private final JwtService jwtService;
        private AuthenticationManager authenticationManager;
        private final EmailService emailService;

        @Autowired
        private DeviceActivityRepository deviceActivityRepository;
        @Autowired
        private ScheduleRepository scheduleRepository;
        @Autowired
        private NotificationRepository notificationRepository;
        @Autowired
        private PushSubscriptionRepository pushSubscriptionRepository;
        @Autowired
        private CameraRecordingRepository cameraRecordingRepository;

        public UserServiceImpl(UserRepository userRepository,
                        PasswordEncoder passwordEncoder,
                        JwtService jwtService,
                        EmailService emailService) {
                this.userRepository = userRepository;
                this.passwordEncoder = passwordEncoder;
                this.jwtService = jwtService;
                this.emailService = emailService;
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
                var jwtToken = jwtService.generateToken(user, user.getUserId());
                // trả về thông tin user và token
                return AuthResponse.builder()
                                .token(jwtToken)
                                .userId(user.getUserId())
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

                var jwtToken = jwtService.generateToken(user, user.getUserId());

                return AuthResponse.builder()
                                .token(jwtToken)
                                .userId(user.getUserId())
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

        @Override
        public boolean changePassword(ChangePasswordRequest request) {
                // Lấy thông tin người dùng hiện tại
                User currentUser = getCurrentUser();

                // Kiểm tra mật khẩu cũ có khớp không
                if (!passwordEncoder.matches(request.getOldPassword(), currentUser.getPassword())) {
                        throw new RuntimeException("Mật khẩu cũ không chính xác");
                }

                // Đặt mật khẩu mới đã được mã hóa
                currentUser.setPassword(passwordEncoder.encode(request.getNewPassword()));

                // Lưu thông tin người dùng với mật khẩu mới
                userRepository.save(currentUser);

                return true;
        }

        @Override
        public boolean resetPassword(String email) {
                try {
                        // Tìm user theo email
                        User user = userRepository.findByEmail(email)
                                        .orElseThrow(() -> new RuntimeException(
                                                        "Không tìm thấy người dùng với email: " + email));

                        // Tạo mật khẩu mới ngẫu nhiên 8 ký tự
                        String newPassword = PasswordGenerator.generateRandomPassword(8);

                        // Cập nhật mật khẩu mới trong database (đã mã hóa)
                        user.setPassword(passwordEncoder.encode(newPassword));
                        userRepository.save(user);

                        // Gửi email chứa mật khẩu mới (bất đồng bộ)
                        String subject = "Đặt lại mật khẩu cho tài khoản Smart Home";
                        String body = "Xin chào " + user.getFullName() + ",\n\n"
                                        + "Mật khẩu mới của bạn là: " + newPassword + "\n\n"
                                        + "Vui lòng đăng nhập và đổi mật khẩu ngay sau khi nhận được email này.\n\n"
                                        + "Trân trọng,\nĐội ngũ Smart Home";

                        // Dùng gửi email bất đồng bộ để không chặn luồng xử lý chính
                        emailService.sendEmailAsync(email, subject, body)
                                        .exceptionally(ex -> {
                                                System.err.println("Lỗi khi gửi email đặt lại mật khẩu: "
                                                                + ex.getMessage());
                                                return null;
                                        });

                        return true;
                } catch (Exception e) {
                        System.err.println("Lỗi khi đặt lại mật khẩu: " + e.getMessage());
                        throw e;
                }
        }

        @Override
        public User updateProfile(UpdateProfileRequest request) {
                // Lấy thông tin người dùng hiện tại
                User currentUser = getCurrentUser();

                // // Kiểm tra email mới có bị trùng với người dùng khác không
                // if (!currentUser.getEmail().equals(request.getEmail()) &&
                // userRepository.existsByEmail(request.getEmail())) {
                // throw new RuntimeException("Email đã được sử dụng bởi tài khoản khác");
                // }

                // Cập nhật thông tin
                currentUser.setFullName(request.getFullName());
                currentUser.setEmail(request.getEmail());

                // Lưu lại vào database
                return userRepository.save(currentUser);
        }

        @Override
        public List<User> getAllUsers() {
                return userRepository.findAll();
        }

        @Override
        public User getUserById(Long userId) {
                return userRepository.findById(userId)
                                .orElseThrow(() -> new RuntimeException("Không tìm thấy người dùng"));
        }

        @Override
        @Transactional
        public boolean deleteUser(Long userId) {
                User user = userRepository.findById(userId)
                                .orElseThrow(() -> new RuntimeException("Không tìm thấy người dùng"));

                // Xử lý bất đồng bộ các thao tác xóa dữ liệu
                CompletableFuture<Void> deleteActivities = CompletableFuture.runAsync(() -> {
                        deviceActivityRepository.findByUser_UserId(userId)
                                        .forEach(activity -> deviceActivityRepository.delete(activity));
                });

                CompletableFuture<Void> deleteSchedules = CompletableFuture.runAsync(() -> {
                        scheduleRepository.findByUser_UserId(userId)
                                        .forEach(schedule -> scheduleRepository.delete(schedule));
                });

                CompletableFuture<Void> deleteNotifications = CompletableFuture.runAsync(() -> {
                        notificationRepository.deleteByUser_UserId(userId);
                });

                CompletableFuture<Void> deleteSubscriptions = CompletableFuture.runAsync(() -> {
                        pushSubscriptionRepository.findByUserId(userId)
                                        .forEach(subscription -> pushSubscriptionRepository.delete(subscription));
                });

                CompletableFuture<Void> deleteCameraRecordings = CompletableFuture.runAsync(() -> {
                        user.getCameras().forEach(camera -> cameraRecordingRepository
                                        .findByCamera_CameraIdOrderByStartTimeDesc(camera.getCameraId())
                                        .forEach(recording -> cameraRecordingRepository.delete(recording)));
                });

                // Xử lý quan hệ chia sẻ camera
                CompletableFuture<Void> handleSharedCameras = CompletableFuture.runAsync(() -> {
                        if (user.getSharedCameras() != null) {
                                user.getSharedCameras().forEach(camera -> {
                                        camera.getSharedUsers().remove(user);
                                });
                        }
                });

                // Đặt owner của các thiết bị về null
                CompletableFuture<Void> clearDeviceOwners = CompletableFuture.runAsync(() -> {
                        user.getDoors().forEach(door -> door.setUser(null));
                        user.getLights().forEach(light -> light.setUser(null));
                        user.getCameras().forEach(camera -> camera.setUser(null));
                });

                try {
                        // Chờ tất cả các thao tác xóa hoàn thành
                        CompletableFuture.allOf(
                                        deleteActivities,
                                        deleteSchedules,
                                        deleteNotifications,
                                        deleteSubscriptions,
                                        deleteCameraRecordings,
                                        handleSharedCameras,
                                        clearDeviceOwners).get(30, TimeUnit.SECONDS); // Timeout sau 30 giây

                        // Xóa người dùng sau khi đã xử lý xong các quan hệ
                        userRepository.delete(user);
                        return true;
                } catch (Exception e) {
                        throw new RuntimeException("Lỗi khi xóa người dùng: " + e.getMessage());
                }
        }

}