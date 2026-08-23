package com.secuaudit.controller;

import com.secuaudit.model.AuditRecord;
import com.secuaudit.service.AuditLogService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;

/**
 * 审计日志REST接口
 * 路径前缀：/api/audit-log
 */
@RestController
@RequestMapping("/api/audit-log")
public class AuditLogController {

    private final AuditLogService service;

    public AuditLogController(AuditLogService service) {
        this.service = service;
    }

    /**
     * 查询最近审计日志
     *
     * @param limit 返回条数，默认50
     * @return 审计记录列表
     */
    @GetMapping
    public List<AuditRecord> list(@RequestParam(defaultValue = "50") int limit) {
        return service.getRecentLogs(limit);
    }

    /**
     * 查询攻击拦截记录
     *
     * @return 拦截记录列表
     */
    @GetMapping("/attacks")
    public List<AuditRecord> attacks() {
        return service.getAttackLogs();
    }

    /**
     * 导出审计日志CSV
     *
     * @return CSV文件
     */
    @GetMapping("/export")
    public ResponseEntity<String> export() {
        String csv = service.exportCSV();
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=audit_log.csv")
                .header(HttpHeaders.CONTENT_TYPE, "text/csv; charset=UTF-8")
                .body(csv);
    }

    /**
     * 查询MCP调用统计
     *
     * @return 统计信息
     */
    @GetMapping("/mcp-stats")
    public Map<String, Object> mcpStats() {
        return service.getMCPStats();
    }
}