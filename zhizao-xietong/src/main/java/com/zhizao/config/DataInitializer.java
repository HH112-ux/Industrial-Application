package com.zhizao.config;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zhizao.entity.SysUser;
import com.zhizao.mapper.SysUserMapper;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * 启动时初始化默认管理员账号：admin / admin123。
 */
@Component
public class DataInitializer implements CommandLineRunner {

    private final SysUserMapper userMapper;
    private final PasswordEncoder passwordEncoder;

    public DataInitializer(SysUserMapper userMapper, PasswordEncoder passwordEncoder) {
        this.userMapper = userMapper;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void run(String... args) {
        Long count = userMapper.selectCount(
                new LambdaQueryWrapper<SysUser>().eq(SysUser::getUsername, "admin"));
        if (count == null || count == 0) {
            SysUser admin = new SysUser();
            admin.setUsername("admin");
            admin.setPassword(passwordEncoder.encode("admin123"));
            admin.setNickname("系统管理员");
            admin.setRole("ADMIN");
            userMapper.insert(admin);
        }

        Long opCount = userMapper.selectCount(
                new LambdaQueryWrapper<SysUser>().eq(SysUser::getUsername, "zhangsan"));
        if (opCount == null || opCount == 0) {
            SysUser operator = new SysUser();
            operator.setUsername("zhangsan");
            operator.setPassword(passwordEncoder.encode("123456"));
            operator.setNickname("张三");
            operator.setRole("USER");
            operator.setEnabled(1);
            operator.setTokenVersion(0);
            operator.setWorkshopScope("一车间");
            userMapper.insert(operator);
        }
    }
}
