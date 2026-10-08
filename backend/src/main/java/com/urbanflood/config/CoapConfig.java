package com.urbanflood.config;

import com.urbanflood.coap.CoapTelemetryResource;
import com.urbanflood.service.TelemetryService;
import org.eclipse.californium.core.CoapServer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * CoAP 配置：启动 Californium CoAP 服务端，挂载遥测资源。
 */
@Configuration
public class CoapConfig {

    @Value("${coap.port:5683}")
    private int port;

    @Bean(initMethod = "start", destroyMethod = "stop")
    @ConditionalOnProperty(prefix = "coap", name = "enabled", havingValue = "true", matchIfMissing = true)
    public CoapServer coapServer(TelemetryService telemetryService) {
        CoapServer server = new CoapServer(port);
        server.add(new CoapTelemetryResource(telemetryService));
        return server;
    }
}
