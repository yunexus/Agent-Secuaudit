package com.secuaudit.controller;

import com.secuaudit.core.CryptoEngine;
import org.springframework.web.bind.annotation.*;

import java.util.*;

/**
 * 密钥服务器管理接口
 */
@RestController
@RequestMapping("/api/keyservers")
public class KSController {

    private final CryptoEngine engine;

    public KSController(CryptoEngine engine) {
        this.engine = engine;
    }

    /**
     * 获取密钥服务器列表
     */
    @GetMapping
    public List<Map<String, Object>> list() {
        return engine.getKsSummary();
    }

    /**
     * 获取各KS的DKG多项式系数
     */
    @GetMapping("/polynomials")
    public List<Map<String, Object>> polynomials() {
        return engine.getPolynomials();
    }

    /**
     * 切换KS在线/离线状态
     */
    @PostMapping("/{id}/toggle")
    public Map<String, Object> toggle(@PathVariable int id) {
        return engine.toggleKs(id);
    }
}