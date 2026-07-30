package com.yizhaoqi.smartpai.service;

import com.yizhaoqi.smartpai.model.*;
import com.yizhaoqi.smartpai.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 从已持久化表格中抽取财务事实。
 *
 * <p>先精确识别“行指标”，再读取同一行的数值单元格，并从同列标题推导期间。
 * 任何一个关键步骤不确定时，宁可不生成事实或标记为 PENDING，绝不猜测数值口径。</p>
 */
@Service
public class FactExtractor {
    public static final String EXTRACTOR_VERSION = "financial-fact-v1";
    private static final Pattern YEAR = Pattern.compile("(20\\d{2})\\s*年?");

    private final TableModelRepository tableRepository;
    private final TableCellRepository cellRepository;
    private final FinancialReportMetadataRepository metadataRepository;
    private final FinancialFactRepository factRepository;
    private final DocumentPageRepository pageRepository;
    private final MetricDictionary metricDictionary;
    private final UnitNormalizer unitNormalizer;

    public FactExtractor(TableModelRepository tableRepository, TableCellRepository cellRepository,
                         FinancialReportMetadataRepository metadataRepository, FinancialFactRepository factRepository,
                         DocumentPageRepository pageRepository, MetricDictionary metricDictionary, UnitNormalizer unitNormalizer) {
        this.tableRepository = tableRepository; this.cellRepository = cellRepository;
        this.metadataRepository = metadataRepository; this.factRepository = factRepository;
        this.pageRepository = pageRepository;
        this.metricDictionary = metricDictionary; this.unitNormalizer = unitNormalizer;
    }

    /** 重建单个版本的事实快照；版本内先删后写，因此重复执行不会累计重复记录。 */
    @Transactional
    public List<FinancialFact> replaceFacts(Long versionId) {
        factRepository.deleteByVersionId(versionId);
        Optional<FinancialReportMetadata> metadataOptional = metadataRepository.findByVersionId(versionId);
        if (metadataOptional.isEmpty()) return List.of();
        FinancialReportMetadata metadata = metadataOptional.get();
        Map<Long, Integer> pageNos = new HashMap<>();
        pageRepository.findByVersionIdOrderByPageNoAsc(versionId)
                .forEach(page -> pageNos.put(page.getId(), page.getPageNo()));
        List<FinancialFact> facts = new ArrayList<>();
        for (TableModel table : tableRepository.findByVersionIdOrderByPageStartAscIdAsc(versionId)) {
            facts.addAll(extractTable(versionId, metadata, table,
                    cellRepository.findByTableIdOrderByRowNoAscColumnNoAsc(table.getId()), pageNos));
        }
        return factRepository.saveAll(facts);
    }

    private List<FinancialFact> extractTable(Long versionId, FinancialReportMetadata metadata, TableModel table,
                                             List<TableCell> cells, Map<Long, Integer> pageNos) {
        Map<Integer, List<TableCell>> rows = new TreeMap<>();
        cells.forEach(cell -> rows.computeIfAbsent(cell.getRowNo(), ignored -> new ArrayList<>()).add(cell));
        Map<Integer, String> headers = headers(rows);
        List<FinancialFact> facts = new ArrayList<>();
        for (List<TableCell> row : rows.values()) {
            row.sort(Comparator.comparing(TableCell::getColumnNo));
            for (TableCell labelCell : row) {
                Optional<FinancialMetric> metric = metricDictionary.resolve(labelCell.getTextContent());
                if (metric.isEmpty()) continue;
                for (TableCell valueCell : row) {
                    if (Objects.equals(valueCell.getId(), labelCell.getId())) continue;
                    unitNormalizer.normalize(valueCell.getTextContent(), table.getUnitText(), metadata.getCurrency())
                            .ifPresent(number -> facts.add(toFact(versionId, metadata, table, metric.get(), labelCell,
                                    valueCell, headers.get(valueCell.getColumnNo()), pageNos.get(valueCell.getPageId()), number)));
                }
                // 同一行只允许一个指标标签，防止标题行中多个别名导致重复抽取。
                break;
            }
        }
        return facts;
    }

    private Map<Integer, String> headers(Map<Integer, List<TableCell>> rows) {
        List<TableCell> headerRow = rows.getOrDefault(0, List.of());
        Map<Integer, String> result = new HashMap<>();
        headerRow.forEach(cell -> result.put(cell.getColumnNo(), cell.getTextContent()));
        return result;
    }

    private FinancialFact toFact(Long versionId, FinancialReportMetadata metadata, TableModel table, FinancialMetric metric,
                                 TableCell labelCell, TableCell valueCell, String columnHeader,
                                 Integer pageNo, UnitNormalizer.NormalizedNumber number) {
        FinancialFact fact = new FinancialFact();
        fact.setVersionId(versionId); fact.setMetricCode(metric.getMetricCode());
        fact.setPeriod(resolvePeriod(columnHeader, metadata.getFiscalYear())); fact.setScope(metadata.getScope());
        fact.setValue(number.value()); fact.setRawValue(number.rawValue()); fact.setRawUnit(number.rawUnit());
        fact.setCurrency(number.currency()); fact.setScale(number.scale());
        fact.setTableId(table.getId()); fact.setRowNo(valueCell.getRowNo()); fact.setColumnNo(valueCell.getColumnNo());
        fact.setSourceCellId(valueCell.getId()); fact.setPageNo(pageNo == null ? table.getPageStart() : pageNo);
        fact.setX0(valueCell.getX0()); fact.setY0(valueCell.getY0()); fact.setX1(valueCell.getX1()); fact.setY1(valueCell.getY1());
        fact.setEvidenceText("表格：" + nullToEmpty(table.getTitleText()) + "；指标：" + nullToEmpty(labelCell.getTextContent())
                + "；列：" + nullToEmpty(columnHeader) + "；原始值：" + nullToEmpty(valueCell.getTextContent()));
        fact.setConfidence(confidence(table, labelCell, valueCell, columnHeader));
        fact.setReviewStatus(fact.getConfidence() == FinancialFact.Confidence.LOW
                ? FinancialFact.ReviewStatus.PENDING : FinancialFact.ReviewStatus.APPROVED);
        fact.setExtractorVersion(EXTRACTOR_VERSION);
        return fact;
    }

    private String resolvePeriod(String header, Integer fiscalYear) {
        Matcher matcher = YEAR.matcher(nullToEmpty(header));
        if (matcher.find()) return "FY" + matcher.group(1);
        String normalized = nullToEmpty(header);
        if (normalized.contains("上期") || normalized.contains("上年")) return "FY" + (fiscalYear - 1);
        return "FY" + fiscalYear;
    }
    private FinancialFact.Confidence confidence(TableModel table, TableCell label, TableCell value, String header) {
        if (table.getConfidence().compareTo(new BigDecimal("0.80")) >= 0
                && label.getConfidence().compareTo(new BigDecimal("0.80")) >= 0
                && value.getConfidence().compareTo(new BigDecimal("0.80")) >= 0 && header != null) return FinancialFact.Confidence.HIGH;
        if (table.getConfidence().compareTo(new BigDecimal("0.60")) >= 0) return FinancialFact.Confidence.MEDIUM;
        return FinancialFact.Confidence.LOW;
    }
    private String nullToEmpty(String value) { return value == null ? "" : value; }
}
