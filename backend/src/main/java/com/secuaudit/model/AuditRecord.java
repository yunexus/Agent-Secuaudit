package com.secuaudit.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * 审计记录实体类
 * 映射数据库表audit_records，记录所有审计事件
 */
@Entity
@Table(name = "audit_records")
public class AuditRecord {

    /** 主键ID，自增 */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 审计ID，唯一标识一次审计 */
    @Column(name = "audit_id")
    private String auditId;

    /** 事件类型：mcp_call / audit / attack_blocked */
    @Column(name = "event_type")
    private String eventType;

    /** 发起审计的用户 */
    @Column(name = "\"user\"")
    private String user;

    /** 被审计的文件 */
    private String file;

    /** 调用的工具：read_file / query_db */
    private String tool;

    /** 挑战的块数 */
    @Column(name = "challenged_blocks")
    private int challengedBlocks;

    /** 审计状态：passed / rejected / blocked */
    private String status;

    /** 失败原因 */
    private String reason;

    /** 响应延迟（毫秒） */
    private long latencyMs;

    /** 详细信息 */
    @Column(length = 2000)
    private String detail;

    /** 创建时间 */
    @Column(name = "created_at")
    private LocalDateTime createdAt = LocalDateTime.now();

    /** 无参构造函数（JPA要求） */
    public AuditRecord() {}

    /** 全参构造函数 */
    public AuditRecord(String auditId, String eventType, String user, String file, String tool,
                       int challengedBlocks, String status, String reason, long latencyMs, String detail) {
        this.auditId = auditId;
        this.eventType = eventType;
        this.user = user;
        this.file = file;
        this.tool = tool;
        this.challengedBlocks = challengedBlocks;
        this.status = status;
        this.reason = reason;
        this.latencyMs = latencyMs;
        this.detail = detail;
        this.createdAt = LocalDateTime.now();
    }
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getAuditId() { return auditId; }
    public void setAuditId(String auditId) { this.auditId = auditId; }
    public String getEventType() { return eventType; }
    public void setEventType(String eventType) { this.eventType = eventType; }
    public String getUser() { return user; }
    public void setUser(String user) { this.user = user; }
    public String getFile() { return file; }
    public void setFile(String file) { this.file = file; }
    public String getTool() { return tool; }
    public void setTool(String tool) { this.tool = tool; }
    public int getChallengedBlocks() { return challengedBlocks; }
    public void setChallengedBlocks(int challengedBlocks) { this.challengedBlocks = challengedBlocks; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
    public long getLatencyMs() { return latencyMs; }
    public void setLatencyMs(long latencyMs) { this.latencyMs = latencyMs; }
    public String getDetail() { return detail; }
    public void setDetail(String detail) { this.detail = detail; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}