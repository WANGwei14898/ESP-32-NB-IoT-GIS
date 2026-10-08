package com.urbanflood.common;

import lombok.Getter;

/**
 * 业务异常，由全局异常处理器统一转换为 Result。
 */
@Getter
public class BusinessException extends RuntimeException {

    private final int code;

    public BusinessException(int code, String message) {
        super(message);
        this.code = code;
    }
}
