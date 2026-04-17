package com.renlq.bookrecommendsystem.service;

import com.renlq.bookrecommendsystem.entity.SystemConfig;
import com.renlq.bookrecommendsystem.repository.SystemConfigRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ConfigService {
    private static final Logger logger = LoggerFactory.getLogger(ConfigService.class);

    private final SystemConfigRepository configRepository;

    public double getAlpha(){
        SystemConfig config = configRepository.findByConfigKey("alpha");
        if(config == null){
            return 0.7;
        }
        return Double.parseDouble(config.getConfigValue());
    }

    @Transactional
    public void setAlpha(double alpha){
        if(alpha < 0 || alpha > 1){
            throw new IllegalArgumentException("Alpha must be between 0 and 1");
        }

        double currentBeta = getBeta();
        if(alpha + currentBeta > 1){
            throw new IllegalArgumentException("Alpha + Beta must not exceed 1");
        }

        SystemConfig config = configRepository.findByConfigKey("alpha");
        if(config == null){
            config = new SystemConfig();
            config.setConfigKey("alpha");
        }
        config.setConfigValue(String.valueOf(alpha));
        configRepository.save(config);
    }

    public double getBeta(){
        SystemConfig config = configRepository.findByConfigKey("beta");
        if(config == null){
            return 0.3;
        }
        return Double.parseDouble(config.getConfigValue());
    }

    @Transactional
    public void setBeta(double beta){
        if(beta < 0 || beta > 1){
            throw new IllegalArgumentException("Beta must be between 0 and 1");
        }

        double currentAlpha = getAlpha();
        if(currentAlpha + beta > 1){
            throw new IllegalArgumentException("Alpha + Beta must not exceed 1");
        }

        SystemConfig config = configRepository.findByConfigKey("beta");
        if(config == null){
            config = new SystemConfig();
            config.setConfigKey("beta");
        }
        config.setConfigValue(String.valueOf(beta));
        configRepository.save(config);
    }

    public int getTopN(){
        SystemConfig config = configRepository.findByConfigKey("topN");
        logger.info("getTopN() called, config found: {}", config != null);
        if(config != null) {
            logger.info("TopN value: {}", config.getConfigValue());
        }

        if(config == null){
            logger.info("TopN config not found in database, returning default 3");
            return 3;
        }

        return Integer.parseInt(config.getConfigValue());
    }

    @Transactional
    public void setTopN(int topN){
        if(topN < 1 || topN > 20){
            throw new IllegalArgumentException("TopN must be between 1 and 20");
        }

        logger.info("Setting TopN to: {}", topN);

        SystemConfig config = configRepository.findByConfigKey("topN");
        logger.info("Found existing config: {}", config != null);

        if(config == null){
            config = new SystemConfig();
            config.setConfigKey("topN");
            logger.info("Creating new TopN config");
        }

        config.setConfigValue(String.valueOf(topN));
        configRepository.save(config);
        logger.info("TopN saved successfully");
    }
}