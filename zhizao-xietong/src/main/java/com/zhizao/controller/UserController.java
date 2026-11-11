package com.zhizao.controller;

import com.zhizao.common.PageResult;
import com.zhizao.common.Result;
import com.zhizao.common.UnauthorizedException;
import com.zhizao.dto.PasswordChangeRequest;
import com.zhizao.dto.PasswordResetRequest;
import com.zhizao.dto.RoleAssignRequest;
import com.zhizao.dto.UserCreateRequest;
import com.zhizao.dto.UserCreateResponse;
import com.zhizao.dto.UserView;
import com.zhizao.dto.WorkshopAssignRequest;
import com.zhizao.security.UserPrincipal;
import com.zhizao.service.AuthService;
import com.zhizao.service.UserService;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
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
     * @param ssoOnly  true 时只返回仍是纯 SSO、尚未开通本地密码的账号
     * @return 脱敏后的用户分页结果
     */
    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping
    public Result<PageResult<UserView>> list(@RequestParam(defaultValue = "1") int pageNum,
                                             @RequestParam(defaultValue = "10") int pageSize,
                                             @RequestParam(required = false) String keyword,
                                             @RequestParam(defaultValue = "false") boolean ssoOnly) {
        return Result.ok(userService.list(pageNum, pageSize, keyword, ssoOnly));
    }

    /**
     * 管理员创建本地账号。初始密码只在本次响应返回一次，新建账号首次登录必须改密。
     *
     * @param request 用户名、姓名、角色、车间与可选初始密码
     * @return 新建账号视图与一次性初始密码
     */
    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping
    public Result<UserCreateResponse> create(@RequestBody UserCreateRequest request) {
        return Result.ok(userService.create(request));
    }

    /**
     * 查询当前登录用户自身信息。强制改密期间仍可访问。
     *
     * @param authentication 当前认证信息
     * @return 当前用户视图
     */
    @GetMapping("/me")
    public Result<UserView> me(Authentication authentication) {
        return Result.ok(userService.profile(principal(authentication).username()));
    }

    /**
     * 启用或禁用用户。禁用后该用户的已签发令牌在下一次请求即被拒绝。
     *
     * <p>不能禁用自己，也不能禁用最后一个启用的本地管理员。</p>
     *
     * @param id             用户主键
     * @param enabled        true 启用，false 禁用
     * @param authentication 当前认证信息，用于自身保护
     * @return 更新后的用户视图
     */
    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/{id}/status")
    public Result<UserView> changeStatus(@PathVariable Long id, @RequestParam boolean enabled,
                                         Authentication authentication) {
        return Result.ok(userService.setEnabled(id, enabled, principal(authentication).username()));
    }

    /**
     * 软删除用户：禁用并保留用户名，同名账号不能再次创建。
     *
     * <p>不能删除自己，也不能删除最后一个启用的本地管理员。</p>
     *
     * @param id             用户主键
     * @param authentication 当前认证信息，用于自身保护
     * @return 空响应
     */
    @PreAuthorize("hasRole('ADMIN')")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id, Authentication authentication) {
        userService.delete(id, principal(authentication).username());
        return Result.ok();
    }

    /**
     * 管理员重置用户密码，该用户所有旧令牌立即失效。
     *
     * <p>对纯 SSO 账号即开通本地密码通道：认证来源转 BOTH，扫码仍可用，首登强制改密。</p>
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
     * 管理员为用户分配车间数据域。分配后该用户下一次请求即按新车间过滤。
     *
     * @param id      用户主键
     * @param request 车间名称
     * @return 更新后的用户视图
     */
    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/{id}/workshop")
    public Result<UserView> assignWorkshop(@PathVariable Long id, @RequestBody WorkshopAssignRequest request) {
        return Result.ok(userService.assignWorkshop(id, request.workshopScope()));
    }

    /**
     * 管理员调整用户角色。角色变更写审计，旧令牌在下一次请求失效。
     *
     * @param id      用户主键
     * @param request 目标角色
     * @return 更新后的用户视图
     */
    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/{id}/role")
    public Result<UserView> assignRole(@PathVariable Long id, @RequestBody RoleAssignRequest request) {
        return Result.ok(userService.assignRole(id, request.role()));
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
        authService.changePassword(principal(authentication).username(), request);
        return Result.ok();
    }

    /**
     * 提取当前登录主体，未登录时按 401 处理。
     *
     * @param authentication 当前认证信息
     * @return 登录主体
     */
    private UserPrincipal principal(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof UserPrincipal user)) {
            throw new UnauthorizedException("未登录或登录已过期");
        }
        return user;
    }
}
