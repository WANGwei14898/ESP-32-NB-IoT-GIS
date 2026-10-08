package com.urbanflood.websocket;

import com.urbanflood.util.JsonUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 实时推送 WebSocket 处理器。
 * 消息结构约定：{ "type": "telemetry" | "alert" | "prediction" | "station" | "pong", "data": any, "timestamp": number }
 */
@Slf4j
@Component
public class RealtimePushHandler extends TextWebSocketHandler {

    private final Map<String, WebSocketSession> sessions = new ConcurrentHashMap<>();

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        sessions.put(session.getId(), session);
        log.info("WebSocket 连接建立: {}，当前连接数 {}", session.getId(), sessions.size());
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        sessions.remove(session.getId());
        log.info("WebSocket 连接关闭: {}，当前连接数 {}", session.getId(), sessions.size());
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        // 心跳：收到 ping 回复 pong
        String payload = message.getPayload();
        if (payload != null && payload.contains("\"ping\"")) {
            sendTo(session, build("pong", null));
        }
    }

    /** 推送遥测数据。 */
    public void pushTelemetry(Object data) {
        broadcast(build("telemetry", data));
    }

    /** 推送告警。 */
    public void pushAlert(Object data) {
        broadcast(build("alert", data));
    }

    /** 推送预测结果。 */
    public void pushPrediction(Object data) {
        broadcast(build("prediction", data));
    }

    /** 推送站点状态更新。 */
    public void pushStation(Object data) {
        broadcast(build("station", data));
    }

    /** 广播消息给所有在线客户端。 */
    private void broadcast(String json) {
        sessions.values().forEach(s -> sendTo(s, json));
    }

    private void sendTo(WebSocketSession session, String json) {
        try {
            if (session.isOpen()) {
                synchronized (session) {
                    session.sendMessage(new TextMessage(json));
                }
            }
        } catch (IOException e) {
            log.warn("WebSocket 消息发送失败: {}", e.getMessage());
        }
    }

    private String build(String type, Object data) {
        StringBuilder sb = new StringBuilder();
        sb.append("{\"type\":\"").append(type).append("\",");
        sb.append("\"data\":").append(data == null ? "null" : JsonUtil.toJson(data)).append(",");
        sb.append("\"timestamp\":").append(System.currentTimeMillis()).append("}");
        return sb.toString();
    }
}
