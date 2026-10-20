package com.lingsql.agent.memory;

import com.lingsql.agent.entity.AgentMemoryFact;
import com.lingsql.agent.orchestration.AgentExecutionContext;
import com.lingsql.agent.repository.AgentMemoryFactRepository;
import com.lingsql.chat.service.ChatMemoryService;
import com.lingsql.common.exception.BizException;
import com.lingsql.common.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * 记忆候选服务：承接模型提炼出的候选事实，负责分类、去重、审核与采纳。
 *
 * <p>受控边界：</p>
 * <ul>
 *     <li>候选默认不生效：候选行状态只写 {@code CANDIDATE}，提示词注入只读取 {@code ACTIVE}，
 *     因此候选永远不会进入模型上下文。</li>
 *     <li>采纳必须由用户确认或管理端批准，并复用 {@link AgentMemoryService#remember}
 *     的统一写入路径，写入前仍强制经过 {@link MemoryContentGuard} 的注入与敏感信息校验。</li>
 *     <li>敏感候选保留证据但一律拒绝采纳；命中注入特征的候选在提取阶段直接丢弃，不落库。</li>
 *     <li>候选与记忆同表管理，查询强制按租户隔离，跨用户操作返回 403。</li>
 * </ul>
 *
 * @author 余炘洋
 * @date 2026-10-19
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MemoryCandidateService {

    /**
     * 拒绝原因最大长度，与数据库列宽保持一致。
     */
    private static final int MAX_REJECT_REASON_LENGTH = 256;

    /**
     * 候选来源标识最大长度，与数据库列宽保持一致。
     */
    private static final int MAX_SOURCE_REF_LENGTH = 128;

    private static final int USER_CANDIDATE_LIMIT = 50;

    private static final int ADMIN_CANDIDATE_LIMIT = 200;

    /**
     * 候选保留天数：候选只是待确认建议，默认一年内有效，避免候选池无限堆积。
     */
    private static final int CANDIDATE_RETENTION_DAYS = 365;

    private final AgentMemoryFactRepository memoryFactRepository;

    private final MemoryContentGuard memoryContentGuard;

    private final MemoryCandidateExtractor memoryCandidateExtractor;

    private final AgentMemoryService agentMemoryService;

    private final ChatMemoryService chatMemoryService;

    /**
     * 从当前会话窗口提炼候选事实：只落候选池，不生效。
     *
     * @param context       Agent 执行上下文
     * @param sourceRef     候选来源标识，为空时使用会话标识
     * @param maxCandidates 候选条数上限，为空使用默认值
     * @return 本次新增的候选
     * @author 余炘洋
     * @date 2026-10-19
     */
    public List<AgentMemoryFact> extractCandidates(AgentExecutionContext context,
                                                   String sourceRef,
                                                   Integer maxCandidates) {
        if (context == null || context.userId() == null) {
            throw new BizException(ErrorCode.INVALID_PARAM, "记忆候选提取必须绑定用户");
        }
        String tenantId = requireTenant(context.tenantId());
        String history = chatMemoryService.getHistoryPrompt();
        if (!StringUtils.hasText(history)) {
            log.info("会话窗口为空，跳过记忆候选提取: tenantId={}, userId={}", tenantId, context.userId());
            return List.of();
        }
        int limit = maxCandidates == null
                ? MemoryCandidateExtractor.MAX_CANDIDATES
                : Math.max(1, Math.min(maxCandidates, MemoryCandidateExtractor.MAX_CANDIDATES));
        List<MemoryCandidateExtractor.ExtractedCandidate> extracted =
                memoryCandidateExtractor.extract(context, history, limit);
        if (extracted.isEmpty()) {
            return List.of();
        }
        String reference = resolveSourceRef(sourceRef, context);
        LocalDateTime now = LocalDateTime.now();
        List<AgentMemoryFact> saved = new ArrayList<>();
        int skippedInjection = 0;
        int skippedHistory = 0;
        int skippedInvalid = 0;
        for (MemoryCandidateExtractor.ExtractedCandidate candidate : extracted) {
            String factText = candidate.factText();
            if (!StringUtils.hasText(factText) || factText.length() > MemoryContentGuard.MAX_FACT_LENGTH) {
                skippedInvalid++;
                continue;
            }
            // 说明：命中注入特征的文本属于高信任上下文的污染源，直接丢弃且不留存。
            if (memoryContentGuard.containsInjection(factText)) {
                skippedInjection++;
                continue;
            }
            Optional<AgentMemoryFact> historical = memoryFactRepository
                    .findFirstByTenantIdAndUserIdAndTierAndFactText(
                            tenantId, context.userId(), MemoryTier.PERMANENT.name(), factText);
            if (historical.isPresent()) {
                skippedHistory++;
                continue;
            }
            saved.add(memoryFactRepository.save(buildCandidate(
                    tenantId, context.userId(), factText, candidate.confidence(), reference, now)));
        }
        log.info("模型候选记忆提取: tenantId={}, userId={}, extracted={}, saved={}, skippedHistory={}, "
                        + "skippedInjection={}, skippedInvalid={}",
                tenantId, context.userId(), extracted.size(), saved.size(),
                skippedHistory, skippedInjection, skippedInvalid);
        return saved;
    }

    /**
     * 查询用户自己的待确认候选。
     *
     * @param tenantId 租户标识
     * @param userId   用户标识
     * @return 候选列表
     * @author 余炘洋
     * @date 2026-10-19
     */
    public List<AgentMemoryFact> listOwnCandidates(String tenantId, Long userId) {
        if (userId == null) {
            throw new BizException(ErrorCode.INVALID_PARAM, "用户标识不能为空");
        }
        return listCandidates(requireTenant(tenantId), userId, USER_CANDIDATE_LIMIT);
    }

    /**
     * 查询租户内待确认候选，用于管理端治理列表。
     *
     * @param tenantId 租户标识
     * @param userId   用户标识，可为空
     * @return 候选列表
     * @author 余炘洋
     * @date 2026-10-19
     */
    public List<AgentMemoryFact> listCandidatesForAdmin(String tenantId, Long userId) {
        String tenant = requireTenant(tenantId);
        LocalDateTime now = LocalDateTime.now();
        if (userId != null) {
            return listCandidates(tenant, userId, ADMIN_CANDIDATE_LIMIT);
        }
        return memoryFactRepository
                .findTop200ByTenantIdAndStatusOrderByUpdatedAtDesc(tenant, MemoryStatus.CANDIDATE.name())
                .stream()
                .filter(fact -> !isExpired(fact, now))
                .limit(ADMIN_CANDIDATE_LIMIT)
                .collect(Collectors.toList());
    }

    /**
     * 用户确认采纳自己的候选：生成生效记忆，候选记录标记为已采纳。
     *
     * @param tenantId    租户标识
     * @param userId      用户标识
     * @param candidateId 候选主键
     * @return 生成的生效记忆
     * @author 余炘洋
     * @date 2026-10-19
     */
    @Transactional
    public AgentMemoryFact approveByUser(String tenantId, Long userId, Long candidateId) {
        if (userId == null) {
            throw new BizException(ErrorCode.INVALID_PARAM, "用户标识不能为空");
        }
        AgentMemoryFact candidate = loadCandidate(tenantId, candidateId);
        if (!userId.equals(candidate.getUserId())) {
            throw new BizException(ErrorCode.FORBIDDEN, "无权限访问");
        }
        return promote(candidate, MemorySourceType.USER_CONFIRMED, String.valueOf(userId));
    }

    /**
     * 管理端批准候选：生成生效记忆，候选记录标记为已采纳。
     *
     * @param tenantId    租户标识
     * @param candidateId 候选主键
     * @param operator    操作人标识
     * @return 生成的生效记忆
     * @author 余炘洋
     * @date 2026-10-19
     */
    @Transactional
    public AgentMemoryFact approveByAdmin(String tenantId, Long candidateId, String operator) {
        return promote(loadCandidate(tenantId, candidateId), MemorySourceType.ADMIN_APPROVED, operator);
    }

    /**
     * 用户拒绝自己的候选：候选记录标记为已拒绝，不生成任何生效记忆。
     *
     * @param tenantId    租户标识
     * @param userId      用户标识
     * @param candidateId 候选主键
     * @param reason      拒绝原因，可为空
     * @return 候选记录
     * @author 余炘洋
     * @date 2026-10-19
     */
    @Transactional
    public AgentMemoryFact rejectByUser(String tenantId, Long userId, Long candidateId, String reason) {
        if (userId == null) {
            throw new BizException(ErrorCode.INVALID_PARAM, "用户标识不能为空");
        }
        AgentMemoryFact candidate = loadCandidate(tenantId, candidateId);
        if (!userId.equals(candidate.getUserId())) {
            throw new BizException(ErrorCode.FORBIDDEN, "无权限访问");
        }
        return applyReject(candidate, reason, String.valueOf(userId));
    }

    /**
     * 管理端拒绝候选：候选记录标记为已拒绝，不生成任何生效记忆。
     *
     * @param tenantId    租户标识
     * @param candidateId 候选主键
     * @param reason      拒绝原因，可为空
     * @param operator    操作人标识
     * @return 候选记录
     * @author 余炘洋
     * @date 2026-10-19
     */
    @Transactional
    public AgentMemoryFact rejectByAdmin(String tenantId, Long candidateId, String reason, String operator) {
        return applyReject(loadCandidate(tenantId, candidateId), reason, operator);
    }

    /**
     * 采纳候选：校验敏感分类与保留期后走统一写入路径，再回写候选状态。
     *
     * @param candidate  候选记录
     * @param sourceType 采纳来源
     * @param operator   操作人标识
     * @return 生成的生效记忆
     * @author 余炘洋
     * @date 2026-10-19
     */
    private AgentMemoryFact promote(AgentMemoryFact candidate, MemorySourceType sourceType, String operator) {
        MemorySensitivity sensitivity = MemorySensitivity.of(candidate.getSensitivity());
        if (sensitivity != null && !sensitivity.promotable()) {
            throw new BizException(ErrorCode.SECURITY_VIOLATION, "敏感候选不允许采纳为长期记忆");
        }
        LocalDateTime now = LocalDateTime.now();
        if (isExpired(candidate, now)) {
            throw new BizException(ErrorCode.CONFLICT, "候选已过保留期，无法采纳");
        }
        AgentMemoryFact memory = agentMemoryService.remember(
                candidate.getTenantId(),
                candidate.getUserId(),
                MemoryTier.of(candidate.getTier()),
                sourceType,
                candidate.getFactText(),
                null,
                operator
        );
        candidate.setStatus(MemoryStatus.APPROVED.name());
        candidate.setReviewedBy(operator);
        candidate.setReviewedAt(now);
        candidate.setApprovedMemoryId(memory.getId());
        candidate.setUpdatedAt(now);
        memoryFactRepository.save(candidate);
        log.info("采纳记忆候选: tenantId={}, candidateId={}, memoryId={}, sourceType={}",
                candidate.getTenantId(), candidate.getId(), memory.getId(), sourceType.name());
        return memory;
    }

    /**
     * 拒绝候选：保留候选正文作为证据，只更新状态与复核信息。
     *
     * @param candidate 候选记录
     * @param reason    拒绝原因
     * @param operator  操作人标识
     * @return 候选记录
     * @author 余炘洋
     * @date 2026-10-19
     */
    private AgentMemoryFact applyReject(AgentMemoryFact candidate, String reason, String operator) {
        LocalDateTime now = LocalDateTime.now();
        candidate.setStatus(MemoryStatus.REJECTED.name());
        candidate.setRejectReason(normalizeReason(reason));
        candidate.setReviewedBy(operator);
        candidate.setReviewedAt(now);
        candidate.setUpdatedAt(now);
        AgentMemoryFact saved = memoryFactRepository.save(candidate);
        log.info("拒绝记忆候选: tenantId={}, candidateId={}, reviewedBy={}",
                candidate.getTenantId(), candidate.getId(), operator);
        return saved;
    }

    /**
     * 构建候选记录：来源固定为模型候选，状态固定为待确认。
     *
     * @param tenantId   租户标识
     * @param userId     用户标识
     * @param factText   候选正文
     * @param confidence 置信度
     * @param sourceRef  来源标识
     * @param now        当前时间
     * @return 候选记录
     * @author 余炘洋
     * @date 2026-10-19
     */
    private AgentMemoryFact buildCandidate(String tenantId,
                                           Long userId,
                                           String factText,
                                           int confidence,
                                           String sourceRef,
                                           LocalDateTime now) {
        AgentMemoryFact fact = new AgentMemoryFact();
        fact.setTenantId(tenantId);
        fact.setUserId(userId);
        fact.setTier(MemoryTier.PERMANENT.name());
        fact.setFactText(factText);
        fact.setSourceType(MemorySourceType.MODEL_CANDIDATE.name());
        fact.setStatus(MemoryStatus.CANDIDATE.name());
        fact.setConfidence(confidence);
        fact.setVersion(1);
        fact.setCreatedBy(MemoryCandidateExtractor.MODEL_CALLER_TYPE);
        fact.setSourceRef(sourceRef);
        fact.setSensitivity(memoryContentGuard.classify(factText).name());
        fact.setExpiresAt(now.plusDays(CANDIDATE_RETENTION_DAYS));
        fact.setCreatedAt(now);
        fact.setUpdatedAt(now);
        return fact;
    }

    /**
     * 查询候选列表，跳过已过保留期的候选。
     *
     * @param tenantId 租户标识
     * @param userId   用户标识
     * @param limit    返回上限
     * @return 候选列表
     * @author 余炘洋
     * @date 2026-10-19
     */
    private List<AgentMemoryFact> listCandidates(String tenantId, Long userId, int limit) {
        LocalDateTime now = LocalDateTime.now();
        return memoryFactRepository
                .findTop50ByTenantIdAndUserIdAndStatusAndTierOrderByUpdatedAtDesc(
                        tenantId, userId, MemoryStatus.CANDIDATE.name(), MemoryTier.PERMANENT.name())
                .stream()
                .filter(fact -> !isExpired(fact, now))
                .limit(limit)
                .collect(Collectors.toList());
    }

    /**
     * 按租户加载待确认候选。
     *
     * @param tenantId    租户标识
     * @param candidateId 候选主键
     * @return 候选记录
     * @author 余炘洋
     * @date 2026-10-19
     */
    private AgentMemoryFact loadCandidate(String tenantId, Long candidateId) {
        if (candidateId == null) {
            throw new BizException(ErrorCode.INVALID_PARAM, "候选标识不能为空");
        }
        AgentMemoryFact fact = memoryFactRepository
                .findByIdAndTenantId(candidateId, requireTenant(tenantId))
                .orElseThrow(() -> new BizException(ErrorCode.NOT_FOUND, "记忆候选不存在"));
        if (!MemoryStatus.CANDIDATE.name().equals(fact.getStatus())) {
            throw new BizException(ErrorCode.CONFLICT, "候选已被处理，无法重复操作");
        }
        return fact;
    }

    /**
     * 解析候选来源标识：优先使用显式入参，其次会话标识，最后请求标识。
     *
     * @param sourceRef 来源标识
     * @param context   Agent 执行上下文
     * @return 规范化来源标识
     * @author 余炘洋
     * @date 2026-10-19
     */
    private String resolveSourceRef(String sourceRef, AgentExecutionContext context) {
        String candidate = StringUtils.hasText(sourceRef) ? sourceRef.trim()
                : (StringUtils.hasText(context.conversationId()) ? context.conversationId() : context.requestId());
        if (!StringUtils.hasText(candidate)) {
            return null;
        }
        return candidate.length() > MAX_SOURCE_REF_LENGTH
                ? candidate.substring(0, MAX_SOURCE_REF_LENGTH)
                : candidate;
    }

    /**
     * 规范化拒绝原因。
     *
     * @param reason 原始原因
     * @return 规范化原因，空值返回 null
     * @author 余炘洋
     * @date 2026-10-19
     */
    private String normalizeReason(String reason) {
        if (!StringUtils.hasText(reason)) {
            return null;
        }
        String normalized = reason.trim();
        return normalized.length() > MAX_REJECT_REASON_LENGTH
                ? normalized.substring(0, MAX_REJECT_REASON_LENGTH)
                : normalized;
    }

    /**
     * 校验并规范化租户标识。
     *
     * @param tenantId 租户标识
     * @return 规范化租户标识
     * @author 余炘洋
     * @date 2026-10-19
     */
    private String requireTenant(String tenantId) {
        if (!StringUtils.hasText(tenantId)) {
            throw new BizException(ErrorCode.INVALID_PARAM, "租户标识不能为空");
        }
        return tenantId.trim();
    }

    /**
     * 判断记录是否已过保留期。
     *
     * @param fact 记录
     * @param now  当前时间
     * @return 是否已过期
     * @author 余炘洋
     * @date 2026-10-19
     */
    private boolean isExpired(AgentMemoryFact fact, LocalDateTime now) {
        return fact.getExpiresAt() != null && !fact.getExpiresAt().isAfter(now);
    }
}
