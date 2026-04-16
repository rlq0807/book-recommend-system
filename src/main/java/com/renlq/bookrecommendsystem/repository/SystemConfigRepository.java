package com.renlq.bookrecommendsystem.repository;

import com.renlq.bookrecommendsystem.entity.SystemConfig;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SystemConfigRepository
        extends JpaRepository<SystemConfig, Integer> {

    SystemConfig findByConfigKey(String configKey);
}