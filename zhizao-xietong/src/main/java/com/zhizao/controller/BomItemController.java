package com.zhizao.controller;

import com.zhizao.common.Result;
import com.zhizao.entity.BomItem;
import com.zhizao.service.BomItemService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import io.swagger.v3.oas.annotations.tags.Tag;

import java.util.List;

/**
 * 工艺物料清单（BOM）接口。BOM 直接挂在工艺版本行下，不再单独维护 BOM 版本。
 *
 * @author Caspian
 */
@Tag(name = "工艺物料清单(BOM)")
@RestController
@RequestMapping("/api/processes/{processId}/bom")
public class BomItemController {

    private final BomItemService bomItemService;

    public BomItemController(BomItemService bomItemService) {
        this.bomItemService = bomItemService;
    }

    @GetMapping
    public Result<List<BomItem>> list(@PathVariable Long processId) {
        return Result.ok(bomItemService.listByProcess(processId));
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping
    public Result<BomItem> create(@PathVariable Long processId, @RequestBody BomItem item) {
        return Result.ok(bomItemService.create(processId, item));
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PutMapping("/{id}")
    public Result<BomItem> update(@PathVariable Long processId, @PathVariable Long id, @RequestBody BomItem item) {
        return Result.ok(bomItemService.update(processId, id, item));
    }

    @PreAuthorize("hasRole('ADMIN')")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long processId, @PathVariable Long id) {
        bomItemService.delete(processId, id);
        return Result.ok();
    }
}
