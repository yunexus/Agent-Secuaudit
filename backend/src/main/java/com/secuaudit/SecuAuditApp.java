package com.secuaudit;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * SecuAudit系统启动类
 */
@SpringBootApplication
public class SecuAuditApp {

    public static void main(String[] args) {
        SpringApplication.run(SecuAuditApp.class, args);
        System.out.println("============================================");
        System.out.println("  SecuAudit Web Dashboard Started");
        System.out.println("  http://localhost:8080");
        System.out.println("  MCP Monitor WebSocket: ws://localhost:8080/ws/audit");
        System.out.println("============================================");
    }
}
