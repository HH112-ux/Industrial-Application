package com.zhizao.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zhizao.common.BusinessException;
import com.zhizao.entity.BomItem;
import com.zhizao.mapper.BomItemMapper;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * 工艺物料清单（BOM）服务，支持版本管理。
 * 仅最新版本可编辑，历史版本只读（用于追溯）。
 */
@Service
public class BomItemService {

    private final BomItemMapper bomItemMapper;
    private final MaterialService materialService;

    public BomItemService(BomItemMapper bomItemMapper, MaterialService materialService) {
        this.bomItemMapper = bomItemMapper;
        this.materialService = materialService;
    }

    /** 列出指定版本的 BOM 项；version 为空时取最新版本。 */
    public List<BomItem> listByProcess(Long processId, Integer version) {
        int v = version != null ? version : latestVersion(processId);
        return bomItemMapper.selectList(new LambdaQueryWrapper<BomItem>()
                .eq(BomItem::getProcessId, processId)
                .eq(BomItem::getVersion, v));
    }

    /** 列出所有版本号（降序）。 */
    public List<Integer> listVersions(Long processId) {
        List<BomItem> all = bomItemMapper.selectList(new LambdaQueryWrapper<BomItem>()
                .eq(BomItem::getProcessId, processId)
                .select(BomItem::getVersion));
        return all.stream().map(BomItem::getVersion).filter(Objects::nonNull)
                .distinct().sorted(Comparator.reverseOrder()).toList();
    }

    /** 当前最新版本号，无任何版本时返回 1。 */
    public int latestVersion(Long processId) {
        List<Integer> versions = listVersions(processId);
        return versions.isEmpty() ? 1 : versions.get(0);
    }

    /** 基于最新版本新建一个版本（复制其 BOM 项），返回新版本号。 */
    public int newVersion(Long processId) {
        int latest = latestVersion(processId);
        int newV = latest + 1;
        for (BomItem item : listByProcess(processId, latest)) {
            BomItem copy = new BomItem();
            copy.setProcessId(processId);
            copy.setVersion(newV);
            copy.setMatCode(item.getMatCode());
            copy.setRequiredQty(item.getRequiredQty());
            bomItemMapper.insert(copy);
        }
        return newV;
    }

    public BomItem create(Long processId, BomItem item) {
        validateMaterial(item.getMatCode());
        item.setId(null);
        item.setProcessId(processId);
        item.setVersion(latestVersion(processId));
        if (item.getRequiredQty() == null || item.getRequiredQty() <= 0) {
            item.setRequiredQty(1);
        }
        bomItemMapper.insert(item);
        return item;
    }

    public BomItem update(Long processId, Long id, BomItem item) {
        validateMaterial(item.getMatCode());
        BomItem existing = requireOwned(processId, id);
        existing.setMatCode(item.getMatCode());
        existing.setRequiredQty(item.getRequiredQty() == null || item.getRequiredQty() <= 0
                ? 1 : item.getRequiredQty());
        bomItemMapper.updateById(existing);
        return existing;
    }

    public void delete(Long processId, Long id) {
        requireOwned(processId, id);
        bomItemMapper.deleteById(id);
    }

    private BomItem requireOwned(Long processId, Long id) {
        BomItem existing = bomItemMapper.selectById(id);
        if (existing == null || !Objects.equals(processId, existing.getProcessId())) {
            throw new BusinessException("BOM 项不存在");
        }
        if (!Objects.equals(existing.getVersion(), latestVersion(processId))) {
            throw new BusinessException("历史版本不可修改");
        }
        return existing;
    }

    private void validateMaterial(String matCode) {
        if (matCode == null || matCode.isBlank()) {
            throw new BusinessException("请选择物料");
        }
        if (materialService.getByCode(matCode) == null) {
            throw new BusinessException("物料不存在：" + matCode);
        }
    }
}
