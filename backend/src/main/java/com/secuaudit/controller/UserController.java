package com.secuaudit.controller;

import com.secuaudit.core.CryptoEngine;
import org.springframework.web.bind.annotation.*;

import java.util.*;

/**
 * 用户管理接口
 */
@RestController
@RequestMapping("/api/users")
public class UserController {

    private final CryptoEngine engine;

    public UserController(CryptoEngine engine) {
        this.engine = engine;
    }

    /**
     * 获取用户列表
     */
    @GetMapping
    public List<Map<String, Object>> list() {
        return engine.getUsers();
    }

    /**
     * 注册用户（Keygen）
     */
    @PostMapping("/register")
    public Map<String, Object> register(@RequestBody Map<String, Object> body) {
        String userId = (String) body.get("user_id");
        String policy = (String) body.getOrDefault("policy", "confidential");
        String validUntil = (String) body.getOrDefault("valid_until", "2026-12-31");
        return engine.keygen(userId, policy, validUntil);
    }

    /**
     * 撤销用户
     */
    @PostMapping("/{userId}/revoke")
    public Map<String, Object> revoke(@PathVariable String userId) {
        return engine.revokeUser(userId);
    }
}