package com.secuaudit.ws;

import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;

/**
 * WebSocket处理器
 * 用于向MCP Monitor页面实时推送审计事件和告警
 */
@Component
public class AuditWebSocketHandler extends TextWebSocketHandler {

    /** 当前连接的所有WebSocket会话 */
    private final Set<WebSocketSession> sessions = new CopyOnWriteArraySet<>();

    /**
     * WebSocket连接建立时触发
     */
    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        sessions.add(session);
    }

    /**
     * WebSocket连接关闭时触发
     */
    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        sessions.remove(session);
    }

    /**
     * 广播消息给所有连接的客户端
     */
    public void broadcast(String message) {
        TextMessage textMessage = new TextMessage(message);
        for (WebSocketSession session : sessions) {
            try {
                if (session.isOpen()) {
                    session.sendMessage(textMessage);
                }
            } catch (IOException e) {
                sessions.remove(session);
            }
        }
    }
}