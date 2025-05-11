package com.example.smart.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.example.smart.entities.Light;
import java.util.List;

@Repository
public interface LightRepositories extends JpaRepository<Light, Long> {
      public Light findByLightName(String lightName);

      public Light findByLightIp(String lightIp);

      public List<Light> findByUser_UserId(Long userId);

      List<Light> findByLightIdBetween(Long start, Long end);
      
}
