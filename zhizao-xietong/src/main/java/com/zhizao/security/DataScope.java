package com.zhizao.security;

import com.zhizao.common.ForbiddenException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/**
 * 车间数据域：把当前登录用户的车间范围翻译成查询条件与访问校验。
 *
 * <p>管理员与未认证调用（如 MQTT 网关、定时任务）不受限制，返回 null。
 * 非管理员始终受限：{@code workshop_scope} 为空时按「无任何车间可见」处理，
 * 避免漏配范围的账号意外看到全量数据。</p>
 *
 * @author Caspian
 */
@Component
public class DataScope {

    /**
     * 读取当前用户的车间过滤值。
     *
     * @return null 表示不限制；非 null 表示只能访问该车间（空串表示无可见数据）
     */
    public String workshopFilter() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof UserPrincipal principal) || principal.isAdmin()) {
            return null;
        }
        String scope = principal.workshopScope();
        return scope == null ? "" : scope.trim();
    }

    /**
     * 校验资源是否属于当前用户的车间。
     *
     * @param workshop 资源所属车间
     * @throws ForbiddenException 资源属于其他车间或当前账号未配置车间时抛出
     */
    public void requireWorkshop(String workshop) {
        String scope = workshopFilter();
        if (scope == null) {
            return;
        }
        if (scope.isEmpty()) {
            throw new ForbiddenException("当前账号未配置车间数据域");
        }
        if (!scope.equals(workshop)) {
            throw new ForbiddenException("无权访问其他车间的数据");
        }
    }
}
