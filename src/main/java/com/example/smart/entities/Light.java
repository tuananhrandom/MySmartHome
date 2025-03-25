package com.example.smart.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.FetchType;

@Entity
@Getter
@Setter
@Table(name = "lights")
public class Light {

    // @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Id
    Long lightId;

    @Column(name = "lightname", nullable = true)
    private String lightName;

    @Column(name = "lightstatus")
    private Integer lightStatus = null;

    @Column(name = "lightIP")
    private String lightIp = null;
    @ManyToOne(fetch = FetchType.LAZY)
    @JsonIgnore
    @JoinColumn(name = "ownerId", nullable = true)
    User user;

}
