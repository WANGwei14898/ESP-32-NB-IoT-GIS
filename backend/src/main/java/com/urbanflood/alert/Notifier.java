package com.urbanflood.alert;

import com.urbanflood.entity.Alert;
import com.urbanflood.entity.PushLog;
import com.urbanflood.entity.Station;
import com.urbanflood.mapper.PushLogMapper;
import com.urbanflood.websocket.RealtimePushHandler;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;

/**
 * 告警通知推送器，模拟短信 / 微信 / 邮件 / App 推送。
 * <p>
 * 短信、微信、App 采用日志模拟；邮件通过 JavaMailSender 发送；Webhook 通过 HTTP 调用；
 * 所有渠道均写入 push_log 表，同时通过 WebSocket 实时推送。
 * </p>
 */
@Slf4j
@Component
public class Notifier {

    private final PushLogMapper pushLogMapper;
    private final RealtimePushHandler realtimePushHandler;
    private final RestTemplate restTemplate = new RestTemplate();

    /** 邮件发送器，未配置 SMTP 时可为空。 */
    @Autowired(required = false)
    private JavaMailSender mailSender;

    @Value("${push.mail.to:alert@example.com}")
    private String mailTo;

    @Value("${push.mail.from:no-reply@urbanflood.local}")
    private String mailFrom;

    @Value("${push.webhook-url:}")
    private String webhookUrl;

    @Value("${push.sms-target:13800000000}")
    private String smsTarget;

    public Notifier(PushLogMapper pushLogMapper, RealtimePushHandler realtimePushHandler) {
        this.pushLogMapper = pushLogMapper;
        this.realtimePushHandler = realtimePushHandler;
    }

    /** 推送一条告警到所有渠道。 */
    public void notify(Alert alert, Station station) {
        String content = buildContent(alert, station);

        pushSms(alert, content);
        pushWechat(alert, content);
        pushEmail(alert, content);
        pushApp(alert, content);
        pushWebhook(alert, content);
        pushWebSocket(alert);
    }

    private String buildContent(Alert alert, Station station) {
        String stationName = station != null ? station.getName() : ("站点" + alert.getStationId());
        return String.format("【城市内涝预警】%s 触发%s：%s（当前水位 %s m）",
                stationName, levelText(alert.getLevel()), alert.getMessage(), alert.getWaterLevel());
    }

    private String levelText(String level) {
        return switch (level == null ? "" : level) {
            case "BLUE" -> "蓝色预警";
            case "YELLOW" -> "黄色预警";
            case "ORANGE" -> "橙色预警";
            case "RED" -> "红色预警";
            default -> "预警";
        };
    }

    private void pushSms(Alert alert, String content) {
        log.info("[短信推送] 目标={} 内容={}", smsTarget, content);
        saveLog(alert.getId(), "SMS", smsTarget, content, "SUCCESS");
    }

    private void pushWechat(Alert alert, String content) {
        log.info("[微信推送] 内容={}", content);
        saveLog(alert.getId(), "WECHAT", "flood-alert-group", content, "SUCCESS");
    }

    private void pushEmail(Alert alert, String content) {
        try {
            if (mailSender == null) {
                log.info("[邮件推送-模拟] 未配置 SMTP，目标={} 内容={}", mailTo, content);
                saveLog(alert.getId(), "EMAIL", mailTo, content, "SUCCESS");
                return;
            }
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(mailFrom);
            message.setTo(mailTo);
            message.setSubject("城市内涝监测告警");
            message.setText(content);
            mailSender.send(message);
            log.info("[邮件推送] 目标={} 内容={}", mailTo, content);
            saveLog(alert.getId(), "EMAIL", mailTo, content, "SUCCESS");
        } catch (Exception e) {
            log.error("[邮件推送] 发送失败: {}", e.getMessage());
            saveLog(alert.getId(), "EMAIL", mailTo, content, "FAILED");
        }
    }

    private void pushApp(Alert alert, String content) {
        log.info("[App推送] 内容={}", content);
        saveLog(alert.getId(), "APP", "all-devices", content, "SUCCESS");
    }

    private void pushWebhook(Alert alert, String content) {
        if (webhookUrl == null || webhookUrl.isBlank()) {
            log.info("[Webhook推送-模拟] 未配置 webhook-url，内容={}", content);
            saveLog(alert.getId(), "WEBHOOK", "webhook", content, "SUCCESS");
            return;
        }
        try {
            restTemplate.postForEntity(webhookUrl, content, String.class);
            log.info("[Webhook推送] url={} 内容={}", webhookUrl, content);
            saveLog(alert.getId(), "WEBHOOK", webhookUrl, content, "SUCCESS");
        } catch (Exception e) {
            log.error("[Webhook推送] 失败: {}", e.getMessage());
            saveLog(alert.getId(), "WEBHOOK", webhookUrl, content, "FAILED");
        }
    }

    private void pushWebSocket(Alert alert) {
        realtimePushHandler.pushAlert(alert);
        saveLog(alert.getId(), "WEBSOCKET", "all-clients", alert.getMessage(), "SUCCESS");
    }

    private void saveLog(Long alertId, String channel, String target, String content, String status) {
        PushLog logEntity = new PushLog();
        logEntity.setAlertId(alertId);
        logEntity.setChannel(channel);
        logEntity.setTarget(target);
        logEntity.setContent(content);
        logEntity.setStatus(status);
        logEntity.setPushTime(LocalDateTime.now());
        pushLogMapper.insert(logEntity);
    }
}
