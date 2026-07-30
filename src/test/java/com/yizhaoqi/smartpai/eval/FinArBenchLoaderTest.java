package com.yizhaoqi.smartpai.eval;

import com.yizhaoqi.smartpai.eval.model.EvaluationCase;
import com.yizhaoqi.smartpai.eval.model.GroundTruthFact;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class FinArBenchLoaderTest {

    private final FinArBenchLoader loader = new FinArBenchLoader();

    @Test
    void extractsStockCodeFromCompanyCode() throws IOException {
        String jsonl = """
                {"table":"# table","instances":[{"task_id":"t1","task":"test","ground_truth":"","task_type":"fact","company":"测试公司","company_code":"603421.SH"}],"file_path":""}
                """;
        List<EvaluationCase> cases = loadString(jsonl);
        assertEquals(1, cases.size());
        assertEquals("603421", cases.get(0).stockCode());
    }

    @Test
    void extractsLatestYearFromTask() throws IOException {
        String jsonl = """
                {"table":"# table","instances":[{"task_id":"t1","task":"获取2022和2023的营收数据","ground_truth":"","task_type":"fact","company":"测试","company_code":"600519.SH"}],"file_path":""}
                """;
        List<EvaluationCase> cases = loadString(jsonl);
        assertEquals(2023, cases.get(0).fiscalYear());
    }

    @Test
    void returnsNullWhenNoYearInTask() throws IOException {
        String jsonl = """
                {"table":"# table","instances":[{"task_id":"t1","task":"公司的偿债风险有哪些","ground_truth":"","task_type":"reasoning","company":"测试","company_code":"600519.SH"}],"file_path":""}
                """;
        List<EvaluationCase> cases = loadString(jsonl);
        assertNull(cases.get(0).fiscalYear());
    }

    @Test
    void parsesGroundTruthTable() {
        String markdown = """
                | 项目 | 2022 | 2023 |
                |------|------|------|
                | 营业收入 | 3,114,981,021.66 | 3,632,703,199.78 |
                | 净利润 | 118,680,630.51 | 131,220,189.16 |
                """;
        List<GroundTruthFact> facts = loader.parseGroundTruth(markdown);
        assertEquals(2, facts.size());

        GroundTruthFact f1 = facts.get(0);
        assertEquals("营业收入", f1.metricName());
        assertEquals("3114981021.66", f1.yearValues().get("2022"));
        assertEquals("3632703199.78", f1.yearValues().get("2023"));

        GroundTruthFact f2 = facts.get(1);
        assertEquals("净利润", f2.metricName());
        assertEquals("118680630.51", f2.yearValues().get("2022"));
    }

    @Test
    void parsesGroundTruthWithDifferentYears() {
        String markdown = """
                | 项目 | 2021 | 2022 |
                |------|------|------|
                | 总资产 | 1,000,000.00 | 1,200,000.00 |
                """;
        List<GroundTruthFact> facts = loader.parseGroundTruth(markdown);
        assertEquals(1, facts.size());
        GroundTruthFact f = facts.get(0);
        assertEquals("1000000.00", f.yearValues().get("2021"));
        assertEquals("1200000.00", f.yearValues().get("2022"));
    }

    @Test
    void handlesMultipleCompaniesAndInstances(@TempDir Path tmpDir) throws IOException {
        String jsonl = """
                {"table":"# t1","instances":[{"task_id":"t1","task":"2023 营收","ground_truth":"","task_type":"fact","company":"A","company_code":"600519.SH"},{"task_id":"t2","task":"2023 利润","ground_truth":"","task_type":"fact","company":"A","company_code":"600519.SH"}],"file_path":""}
                {"table":"# t2","instances":[{"task_id":"t3","task":"2023 资产","ground_truth":"","task_type":"indicator","company":"B","company_code":"000002.SZ"}],"file_path":""}
                """;
        Path file = tmpDir.resolve("test.txt");
        Files.writeString(file, jsonl);
        List<EvaluationCase> cases = loader.load(file);
        assertEquals(3, cases.size());
        assertEquals("600519", cases.get(0).stockCode());
        assertEquals("600519", cases.get(1).stockCode());
        assertEquals("000002", cases.get(2).stockCode());
        assertNotEquals(cases.get(0).tableContext(), cases.get(2).tableContext());
    }

    @Test
    void normalizeNumberRemovesCommas() {
        assertEquals("3632703199.78", FinArBenchLoader.normalizeNumber("3,632,703,199.78"));
        assertEquals("1000", FinArBenchLoader.normalizeNumber("1,000"));
        assertEquals("0.00", FinArBenchLoader.normalizeNumber("0.00"));
    }

    private List<EvaluationCase> loadString(String jsonl) throws IOException {
        Path tmp = Files.createTempFile("finarbench-test-", ".txt");
        Files.writeString(tmp, jsonl);
        try {
            return loader.load(tmp);
        } finally {
            Files.deleteIfExists(tmp);
        }
    }
}
