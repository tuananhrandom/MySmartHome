package com.example.smart.entities;
import jakarta.persistence.Id;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Entity
@Getter
@Setter
@Table(name = "cameras")
public class Camera {
    @Id
    private Long cameraId;

    @Column(name = "cameraName", nullable = false)
    private String cameraName;
    @Column(name = "cameraIp", nullable = false)
    private String cameraIp;
    @Column(name = "cameraStatus", nullable = false)
    private Integer cameraStatus;
    @ManyToOne
    @JoinColumn(name = "userId", nullable = false)
    private User user;

}