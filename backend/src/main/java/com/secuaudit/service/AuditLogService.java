package com.secuaudit.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.secuaudit.core.CryptoEngine;
import com.secuaudit.model.AuditRecord;
import com.secuaudit.model.AuditRecordRepository;
import com.secuaudit.ws.AuditWebSocketHandler;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;

/**
 * 审计日志服务
 * 处理审计记录的存储、查询、导出和WebSocket推送
 */
@Service
public class AuditLogService {

    private final AuditRecordRepository repo;
    private final AuditWebSocketHandler wsHandler;
    private final ObjectMapper mapper = new ObjectMapper();

    /** MCP调用统计 */
    private int mcpCallCount = 0;
    private int totalPassed = 0;
    private int totalRejected = 0;
    private long totalLatency = 0;

    public AuditLogService(AuditRecordRepository repo, AuditWebSocketHandler wsHandler) {
        this.repo = repo;
        this.wsHandler = wsHandler;
    }

    /**
     * 记录MCP调用日志
     */
    public AuditRecord logMCPCall(String user, String file, String tool, int challengedBlocks,
                                  String status, String reason, long latencyMs, String detail) {
        mcpCallCount++;
        if ("passed".equals(status)) totalPassed++;
        else if ("rejected".equals(status) || "blocked".equals(status)) totalRejected++;
        totalLatency += latencyMs;

        AuditRecord record = new AuditRecord(
                "audit_" + System.currentTimeMillis(),
                "mcp_call", user, file, tool, challengedBlocks, status, reason, latencyMs, detail
        );
        repo.save(record);

        // WebSocket推送
        try {
            Map<String, Object> event = new LinkedHashMap<>();
            event.put("type", "mcp_call");
            event.put("audit_id", record.getAuditId());
            event.put("user", user);
            event.put("file", file);
            event.put("tool", tool);
            event.put("challenged_blocks", challengedBlocks);
            event.put("status", status);
            event.put("reason", reason);
            event.put("latency_ms", latencyMs);
            event.put("detail", detail);
            event.put("timestamp", LocalDateTime.now().toString());
            event.put("total_calls", mcpCallCount);
            event.put("total_passed", totalPassed);
            event.put("total_rejected", totalRejected);
            event.put("avg_latency_ms", mcpCallCount > 0 ? totalLatency / mcpCallCount : 0);
            wsHandler.broadcast(mapper.writeValueAsString(event));
        } catch (Exception ignored) {}

        return record;
    }

    /**
     * 记录攻击拦截日志
     */
    public void logAttack(String user, String file, String attackType, String detail) {
        AuditRecord record = new AuditRecord(
                "attack_" + System.currentTimeMillis(),
                "attack_blocked", user, file, "read_file", 0,
                "blocked", attackType, 0, detail
        );
        repo.save(record);
        totalRejected++;

        // WebSocket推送告警
        try {
            Map<String, Object> event = new LinkedHashMap<>();
            event.put("type", "attack_alert");
            event.put("attack_type", attackType);
            event.put("user", user);
            event.put("file", file);
            event.put("detail", detail);
            event.put("timestamp", LocalDateTime.now().toString());
            wsHandler.broadcast(mapper.writeValueAsString(event));
        } catch (Exception ignored) {}
    }

    /**
     * 获取最近N条日志
     */
    public List<AuditRecord> getRecentLogs(int limit) {
        List<AuditRecord> all = repo.findAllByOrderByCreatedAtDesc();
        return all.size() > limit ? all.subList(0, limit) : all;
    }

    /**
     * 获取攻击拦截日志
     */
    public List<AuditRecord> getAttackLogs() {
        return repo.findByStatusOrderByCreatedAtDesc("blocked");
    }

    /**
     * 导出CSV
     */
    public String exportCSV() {
        StringBuilder sb = new StringBuilder("Time,EventType,User,File,Tool,ChallengedBlocks,Status,Reason,LatencyMs,Detail\n");
        for (AuditRecord r : repo.findAllByOrderByCreatedAtDesc()) {
            sb.append(r.getCreatedAt()).append(",")
                    .append(r.getEventType()).append(",")
                    .append(r.getUser()).append(",")
                    .append(r.getFile()).append(",")
                    .append(r.getTool()).append(",")
                    .append(r.getChallengedBlocks()).append(",")
                    .append(r.getStatus()).append(",")
                    .append(r.getReason()).append(",")
                    .append(r.getLatencyMs()).append(",")
                    .append("\"").append(r.getDetail() != null ? r.getDetail() : "").append("\"\n");
        }
        return sb.toString();
    }

    /**
     * 获取MCP统计
     */
    public Map<String, Object> getMCPStats() {
        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("total_calls", mcpCallCount);
        stats.put("total_passed", totalPassed);
        stats.put("total_rejected", totalRejected);
        stats.put("avg_latency_ms", mcpCallCount > 0 ? totalLatency / mcpCallCount : 0);
        stats.put("started_at", "2026-07-05 19:30:00");
        return stats;
    }

    /**
     * 重置统计数据
     */
    public void resetStats() {
        mcpCallCount = 0;
        totalPassed = 0;
        totalRejected = 0;
        totalLatency = 0;
        repo.deleteAll();
    }
}
