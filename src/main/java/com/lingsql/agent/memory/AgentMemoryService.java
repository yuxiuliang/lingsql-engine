package com.lingsql.agent.memory;

import com.lingsql.agent.entity.AgentMemoryFact;
import com.lingsql.agent.repository.AgentMemoryFactRepository;
import com.lingsql.common.exception.BizException;
import com.lingsql.common.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * 分层记忆服务：临时记忆由 Redis 会话窗口承担，程序记忆与永久记忆在此持久化并接受保留期治理。
 *
 * <p>写入通道只有用户显式确认与管理端批准，模型不能自行决定永久保存对话内容；
 * 用户纠正会生成新版本并使旧事实失效，不会无痕覆盖。</p>
 *
 * @author 余炘洋
 * @date 2026-10-18
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AgentMemoryService {

    /**
     * 提示词中最多注入的长期记忆条数。
     */
    public static final int PROMPT_MEMORY_LIMIT = 10;

    /**
     * 写入提示词的记忆正文最大长度，超出截断。
     */
    private static final int PROMPT_FACT_MAX_LENGTH = 200;

    /**
     * 用户级永久记忆默认保留天数。
     */
    private static final int DEFAULT_PERMANENT_RETENTION_DAYS = 365;

    /**
     * 租户级程序记忆默认保留天数。
     */
    private static final int DEFAULT_PROCEDURAL_RETENTION_DAYS = 730;

    /**
     * 最小保留天数。
     */
    private static final int MIN_RETENTION_DAYS = 1;

    /**
     * 最大保留天数。
     */
    private static final int MAX_RETENTION_DAYS = 3650;

    /**
     * 管理端治理列表最大返回条数。
     */
    private static final int ADMIN_LIST_LIMIT = 200;

    private final AgentMemoryFactRepository memoryFactRepository;

    private final MemoryContentGuard memoryContentGuard;

    /**
     * 查询用户自己的永久记忆。
     *
     * @param tenantId 租户标识
     * @param userId   用户标识
     * @return 生效中的记忆列表
     * @author 余炘洋
     * @date 2026-10-18
     */
    public List<AgentMemoryFact> listOwn(String tenantId, Long userId) {
        requireScope(tenantId, userId, MemoryTier.PERMANENT);
        LocalDateTime now = LocalDateTime.now();
        return memoryFactRepository
                .findTop50ByTenantIdAndUserIdAndStatusOrderByUpdatedAtDesc(
                        tenantId.trim(), userId, MemoryStatus.ACTIVE.name())
                .stream()
                .filter(fact -> !isExpired(fact, now))
                .collect(Collectors.toList());
    }

    /**
     * 管理端治理列表：按租户隔离，可叠加用户、层级与状态过滤。
     *
     * @param tenantId 租户标识
     * @param userId   用户标识，可为空
     * @param tier     记忆层级，可为空
     * @param status   记忆状态，可为空
     * @return 记忆列表
     * @author 余炘洋
     * @date 2026-10-18
     */
    public List<AgentMemoryFact> listForAdmin(String tenantId, Long userId, MemoryTier tier, MemoryStatus status) {
        if (!StringUtils.hasText(tenantId)) {
            throw new BizException(ErrorCode.INVALID_PARAM, "租户标识不能为空");
        }
        return memoryFactRepository.findTop200ByTenantIdOrderByUpdatedAtDesc(tenantId.trim())
                .stream()
                .filter(fact -> userId == null || userId.equals(fact.getUserId()))
                .filter(fact -> tier == null || tier.name().equals(fact.getTier()))
                .filter(fact -> status == null || status.name().equals(fact.getStatus()))
                .limit(ADMIN_LIST_LIMIT)
                .collect(Collectors.toList());
    }

    /**
     * 写入一条长期记忆，重复写入同一事实返回既有记忆。
     *
     * @param tenantId      租户标识
     * @param userId        用户标识，程序记忆必须为空
     * @param tier          记忆层级
     * @param sourceType    记忆来源
     * @param factText      记忆正文
     * @param retentionDays 保留天数，为空使用层级默认值
     * @param operator      写入人标识
     * @return 记忆
     * @author 余炘洋
     * @date 2026-10-18
     */
    @Transactional
    public AgentMemoryFact remember(String tenantId,
                                    Long userId,
                                    MemoryTier tier,
                                    MemorySourceType sourceType,
                                    String factText,
                                    Integer retentionDays,
                                    String operator) {
        requireScope(tenantId, userId, tier);
        if (sourceType == null || !sourceType.writeAllowed()) {
            throw new BizException(ErrorCode.SECURITY_VIOLATION, "该记忆来源当前不允许写入");
        }
        String normalized = memoryContentGuard.validateAndNormalize(factText);
        LocalDateTime now = LocalDateTime.now();
        String tenant = tenantId.trim();
        Optional<AgentMemoryFact> existing = findActive(tenant, userId, tier, normalized);
        if (existing.isPresent()) {
            AgentMemoryFact fact = existing.get();
            if (!isExpired(fact, now)) {
                return fact;
            }
            fact.setExpiresAt(resolveExpiresAt(tier, retentionDays, now));
            fact.setUpdatedAt(now);
            AgentMemoryFact renewed = memoryFactRepository.save(fact);
            log.info("长期记忆到期后续期: tenantId={}, userId={}, tier={}, memoryId={}",
                    tenant, userId, tier.name(), renewed.getId());
            return renewed;
        }
        AgentMemoryFact fact = new AgentMemoryFact();
        fact.setTenantId(tenant);
        fact.setUserId(userId);
        fact.setTier(tier.name());
        fact.setFactText(normalized);
        fact.setSourceType(sourceType.name());
        fact.setStatus(MemoryStatus.ACTIVE.name());
        fact.setConfidence(sourceType.confidence());
        fact.setVersion(1);
        fact.setCreatedBy(operator);
        fact.setExpiresAt(resolveExpiresAt(tier, retentionDays, now));
        fact.setCreatedAt(now);
        fact.setUpdatedAt(now);
        AgentMemoryFact saved = memoryFactRepository.save(fact);
        log.info("写入长期记忆: tenantId={}, userId={}, tier={}, sourceType={}, memoryId={}",
                tenant, userId, tier.name(), sourceType.name(), saved.getId());
        return saved;
    }

    /**
     * 用户纠正自己的永久记忆：旧事实失效并生成新版本。
     *
     * @param tenantId      租户标识
     * @param userId        用户标识
     * @param id            记忆主键
     * @param factText      新记忆正文
     * @param retentionDays 保留天数，为空使用默认值
     * @return 新版本记忆
     * @author 余炘洋
     * @date 2026-10-18
     */
    @Transactional
    public AgentMemoryFact correctOwn(String tenantId,
                                      Long userId,
                                      Long id,
                                      String factText,
                                      Integer retentionDays) {
        requireScope(tenantId, userId, MemoryTier.PERMANENT);
        AgentMemoryFact fact = loadActive(tenantId, id);
        if (!userId.equals(fact.getUserId())) {
            throw new BizException(ErrorCode.FORBIDDEN, "无权限访问");
        }
        return applyCorrection(fact, MemorySourceType.USER_CONFIRMED, factText, retentionDays, String.valueOf(userId));
    }

    /**
     * 管理端纠正租户内记忆：旧事实失效并生成新版本。
     *
     * @param tenantId      租户标识
     * @param id            记忆主键
     * @param factText      新记忆正文
     * @param retentionDays 保留天数，为空使用默认值
     * @param operator      操作人标识
     * @return 新版本记忆
     * @author 余炘洋
     * @date 2026-10-18
     */
    @Transactional
    public AgentMemoryFact correctByAdmin(String tenantId,
                                          Long id,
                                          String factText,
                                          Integer retentionDays,
                                          String operator) {
        if (!StringUtils.hasText(tenantId)) {
            throw new BizException(ErrorCode.INVALID_PARAM, "租户标识不能为空");
        }
        AgentMemoryFact fact = loadActive(tenantId, id);
        return applyCorrection(fact, MemorySourceType.ADMIN_APPROVED, factText, retentionDays, operator);
    }

    /**
     * 用户撤销自己的永久记忆。
     *
     * @param tenantId 租户标识
     * @param userId   用户标识
     * @param id       记忆主键
     * @return 撤销后的记忆
     * @author 余炘洋
     * @date 2026-10-18
     */
    @Transactional
    public AgentMemoryFact revokeOwn(String tenantId, Long userId, Long id) {
        requireScope(tenantId, userId, MemoryTier.PERMANENT);
        AgentMemoryFact fact = load(tenantId, id);
        if (!userId.equals(fact.getUserId())) {
            throw new BizException(ErrorCode.FORBIDDEN, "无权限访问");
        }
        return applyRevoke(fact);
    }

    /**
     * 管理端撤销租户内记忆。
     *
     * @param tenantId 租户标识
     * @param id       记忆主键
     * @return 撤销后的记忆
     * @author 余炘洋
     * @date 2026-10-18
     */
    @Transactional
    public AgentMemoryFact revokeByAdmin(String tenantId, Long id) {
        if (!StringUtils.hasText(tenantId)) {
            throw new BizException(ErrorCode.INVALID_PARAM, "租户标识不能为空");
        }
        return applyRevoke(load(tenantId, id));
    }

    /**
     * 构建提示词中的长期记忆片段，带层级、来源与时间标注。
     *
     * @param tenantId 租户标识
     * @param userId   用户标识，可为空
     * @return 记忆片段，无可用记忆时返回空串
     * @author 余炘洋
     * @date 2026-10-18
     */
    public String buildPromptContext(String tenantId, Long userId) {
        if (!StringUtils.hasText(tenantId)) {
            return "";
        }
        String tenant = tenantId.trim();
        LocalDateTime now = LocalDateTime.now();
        List<AgentMemoryFact> candidates = new ArrayList<>(memoryFactRepository
                .findTop50ByTenantIdAndUserIdIsNullAndTierAndStatusOrderByUpdatedAtDesc(
                        tenant, MemoryTier.PROCEDURAL.name(), MemoryStatus.ACTIVE.name()));
        if (userId != null) {
            candidates.addAll(memoryFactRepository.findTop50ByTenantIdAndUserIdAndStatusOrderByUpdatedAtDesc(
                    tenant, userId, MemoryStatus.ACTIVE.name()));
        }
        if (candidates.isEmpty()) {
            return "";
        }
        return candidates.stream()
                .filter(fact -> !isExpired(fact, now))
                .sorted(Comparator.comparing(AgentMemoryFact::getUpdatedAt).reversed())
                .limit(PROMPT_MEMORY_LIMIT)
                .map(this::renderPromptLine)
                .collect(Collectors.joining("\n"));
    }

    /**
     * 查询同层同文生效记忆。
     *
     * @param tenantId 租户标识
     * @param userId   用户标识
     * @param tier     记忆层级
     * @param factText 记忆正文
     * @return 记忆
     * @author 余炘洋
     * @date 2026-10-18
     */
    private Optional<AgentMemoryFact> findActive(String tenantId, Long userId, MemoryTier tier, String factText) {
        String status = MemoryStatus.ACTIVE.name();
        if (userId == null) {
            return memoryFactRepository.findFirstByTenantIdAndUserIdIsNullAndTierAndFactTextAndStatus(
                    tenantId, tier.name(), factText, status);
        }
        return memoryFactRepository.findFirstByTenantIdAndUserIdAndTierAndFactTextAndStatus(
                tenantId, userId, tier.name(), factText, status);
    }

    /**
     * 生成新版本并使旧事实失效。
     *
     * @param fact          旧记忆
     * @param sourceType    新版本来源
     * @param factText      新记忆正文
     * @param retentionDays 保留天数
     * @param operator      操作人标识
     * @return 新版本记忆
     * @author 余炘洋
     * @date 2026-10-18
     */
    private AgentMemoryFact applyCorrection(AgentMemoryFact fact,
                                            MemorySourceType sourceType,
                                            String factText,
                                            Integer retentionDays,
                                            String operator) {
        String normalized = memoryContentGuard.validateAndNormalize(factText);
        LocalDateTime now = LocalDateTime.now();
        fact.setStatus(MemoryStatus.SUPERSEDED.name());
        fact.setUpdatedAt(now);
        memoryFactRepository.save(fact);

        AgentMemoryFact next = new AgentMemoryFact();
        next.setTenantId(fact.getTenantId());
        next.setUserId(fact.getUserId());
        next.setTier(fact.getTier());
        next.setFactText(normalized);
        next.setSourceType(sourceType.name());
        next.setStatus(MemoryStatus.ACTIVE.name());
        next.setConfidence(sourceType.confidence());
        next.setVersion(fact.getVersion() + 1);
        next.setSupersedesId(fact.getId());
        next.setCreatedBy(operator);
        next.setExpiresAt(resolveExpiresAt(MemoryTier.of(fact.getTier()), retentionDays, now));
        next.setCreatedAt(now);
        next.setUpdatedAt(now);
        AgentMemoryFact saved = memoryFactRepository.save(next);
        log.info("纠正长期记忆: tenantId={}, memoryId={}, supersedesId={}, version={}",
                fact.getTenantId(), saved.getId(), fact.getId(), saved.getVersion());
        return saved;
    }

    /**
     * 撤销记忆，重复撤销保持幂等。
     *
     * @param fact 记忆
     * @return 撤销后的记忆
     * @author 余炘洋
     * @date 2026-10-18
     */
    private AgentMemoryFact applyRevoke(AgentMemoryFact fact) {
        if (!MemoryStatus.ACTIVE.name().equals(fact.getStatus())) {
            return fact;
        }
        fact.setStatus(MemoryStatus.REVOKED.name());
        fact.setUpdatedAt(LocalDateTime.now());
        AgentMemoryFact saved = memoryFactRepository.save(fact);
        log.info("撤销长期记忆: tenantId={}, memoryId={}", fact.getTenantId(), fact.getId());
        return saved;
    }

    /**
     * 按租户加载记忆。
     *
     * @param tenantId 租户标识
     * @param id       记忆主键
     * @return 记忆
     * @author 余炘洋
     * @date 2026-10-18
     */
    private AgentMemoryFact load(String tenantId, Long id) {
        if (id == null) {
            throw new BizException(ErrorCode.INVALID_PARAM, "记忆标识不能为空");
        }
        return memoryFactRepository.findByIdAndTenantId(id, tenantId.trim())
                .orElseThrow(() -> new BizException(ErrorCode.NOT_FOUND, "记忆不存在"));
    }

    /**
     * 加载仍然生效且未过保留期的记忆。
     *
     * @param tenantId 租户标识
     * @param id       记忆主键
     * @return 记忆
     * @author 余炘洋
     * @date 2026-10-18
     */
    private AgentMemoryFact loadActive(String tenantId, Long id) {
        AgentMemoryFact fact = load(tenantId, id);
        if (!MemoryStatus.ACTIVE.name().equals(fact.getStatus())) {
            throw new BizException(ErrorCode.CONFLICT, "记忆已失效，无法继续操作");
        }
        if (isExpired(fact, LocalDateTime.now())) {
            throw new BizException(ErrorCode.CONFLICT, "记忆已过保留期，无法继续操作");
        }
        return fact;
    }

    /**
     * 校验租户、用户与记忆层级的绑定关系。
     *
     * @param tenantId 租户标识
     * @param userId   用户标识
     * @param tier     记忆层级
     * @author 余炘洋
     * @date 2026-10-18
     */
    private void requireScope(String tenantId, Long userId, MemoryTier tier) {
        if (!StringUtils.hasText(tenantId)) {
            throw new BizException(ErrorCode.INVALID_PARAM, "租户标识不能为空");
        }
        if (tier == null) {
            throw new BizException(ErrorCode.INVALID_PARAM, "记忆层级不合法");
        }
        if (tier.tenantScoped() && userId != null) {
            throw new BizException(ErrorCode.INVALID_PARAM, "租户级程序记忆不允许绑定用户");
        }
        if (!tier.tenantScoped() && userId == null) {
            throw new BizException(ErrorCode.INVALID_PARAM, "用户级永久记忆必须绑定用户");
        }
    }

    /**
     * 解析保留期到期时间。
     *
     * @param tier          记忆层级
     * @param retentionDays 保留天数
     * @param now           当前时间
     * @return 到期时间
     * @author 余炘洋
     * @date 2026-10-18
     */
    private LocalDateTime resolveExpiresAt(MemoryTier tier, Integer retentionDays, LocalDateTime now) {
        int days;
        if (retentionDays == null) {
            days = tier != null && tier.tenantScoped() ? DEFAULT_PROCEDURAL_RETENTION_DAYS
                    : DEFAULT_PERMANENT_RETENTION_DAYS;
        } else {
            days = retentionDays;
        }
        if (days < MIN_RETENTION_DAYS || days > MAX_RETENTION_DAYS) {
            throw new BizException(ErrorCode.INVALID_PARAM,
                    "保留期必须在 " + MIN_RETENTION_DAYS + "-" + MAX_RETENTION_DAYS + " 天之间");
        }
        return now.plusDays(days);
    }

    /**
     * 判断记忆是否已过保留期。
     *
     * @param fact 记忆
     * @param now  当前时间
     * @return 是否过期
     * @author 余炘洋
     * @date 2026-10-18
     */
    private boolean isExpired(AgentMemoryFact fact, LocalDateTime now) {
        return fact.getExpiresAt() != null && !fact.getExpiresAt().isAfter(now);
    }

    /**
     * 渲染单条记忆提示行。
     *
     * @param fact 记忆
     * @return 提示行
     * @author 余炘洋
     * @date 2026-10-18
     */
    private String renderPromptLine(AgentMemoryFact fact) {
        MemoryTier tier = MemoryTier.of(fact.getTier());
        MemorySourceType sourceType = MemorySourceType.of(fact.getSourceType());
        String text = fact.getFactText() == null ? "" : fact.getFactText();
        if (text.length() > PROMPT_FACT_MAX_LENGTH) {
            text = text.substring(0, PROMPT_FACT_MAX_LENGTH) + "…";
        }
        return "- [" + (tier == null ? fact.getTier() : tier.label())
                + "]（" + (sourceType == null ? fact.getSourceType() : sourceType.label())
                + " · " + fact.getUpdatedAt().toLocalDate() + "）" + text;
    }
}
