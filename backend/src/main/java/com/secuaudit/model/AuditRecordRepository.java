package com.secuaudit.model;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

/**
 * 审计记录数据仓库接口
 */
@Repository
public interface AuditRecordRepository extends JpaRepository<AuditRecord, Long> {

    /**
     * 按状态查询，按创建时间倒序
     */
    List<AuditRecord> findByStatusOrderByCreatedAtDesc(String status);

    /**
     * 查询全部，按创建时间倒序
     */
    List<AuditRecord> findAllByOrderByCreatedAtDesc();
}