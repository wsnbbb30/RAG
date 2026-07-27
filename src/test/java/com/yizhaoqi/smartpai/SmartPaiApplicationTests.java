package com.yizhaoqi.smartpai;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
@Disabled("需要 Docker 环境（MySQL/Redis/Kafka/ES/MinIO），在集成测试 profile 中启用")
class SmartPaiApplicationTests {

    @Test
    void contextLoads() {
    }

}
