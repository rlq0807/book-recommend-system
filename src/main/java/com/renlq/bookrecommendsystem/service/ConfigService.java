package com.renlq.bookrecommendsystem.service;

import com.renlq.bookrecommendsystem.entity.SystemConfig;
import com.renlq.bookrecommendsystem.repository.SystemConfigRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ConfigService {

    private final SystemConfigRepository configRepository;

    // 获取 alpha
    public double getAlpha(){

        SystemConfig config =
                configRepository.findByConfigKey("alpha");

        if(config == null){
            return 0.7; // 默认值
        }

        return Double.parseDouble(config.getConfigValue());
    }

    // 修改 alpha
    public void setAlpha(double alpha){
        // 参数检查：确保alpha在0-1之间
        if(alpha < 0 || alpha > 1){
            throw new IllegalArgumentException("Alpha must be between 0 and 1");
        }
        
        // 检查alpha和beta的和是否超过1
        double currentBeta = getBeta();
        if(alpha + currentBeta > 1){
            throw new IllegalArgumentException("Alpha + Beta must not exceed 1");
        }

        SystemConfig config =
                configRepository.findByConfigKey("alpha");

        if(config == null){
            config = new SystemConfig();
            config.setConfigKey("alpha");
        }

        config.setConfigValue(String.valueOf(alpha));

        configRepository.save(config);
    }

    // 获取 beta
    public double getBeta(){

        SystemConfig config =
                configRepository.findByConfigKey("beta");

        if(config == null){
            return 0.3; // 默认值
        }

        return Double.parseDouble(config.getConfigValue());
    }

    // 修改 beta
    public void setBeta(double beta){
        // 参数检查：确保beta在0-1之间
        if(beta < 0 || beta > 1){
            throw new IllegalArgumentException("Beta must be between 0 and 1");
        }
        
        // 检查alpha和beta的和是否超过1
        double currentAlpha = getAlpha();
        if(currentAlpha + beta > 1){
            throw new IllegalArgumentException("Alpha + Beta must not exceed 1");
        }

        SystemConfig config =
                configRepository.findByConfigKey("beta");

        if(config == null){
            config = new SystemConfig();
            config.setConfigKey("beta");
        }

        config.setConfigValue(String.valueOf(beta));

        configRepository.save(config);
    }
}