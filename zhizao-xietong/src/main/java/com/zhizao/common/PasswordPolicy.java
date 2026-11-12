package com.zhizao.common;

import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Set;

/**
 * 密码强度校验：本地账号所有「设置密码」入口共用同一份规则。
 *
 * <p>规则刻意保持简单：长度 8-64 位、UTF-8 编码不超过 72 字节、禁止全部同一字符、
 * 禁止包含用户名、拒绝少量常见弱口令。
 * 不做定期过期、历史密码与账号锁定——车间场景下过严的规则会把密码推向纸条与共享，
 * 反而降低安全性。取舍见《独立账号体系方案》§5。</p>
 *
 * <p>不满足规则时统一抛 {@link BadRequestException}，由全局处理器转成 HTTP 400。
 * 前端直接展示异常 message，因此每条文案都要说明「哪里不合规、应该怎么改」，
 * 不能只回一句「密码不符合要求」。</p>
 *
 * @author Caspian
 */
public final class PasswordPolicy {

    /** 密码最小长度：兼顾车间终端的输入成本与基本强度。 */
    public static final int MIN_LENGTH = 8;

    /** 密码最大字符数：与前端「8-64 位」提示保持一致。 */
    public static final int MAX_LENGTH = 64;

    /**
     * 密码最大字节数：BCrypt 只使用前 72 字节，超出部分会被静默截断，
     * 导致两个前 72 字节相同的密码可以互相通过校验。
     */
    public static final int MAX_UTF8_BYTES = 72;

    /** 用户名参与「禁止包含」判断的最短长度，1-2 个字符的用户名会误伤大量正常密码。 */
    private static final int MIN_USERNAME_LENGTH_FOR_CHECK = 3;

    /** 常见弱口令名单，全部小写；只收录高频、明显可猜的值，不做大字典。 */
    private static final Set<String> WEAK_PASSWORDS = Set.of(
            "password", "password1", "password123", "passw0rd", "p@ssw0rd",
            "12345678", "123456789", "1234567890", "qwertyui", "qwerty123",
            "1qaz2wsx", "abc12345", "abcd1234", "admin123", "admin1234",
            "iloveyou", "letmein1", "welcome1", "woaini1314", "a1234567");

    private PasswordPolicy() {
    }

    /**
     * 校验密码是否满足策略，不满足时抛出可直接展示给用户的原因。
     *
     * <p>校验顺序固定为「空 → 长度（字符数与 UTF-8 字节数）→ 全同字符 → 含用户名 → 弱口令名单」，
     * 先报最直观的问题，避免用户按提示改完又撞上下一条规则。</p>
     *
     * @param password 明文密码，可为 null
     * @param username 目标账号的用户名，用于「禁止包含用户名」判断，可为 null
     * @throws BadRequestException 密码为空、长度越界、全部同字符、包含用户名或命中弱口令名单
     */
    public static void validate(String password, String username) {
        if (password == null || password.isBlank()) {
            throw new BadRequestException("密码不能为空");
        }
        if (password.length() < MIN_LENGTH || password.length() > MAX_LENGTH) {
            throw new BadRequestException("密码长度需为 " + MIN_LENGTH + "-" + MAX_LENGTH
                    + " 位，当前 " + password.length() + " 位");
        }
        int utf8Bytes = password.getBytes(StandardCharsets.UTF_8).length;
        if (utf8Bytes > MAX_UTF8_BYTES) {
            throw new BadRequestException("密码按 UTF-8 编码不能超过 " + MAX_UTF8_BYTES
                    + " 字节，当前 " + utf8Bytes + " 字节（中文、emoji 等字符占多个字节），请缩短后重试");
        }
        if (allSameCharacter(password)) {
            throw new BadRequestException("密码不能全部使用同一个字符，请混合字母、数字或符号");
        }
        if (containsUsername(password, username)) {
            throw new BadRequestException("密码不能包含用户名，请更换其他组合");
        }
        if (WEAK_PASSWORDS.contains(password.toLowerCase(Locale.ROOT))) {
            throw new BadRequestException("该密码过于常见，请更换一个更难猜测的密码");
        }
    }

    /**
     * 判断密码是否由同一个字符重复组成。
     *
     * @param password 已通过空值与长度校验的密码
     * @return true 表示全部字符相同
     */
    private static boolean allSameCharacter(String password) {
        char first = password.charAt(0);
        for (int i = 1; i < password.length(); i++) {
            if (password.charAt(i) != first) {
                return false;
            }
        }
        return true;
    }

    /**
     * 判断密码是否包含用户名（忽略大小写与首尾空白）。
     *
     * @param password 已通过空值与长度校验的密码
     * @param username 用户名，可为 null
     * @return true 表示密码包含用户名
     */
    private static boolean containsUsername(String password, String username) {
        if (username == null) {
            return false;
        }
        String name = username.trim().toLowerCase(Locale.ROOT);
        return name.length() >= MIN_USERNAME_LENGTH_FOR_CHECK
                && password.toLowerCase(Locale.ROOT).contains(name);
    }
}
