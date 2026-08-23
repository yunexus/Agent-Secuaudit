package com.secuaudit.ws;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

/**
 * WebSocket配置类
 * 注册审计事件推送端点
 */
@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    private final AuditWebSocketHandler auditHandler;

    public WebSocketConfig(AuditWebSocketHandler auditHandler) {
        this.auditHandler = auditHandler;
    }

    /**
     * 注册WebSocket处理器
     * 端点路径：/ws/audit
     * 允许所有来源跨域访问
     */
    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(auditHandler, "/ws/audit")
                .setAllowedOrigins("*");
    }
}