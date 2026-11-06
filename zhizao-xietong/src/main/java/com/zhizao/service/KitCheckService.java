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

/**
 * 物料齐套校验：按工单锁定的工艺版本（{@code processId} 指向工艺版本行）读取 BOM，
 * 以「单件用量 × 工单数量」核算所需物料，批量查物料（消除 N+1）。
 *
 * <p>工艺改版会生成新的版本行，在制工单仍指向原版本行，因此齐套结果不受后续发布影响。</p>
 *
 * @author Caspian
 */
@Service
public class KitCheckService {

    private final MaterialService materialService;
    private final BomItemMapper bomItemMapper;

    public KitCheckService(MaterialService materialService, BomItemMapper bomItemMapper) {
        this.materialService = materialService;
        this.bomItemMapper = bomItemMapper;
    }

    /**
     * 校验工单所需物料是否齐套。
     *
     * @param order 工单，processId 为空时直接通过
     * @return 齐套结果与缺料明细
     */
    public KitCheckResult check(WorkOrder order) {
        if (order.getProcessId() == null) {
            return KitCheckResult.pass();
        }
        List<BomItem> bom = bomItemMapper.selectList(
                new LambdaQueryWrapper<BomItem>().eq(BomItem::getProcessId, order.getProcessId()));
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
