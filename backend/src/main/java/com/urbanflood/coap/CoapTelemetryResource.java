package com.urbanflood.coap;

import com.fasterxml.jackson.databind.JsonNode;
import com.urbanflood.service.TelemetryService;
import com.urbanflood.util.JsonUtil;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.californium.core.CoapResource;
import org.eclipse.californium.core.coap.CoAP;
import org.eclipse.californium.core.server.resources.CoapExchange;

import java.nio.charset.StandardCharsets;

/**
 * CoAP 遥测资源：接收 PUT /telemetry/{station_id} 上报的遥测数据。
 */
@Slf4j
public class CoapTelemetryResource extends CoapResource {

    private final TelemetryService telemetryService;

    public CoapTelemetryResource(TelemetryService telemetryService) {
        super("telemetry");
        this.telemetryService = telemetryService;
    }

    /** 动态匹配 /telemetry/{station_id} 子路径。 */
    @Override
    public CoapResource getChild(String name) {
        return new StationTelemetryResource(name, telemetryService);
    }

    /**
     * 单个站点的遥测子资源。
     */
    private static class StationTelemetryResource extends CoapResource {

        private final String stationCode;
        private final TelemetryService telemetryService;

        StationTelemetryResource(String stationCode, TelemetryService telemetryService) {
            super(stationCode);
            this.stationCode = stationCode;
            this.telemetryService = telemetryService;
        }

        @Override
        public void handlePUT(CoapExchange exchange) {
            process(exchange);
        }

        @Override
        public void handlePOST(CoapExchange exchange) {
            process(exchange);
        }

        private void process(CoapExchange exchange) {
            byte[] payload = exchange.getRequestPayload();
            if (payload == null || payload.length == 0) {
                exchange.respond(CoAP.ResponseCode.BAD_REQUEST, "empty payload");
                return;
            }
            String json = new String(payload, StandardCharsets.UTF_8);
            JsonNode node = JsonUtil.parse(json);
            if (node == null) {
                exchange.respond(CoAP.ResponseCode.BAD_REQUEST, "invalid json");
                return;
            }
            try {
                telemetryService.processInbound(node, stationCode);
                exchange.respond(CoAP.ResponseCode.CHANGED);
            } catch (Exception e) {
                log.error("CoAP 遥测处理失败: {}", e.getMessage(), e);
                exchange.respond(CoAP.ResponseCode.INTERNAL_SERVER_ERROR, e.getMessage());
            }
        }
    }
}
