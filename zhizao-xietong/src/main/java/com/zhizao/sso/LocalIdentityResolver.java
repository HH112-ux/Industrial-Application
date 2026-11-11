package com.zhizao.sso;

import com.zhizao.entity.SysUser;

/**
 * 外部身份到本地账号的解析：已绑定返回既有账号，未绑定按最低权限建档。
 *
 * @author Caspian
 */
public interface LocalIdentityResolver {

    /**
     * 解析外部身份对应的本地账号，不存在时自动建档。
     *
     * @param identity 归一化后的外部身份
     * @return 已存在或新建的本地账号
     */
    SysUser resolve(IdentityUser identity);
}
