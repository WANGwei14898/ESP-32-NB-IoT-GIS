package com.urbanflood.config;

import com.urbanflood.coap.CoapTelemetryResource;
import com.urbanflood.service.TelemetryService;
import org.eclipse.californium.core.CoapServer;
import org.eclipse.californium.elements.config.Configuration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;

/**
 * CoAP 配置：启动 Californium CoAP 服务端，挂载遥测资源。
 */
@org.springframework.context.annotation.Configuration
public class CoapConfig {

    @Value("${coap.port:5683}")
    private int port;

    @Bean(initMethod = "start", destroyMethod = "stop")
    @ConditionalOnProperty(prefix = "coap", name = "enabled", havingValue = "true", matchIfMissing = true)
    public CoapServer coapServer(TelemetryService telemetryService) {
        // 显式创建标准配置（不读取任何文件），避免 Californium3.properties 为空导致异常
        Configuration config = Configuration.createStandardWithoutFile();
        config.set(org.eclipse.californium.core.config.CoapConfig.COAP_PORT, port);
        CoapServer server = new CoapServer(config);
        server.add(new CoapTelemetryResource(telemetryService));
        return server;
    }
}
