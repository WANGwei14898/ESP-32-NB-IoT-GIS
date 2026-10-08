package com.urbanflood.config;

import com.urbanflood.websocket.RealtimePushHandler;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

/**
 * WebSocket 配置：注册实时推送端点 /ws/realtime。
 */
@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    private final RealtimePushHandler realtimePushHandler;

    public WebSocketConfig(RealtimePushHandler realtimePushHandler) {
        this.realtimePushHandler = realtimePushHandler;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(realtimePushHandler, "/ws/realtime").setAllowedOriginPatterns("*");
    }
}
