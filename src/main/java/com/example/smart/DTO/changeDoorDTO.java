package com.example.smart.DTO;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class changeDoorDTO {
    private Integer doorLockDown;
    private Integer doorStatus;
    private Integer doorAlert;
    private String doorIp;
}
