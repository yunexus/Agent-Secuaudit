package com.secuaudit.controller;

import com.secuaudit.core.CryptoEngine;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.*;

/**
 * 文件管理接口
 */
@RestController
@RequestMapping("/api/files")
public class FileController {

    private final CryptoEngine engine;

    public FileController(CryptoEngine engine) {
        this.engine = engine;
    }

    /**
     * 获取文件列表
     */
    @GetMapping
    public List<Map<String, Object>> list() {
        return engine.getFiles();
    }

    /**
     * 上传文件并签名
     */
    @PostMapping("/upload")
    public Map<String, Object> upload(
            @RequestParam("file") MultipartFile file,
            @RequestParam("user_id") String userId) {
        try {
            String fileId = file.getOriginalFilename();
            return engine.signFile(fileId, userId, file.getBytes());
        } catch (IOException e) {
            return Map.of("error", "upload_failed", "message", e.getMessage());
        }
    }

    /**
     * 执行文件审计
     */
    @PostMapping("/{fileId}/audit")
    public Map<String, Object> audit(@PathVariable String fileId,
                                     @RequestBody Map<String, Object> body) {
        int challenge = body.get("challenge_blocks") != null
                ? ((Number) body.get("challenge_blocks")).intValue() : 100;
        return engine.audit(fileId, challenge);
    }

    /**
     * 模拟文件篡改
     */
    @PostMapping("/{fileId}/tamper")
    public Map<String, Object> tamper(@PathVariable String fileId) {
        return engine.tamperFile(fileId);
    }
}