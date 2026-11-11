package com.zhizao.sso;

/**
 * 企业身份提供方：发起授权跳转、用授权码换取归一化外部身份。
 * 首期只有企微实现与测试用假实现；飞书、钉钉只保留实现位。
 *
 * @author Caspian
 */
public interface EnterpriseIdentityProvider {

    /** 提供方标识，写入 sys_user_identity.provider。 */
    String provider();

    /** 生成授权页地址，state 由调用方生成并负责校验。 */
    String authorizeUrl(String state, String redirectUri);

    /** 用授权码换取外部身份；授权码一次性，失败不得重试同一个 code。 */
    IdentityUser exchange(String code, String redirectUri);
}
