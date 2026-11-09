package com.zhizao.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zhizao.common.BusinessException;
import com.zhizao.common.PageResult;
import com.zhizao.entity.Material;
import com.zhizao.mapper.MaterialMapper;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class MaterialService {

    private final MaterialMapper materialMapper;
    private final CodeGenerator codeGenerator;

    public MaterialService(MaterialMapper materialMapper, CodeGenerator codeGenerator) {
        this.materialMapper = materialMapper;
        this.codeGenerator = codeGenerator;
    }

    public PageResult<Material> list(int pageNum, int pageSize, String keyword, String kitStatus) {
        Page<Material> page = new Page<>(pageNum, pageSize);
        LambdaQueryWrapper<Material> qw = new LambdaQueryWrapper<>();
        qw.and(StringUtils.hasText(keyword), w -> w
                .like(Material::getMatCode, keyword)
                .or().like(Material::getMatName, keyword)
                .or().like(Material::getMatType, keyword));
        qw.eq(StringUtils.hasText(kitStatus), Material::getKitStatus, kitStatus);
        qw.orderByDesc(Material::getId);
        materialMapper.selectPage(page, qw);
        return PageResult.of(page.getTotal(), page.getRecords());
    }

    @Cacheable(cacheNames = "material", key = "#id")
    public Material getById(Long id) {
        Material material = materialMapper.selectById(id);
        if (material == null) {
            throw new BusinessException("物料不存在");
        }
        return material;
    }

    /** 按编码查询，未找到返回 null（供齐套校验内部使用）。 */
    public Material getByCode(String code) {
        return materialMapper.selectOne(new LambdaQueryWrapper<Material>().eq(Material::getMatCode, code));
    }

    /** 批量按编码查询（消除齐套校验 N+1）。 */
    public Map<String, Material> listByCodes(List<String> codes) {
        if (codes == null || codes.isEmpty()) {
            return Map.of();
        }
        List<Material> materials = materialMapper.selectList(
                new LambdaQueryWrapper<Material>().in(Material::getMatCode, codes));
        return materials.stream().collect(Collectors.toMap(Material::getMatCode, m -> m));
    }

    /**
     * 批量按编码查询并对物料行加锁，供开工预占在事务内串行化同一物料的可用量核算。
     *
     * @param codes 物料编码
     * @return 物料编码到物料的映射
     */
    public Map<String, Material> listByCodesForUpdate(List<String> codes) {
        if (codes == null || codes.isEmpty()) {
            return Map.of();
        }
        List<Material> materials = materialMapper.selectList(
                new LambdaQueryWrapper<Material>().in(Material::getMatCode, codes).last("FOR UPDATE"));
        return materials.stream().collect(Collectors.toMap(Material::getMatCode, m -> m));
    }

    /**
     * 扣减物料库存，库存不足时按 0 封底；同时清空物料缓存，避免读到扣减前的数量。
     *
     * @param matCode 物料编码
     * @param qty     扣减数量，非正数时忽略
     */
    @CacheEvict(cacheNames = "material", allEntries = true)
    public void deductStock(String matCode, int qty) {
        if (qty <= 0) {
            return;
        }
        materialMapper.update(null, new LambdaUpdateWrapper<Material>()
                .eq(Material::getMatCode, matCode)
                .setSql("stock_qty = GREATEST(stock_qty - " + qty + ", 0)"));
    }

    @CacheEvict(cacheNames = "material", allEntries = true)
    public Material create(Material material) {
        material.setId(null);
        material.setMatCode(codeGenerator.next("MT"));
        material.setKitStatus(computeKitStatus(material));
        materialMapper.insert(material);
        return material;
    }

    @CacheEvict(cacheNames = "material", allEntries = true)
    public Material update(Material material) {
        getById(material.getId());
        material.setMatCode(null);
        material.setKitStatus(computeKitStatus(material));
        materialMapper.updateById(material);
        return getById(material.getId());
    }

    @CacheEvict(cacheNames = "material", allEntries = true)
    public void delete(Long id) {
        getById(id);
        materialMapper.deleteById(id);
    }

    /** 齐套状态：缺料(0) / 偏低(0<stock<safe) / 充足(stock>=safe)。 */
    private String computeKitStatus(Material m) {
        int stock = m.getStockQty() == null ? 0 : m.getStockQty();
        int safe = m.getSafeStock() == null ? 0 : m.getSafeStock();
        if (stock == 0) {
            return "SHORTAGE";
        }
        if (stock < safe) {
            return "LOW";
        }
        return "SUFFICIENT";
    }
}
