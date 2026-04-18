package com.tianji.auth.controller;

import com.tianji.api.dto.user.LoginFormDTO;
import com.tianji.auth.common.constants.JwtConstants;
import com.tianji.auth.service.IAccountService;
import com.tianji.common.exceptions.BadRequestException;
import com.tianji.common.utils.WebUtils;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;

@RestController
@RequestMapping("/accounts")
@Api(tags = "账户管理")
@RequiredArgsConstructor
public class AccountController {

    private final IAccountService accountService;

    @ApiOperation("登录并获取token")
    @PostMapping("/login")
    public String loginByPw(@RequestBody LoginFormDTO loginFormDTO) {
        return accountService.login(loginFormDTO, false);
    }

    @ApiOperation("管理端登录并获取token")
    @PostMapping("/admin/login")
    public String adminLoginByPw(@RequestBody LoginFormDTO loginFormDTO) {
        return accountService.login(loginFormDTO, true);
    }

    @ApiOperation("退出登录")
    @PostMapping("/logout")
    public void logout() {
        accountService.logout();
    }

    @ApiOperation("刷新token")
    @GetMapping("/refresh")
    public String refreshToken(
            @CookieValue(value = JwtConstants.REFRESH_HEADER, required = false) String studentToken,
            @CookieValue(value = JwtConstants.ADMIN_REFRESH_HEADER, required = false) String adminToken
    ) {
        if (studentToken == null && adminToken == null) {
            throw new BadRequestException("登录超时");
        }
        String origin = WebUtils.getHeader("origin");
        if (origin == null) {
            throw new BadRequestException("登录超时");
        }
        String token = isStudentSite(origin) ? studentToken : adminToken;
        if (token == null) {
            throw new BadRequestException("登录超时");
        }
        return accountService.refreshToken(WebUtils.cookieBuilder().decode(token));
    }

    private boolean isStudentSite(String origin) {
        try {
            String host = URI.create(origin).getHost();
            return host != null && host.startsWith("www.");
        } catch (Exception e) {
            return origin.contains("://www.") || origin.startsWith("www.");
        }
    }
}
