package com.urbanflood.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.urbanflood.common.BusinessException;
import com.urbanflood.common.Result;
import com.urbanflood.entity.User;
import com.urbanflood.mapper.UserMapper;
import com.urbanflood.util.JwtUtil;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 认证接口。
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final UserMapper userMapper;
    private final JwtUtil jwtUtil;

    public AuthController(UserMapper userMapper, JwtUtil jwtUtil) {
        this.userMapper = userMapper;
        this.jwtUtil = jwtUtil;
    }

    @PostMapping("/login")
    public Result<Map<String, Object>> login(@RequestBody Map<String, String> body) {
        String username = body.get("username");
        String password = body.get("password");
        if (username == null || password == null) {
            throw new BusinessException(400, "用户名和密码不能为空");
        }
        User user = userMapper.selectOne(new LambdaQueryWrapper<User>().eq(User::getUsername, username));
        if (user == null || !sha256(password).equalsIgnoreCase(user.getPassword())) {
            throw new BusinessException(401, "用户名或密码错误");
        }
        String token = jwtUtil.generate(user.getId(), user.getUsername(), user.getRole());
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("token", token);
        result.put("userInfo", userInfo(user));
        return Result.ok(result);
    }

    @PostMapping("/logout")
    public Result<Void> logout() {
        // JWT 无状态，客户端删除 token 即可
        return Result.ok();
    }

    @GetMapping("/me")
    public Result<Map<String, Object>> me(@RequestHeader(value = "Authorization", required = false) String auth) {
        String token = extractToken(auth);
        String username = token == null ? null : jwtUtil.getUsername(token);
        if (username == null) {
            throw new BusinessException(401, "未登录或登录已过期");
        }
        User user = userMapper.selectOne(new LambdaQueryWrapper<User>().eq(User::getUsername, username));
        if (user == null) {
            throw new BusinessException(401, "用户不存在");
        }
        return Result.ok(userInfo(user));
    }

    private Map<String, Object> userInfo(User user) {
        Map<String, Object> info = new LinkedHashMap<>();
        info.put("id", user.getId());
        info.put("username", user.getUsername());
        info.put("nickname", user.getNickname());
        info.put("role", user.getRole());
        return info;
    }

    private String extractToken(String auth) {
        if (auth != null && auth.startsWith("Bearer ")) {
            return auth.substring(7);
        }
        return null;
    }

    private String sha256(String text) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(text.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            throw new RuntimeException("SHA-256 计算失败", e);
        }
    }
}
