package com.zhizao.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zhizao.entity.BomItem;
import com.zhizao.entity.Material;
import com.zhizao.entity.WorkOrder;
import com.zhizao.mapper.BomItemMapper;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 物料齐套校验：基于 BOM 最新版本，按「单件用量 × 工单数量」核算所需物料，
 * 批量查物料（消除 N+1），库存不足即缺料。
 */
@Service
public class KitCheckService {

    private final MaterialService materialService;
    private final BomItemMapper bomItemMapper;

    public KitCheckService(MaterialService materialService, BomItemMapper bomItemMapper) {
        this.materialService = materialService;
        this.bomItemMapper = bomItemMapper;
    }

    public KitCheckResult check(WorkOrder order) {
        if (order.getProcessId() == null) {
            return KitCheckResult.pass();
        }
        List<BomItem> allBom = bomItemMapper.selectList(
                new LambdaQueryWrapper<BomItem>().eq(BomItem::getProcessId, order.getProcessId()));
        int latestVersion = allBom.stream()
                .map(BomItem::getVersion).filter(Objects::nonNull)
                .mapToInt(Integer::intValue).max().orElse(1);
        List<BomItem> bom = allBom.stream()
                .filter(i -> i.getVersion() != null && i.getVersion() == latestVersion).toList();
        if (bom.isEmpty()) {
            return KitCheckResult.pass();
        }

        List<String> codes = bom.stream().map(BomItem::getMatCode).distinct().toList();
        Map<String, Material> materialMap = materialService.listByCodes(codes);

        int orderQty = order.getQty() == null ? 0 : order.getQty();
        List<KitCheckResult.ShortageItem> shortages = new ArrayList<>();
        for (BomItem item : bom) {
            Material material = materialMap.get(item.getMatCode());
            String matName = material == null ? item.getMatCode() : material.getMatName();
            int need = (item.getRequiredQty() == null ? 0 : item.getRequiredQty()) * orderQty;
            int stock = material == null ? 0 : (material.getStockQty() == null ? 0 : material.getStockQty());
            if (stock < need) {
                shortages.add(new KitCheckResult.ShortageItem(item.getMatCode(), matName, need, stock));
            }
        }
        return new KitCheckResult(shortages.isEmpty(), shortages);
    }
}
