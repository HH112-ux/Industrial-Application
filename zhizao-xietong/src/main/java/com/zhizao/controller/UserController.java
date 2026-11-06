package com.zhizao.controller;

import com.zhizao.common.PageResult;
import com.zhizao.common.Result;
import com.zhizao.common.UnauthorizedException;
import com.zhizao.dto.PasswordChangeRequest;
import com.zhizao.dto.PasswordResetRequest;
import com.zhizao.dto.UserView;
import com.zhizao.security.UserPrincipal;
import com.zhizao.service.AuthService;
import com.zhizao.service.UserService;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 用户管理接口：管理员查看、启停与重置用户密码，登录用户自助改密。
 *
 * @author Caspian
 */
@Tag(name = "用户管理")
@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserService userService;
    private final AuthService authService;

    /**
     * 构造用户管理接口。
     *
     * @param userService 用户管理服务
     * @param authService 认证服务，用于自助改密
     */
    public UserController(UserService userService, AuthService authService) {
        this.userService = userService;
        this.authService = authService;
    }

    /**
     * 分页查询用户，仅管理员可用。
     *
     * @param pageNum  页码
     * @param pageSize 每页条数
     * @param keyword  用户名或昵称关键字
     * @return 脱敏后的用户分页结果
     */
    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping
    public Result<PageResult<UserView>> list(@RequestParam(defaultValue = "1") int pageNum,
                                             @RequestParam(defaultValue = "10") int pageSize,
                                             @RequestParam(required = false) String keyword) {
        return Result.ok(userService.list(pageNum, pageSize, keyword));
    }

    /**
     * 启用或禁用用户。禁用后该用户的已签发令牌在下一次请求即被拒绝。
     *
     * @param id      用户主键
     * @param enabled true 启用，false 禁用
     * @return 更新后的用户视图
     */
    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/{id}/status")
    public Result<UserView> changeStatus(@PathVariable Long id, @RequestParam boolean enabled) {
        return Result.ok(userService.setEnabled(id, enabled));
    }

    /**
     * 管理员重置用户密码，该用户所有旧令牌立即失效。
     *
     * @param id      用户主键
     * @param request 新密码
     * @return 空响应
     */
    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/{id}/password")
    public Result<Void> resetPassword(@PathVariable Long id, @RequestBody PasswordResetRequest request) {
        userService.resetPassword(id, request.newPassword());
        return Result.ok();
    }

    /**
     * 自助改密。成功后当前令牌立即失效，需要用新密码重新登录。
     *
     * @param request        原密码与新密码
     * @param authentication 当前认证信息
     * @return 空响应
     */
    @PostMapping("/me/password")
    public Result<Void> changePassword(@RequestBody PasswordChangeRequest request,
                                       Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof UserPrincipal principal)) {
            throw new UnauthorizedException("未登录或登录已过期");
        }
        authService.changePassword(principal.username(), request);
        return Result.ok();
    }
}
