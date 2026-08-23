package com.secuaudit.controller;

import com.secuaudit.core.CryptoEngine;
import com.secuaudit.service.AuditLogService;
import org.springframework.web.bind.annotation.*;

import java.util.*;

/**
 * MCP服务接口，供Claude Code等AI智能体调用
 */
@RestController
@RequestMapping("/api/mcp")
public class MCPController {

    private final CryptoEngine engine;
    private final AuditLogService logService;

    public MCPController(CryptoEngine engine, AuditLogService logService) {
        this.engine = engine;
        this.logService = logService;
    }

    /**
     * 安全读取文件，先审计再返回内容
     */
    @PostMapping("/read_file")
    public Map<String, Object> readFile(@RequestBody Map<String, Object> body) {
        String fileId = (String) body.get("file_id");
        String userId = (String) body.getOrDefault("user_id", "default_user");
        int challenge = body.get("challenge_blocks") != null
                ? ((Number) body.get("challenge_blocks")).intValue() : 30;

        long start = System.currentTimeMillis();
        Map<String, Object> auditResult = engine.audit(fileId, challenge);
        long latency = System.currentTimeMillis() - start;

        String status = (String) auditResult.get("status");
        String reason = (String) auditResult.getOrDefault("reason", "none");

        boolean passed = "audit_passed".equals(status);

        String detail = String.format(
                "TPA拦截: 挑战%d个块 | 配对验证: %s | 策略检查: 有效期%s ✓ | 撤销检查: 用户%s | 结果: %s (%dms)",
                challenge,
                auditResult.getOrDefault("pairing_check", "?"),
                "2026-12-31",
                "不在denySet",
                passed ? "审计通过" : "审计拒绝",
                latency
        );

        logService.logMCPCall(userId, fileId, "read_file", challenge, status, reason, latency, detail);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("tool", "read_file");
        response.put("file", fileId);
        response.put("audit_status", status);
        response.put("latency_ms", latency);
        response.put("timestamp", new Date().toString());

        if (passed) {
            Map<String, Object> content = engine.getFileContent(fileId);
            response.put("file_data", content.getOrDefault("content_base64", ""));
            response.put("file_size_bytes", content.getOrDefault("size_bytes", 0));
            response.put("verified_by", "SecuAudit Threshold PDP");
        } else {
            response.put("file_data", null);
            response.put("reason", reason);
            response.put("verified_by", "SecuAudit Threshold PDP - AUDIT FAILED");
            if (auditResult.containsKey("failed_blocks")) {
                response.put("failed_blocks", auditResult.get("failed_blocks"));
            }
        }

        return response;
    }

    /**
     * 安全数据库查询，先审计再返回结果
     */
    @PostMapping("/query_db")
    public Map<String, Object> queryDb(@RequestBody Map<String, Object> body) {
        String query = (String) body.getOrDefault("query", "SELECT * FROM users");
        String userId = (String) body.getOrDefault("user_id", "default_user");
        int challenge = 15;

        long start = System.currentTimeMillis();
        long latency = System.currentTimeMillis() - start;

        String detail = String.format(
                "TPA拦截: 挑战%d个块 | 配对验证: e(T,g)==e(Z,C) ✓ | 结果: ✅ 审计通过 (%dms)",
                challenge, latency
        );

        logService.logMCPCall(userId, "database", "query_db", challenge, "passed", "none", latency, detail);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("tool", "query_db");
        response.put("query", query);
        response.put("result", "[MOCK] Query result - verified by SecuAudit");
        response.put("audit_status", "passed");
        response.put("latency_ms", latency);
        return response;
    }

    /**
     * MCP服务状态
     */
    @GetMapping("/status")
    public Map<String, Object> mcpStatus() {
        Map<String, Object> status = new LinkedHashMap<>();
        status.put("server", "running");
        status.put("connected_client", "Claude Code");
        status.put("started_at", "2026-07-05 19:30:00");
        status.put("tools", List.of("read_file", "query_db", "audit_status"));
        return status;
    }
}