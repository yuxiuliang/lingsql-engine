package com.lingsql.schema.repository;

import com.lingsql.schema.entity.SemanticLexiconItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * 语义字典仓库。
 *
 * @author 余炘洋
 * @date 2026-01-22
 */
public interface SemanticLexiconRepository extends JpaRepository<SemanticLexiconItem, Long> {

    /**
     * 查询租户级别词典。
     *
     * @param tenantId 租户标识
     * @param status   状态
     * @return 词典列表
     * @author 余炘洋
     * @date 2026-01-22
     */
    List<SemanticLexiconItem> findByTenantIdAndStatus(String tenantId, Integer status);

    /**
     * 查询租户级别词典（包含全部状态）。
     *
     * @param tenantId 租户标识
     * @return 词典列表
     * @author 余炘洋
     * @date 2026-01-26
     */
    List<SemanticLexiconItem> findByTenantId(String tenantId);

    /**
     * 查询租户或全局词典（包含全部状态）。
     *
     * @param tenantId 租户标识
     * @return 词典列表
     * @author 余炘洋
     * @date 2026-01-26
     */
    List<SemanticLexiconItem> findByTenantIdOrTenantIdIsNull(String tenantId);

    /**
     * 查询全局词典。
     *
     * @param status 状态
     * @return 词典列表
     * @author 余炘洋
     * @date 2026-01-22
     */
    List<SemanticLexiconItem> findByTenantIdIsNullAndStatus(Integer status);
}
