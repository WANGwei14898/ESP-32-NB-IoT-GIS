package com.urbanflood.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 系统用户实体，对应 user 表。
 */
@Data
@TableName("user")
public class User {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 登录名 */
    private String username;

    /** 密码（SHA-256 摘要） */
    private String password;

    /** 昵称 */
    private String nickname;

    /** 角色：ADMIN / OPERATOR / VIEWER */
    private String role;

    /** 创建时间 */
    private LocalDateTime createTime;
}
