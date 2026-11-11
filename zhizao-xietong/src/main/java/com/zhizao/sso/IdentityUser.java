package com.zhizao.sso;

import java.util.List;

/** 归一化的外部身份：externalId 为稳定外部主键（企微 userid），姓名/手机/邮箱仅展示，部门仅作车间建议。 */
public record IdentityUser(String provider, String externalId, String externalUsername, String displayName,
                           String mobile, String email, List<String> departmentIds, boolean active) {
}
