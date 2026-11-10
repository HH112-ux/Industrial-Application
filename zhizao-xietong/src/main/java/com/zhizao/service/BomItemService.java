package com.zhizao.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zhizao.common.BusinessException;
import com.zhizao.entity.BomItem;
import com.zhizao.mapper.BomItemMapper;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;

/**
 * 工艺物料清单（BOM）服务。
 *
 * <p>BOM 直接挂在工艺版本行（{@code processId}）下，工艺改版时由
 * {@link ProcessService#createVersion(Long)} 复制到新的工艺版本行。</p>
 *
 * @author Caspian
 */
@Service
public class BomItemService {

    private final BomItemMapper bomItemMapper;
    private final MaterialService materialService;
    private final ProcessService processService;

    public BomItemService(BomItemMapper bomItemMapper, MaterialService materialService,
                          ProcessService processService) {
        this.bomItemMapper = bomItemMapper;
        this.materialService = materialService;
        this.processService = processService;
    }

    /**
     * 列出工艺版本下的全部 BOM 项。
     *
     * @param processId 工艺版本 ID
     * @return BOM 项列表
     */
    public List<BomItem> listByProcess(Long processId) {
        return bomItemMapper.selectList(new LambdaQueryWrapper<BomItem>()
                .eq(BomItem::getProcessId, processId));
    }

    /**
     * 新增 BOM 项，仅未发布工艺可改。
     *
     * @param processId 工艺版本 ID
     * @param item      BOM 项
     * @return 落库后的 BOM 项
     */
    public BomItem create(Long processId, BomItem item) {
        processService.requireEditable(processId);
        validateMaterial(item.getMatCode());
        requireMaterialAvailable(processId, item.getMatCode(), null);
        item.setId(null);
        item.setProcessId(processId);
        item.setRequiredQty(requireQty(item.getRequiredQty()));
        bomItemMapper.insert(item);
        return item;
    }

    /**
     * 修改 BOM 项，仅未发布工艺可改。
     *
     * @param processId 工艺版本 ID
     * @param id        BOM 项 ID
     * @param item      新的 BOM 内容
     * @return 修改后的 BOM 项
     */
    public BomItem update(Long processId, Long id, BomItem item) {
        processService.requireEditable(processId);
        BomItem existing = requireOwned(processId, id);
        validateMaterial(item.getMatCode());
        requireMaterialAvailable(processId, item.getMatCode(), id);
        existing.setMatCode(item.getMatCode());
        existing.setRequiredQty(requireQty(item.getRequiredQty()));
        bomItemMapper.updateById(existing);
        return existing;
    }

    /**
     * 删除 BOM 项，仅未发布工艺可改。
     *
     * @param processId 工艺版本 ID
     * @param id        BOM 项 ID
     */
    public void delete(Long processId, Long id) {
        processService.requireEditable(processId);
        requireOwned(processId, id);
        bomItemMapper.deleteById(id);
    }

    private BomItem requireOwned(Long processId, Long id) {
        BomItem existing = bomItemMapper.selectById(id);
        if (existing == null || !Objects.equals(processId, existing.getProcessId())) {
            throw new BusinessException("BOM 项不存在");
        }
        return existing;
    }

    private void requireMaterialAvailable(Long processId, String matCode, Long excludeId) {
        Long count = bomItemMapper.selectCount(new LambdaQueryWrapper<BomItem>()
                .eq(BomItem::getProcessId, processId)
                .eq(BomItem::getMatCode, matCode)
                .ne(excludeId != null, BomItem::getId, excludeId));
        if (count != null && count > 0) {
            throw new BusinessException("该物料已在 BOM 中：" + matCode);
        }
    }

    private int requireQty(Integer qty) {
        if (qty == null || qty <= 0) {
            throw new BusinessException("BOM 用量必须大于 0");
        }
        return qty;
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
