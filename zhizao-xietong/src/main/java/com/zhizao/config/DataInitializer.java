package com.zhizao.config;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zhizao.entity.SysUser;
import com.zhizao.mapper.SysUserMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.security.SecureRandom;

/**
 * 启动时初始化账号。
 *
 * <p>管理员初始口令必须通过环境变量 {@code APP_BOOTSTRAP_ADMIN_PASSWORD} 注入。
 * {@code dev} profile 未配置时生成一次性安全随机口令并在启动日志中打印一次；
 * 其他环境未配置时直接启动失败，绝不静默生成口令或回退到硬编码弱口令。
 * 演示账号仅在 {@code dev} profile 下创建，口令来自 {@code DEMO_INIT_PASSWORD}，
 * 未配置时同样生成随机口令。</p>
 *
 * @author Caspian
 */
@Component
public class DataInitializer implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DataInitializer.class);
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final String PASSWORD_CHARS =
            "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789!@#$%^&*";
    private static final int GENERATED_PASSWORD_LENGTH = 20;

    private final SysUserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final Environment environment;

    @Value("${app.bootstrap.admin-password:}")
    private String adminPassword;

    @Value("${app.bootstrap.demo-password:}")
    private String demoPassword;

    public DataInitializer(SysUserMapper userMapper, PasswordEncoder passwordEncoder, Environment environment) {
        this.userMapper = userMapper;
        this.passwordEncoder = passwordEncoder;
        this.environment = environment;
    }

    @Override
    public void run(String... args) {
        boolean devProfile = environment.acceptsProfiles(Profiles.of("dev"));
        ensureUser("admin", "系统管理员", "ADMIN", null,
                "app.bootstrap.admin-password（环境变量 APP_BOOTSTRAP_ADMIN_PASSWORD）", adminPassword, devProfile);
        if (devProfile) {
            ensureUser("zhangsan", "张三", "USER", "一车间",
                    "app.bootstrap.demo-password（环境变量 DEMO_INIT_PASSWORD）", demoPassword, true);
        }
    }

    /**
     * 账号不存在时创建，口令优先取配置值。
     *
     * @param username              登录名
     * @param nickname              昵称
     * @param role                  角色
     * @param workshopScope         车间范围，为空表示不限制
     * @param passwordSource        口令配置来源，用于日志与错误提示
     * @param configuredValue       已配置的口令，可为空
     * @param allowGeneratedPassword 是否允许在缺省时生成一次性随机口令
     */
    private void ensureUser(String username, String nickname, String role, String workshopScope,
                            String passwordSource, String configuredValue, boolean allowGeneratedPassword) {
        Long count = userMapper.selectCount(
                new LambdaQueryWrapper<SysUser>().eq(SysUser::getUsername, username));
        if (count != null && count > 0) {
            return;
        }
        String password;
        if (StringUtils.hasText(configuredValue)) {
            password = configuredValue;
        } else if (allowGeneratedPassword) {
            password = generatePassword(passwordSource, username);
        } else {
            throw new IllegalStateException("未配置 " + passwordSource + "，拒绝在非 dev 环境创建账号 "
                    + username + "。请设置该环境变量后重新启动。");
        }
        SysUser user = new SysUser();
        user.setUsername(username);
        user.setPassword(passwordEncoder.encode(password));
        user.setNickname(nickname);
        user.setRole(role);
        user.setEnabled(1);
        user.setTokenVersion(0);
        user.setWorkshopScope(workshopScope);
        userMapper.insert(user);
    }

    /**
     * 生成一次性随机口令并打印一次，提示运维通过环境变量注入固定口令。
     *
     * @param passwordSource 口令配置来源
     * @param username       登录名
     * @return 随机口令
     */
    private String generatePassword(String passwordSource, String username) {
        StringBuilder builder = new StringBuilder(GENERATED_PASSWORD_LENGTH);
        for (int i = 0; i < GENERATED_PASSWORD_LENGTH; i++) {
            builder.append(PASSWORD_CHARS.charAt(RANDOM.nextInt(PASSWORD_CHARS.length())));
        }
        String password = builder.toString();
        log.warn("未配置 {}，已为账号 {} 生成一次性随机口令：{}。请登录后立即修改。",
                passwordSource, username, password);
        return password;
    }
}
