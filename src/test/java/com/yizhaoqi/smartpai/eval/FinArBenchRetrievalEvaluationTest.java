package com.yizhaoqi.smartpai.eval;

import com.yizhaoqi.smartpai.eval.model.EvalRunReport;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.TestPropertySource;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

/**
 * FinAR-Bench 检索评测集成测试。
 *
 * <p>需要 ES 和 MySQL 运行。Redis 通过排除自动配置 + mock 降级。</p>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@TestPropertySource(properties = {
        "spring.flyway.enabled=false",
        "spring.autoconfigure.exclude=" +
                "org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration," +
                "org.springframework.boot.autoconfigure.data.redis.RedisReactiveAutoConfiguration," +
                "org.springframework.boot.autoconfigure.data.redis.RedisRepositoriesAutoConfiguration",
        "retrieval.vector-enabled=true",
        "retrieval.fact-enabled=false",
        "logging.level.com.yizhaoqi.smartpai.eval=DEBUG"
})
@Tag("integration")
class FinArBenchRetrievalEvaluationTest {

    @Autowired
    private FinArBenchEvalService evalService;

    /** Mock Redis 依赖链：ConnectionFactory → RedisConfig 创建 RedisTemplate<String,Object>；StringRedisTemplate 供 ChatHandler 等使用。 */
    @TestConfiguration
    static class RedisMockConfig {
        @Bean
        @Primary
        RedisConnectionFactory redisConnectionFactory() {
            return mock(RedisConnectionFactory.class);
        }

        @Bean
        @Primary
        StringRedisTemplate stringRedisTemplate() {
            return mock(StringRedisTemplate.class);
        }
    }

    @Test
    void evaluateFinArBenchRetrieval() throws Exception {
        EvalRunReport report = evalService.evaluate(
                Path.of("data/FinAR-Bench/dev.txt"), 10, false);

        assertNotNull(report);
        System.out.println("======================================");
        System.out.println("  Evaluation complete!");
        System.out.println("  Total:   " + report.totalCases());
        System.out.println("  Passed:  " + report.passedCases());
        System.out.println("  Skipped: " + report.skippedCases());
        System.out.println("  Recall@10: " + String.format("%.2f", report.avgRecallAt10()));
        System.out.println("  MRR:       " + String.format("%.2f", report.avgMrr()));
        System.out.println("======================================");

        assertTrue(report.totalCases() > 0, "should have loaded cases");
        assertTrue(report.passedCases() >= 0, "should have counted passed cases");
    }
}
