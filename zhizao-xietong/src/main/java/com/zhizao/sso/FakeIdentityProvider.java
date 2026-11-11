package com.zhizao.sso;

import com.zhizao.common.BadRequestException;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * 测试/开发用假身份提供方：不发网络请求，用 code 直接映射固定身份。
 *
 * <p>双重闸门：仅 dev、test profile 且显式配置 app.sso.fake-provider-enabled=true 时装配。
 * 约定 code：fake-&lt;userid&gt; 为启用成员，fake-disabled-&lt;userid&gt; 为停用成员。</p>
 *
 * @author Caspian
 */
@Component
@Primary
@Profile({"dev", "test"})
@ConditionalOnProperty(name = "app.sso.fake-provider-enabled", havingValue = "true")
public class FakeIdentityProvider implements EnterpriseIdentityProvider {

    public static final String PROVIDER = "FAKE";
    private static final String PREFIX = "fake-";
    private static final String DISABLED_PREFIX = "fake-disabled-";

    @Override
    public String provider() {
        return PROVIDER;
    }

    @Override
    public String authorizeUrl(String state, String redirectUri) {
        return redirectUri + (redirectUri.contains("?") ? "&" : "?") + "code=fake-dev-worker&state=" + state;
    }

    @Override
    public IdentityUser exchange(String code, String redirectUri) {
        if (!StringUtils.hasText(code) || !code.startsWith(PREFIX)) {
            throw new BadRequestException("假身份授权码不合法");
        }
        boolean active = !code.startsWith(DISABLED_PREFIX);
        String externalId = code.substring((active ? PREFIX : DISABLED_PREFIX).length());
        if (!StringUtils.hasText(externalId)) {
            throw new BadRequestException("假身份授权码缺少用户标识");
        }
        return new IdentityUser(PROVIDER, externalId, externalId, "测试成员 " + externalId,
                null, null, List.of("1", "2"), active);
    }
}
