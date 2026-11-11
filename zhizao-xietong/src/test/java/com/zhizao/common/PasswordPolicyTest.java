package com.zhizao.common;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PasswordPolicyTest {

    @Test
    void acceptsStrongPassword() {
        assertDoesNotThrow(() -> PasswordPolicy.validate("Xh7#kLm2", "zhangsan"));
    }

    @Test
    void rejectsPasswordShorterThanEightCharacters() {
        BadRequestException error = assertThrows(BadRequestException.class,
                () -> PasswordPolicy.validate("Ab3#def", "zhangsan"));

        assertTrue(error.getMessage().contains("8-64"), error.getMessage());
    }

    @Test
    void rejectsPasswordLongerThanSixtyFourCharacters() {
        String password = "a".repeat(PasswordPolicy.MAX_LENGTH) + "b";

        BadRequestException error = assertThrows(BadRequestException.class,
                () -> PasswordPolicy.validate(password, "zhangsan"));

        assertTrue(error.getMessage().contains("8-64"), error.getMessage());
    }

    @Test
    void rejectsPasswordMadeOfOneRepeatedCharacter() {
        BadRequestException error = assertThrows(BadRequestException.class,
                () -> PasswordPolicy.validate("aaaaaaaa", "zhangsan"));

        assertEquals("密码不能全部使用同一个字符，请混合字母、数字或符号", error.getMessage());
    }

    @Test
    void rejectsPasswordContainingUsernameIgnoringCase() {
        BadRequestException error = assertThrows(BadRequestException.class,
                () -> PasswordPolicy.validate("Zhangsan2026", "zhangsan"));

        assertEquals("密码不能包含用户名，请更换其他组合", error.getMessage());
    }

    @Test
    void ignoresUsernameShorterThanThreeCharactersWhenCheckingContainment() {
        assertDoesNotThrow(() -> PasswordPolicy.validate("ab123456", "ab"));
    }

    @Test
    void rejectsCommonWeakPassword() {
        BadRequestException error = assertThrows(BadRequestException.class,
                () -> PasswordPolicy.validate("password123", "zhangsan"));

        assertEquals("该密码过于常见，请更换一个更难猜测的密码", error.getMessage());
    }

    @Test
    void rejectsNullAndBlankPassword() {
        assertThrows(BadRequestException.class, () -> PasswordPolicy.validate(null, "zhangsan"));
        assertThrows(BadRequestException.class, () -> PasswordPolicy.validate("        ", "zhangsan"));
    }
}
