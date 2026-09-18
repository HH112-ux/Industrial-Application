package com.zhizao.controller;

import com.zhizao.common.PageResult;
import com.zhizao.common.Result;
import com.zhizao.entity.Material;
import com.zhizao.service.MaterialService;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.security.access.prepost.PreAuthorize;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "物料管理")
@RestController
@RequestMapping("/api/materials")
public class MaterialController {

    private final MaterialService materialService;

    public MaterialController(MaterialService materialService) {
        this.materialService = materialService;
    }

    @GetMapping
    public Result<PageResult<Material>> list(@RequestParam(defaultValue = "1") int pageNum,
                                             @RequestParam(defaultValue = "10") int pageSize,
                                             @RequestParam(required = false) String keyword,
                                             @RequestParam(required = false) String kitStatus) {
        return Result.ok(materialService.list(pageNum, pageSize, keyword, kitStatus));
    }

    @GetMapping("/{id}")
    public Result<Material> get(@PathVariable Long id) {
        return Result.ok(materialService.getById(id));
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping
    public Result<Material> create(@RequestBody Material material) {
        return Result.ok(materialService.create(material));
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PutMapping("/{id}")
    public Result<Material> update(@PathVariable Long id, @RequestBody Material material) {
        material.setId(id);
        return Result.ok(materialService.update(material));
    }

    @PreAuthorize("hasRole('ADMIN')")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        materialService.delete(id);
        return Result.ok();
    }
}
