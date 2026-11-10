package com.zhizao.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zhizao.entity.BomItem;
import com.zhizao.entity.Material;
import com.zhizao.entity.MaterialReservation;
import com.zhizao.entity.WorkOrder;
import com.zhizao.mapper.BomItemMapper;
import com.zhizao.mapper.MaterialReservationMapper;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 物料齐套校验：按工单锁定的工艺版本（{@code processId} 指向工艺版本行）读取 BOM，
 * 以「单件用量 × 工单数量」核算所需物料，批量查物料（消除 N+1）。
 *
 * <p>可用量口径为「库存 − 该物料已被其他工单预占的数量」，
 * 因此两张工单不能同时用同一批库存通过齐套；开工时写入预占记录，完工时转为实际消耗并扣减库存。</p>
 *
 * <p>工艺改版会生成新的版本行，在制工单仍指向原版本行，因此齐套结果不受后续发布影响。</p>
 *
 * @author Caspian
 */
@Service
public class KitCheckService {

    private final MaterialService materialService;
    private final BomItemMapper bomItemMapper;
    private final MaterialReservationMapper reservationMapper;

    public KitCheckService(MaterialService materialService, BomItemMapper bomItemMapper,
                           MaterialReservationMapper reservationMapper) {
        this.materialService = materialService;
        this.bomItemMapper = bomItemMapper;
        this.reservationMapper = reservationMapper;
    }

    /**
     * 校验工单所需物料是否齐套，只读，不产生预占。
     *
     * <p>工单未绑定工艺或 BOM 为空时视为「未配置 BOM」，返回失败并给出明确原因，
     * 不能当作齐套通过。</p>
     *
     * @param order 工单
     * @return 齐套结果、缺料明细或未配置 BOM 的失败原因
     */
    public KitCheckResult check(WorkOrder order) {
        Map<String, Integer> needs = requiredByCode(order);
        if (needs.isEmpty()) {
            return KitCheckResult.missingBom();
        }
        Map<String, Material> materialMap = materialService.listByCodes(new ArrayList<>(needs.keySet()));
        return evaluate(order, needs, materialMap);
    }

    /**
     * 开工预占：校验可用量通过后，按 BOM 需求写入预占记录。
     *
     * <p>物料行在事务内加锁，串行化同一物料的可用量核算，避免并发开工同时通过校验。
     * 同一工单重复预占时按物料编码幂等跳过。未配置 BOM 时直接失败，不写入任何预占。</p>
     *
     * @param order 工单
     * @return 齐套结果；未通过时不写入任何预占记录
     */
    public KitCheckResult reserve(WorkOrder order) {
        Map<String, Integer> needs = requiredByCode(order);
        if (needs.isEmpty()) {
            return KitCheckResult.missingBom();
        }
        Map<String, Material> materialMap = materialService.listByCodesForUpdate(new ArrayList<>(needs.keySet()));
        KitCheckResult result = evaluate(order, needs, materialMap);
        if (!result.passed()) {
            return result;
        }
        Set<String> reserved = new LinkedHashSet<>();
        if (order.getId() != null) {
            reservationMapper.selectList(new LambdaQueryWrapper<MaterialReservation>()
                            .eq(MaterialReservation::getWorkOrderId, order.getId()))
                    .forEach(item -> reserved.add(item.getMatCode()));
        }
        needs.forEach((matCode, qty) -> {
            if (qty <= 0 || reserved.contains(matCode)) {
                return;
            }
            MaterialReservation reservation = new MaterialReservation();
            reservation.setWorkOrderId(order.getId());
            reservation.setMatCode(matCode);
            reservation.setReservedQty(qty);
            reservation.setStatus(MaterialReservation.STATUS_RESERVED);
            reservationMapper.insert(reservation);
        });
        return result;
    }

    /**
     * 完工消耗：把工单的预占转为实际消耗，并扣减对应物料库存。
     *
     * @param order 工单
     */
    public void consume(WorkOrder order) {
        if (order.getId() == null) {
            return;
        }
        List<MaterialReservation> reservations = reservationMapper.selectList(
                new LambdaQueryWrapper<MaterialReservation>()
                        .eq(MaterialReservation::getWorkOrderId, order.getId())
                        .eq(MaterialReservation::getStatus, MaterialReservation.STATUS_RESERVED));
        for (MaterialReservation reservation : reservations) {
            materialService.deductStock(reservation.getMatCode(), reservation.getReservedQty());
            reservation.setStatus(MaterialReservation.STATUS_CONSUMED);
            reservationMapper.updateById(reservation);
        }
    }

    /**
     * 按 BOM 汇总所需物料数量；物料编码重复时累加。
     *
     * @param order 工单
     * @return 物料编码到需求数量的有序映射，无 BOM 时为空
     */
    private Map<String, Integer> requiredByCode(WorkOrder order) {
        if (order.getProcessId() == null) {
            return Map.of();
        }
        List<BomItem> bom = bomItemMapper.selectList(
                new LambdaQueryWrapper<BomItem>().eq(BomItem::getProcessId, order.getProcessId()));
        if (bom.isEmpty()) {
            return Map.of();
        }
        int orderQty = order.getQty() == null ? 0 : order.getQty();
        Map<String, Integer> needs = new LinkedHashMap<>();
        for (BomItem item : bom) {
            int unitQty = item.getRequiredQty() == null ? 0 : item.getRequiredQty();
            needs.merge(item.getMatCode(), unitQty * orderQty, Integer::sum);
        }
        return needs;
    }

    /**
     * 按「库存 − 其他工单已预占」核算可用量并生成缺料明细。
     *
     * @param order       工单，用于排除自身的预占
     * @param needs       物料编码到需求数量
     * @param materialMap 物料编码到物料
     * @return 齐套结果
     */
    private KitCheckResult evaluate(WorkOrder order, Map<String, Integer> needs,
                                    Map<String, Material> materialMap) {
        Map<String, Integer> reserved = loadReserved(order, needs.keySet());
        List<KitCheckResult.ShortageItem> shortages = new ArrayList<>();
        for (Map.Entry<String, Integer> entry : needs.entrySet()) {
            String matCode = entry.getKey();
            int need = entry.getValue();
            Material material = materialMap.get(matCode);
            String matName = material == null ? matCode : material.getMatName();
            int stock = material == null || material.getStockQty() == null ? 0 : material.getStockQty();
            int available = stock - reserved.getOrDefault(matCode, 0);
            if (available < need) {
                shortages.add(new KitCheckResult.ShortageItem(matCode, matName, need, available));
            }
        }
        return new KitCheckResult(shortages.isEmpty(), shortages, null);
    }

    /**
     * 读取其他工单已预占的物料数量，排除当前工单自身的预占。
     *
     * <p>只查本次 BOM 涉及的物料编码，命中 {@code idx_mat_status (mat_code, status)}，
     * 避免把全表预占记录读进内存再逐条丢弃；结果集中仍按工单 ID 排除自身。</p>
     *
     * @param order    工单
     * @param matCodes 本次需求涉及的物料编码，非空
     * @return 物料编码到已预占数量
     */
    private Map<String, Integer> loadReserved(WorkOrder order, Set<String> matCodes) {
        List<MaterialReservation> reservations = reservationMapper.selectList(
                new LambdaQueryWrapper<MaterialReservation>()
                        .in(MaterialReservation::getMatCode, matCodes)
                        .eq(MaterialReservation::getStatus, MaterialReservation.STATUS_RESERVED)
                        .ne(order.getId() != null, MaterialReservation::getWorkOrderId, order.getId()));
        Map<String, Integer> reserved = new LinkedHashMap<>();
        for (MaterialReservation reservation : reservations) {
            if (order.getId() != null && order.getId().equals(reservation.getWorkOrderId())) {
                continue;
            }
            reserved.merge(reservation.getMatCode(),
                    reservation.getReservedQty() == null ? 0 : reservation.getReservedQty(), Integer::sum);
        }
        return reserved;
    }
}
