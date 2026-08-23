package com.secuaudit.controller;

import com.secuaudit.core.CryptoEngine;
import com.secuaudit.service.AuditLogService;
import org.springframework.web.bind.annotation.*;

import java.util.*;

/**
 * 系统管理接口
 */
@RestController
@RequestMapping("/api/system")
public class SystemController {

    private final CryptoEngine engine;
    private final AuditLogService logService;

    public SystemController(CryptoEngine engine, AuditLogService logService) {
        this.engine = engine;
        this.logService = logService;
    }

    /**
     * 获取系统状态
     */
    @GetMapping("/status")
    public Map<String, Object> status() {
        return engine.getSystemStatus();
    }

    /**
     * 系统初始化（DKG）
     */
    @PostMapping("/init")
    public Map<String, Object> init(@RequestBody Map<String, Object> body) {
        int numKs = body.get("num_ks") != null ? ((Number) body.get("num_ks")).intValue() : 10;
        int threshold = body.get("threshold") != null ? ((Number) body.get("threshold")).intValue() : 5;
        return engine.setup(numKs, threshold);
    }

    /**
     * 重置系统
     */
    @PostMapping("/reset")
    public Map<String, Object> reset() {
        logService.resetStats();
        return engine.reset();
    }
}