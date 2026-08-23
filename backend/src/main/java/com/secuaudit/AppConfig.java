package com.secuaudit;

import com.secuaudit.core.CryptoEngine;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 应用配置类
 */
@Configuration
public class AppConfig {

    /**
     * 注册密码学引擎Bean
     */
    @Bean
    public CryptoEngine cryptoEngine() {
        return new CryptoEngine();
    }
}