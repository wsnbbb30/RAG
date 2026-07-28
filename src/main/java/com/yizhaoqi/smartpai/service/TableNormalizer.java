package com.yizhaoqi.smartpai.service;

import com.yizhaoqi.smartpai.model.DocumentPage;
import com.yizhaoqi.smartpai.model.TableCell;
import com.yizhaoqi.smartpai.model.TableModel;
import com.yizhaoqi.smartpai.parser.ParsedPage;
import com.yizhaoqi.smartpai.parser.ParsedTable;
import com.yizhaoqi.smartpai.parser.ParsedTableCell;
import com.yizhaoqi.smartpai.repository.TableCellRepository;
import com.yizhaoqi.smartpai.repository.TableModelRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 将解析器纯数据对象转换为可审计的表格实体。
 *
 * <p>本类不负责“识别”表格，亦不负责把字符串变成金额；它只做结构规范化与持久化，
 * 从而使 S3-02 的财务事实抽取可以独立演进，不反向污染 PDF 解析逻辑。</p>
 */
@Service
public class TableNormalizer {
    private final TableModelRepository tableRepository;
    private final TableCellRepository cellRepository;
    private final CrossPageTableMerger crossPageTableMerger;

    public TableNormalizer(TableModelRepository tableRepository, TableCellRepository cellRepository,
                           CrossPageTableMerger crossPageTableMerger) {
        this.tableRepository = tableRepository;
        this.cellRepository = cellRepository;
        this.crossPageTableMerger = crossPageTableMerger;
    }

    /**
     * 清空该版本旧表格后写入新快照，保证 Kafka 重放或人工重解析的幂等性。
     * @return 跨页合并后的表格，用于写入 MinIO JSON/Markdown 产物
     */
    public List<ParsedTable> replaceTables(Long versionId, List<ParsedPage> pages, Map<Integer, DocumentPage> pageByNo) {
        List<Long> oldTableIds = tableRepository.findByVersionIdOrderByPageStartAscIdAsc(versionId).stream()
                .map(TableModel::getId).toList();
        if (!oldTableIds.isEmpty()) cellRepository.deleteByTableIdIn(oldTableIds);
        tableRepository.deleteByVersionId(versionId);

        List<ParsedTable> detected = pages.stream().flatMap(page -> page.tables().stream()).toList();
        List<ParsedTable> tables = crossPageTableMerger.merge(detected);
        int sequence = 1;
        for (ParsedTable parsed : tables) {
            TableModel table = new TableModel();
            table.setVersionId(versionId);
            table.setTableRef("table-" + versionId + "-" + sequence++);
            table.setTitleText(parsed.title()); table.setUnitText(parsed.unitText());
            table.setPageStart(parsed.pageStart()); table.setPageEnd(parsed.pageEnd());
            table.setX0(parsed.boundingBox().x0()); table.setY0(parsed.boundingBox().y0());
            table.setX1(parsed.boundingBox().x1()); table.setY1(parsed.boundingBox().y1());
            table.setConfidence(decimal(parsed.confidence()));
            table = tableRepository.save(table);

            List<TableCell> cells = new ArrayList<>();
            for (ParsedTableCell source : parsed.cells()) {
                DocumentPage page = pageByNo.get(source.pageNo());
                // 不写入无法定位页面的单元格，防止破坏外键；正常解析路径不会进入该分支。
                if (page == null) continue;
                TableCell cell = new TableCell();
                cell.setTableId(table.getId()); cell.setPageId(page.getId());
                cell.setRowNo(source.rowNo()); cell.setColumnNo(source.columnNo());
                cell.setRowSpan(Math.max(1, source.rowSpan())); cell.setColumnSpan(Math.max(1, source.columnSpan()));
                cell.setTextContent(source.textContent());
                cell.setX0(source.boundingBox().x0()); cell.setY0(source.boundingBox().y0());
                cell.setX1(source.boundingBox().x1()); cell.setY1(source.boundingBox().y1());
                cell.setConfidence(decimal(source.confidence()));
                cells.add(cell);
            }
            cellRepository.saveAll(cells);
        }
        return tables;
    }

    private BigDecimal decimal(double value) {
        return BigDecimal.valueOf(Math.max(0D, Math.min(1D, value))).setScale(4, java.math.RoundingMode.HALF_UP);
    }
}
