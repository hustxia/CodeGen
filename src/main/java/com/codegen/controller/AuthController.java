package com.codegen.controller;

import com.codegen.dto.LoginDTO;
import com.codegen.service.AuthRouterService;
import com.codegen.service.SysUserService;
import com.codegen.vo.AuthRoutersVO;
import com.codegen.vo.LoginVO;
import com.codegen.vo.SysUserVO;
import com.codegen.common.exception.BusinessException;
import com.codegen.common.result.Result;
import com.codegen.common.result.ResultCode;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * 认证 Controller
 */
@Tag(name = "认证", description = "登录认证")
@RestController
@RequestMapping("/auth")
public class AuthController {

    private static final String BEARER_PREFIX = "Bearer ";

    @Autowired
    private SysUserService userService;

    @Autowired
    private AuthRouterService authRouterService;

    @Operation(summary = "登录")
    @PostMapping("/login")
    public Result<LoginVO> login(@RequestBody LoginDTO dto) {
        return Result.ok(userService.login(dto));
    }

    @Operation(summary = "获取当前登录用户信息")
    @GetMapping("/info")
    public Result<SysUserVO> info(@RequestHeader(value = "Authorization", required = false) String authorization) {
        String token = requireToken(authorization);
        return Result.ok(userService.getByToken(token));
    }

    @Operation(summary = "获取当前用户权限码集合（前端按钮级控制）")
    @GetMapping("/permissions")
    public Result<Set<String>> permissions(@RequestHeader(value = "Authorization", required = false) String authorization) {
        String token = requireToken(authorization);
        SysUserVO user = userService.getByToken(token);
        Set<String> perms = user.getPermissions() == null
                ? new LinkedHashSet<>() : new LinkedHashSet<>(user.getPermissions());
        return Result.ok(perms);
    }

    @Operation(summary = "获取当前用户的动态路由（按角色下发的菜单树）")
    @GetMapping("/routers")
    public Result<AuthRoutersVO> routers(@RequestHeader(value = "Authorization", required = false) String authorization) {
        String token = requireToken(authorization);
        SysUserVO user = userService.getByToken(token);
        return Result.ok(authRouterService.routersOf(user.getId()));
    }

    private String requireToken(String authorization) {
        if (!StringUtils.hasText(authorization) || !authorization.startsWith(BEARER_PREFIX)) {
            throw new BusinessException(ResultCode.UNAUTHORIZED.getCode(), "未登录或token无效");
        }
        return authorization.substring(BEARER_PREFIX.length());
    }
}
