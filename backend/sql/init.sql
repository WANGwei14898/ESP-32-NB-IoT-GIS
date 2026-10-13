-- ============================================================
-- 城市内涝监测预警系统 - 数据库初始化脚本
-- 数据库：flood_monitor
-- 说明：包含 station、device、telemetry、alert、threshold、
--       prediction、push_log、user 八张表，并写入初始种子数据。
-- 用法：mysql -uroot -p < init.sql
-- ============================================================

CREATE DATABASE IF NOT EXISTS flood_monitor DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;
USE flood_monitor;

-- ------------------------------------------------------------
-- 监测站点表
-- ------------------------------------------------------------
DROP TABLE IF EXISTS station;
CREATE TABLE station (
    id            BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    code          VARCHAR(32)  NOT NULL COMMENT '站点编号（如 S001）',
    name          VARCHAR(128) NOT NULL COMMENT '站点名称',
    longitude     DECIMAL(10, 6) NULL COMMENT '经度（WGS84）',
    latitude      DECIMAL(10, 6) NULL COMMENT '纬度（WGS84）',
    address       VARCHAR(255) NULL COMMENT '详细地址',
    region        VARCHAR(64)  NULL COMMENT '所属区域',
    device_id     BIGINT       NULL COMMENT '关联设备 ID',
    status        VARCHAR(16)  NOT NULL DEFAULT 'ONLINE' COMMENT '状态：ONLINE/OFFLINE/FAULT',
    install_time  DATETIME     NULL COMMENT '安装时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_code (code)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '监测站点表';

-- ------------------------------------------------------------
-- 监测设备表
-- ------------------------------------------------------------
DROP TABLE IF EXISTS device;
CREATE TABLE device (
    id             BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    code           VARCHAR(64)  NOT NULL COMMENT '设备编号',
    name           VARCHAR(128) NOT NULL COMMENT '设备名称',
    station_id     BIGINT       NULL COMMENT '关联站点 ID',
    type           VARCHAR(32)  NULL COMMENT '设备类型：NB-IoT/LoRa/4G',
    protocol       VARCHAR(16)  NULL COMMENT '上报协议：MQTT/CoAP',
    status         VARCHAR(16)  NOT NULL DEFAULT 'OFFLINE' COMMENT '状态：ONLINE/OFFLINE/FAULT',
    last_heartbeat DATETIME     NULL COMMENT '最近心跳时间',
    battery        DECIMAL(5, 2) NULL COMMENT '电量（%）',
    signal_strength DECIMAL(6, 2) NULL COMMENT '信号强度（dBm）',
    firmware       VARCHAR(32)  NULL COMMENT '固件版本',
    install_time   DATETIME     NULL COMMENT '安装时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_code (code),
    KEY idx_station (station_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '监测设备表';

-- ------------------------------------------------------------
-- 遥测数据表（按月分区，按时间建索引）
-- 注意：分区键 ts 必须包含在主键中；新增月份分区需定期维护。
-- ------------------------------------------------------------
DROP TABLE IF EXISTS telemetry;
CREATE TABLE telemetry (
    id           BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键',
    station_id   BIGINT        NOT NULL COMMENT '站点 ID',
    device_id    BIGINT        NULL COMMENT '设备 ID',
    water_level  DECIMAL(10, 3) NULL COMMENT '水位（米）',
    rainfall     DECIMAL(10, 2) NULL COMMENT '雨量（mm/h）',
    flow_velocity DECIMAL(10, 3) NULL COMMENT '流速（m/s）',
    battery      DECIMAL(5, 2) NULL COMMENT '电量（%）',
    signal_strength DECIMAL(6, 2) NULL COMMENT '信号强度（dBm）',
    ts           DATETIME      NOT NULL COMMENT '采集时间',
    PRIMARY KEY (id, ts),
    KEY idx_station_ts (station_id, ts),
    KEY idx_ts (ts)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '遥测数据表'
PARTITION BY RANGE (TO_DAYS(ts)) (
    PARTITION p202601 VALUES LESS THAN (TO_DAYS('2026-02-01')),
    PARTITION p202602 VALUES LESS THAN (TO_DAYS('2026-03-01')),
    PARTITION p202603 VALUES LESS THAN (TO_DAYS('2026-04-01')),
    PARTITION p202604 VALUES LESS THAN (TO_DAYS('2026-05-01')),
    PARTITION p202605 VALUES LESS THAN (TO_DAYS('2026-06-01')),
    PARTITION p202606 VALUES LESS THAN (TO_DAYS('2026-07-01')),
    PARTITION p202607 VALUES LESS THAN (TO_DAYS('2026-08-01')),
    PARTITION p202608 VALUES LESS THAN (TO_DAYS('2026-09-01')),
    PARTITION p202609 VALUES LESS THAN (TO_DAYS('2026-10-01')),
    PARTITION p202610 VALUES LESS THAN (TO_DAYS('2026-11-01')),
    PARTITION p202611 VALUES LESS THAN (TO_DAYS('2026-12-01')),
    PARTITION p202612 VALUES LESS THAN (TO_DAYS('2027-01-01')),
    PARTITION pmax VALUES LESS THAN MAXVALUE
);

-- ------------------------------------------------------------
-- 告警表
-- ------------------------------------------------------------
DROP TABLE IF EXISTS alert;
CREATE TABLE alert (
    id           BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    station_id   BIGINT       NOT NULL COMMENT '站点 ID',
    level        VARCHAR(16)  NOT NULL COMMENT '预警等级：BLUE/YELLOW/ORANGE/RED',
    type         VARCHAR(16)  NOT NULL COMMENT '告警类型：WATER/RAIN/FLOW/BATTERY/SIGNAL',
    message      VARCHAR(255) NULL COMMENT '告警描述',
    water_level  DECIMAL(10, 3) NULL COMMENT '触发水位（米）',
    threshold    DECIMAL(10, 3) NULL COMMENT '触发阈值',
    status       VARCHAR(16)  NOT NULL DEFAULT 'ACTIVE' COMMENT '状态：ACTIVE/HANDLED',
    handler_name VARCHAR(64)  NULL COMMENT '处理人',
    remark       VARCHAR(255) NULL COMMENT '处理备注',
    handled_time DATETIME     NULL COMMENT '处理时间',
    create_time  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (id),
    KEY idx_station (station_id),
    KEY idx_status (status),
    KEY idx_create_time (create_time)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '告警表';

-- ------------------------------------------------------------
-- 告警阈值配置表
-- ------------------------------------------------------------
DROP TABLE IF EXISTS threshold;
CREATE TABLE threshold (
    id      BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    code    VARCHAR(32)  NOT NULL COMMENT '阈值编码',
    name    VARCHAR(64)  NOT NULL COMMENT '阈值名称',
    `value` DECIMAL(10, 3) NOT NULL COMMENT '阈值数值',
    unit    VARCHAR(16)  NULL COMMENT '单位',
    level   VARCHAR(16)  NULL COMMENT '关联预警等级',
    enabled TINYINT(1)   NOT NULL DEFAULT 1 COMMENT '是否启用',
    PRIMARY KEY (id),
    UNIQUE KEY uk_code (code)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '告警阈值配置表';

-- ------------------------------------------------------------
-- 水位预测结果表
-- ------------------------------------------------------------
DROP TABLE IF EXISTS prediction;
CREATE TABLE prediction (
    id           BIGINT      NOT NULL AUTO_INCREMENT COMMENT '主键',
    station_id   BIGINT      NOT NULL COMMENT '站点 ID',
    model_type   VARCHAR(32) NULL COMMENT '模型类型：moving_average/arima/lstm',
    predict_time DATETIME    NOT NULL COMMENT '预测生成时间',
    points       TEXT        NULL COMMENT '预测曲线点（JSON 数组）',
    PRIMARY KEY (id),
    KEY idx_station (station_id),
    KEY idx_predict_time (predict_time)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '水位预测结果表';

-- ------------------------------------------------------------
-- 告警推送记录表
-- ------------------------------------------------------------
DROP TABLE IF EXISTS push_log;
CREATE TABLE push_log (
    id        BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    alert_id  BIGINT       NOT NULL COMMENT '关联告警 ID',
    channel   VARCHAR(16)  NOT NULL COMMENT '渠道：SMS/WECHAT/EMAIL/APP/WEBHOOK/WEBSOCKET',
    target    VARCHAR(128) NULL COMMENT '推送目标',
    content   VARCHAR(512) NULL COMMENT '推送内容',
    status    VARCHAR(16)  NOT NULL DEFAULT 'SUCCESS' COMMENT '状态：SUCCESS/FAILED',
    push_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '推送时间',
    PRIMARY KEY (id),
    KEY idx_alert (alert_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '告警推送记录表';

-- ------------------------------------------------------------
-- 系统用户表
-- ------------------------------------------------------------
DROP TABLE IF EXISTS `user`;
CREATE TABLE `user` (
    id          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    username    VARCHAR(64)  NOT NULL COMMENT '登录名',
    password    VARCHAR(128) NOT NULL COMMENT '密码（SHA-256 摘要）',
    nickname    VARCHAR(64)  NULL COMMENT '昵称',
    role        VARCHAR(16)  NOT NULL DEFAULT 'OPERATOR' COMMENT '角色：ADMIN/OPERATOR/VIEWER',
    create_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_username (username)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '系统用户表';

-- ============================================================
-- 种子数据
-- ============================================================

-- 监测站点（广州市区近似坐标，WGS84）
INSERT INTO station (code, name, longitude, latitude, region, status, install_time) VALUES
('S001', '天河区-棠下涌', 113.3570, 23.1291, '天河区', 'ONLINE', NOW()),
('S002', '越秀区-东濠涌', 113.2644, 23.1290, '越秀区', 'ONLINE', NOW()),
('S003', '海珠区-康乐涌', 113.2880, 23.0950, '海珠区', 'ONLINE', NOW()),
('S004', '荔湾区-荔枝湾涌', 113.2380, 23.1170, '荔湾区', 'ONLINE', NOW()),
('S005', '白云区-石井河', 113.2300, 23.2100, '白云区', 'ONLINE', NOW()),
('S006', '黄埔区-南岗河', 113.5400, 23.0930, '黄埔区', 'ONLINE', NOW()),
('S007', '番禺区-市桥水道', 113.3620, 22.9370, '番禺区', 'ONLINE', NOW()),
('S008', '南沙区-蕉门河', 113.5250, 22.8010, '南沙区', 'ONLINE', NOW()),
('S009', '增城区-增江', 113.8300, 23.2900, '增城区', 'ONLINE', NOW()),
('S010', '花都区-天马河', 113.1920, 23.4040, '花都区', 'ONLINE', NOW());

-- 监测设备（每个站点一台）
INSERT INTO device (code, name, station_id, type, protocol, status, firmware, install_time) VALUES
('D001', '棠下涌水位计', 1, 'NB-IoT', 'MQTT', 'OFFLINE', 'v1.0', NOW()),
('D002', '东濠涌水位计', 2, 'NB-IoT', 'MQTT', 'OFFLINE', 'v1.0', NOW()),
('D003', '康乐涌水位计', 3, 'NB-IoT', 'MQTT', 'OFFLINE', 'v1.0', NOW()),
('D004', '荔枝湾涌水位计', 4, 'NB-IoT', 'MQTT', 'OFFLINE', 'v1.0', NOW()),
('D005', '石井河水位计', 5, 'NB-IoT', 'MQTT', 'OFFLINE', 'v1.0', NOW()),
('D006', '南岗河水位计', 6, 'NB-IoT', 'MQTT', 'OFFLINE', 'v1.0', NOW()),
('D007', '市桥水道水位计', 7, 'NB-IoT', 'CoAP', 'OFFLINE', 'v1.0', NOW()),
('D008', '蕉门河水位计', 8, 'NB-IoT', 'MQTT', 'OFFLINE', 'v1.0', NOW()),
('D009', '增江水位计', 9, 'NB-IoT', 'MQTT', 'OFFLINE', 'v1.0', NOW()),
('D010', '天马河水位计', 10, 'NB-IoT', 'MQTT', 'OFFLINE', 'v1.0', NOW());

-- 回填站点关联设备
UPDATE station s JOIN device d ON s.id = d.station_id SET s.device_id = d.id;

-- 告警阈值配置（水位/雨量/流速各蓝黄橙红四级，另含电量/信号辅助阈值）
INSERT INTO threshold (code, name, `value`, unit, level, enabled) VALUES
('WATER_BLUE',   '水位蓝色预警阈值', 1.0,   'm',    'BLUE',   1),
('WATER_YELLOW', '水位黄色预警阈值', 1.2,   'm',    'YELLOW', 1),
('WATER_ORANGE', '水位橙色预警阈值', 1.4,   'm',    'ORANGE', 1),
('WATER_RED',    '水位红色预警阈值', 1.5,   'm',    'RED',    1),
('RAIN_BLUE',    '雨量蓝色预警阈值', 10.0,  'mm/h', 'BLUE',   1),
('RAIN_YELLOW',  '雨量黄色预警阈值', 20.0,  'mm/h', 'YELLOW', 1),
('RAIN_ORANGE',  '雨量橙色预警阈值', 35.0,  'mm/h', 'ORANGE', 1),
('RAIN_RED',     '雨量红色预警阈值', 50.0,  'mm/h', 'RED',    1),
('FLOW_BLUE',    '流速蓝色预警阈值', 1.0,   'm/s',  'BLUE',   1),
('FLOW_YELLOW',  '流速黄色预警阈值', 2.0,   'm/s',  'YELLOW', 1),
('FLOW_ORANGE',  '流速橙色预警阈值', 3.0,   'm/s',  'ORANGE', 1),
('FLOW_RED',     '流速红色预警阈值', 4.0,   'm/s',  'RED',    1),
('BATTERY_LOW',  '低电量阈值',      20.0,  '%',    'YELLOW', 1),
('SIGNAL_WEAK',  '弱信号阈值',      -100,  'dBm',  'BLUE',   1);

-- 系统用户（默认账号 admin / admin123，密码为 SHA-256 摘要）
INSERT INTO `user` (username, password, nickname, role) VALUES
('admin', '240be518fabd2724ddb6f04eeb1da5967448d7e831c08c8fa822809f74c720a9', '系统管理员', 'ADMIN'),
('operator', '240be518fabd2724ddb6f04eeb1da5967448d7e831c08c8fa822809f74c720a9', '值班操作员', 'OPERATOR');
