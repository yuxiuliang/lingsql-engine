package com.lingsql.billing.service;

import com.lingsql.billing.VendorBillEvidenceDigest;
import com.lingsql.billing.entity.VendorBillEvent;
import com.lingsql.billing.invocation.BillingInvocationEntity;
import com.lingsql.billing.invocation.BillingInvocationRepository;
import com.lingsql.billing.invocation.BillingInvocationState;
import com.lingsql.billing.model.VendorBillImportReport;
import com.lingsql.billing.model.VendorBillImportRow;
import com.lingsql.billing.model.VendorBillMatchState;
import com.lingsql.billing.repository.VendorBillEventRepository;
import com.lingsql.common.exception.BizException;
import com.lingsql.common.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 供应商账单导入服务：解析受控 CSV，逐行校验后落库为不可变账单事件，并尝试与平台物理调用匹配。
 *
 * <p>导入策略：</p>
 * <ul>
 *     <li>表头按名称识别（大小写与下划线不敏感），必需列为 tenantId、providerName、externalBillId、amountMinor、currency、status，
 *     可选列为 providerRequestId、vendorTokens；缺少必需列时整批拒绝。</li>
 *     <li>逐行校验：非法行不会落库，以行号与原因进入导入报告；合法行独立导入，便于修正后重传。</li>
 *     <li>幂等：同一供应商与外部账单标识只允许落库一次，文件内重复与库内重复均按重复行记录，不覆盖既有记录。</li>
 *     <li>匹配：账单携带 providerRequestId 时按供应商请求标识匹配物理调用，得出 MATCHED、MISMATCH、UNKNOWN、PENDING、
 *     CONFLICT 或 NOT_FOUND，未携带标识时为 NO_REFERENCE。</li>
 * </ul>
 *
 * <p>本服务只写入供应商账单与匹配结果字段，不修改任何计费账本、余额或物理调用资金状态。</p>
 *
 * @author 余炘洋
 * @date 2026-10-17
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class VendorBillImportService {

    /**
     * 单次导入 CSV 文本长度上限（字符）。
     */
    public static final int MAX_CSV_LENGTH = 256 * 1024;

    /**
     * 单次导入数据行数上限。
     */
    public static final int MAX_ROWS = 1000;

    /**
     * 导入报告保留的逐行明细上限。
     */
    public static final int MAX_REPORT_ROWS = 200;

    /**
     * 行结果：已导入。
     */
    public static final String OUTCOME_IMPORTED = "IMPORTED";

    /**
     * 行结果：重复。
     */
    public static final String OUTCOME_DUPLICATE = "DUPLICATE";

    /**
     * 行结果：拒绝。
     */
    public static final String OUTCOME_REJECTED = "REJECTED";

    private static final Pattern TENANT_ID_PATTERN = Pattern.compile("[A-Za-z0-9_-]{1,64}");

    private static final Pattern PROVIDER_PATTERN = Pattern.compile("[A-Za-z0-9_.-]{1,128}");

    private static final Pattern BILL_ID_PATTERN = Pattern.compile("[A-Za-z0-9_.:-]{1,128}");

    private static final Pattern REQUEST_ID_PATTERN = Pattern.compile("[A-Za-z0-9_.:-]{1,128}");

    private static final Pattern CURRENCY_PATTERN = Pattern.compile("[A-Za-z]{3}");

    private static final Pattern STATUS_PATTERN = Pattern.compile("[A-Za-z0-9_-]{1,32}");

    private final VendorBillEventRepository repository;

    private final BillingInvocationRepository invocationRepository;

    /**
     * 导入供应商账单 CSV。
     *
     * @param csv 受控 CSV 文本
     * @return 导入报告
     * @author 余炘洋
     * @date 2026-10-17
     */
    @Transactional
    public VendorBillImportReport importCsv(String csv) {
        if (!StringUtils.hasText(csv)) {
            throw new BizException(ErrorCode.INVALID_PARAM, "供应商账单 CSV 内容不能为空");
        }
        if (csv.length() > MAX_CSV_LENGTH) {
            throw new BizException(ErrorCode.INVALID_PARAM, "供应商账单 CSV 长度超过上限");
        }
        List<String> lines = List.of(csv.split("\\r\\n|\\r|\\n", -1));
        int headerLineNumber = -1;
        Header header = null;
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            if (isSkippable(line)) {
                continue;
            }
            header = parseHeader(splitCsvLine(line).values());
            headerLineNumber = i + 1;
            break;
        }
        if (header == null) {
            throw new BizException(ErrorCode.INVALID_PARAM, "供应商账单 CSV 缺少表头行");
        }
        List<VendorBillImportRow> reportRows = new ArrayList<>();
        Map<String, Integer> matchSummary = new LinkedHashMap<>();
        Set<String> seenKeys = new HashSet<>();
        int totalRows = 0;
        int importedCount = 0;
        int duplicateCount = 0;
        int rejectedCount = 0;
        LocalDateTime now = LocalDateTime.now();
        for (int i = headerLineNumber; i < lines.size(); i++) {
            String line = lines.get(i);
            if (isSkippable(line)) {
                continue;
            }
            totalRows++;
            if (totalRows > MAX_ROWS) {
                throw new BizException(ErrorCode.INVALID_PARAM, "供应商账单数据行数超过单次导入上限");
            }
            int lineNumber = i + 1;
            SplitLine splitLine = splitCsvLine(line);
            if (splitLine.malformed()) {
                rejectedCount++;
                addReportRow(reportRows, new VendorBillImportRow(lineNumber, null, OUTCOME_REJECTED, null, "引号未闭合"));
                continue;
            }
            ParsedRow parsedRow = parseRow(header, splitLine.values(), lineNumber);
            if (parsedRow.error() != null) {
                rejectedCount++;
                addReportRow(reportRows, new VendorBillImportRow(lineNumber, parsedRow.externalBillId(),
                        OUTCOME_REJECTED, null, parsedRow.error()));
                continue;
            }
            String dedupeKey = parsedRow.providerName() + "\u0000" + parsedRow.externalBillId();
            if (!seenKeys.add(dedupeKey)
                    || repository.findFirstByProviderNameAndExternalBillId(parsedRow.providerName(), parsedRow.externalBillId())
                    .isPresent()) {
                duplicateCount++;
                addReportRow(reportRows, new VendorBillImportRow(lineNumber, parsedRow.externalBillId(),
                        OUTCOME_DUPLICATE, null, "同一供应商与外部账单标识已导入"));
                continue;
            }
            VendorBillEvent event = toEvent(parsedRow, now);
            applyMatchState(event, now);
            repository.save(event);
            importedCount++;
            matchSummary.merge(event.getMatchState(), 1, Integer::sum);
            addReportRow(reportRows, new VendorBillImportRow(lineNumber, event.getExternalBillId(),
                    OUTCOME_IMPORTED, event.getMatchState(), VendorBillMatchState.valueOf(event.getMatchState()).getDescription()));
        }
        if (totalRows == 0) {
            throw new BizException(ErrorCode.INVALID_PARAM, "供应商账单 CSV 不包含数据行");
        }
        log.info("供应商账单导入完成: 数据行={} 导入={} 重复={} 拒绝={}", totalRows, importedCount, duplicateCount, rejectedCount);
        return new VendorBillImportReport(VendorBillEvidenceDigest.sha256Hex(csv), totalRows, importedCount,
                duplicateCount, rejectedCount, matchSummary, reportRows);
    }

    private void addReportRow(List<VendorBillImportRow> rows, VendorBillImportRow row) {
        if (rows.size() < MAX_REPORT_ROWS) {
            rows.add(row);
        }
    }

    private boolean isSkippable(String line) {
        return !StringUtils.hasText(line) || line.trim().startsWith("#");
    }

    private VendorBillEvent toEvent(ParsedRow row, LocalDateTime now) {
        VendorBillEvent event = new VendorBillEvent();
        event.setTenantId(row.tenantId());
        event.setProviderName(row.providerName());
        event.setExternalBillId(row.externalBillId());
        event.setProviderRequestId(row.providerRequestId());
        event.setAmountMinor(row.amountMinor());
        event.setVendorTokens(row.vendorTokens());
        event.setCurrency(row.currency());
        event.setStatus(row.status());
        event.setCreatedAt(now);
        return event;
    }

    /**
     * 按供应商请求标识匹配物理调用并固化匹配状态。
     *
     * @param event 待匹配账单
     * @param now   匹配时间
     * @author 余炘洋
     * @date 2026-10-17
     */
    private void applyMatchState(VendorBillEvent event, LocalDateTime now) {
        VendorBillMatchState state;
        if (!StringUtils.hasText(event.getProviderRequestId())) {
            state = VendorBillMatchState.NO_REFERENCE;
        } else {
            Optional<BillingInvocationEntity> optional = invocationRepository
                    .findFirstByProviderRequestId(event.getProviderRequestId());
            if (optional.isEmpty()) {
                state = VendorBillMatchState.NOT_FOUND;
            } else {
                BillingInvocationEntity invocation = optional.get();
                event.setMatchedInvocationId(invocation.getInvocationId());
                BillingInvocationState invocationState = parseInvocationState(invocation.getState());
                if (invocationState == null) {
                    state = VendorBillMatchState.NOT_FOUND;
                } else {
                    state = switch (invocationState) {
                        case RESERVED, DISPATCHED -> VendorBillMatchState.PENDING;
                        case UNKNOWN -> VendorBillMatchState.UNKNOWN;
                        case SETTLED -> isConsistent(event, invocation)
                                ? VendorBillMatchState.MATCHED : VendorBillMatchState.MISMATCH;
                        case CANCELLED -> VendorBillMatchState.CONFLICT;
                    };
                }
            }
        }
        event.setMatchState(state.name());
        event.setMatchTime(now);
    }

    private boolean isConsistent(VendorBillEvent event, BillingInvocationEntity invocation) {
        if (!Objects.equals(event.getAmountMinor(), invocation.getActualAmount())) {
            return false;
        }
        return event.getVendorTokens() == null || invocation.getActualTokens() == null
                || event.getVendorTokens().equals(invocation.getActualTokens());
    }

    /**
     * 解析物理调用状态，未知取值返回 null。
     *
     * @param state 状态字符串
     * @return 物理调用状态，无法识别时为 null
     * @author 余炘洋
     * @date 2026-10-17
     */
    private BillingInvocationState parseInvocationState(String state) {
        if (!StringUtils.hasText(state)) {
            return null;
        }
        try {
            return BillingInvocationState.valueOf(state.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            log.warn("物理调用状态无法识别，按未匹配处理: {}", state);
            return null;
        }
    }

    private Header parseHeader(List<String> cells) {
        Map<String, Integer> index = new HashMap<>();
        for (int i = 0; i < cells.size(); i++) {
            index.putIfAbsent(normalizeHeader(cells.get(i)), i);
        }
        List<String> missing = new ArrayList<>();
        Integer tenantId = required(index, missing, "tenantId", "tenantid");
        Integer providerName = required(index, missing, "providerName", "providername");
        Integer externalBillId = required(index, missing, "externalBillId", "externalbillid", "billid");
        Integer amountMinor = required(index, missing, "amountMinor", "amountminor", "amount");
        Integer currency = required(index, missing, "currency");
        Integer status = required(index, missing, "status");
        if (!missing.isEmpty()) {
            throw new BizException(ErrorCode.INVALID_PARAM, "供应商账单 CSV 缺少必需列: " + String.join(", ", missing));
        }
        return new Header(cells.size(), tenantId, providerName, externalBillId, amountMinor, currency, status,
                index.get("providerrequestid"), index.get("vendortokens"));
    }

    private Integer required(Map<String, Integer> index, List<String> missing, String... aliases) {
        for (String alias : aliases) {
            Integer value = index.get(alias);
            if (value != null) {
                return value;
            }
        }
        missing.add(aliases.length == 0 ? "unknown" : aliases[0]);
        return null;
    }

    private String normalizeHeader(String name) {
        return name == null ? "" : name.trim().toLowerCase(Locale.ROOT).replace("_", "").replace("-", "");
    }

    private ParsedRow parseRow(Header header, List<String> cells, int lineNumber) {
        if (cells.size() != header.columnCount()) {
            return ParsedRow.error("列数与表头不一致，期望 " + header.columnCount() + " 列，实际 " + cells.size() + " 列");
        }
        String externalBillId = cell(cells, header.externalBillIdIndex());
        if (!BILL_ID_PATTERN.matcher(externalBillId).matches()) {
            return ParsedRow.error("外部账单标识非法");
        }
        String tenantId = cell(cells, header.tenantIdIndex());
        if (!TENANT_ID_PATTERN.matcher(tenantId).matches()) {
            return ParsedRow.error("租户标识非法", externalBillId);
        }
        String providerName = cell(cells, header.providerNameIndex());
        if (!PROVIDER_PATTERN.matcher(providerName).matches()) {
            return ParsedRow.error("供应商名称非法", externalBillId);
        }
        String amountText = cell(cells, header.amountMinorIndex());
        long amountMinor;
        try {
            amountMinor = Long.parseLong(amountText);
        } catch (NumberFormatException exception) {
            return ParsedRow.error("金额（分）必须为整数", externalBillId);
        }
        if (amountMinor < 0) {
            return ParsedRow.error("金额（分）不能为负数", externalBillId);
        }
        String currency = cell(cells, header.currencyIndex()).toUpperCase(Locale.ROOT);
        if (!CURRENCY_PATTERN.matcher(currency).matches()) {
            return ParsedRow.error("币种必须为 3 位字母", externalBillId);
        }
        String status = cell(cells, header.statusIndex()).toUpperCase(Locale.ROOT);
        if (!STATUS_PATTERN.matcher(status).matches()) {
            return ParsedRow.error("账单状态非法", externalBillId);
        }
        String providerRequestId = null;
        if (header.providerRequestIdIndex() != null) {
            String rawRequestId = cell(cells, header.providerRequestIdIndex());
            if (StringUtils.hasText(rawRequestId)) {
                if (!REQUEST_ID_PATTERN.matcher(rawRequestId).matches()) {
                    return ParsedRow.error("供应商请求标识非法", externalBillId);
                }
                providerRequestId = rawRequestId;
            }
        }
        Integer vendorTokens = null;
        if (header.vendorTokensIndex() != null) {
            String rawTokens = cell(cells, header.vendorTokensIndex());
            if (StringUtils.hasText(rawTokens)) {
                try {
                    vendorTokens = Integer.parseInt(rawTokens);
                } catch (NumberFormatException exception) {
                    return ParsedRow.error("Token 数必须为整数", externalBillId);
                }
                if (vendorTokens < 0) {
                    return ParsedRow.error("Token 数不能为负数", externalBillId);
                }
            }
        }
        return new ParsedRow(lineNumber, tenantId, providerName, externalBillId, providerRequestId,
                amountMinor, vendorTokens, currency, status, null);
    }

    private String cell(List<String> cells, Integer index) {
        if (index == null || index < 0 || index >= cells.size()) {
            return "";
        }
        String value = cells.get(index);
        return value == null ? "" : value.trim();
    }

    /**
     * 拆分 CSV 行，支持双引号包裹字段与双引号转义。
     *
     * @param line 原始行
     * @return 拆分结果，引号未闭合时标记为非法
     * @author 余炘洋
     * @date 2026-10-17
     */
    private SplitLine splitCsvLine(String line) {
        List<String> values = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inQuotes = false;
        for (int i = 0; i < line.length(); i++) {
            char ch = line.charAt(i);
            if (inQuotes) {
                if (ch == '"') {
                    if (i + 1 < line.length() && line.charAt(i + 1) == '"') {
                        current.append('"');
                        i++;
                    } else {
                        inQuotes = false;
                    }
                } else {
                    current.append(ch);
                }
                continue;
            }
            if (ch == '"') {
                inQuotes = true;
            } else if (ch == ',') {
                values.add(current.toString());
                current.setLength(0);
            } else {
                current.append(ch);
            }
        }
        if (inQuotes) {
            return new SplitLine(List.of(), true);
        }
        values.add(current.toString());
        return new SplitLine(values, false);
    }

    /**
     * CSV 表头列索引。
     *
     * @param columnCount            表头列数
     * @param tenantIdIndex          租户列索引
     * @param providerNameIndex      供应商列索引
     * @param externalBillIdIndex    外部账单列索引
     * @param amountMinorIndex       金额列索引
     * @param currencyIndex          币种列索引
     * @param statusIndex            状态列索引
     * @param providerRequestIdIndex 供应商请求标识列索引，可为空
     * @param vendorTokensIndex      Token 数列索引，可为空
     * @author 余炘洋
     * @date 2026-10-17
     */
    private record Header(int columnCount,
                          int tenantIdIndex,
                          int providerNameIndex,
                          int externalBillIdIndex,
                          int amountMinorIndex,
                          int currencyIndex,
                          int statusIndex,
                          Integer providerRequestIdIndex,
                          Integer vendorTokensIndex) {
    }

    /**
     * 单行拆分结果。
     *
     * @param values    字段列表
     * @param malformed 是否格式错误
     * @author 余炘洋
     * @date 2026-10-17
     */
    private record SplitLine(List<String> values, boolean malformed) {
    }

    /**
     * 单行解析结果。
     *
     * @param lineNumber        行号
     * @param tenantId          租户标识
     * @param providerName      供应商名称
     * @param externalBillId    外部账单标识
     * @param providerRequestId 供应商请求标识
     * @param amountMinor       金额（分）
     * @param vendorTokens      Token 数
     * @param currency          币种
     * @param status            状态
     * @param error             错误信息，为空表示解析成功
     * @author 余炘洋
     * @date 2026-10-17
     */
    private record ParsedRow(int lineNumber,
                             String tenantId,
                             String providerName,
                             String externalBillId,
                             String providerRequestId,
                             Long amountMinor,
                             Integer vendorTokens,
                             String currency,
                             String status,
                             String error) {

        private static ParsedRow error(String message) {
            return error(message, null);
        }

        private static ParsedRow error(String message, String externalBillId) {
            return new ParsedRow(0, null, null, externalBillId, null, null, null, null, null, message);
        }
    }
}
