package com.example.smart.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.example.smart.entities.Light;

@Repository
public interface LightRepositories extends JpaRepository<Light, Long> {
      public Light findByLightName(String lightName);

      public Light findByLightIp(String lightIp);
}
