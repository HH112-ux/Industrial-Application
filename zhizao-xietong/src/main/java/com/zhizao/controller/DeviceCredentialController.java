package com.zhizao.controller;

import com.zhizao.common.Result;
import com.zhizao.dto.DeviceCredentialIssued;
import com.zhizao.service.DeviceCredentialService;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 设备凭证管理：签发与吊销设备上报凭证，仅管理员可操作。
 *
 * @author Caspian
 */
@Tag(name = "设备凭证")
@RestController
@RequestMapping("/api/devices/{id}/credential")
public class DeviceCredentialController {

    private final DeviceCredentialService credentialService;

    /**
     * 构建设备凭证控制器。
     *
     * @param credentialService 设备凭证服务
     */
    public DeviceCredentialController(DeviceCredentialService credentialService) {
        this.credentialService = credentialService;
    }

    /**
     * 签发或轮换设备凭证。
     *
     * @param id 设备主键
     * @return 凭证标识与仅返回一次的明文密钥
     */
    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping
    public Result<DeviceCredentialIssued> issue(@PathVariable Long id) {
        return Result.ok(credentialService.issue(id));
    }

    /**
     * 吊销设备凭证，吊销后该设备的签名上报立即被拒绝。
     *
     * @param id 设备主键
     * @return 成功返回空响应体
     */
    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/revoke")
    public Result<Void> revoke(@PathVariable Long id) {
        credentialService.revoke(id);
        return Result.ok();
    }
}
