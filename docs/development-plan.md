# PaiSmart 面向秋招的二次开发计划

> 文档性质：后续开发的唯一执行基线（Living Document）  
> 代码基线：2026-07-27 本地 `master` 分支  
> 上游规划：[rag-interview-driven-roadmap.md](./rag-interview-driven-roadmap.md)  
> 目标场景：上市公司年报智能尽调与财务指标核验  
> 约束：本计划只描述待开发事项，不把目标值写成既有成果；每完成一个任务必须同步状态、实测数据和决策记录。

## 0. 使用规则

1. 状态仅使用 `TODO / DOING / BLOCKED / DONE`；任务完成后记录 PR、提交、测试报告和实测指标。
2. P0 阻断 P1/P2；涉及检索、生成、缓存、导出、日志的功能必须先通过 ACL 回归测试。
3. MySQL 是业务元数据和结构化事实的事实源，MinIO 是原文件/解析产物事实源，Elasticsearch 是可重建索引，Redis 只保存可丢失状态。
4. 任何简历数据必须来自固定数据集、固定环境、可复现脚本；不得继续使用“假设 3 ms 网络延迟”等算术模拟。
5. 本文估时按一名开发者、每天 4～6 小时有效开发时间计算。`XS≤0.5d，S=1d，M=2～3d，L=4～5d，XL>5d`；XL 必须继续拆分后才能开工。

## 1. 项目现状摘要

### 1.1 已存在且可复用

| 能力 | 当前代码 | 复用结论 |
|---|---|---|
| 认证与组织权限 | `SecurityConfig`、`JwtAuthenticationFilter`、`JwtUtils`、`TokenCacheService`、`OrgTagCacheService` | JWT + Redis 双令牌和组织标签模型可保留，但所有新入口仍需统一 ACL |
| 分片上传 | `UploadController`、`UploadService`、`ChunkInfo` | Redis BitMap、MinIO 分片、断点续传主干可复用 |
| 异步处理 | `KafkaConfig`、`FileProcessingConsumer`、`FileProcessingTask` | 实际中间件为 Kafka，含重试和 DLT；简历及文档不得写 RocketMQ |
| 基础解析 | `ParseService` | Tika、段落/句子/HanLP 降级逻辑可作为纯文本兜底 |
| 向量化 | `EmbeddingClient`、`VectorizationService` | DashScope OpenAI 兼容接口与批处理逻辑可封装为 Provider |
| 检索 | `HybridSearchService`、`knowledge_base.json` | IK、BM25、2048 维向量是可迁移基础，不应沿用当前混合查询实现 |
| 流式问答 | `ChatHandler`、`DeepSeekClient`、WebSocket | 会话和流式传输可复用，完成信号、上下文和引用协议需重构 |
| 基础设施 | `docs/docker-compose.yaml` | MySQL、Redis、MinIO、Kafka、ES/IK 可作为本地环境基础 |

### 1.2 部分实现或名不副实

- `ParseService` 注释称“父子切片”，实际父块只是一段 1 MB 缓冲区，未持久化 `parentId`、页码、标题、表格或布局。
- `HybridSearchService` 同时配置顶层 `query` 和 `knn`，并用 BM25 rescore；这不是可独立评测的 BM25/KNN 双路召回 + RRF。
- `ChatHandler` 固定 `topK=5`、单片段最多 300 字符，未按 token budget、意图、问题复杂度动态分配上下文。
- Prompt 要求引用和无结果回答，但后端没有相关性阈值、置信度校准、引用存在性校验及强制拒答状态机。
- 日志有自定义 `LogUtils`，但缺少贯穿上传、解析、索引、检索、生成的 `traceId/documentId/versionId/runId` 和标准指标。

### 1.3 明确缺失

- 布局感知 PDF 解析、OCR、图片/表格结构、页码与 bbox。
- 年报元数据、财务事实、单位/币种/合并口径、确定性计算、证据核验。
- 文档版本、chunk hash、差量索引、别名切换、可回滚生命周期。
- RAG 离线评测集、评测运行、Bad Case 归因、消融实验和 CI 门禁。
- LLM/Embedding/Reranker Provider SPI、模型路由、熔断、限流、成本统计。

### 1.4 当前可验证基线

- `mvn -DskipTests package` 可编译打包。
- 2026-07-27 实跑 `mvn test`：24 个测试中 6 个通过、18 个报错。`ParseServiceTest` 与 `UploadServicePerformanceTest` 直接加载 Spring 上下文并依赖本机 MySQL；`UserServiceTest` 缺 `OrganizationTagRepository`；`JwtUtilsRefreshTest` 缺 `TokenCacheService`；上下文测试继发失败。
- `UploadServicePerformanceTest` 只进行延迟假设的算术计算，不是性能测试，不能支撑“100 MB 8 s→2 s”。
- `application*.yml` 大量重复，仍使用 `ddl-auto:update`；`ddl.sql` 存在乱码/疑似引号断裂风险，尚无 Flyway。

## 2. 当前端到端调用链路

```text
JWT/Redis 鉴权
  ├─ 上传：UploadController
  │   → UploadService（Redis BitMap + MinIO chunk）
  │   → mergeChunks（MinIO merged object）
  │   → Kafka file-processing-topic1
  │   → FileProcessingConsumer
  │       → HTTP/本地路径下载
  │       → ParseService（Tika → 字符级子块 → MySQL document_vectors）
  │       → VectorizationService
  │           → EmbeddingClient（text-embedding-v4 / 2048）
  │           → ElasticsearchService（knowledge_base）
  └─ 问答：WebSocket/ChatHandler
      → HybridSearchService
          → EmbeddingClient
          → Elasticsearch 顶层 query + knn + rescore
          → FileUploadRepository 回填文件名
      → 固定 Top5、每片段截断 300 字符
      → DeepSeekClient
      → 以“2 秒无新 token”猜测流结束
      → Redis 会话历史
```

关键断点：

1. 上传成功、Kafka 投递、解析入库、向量索引之间没有明确处理状态和幂等键。
2. Kafka 消息携带预签名 URL，重试时可能已过期；消费者吞掉下载根因并返回 `null`。
3. MySQL 保存文本块，ES 保存向量，删除还涉及 MinIO，失败后可能残留或误删。
4. 解析丢失年报最重要的页、表、行列、单位、口径信息，后续无法生成可验证引用。
5. 检索权限、排序和生成置信度没有形成可测试的逐层契约。

## 3. 当前代码问题清单

| ID | 等级 | 现象与代码证据 | 影响 | 处理任务 |
|---|---|---|---|---|
| B-01 | P0 安全 | `HybridSearchService` 的 ACL 只在顶层 query，未放入 `knn.filter` | 顶层 query 与 KNN 组合语义可能让无权限向量命中 | S0-02 |
| B-02 | P0 安全 | mapping 为 `isPublic`，查询使用 `public`；boolean JavaBean 序列化名也未锁定 | 公共权限过滤失效或行为随序列化配置变化 | S0-02 |
| B-03 | P0 安全 | `SearchController` 匿名分支调用无权限 `search()`，其注释与行为不一致 | 匿名用户可能检索非公开内容 | S0-02 |
| B-04 | P0 质量 | 24 个测试仅 6 个通过；测试依赖本机服务且 mock 漏注入 | 无法安全重构、CI 不可用 | S0-01 |
| B-05 | P0 数据 | `ddl-auto:update`、手写乱码 DDL、无迁移版本 | 环境漂移且无法回滚 | S0-03 |
| B-06 | P0 一致性 | 上传/解析/索引/删除跨四存储，无状态机、幂等及补偿 | 重试重复写、半完成、孤儿数据 | S0-04 |
| B-07 | P0 表述 | 代码是 Kafka；路线/旧简历若写 RocketMQ 会与代码冲突 | 面试可信度风险 | S0-05 |
| B-08 | P0 指标 | 性能测试是 3 ms 假设，不触达 Redis/MinIO | 所有上传优化数字无证据 | S0-05 |
| B-09 | P1 解析 | Tika `BodyContentHandler` 只产生纯文本，父块不持久化 | 无页码、表格、图片、标题、父子关系 | S1-02、S3-01 |
| B-10 | P1 切块 | 512 是字符而非 token，无 overlap；`System.gc()` 控制内存 | 中文/表格语义破坏，吞吐不可控 | S1-03 |
| B-11 | P1 索引 | `VectorizationService` 写死 `deepseek-embed`，真实模型是 `text-embedding-v4` | 模型版本不可追溯，增量重建错误 | S1-04 |
| B-12 | P1 检索 | 并非独立双路召回，不能分别量化 BM25 与向量效果 | 参数无依据，Bad Case 难归因 | S2-01、S2-02 |
| B-13 | P1 生成 | 固定 Top5/300 字符，无 token budget、阈值、拒答、引用校验 | 幻觉和证据截断 | S2-04 |
| B-14 | P1 流式 | 新建裸线程并以 2 秒无增长判断完成 | 并发泄漏、误完成、难取消 | S5-02 |
| B-15 | P1 场景 | 缺年报元数据、财务事实、计算与口径校验 | 仍是通用知识库，不构成场景优化 | S1-01、S3-02～S3-04 |
| B-16 | P1 评测 | 无 EvaluationCase/Run/Result/BadCase | 无法回答“提升多少、为何提升” | S1-05、S2-05 |
| B-17 | P2 生命周期 | ES 固定单索引，无别名、版本和差量索引 | 模型/切块升级需停机全量重建 | S4-01～S4-03 |
| B-18 | P2 模型 | DeepSeek/DashScope 客户端与业务强耦合 | 无多模型切换、路由、成本对照 | S5-01 |

## 4. 目标系统架构

```text
API / WebSocket
  → Security + DocumentAclPolicy（所有入口统一）
  → FinanceQueryOrchestrator
      ├─ QueryClassifier / QueryRewrite
      ├─ RetrievalPipeline
      │   ├─ BM25 Retriever ─┐
      │   ├─ Vector Retriever├─ RRF → Dedup → Reranker → Threshold
      │   └─ Fact Retriever ─┘
      ├─ FinancialCalculator（可解释、确定性）
      ├─ EvidenceAssembler（token budget + parent/table 回填）
      ├─ LlmProvider
      └─ CitationVerifier / AnswerStatus

Kafka Processing Pipeline
  → Parse（PDF layout / OCR fallback）
  → Normalize（DocumentPage / Element / Table）
  → Chunk（token + structure + relation）
  → Extract（metadata / FinancialMetric / FinancialFact）
  → Embed（EmbeddingProvider）
  → Versioned Index（alias + ACL + model metadata）

MySQL：元数据、结构、事实、版本、评测
MinIO：原文件、页图、表格 JSON/Markdown、解析 manifest
Elasticsearch：可重建文本/向量索引
Redis：上传 bitmap、令牌、短期缓存、限流
Metrics/Trace：Micrometer + Actuator + OpenTelemetry（可选导出）
```

架构边界：

- MVP 不引入微服务拆分；保持 Spring Boot 单体，按 `domain/application/infrastructure/interfaces` 包分层。
- OCR、解析、Rerank、模型调用均通过 SPI 隔离，可先实现单一 Provider，再做多模型。
- GraphRAG 与 Agentic RAG 只做可开关实验，不进入默认链路，除非评测证明在多跳问题上收益显著。

## 5. 领域模型与数据存储设计

### 5.1 核心模型

| 模型 | 核心字段 | 存储与索引 | 关系/约束 |
|---|---|---|---|
| `DocumentVersion` | id, documentId, fileMd5, versionNo, parserVersion, chunkerVersion, embeddingModel, status, createdAt | MySQL | 同 documentId/versionNo 唯一；状态机驱动索引 |
| `DocumentPage` | id, versionId, pageNo, width, height, imageObjectKey, ocrUsed | MySQL + MinIO | versionId/pageNo 唯一 |
| `DocumentElement` | id, pageId, type, text, bbox, orderNo, headingLevel, tableId, confidence | MySQL；大 JSON 可在 MinIO | type=`TITLE/PARAGRAPH/TABLE/CELL/IMAGE/CAPTION/HEADER/FOOTER` |
| `DocumentChunk` | id, versionId, chunkNo, chunkType, content, tokenCount, contentHash, pageStart/pageEnd, parentChunkId, aclSnapshot | MySQL + ES | hash 支撑增量；父子关系真实可追溯 |
| `ChunkRelation` | sourceChunkId, targetChunkId, relationType, weight | MySQL | `PARENT/CHILD/PREV/NEXT/SAME_TABLE/CAPTION_OF` |
| `FinancialReportMetadata` | companyName, stockCode, reportType, fiscalYear, period, scope, currency, auditOpinion | MySQL | versionId 唯一；明确合并/母公司口径 |
| `FinancialMetric` | metricCode, canonicalName, aliases, statementType, formula, unitType | MySQL 字典 | 指标编码稳定，不随报告变化 |
| `FinancialFact` | versionId, metricCode, period, scope, value, unit, currency, scale, table/row/column, pageNo, bbox, evidenceText, confidence | MySQL + ES 结构字段 | `(version, metric, period, scope, source cell)` 幂等 |
| `Evidence` | id, queryId, sourceType, chunkId/factId, pageNo, bbox, quote, retrievalScore, rerankScore | MySQL（可按需留存） | 生成前冻结证据快照 |
| `Citation` | answerId, marker, evidenceId, claimText, verificationStatus | MySQL | marker 与回答引用一一校验 |
| `EvaluationCase` | id, dataset, category, question, expectedAnswer, goldEvidence, aclPersona, tags | MySQL/JSONL | 数据版本化 |
| `EvaluationRun` | id, gitSha, configSnapshot, datasetVersion, startedAt, status | MySQL | 关联完整配置和模型版本 |
| `EvaluationResult` | runId, caseId, retrievedIds, answer, metrics, latency, token/cost, error | MySQL/JSON | 可复算、可对比 |
| `BadCase` | resultId, stage, reasonCode, severity, diagnosis, fixVersion, status | MySQL | stage=`PARSE/RETRIEVE/RERANK/GENERATE/CITATION/ACL` |

### 5.2 存储决策

- MySQL 新表全部由 Flyway 管理；生产 profile 设置 `ddl-auto=validate`。
- ES 使用版本化物理索引 `knowledge_base_v{schema}` 和读写别名；文档主键使用稳定 `versionId:chunkId:modelId`，不再随机 UUID。
- ES 字段统一使用 `isPublic`，ACL 同时包含 `ownerId/orgTags/classification/tenantId`；所有 Retriever 接收不可为空的 `AccessScope`。
- MinIO object key 使用 ID 而非文件名：`documents/{documentId}/{versionId}/...`，防止同名覆盖。
- 原 `document_vectors` 在迁移期只读兼容，完成回填和校验后再删除；向量本体只保留在 ES，MySQL 保存 embedding 状态与模型元数据。

## 6. 分阶段开发路线

| 阶段 | 周期 | 结果 | 边界 |
|---|---:|---|---|
| 阶段 0：可信基线 | W1 | 测试可复现、ACL 无泄漏、迁移可控、一致性有状态 | 后续全部工作的硬前置 |
| 阶段 1：金融年报最小闭环 | W2 | 2 家公司×2 年、50 题可上传、解析、检索、回答并定位页码 | MVP |
| 阶段 2：可评测混合检索 | W3～W4 | 独立召回、RRF、Rerank、拒答、引用和消融报告 | MVP 完成 |
| 阶段 3：表格与确定性计算 | W5～W6 | 表格结构、财务事实、跨期/比率计算、数字核验 | 完整场景亮点 |
| 阶段 4：增量索引与生命周期 | W7 | 版本、差量更新、别名切换、补偿与清理 | 完整工程亮点 |
| 阶段 5：多模型与在线工程 | W8 | Provider、路由、限流/熔断、成本与可观测 CI | 完整版本 |
| 阶段 6：研究项 | W9+ 可选 | GraphRAG、HyDE/Query Decomposition 对照实验 | 不阻塞秋招主线 |

MVP = 阶段 0～2；完整版本 = 阶段 0～5；研究版本 = 阶段 6。若时间不足，按此边界停止，绝不牺牲 ACL、评测和可复现性去堆功能。

## 7. 详细任务拆解

### 7.1 阶段 0：可信、安全、可测试基线

#### S0-01 修复测试分层与可复现环境

- **状态/阶段/优先级/工作量：** TODO / 0 / P0 / M。
- **目标：** `mvn test` 不依赖开发者本机服务；单元、集成、外部 API 契约测试边界清晰。
- **代码依据：** 当前 24 个测试仅 6 个通过；`ParseServiceTest`、`UploadServicePerformanceTest` 错误加载全上下文；两个单测漏 mock。
- **依赖/并行：** 无；可与 S0-03、S0-05 并行。
- **现有文件：** `pom.xml`、`src/test/**`、`EsIndexInitializer.java`。
- **新增文件：** `src/test/resources/application-test.yml`、`support/ExternalServiceTestConfiguration.java`、后续 `*IT.java`。
- **数据库/索引变更：** 引入 Testcontainers MySQL/Redis/Kafka/ES；测试时禁用自动 ES 初始化，集成测试显式启动。
- **实施步骤：** 补齐 mock；把纯逻辑测试改为 Mockito；按 `*Test/*IT` 分组；增加 Surefire/Failsafe；提供一条 Docker 可复现命令。
- **接口/类设计：** `@ConditionalOnProperty` 控制 `EsIndexInitializer`；外部客户端通过接口 mock。
- **测试与验收：** `mvn test` 全绿且无 Docker；`mvn verify -Pintegration` 在 Docker 环境全绿；失败日志不含真实密钥。
- **指标：** 单测 <30 s，集成测试目标 <5 min；记录当前/修复后数量。
- **风险/回滚：** Testcontainers 在国内拉镜像慢；可固定镜像并保留 compose profile。回滚仅移除 integration profile，不回退测试修复。
- **建议提交：** `test: isolate unit tests and add reproducible integration profile`。

#### S0-02 统一 ACL 并修复 KNN 权限泄漏

- **状态/阶段/优先级/工作量：** TODO / 0 / P0 / M。
- **目标：** 任意检索路径都不能返回 owner/public/org scope 之外的 chunk。
- **代码依据：** `HybridSearchService` 的 KNN 无 filter，查询 `public` 而 mapping 为 `isPublic`，匿名分支调用无 ACL `search()`。
- **依赖/并行：** S0-01；与 S0-04 可并行。
- **现有文件：** `HybridSearchService.java`、`SearchController.java`、`EsDocument.java`、`knowledge_base.json`。
- **新增文件：** `security/AccessScope.java`、`security/DocumentAclPolicy.java`、`retrieval/AclQueryBuilder.java`、`HybridSearchAclIT.java`。
- **数据库/索引变更：** 新 ES mapping 显式 `isPublic/ownerId/orgTags/tenantId/classification`；旧索引需回填或重建。
- **实施步骤：** 删除无权限公共方法；认证入口构造 `AccessScope`；BM25 与 KNN 各自应用同一 filter；锁定 Jackson 字段；设计公开/本人/同组织/父子组织/无权限矩阵。
- **接口/类设计：** `Retriever.retrieve(Query, AccessScope, k)` 强制 scope；scope 为空 fail-closed。
- **测试与验收：** 至少 20 组权限矩阵；BM25、KNN、fallback、缓存、引用查询分别断言零越权；ES mapping contract test 通过。
- **指标：** ACL 泄漏数必须为 0；权限过滤新增 P95 延迟需记录，目标增幅 <10%。
- **风险/回滚：** mapping 不兼容；用新索引+别名切换，失败切回旧索引但关闭问答入口。
- **建议提交：** `fix(security): enforce identical ACL filters for all retrievers`。

#### S0-03 引入 Flyway 并收敛配置

- **状态/阶段/优先级/工作量：** TODO / 0 / P0 / M。
- **目标：** 数据结构和环境配置可版本化、校验、回滚。
- **代码依据：** `ddl-auto:update`、三份重复 YAML、乱码 DDL。
- **依赖/并行：** 无；可与 S0-01 并行。
- **现有文件：** `pom.xml`、`application*.yml`、`docs/databases/ddl.sql`。
- **新增文件：** `db/migration/V1__baseline.sql`、`V2__processing_state.sql`、`docs/adr/ADR-001-schema-migration.md`。
- **数据库/索引变更：** 以实体和实际库反向校对 V1；dev/test 可 migrate，prod `ddl-auto=validate`。
- **实施步骤：** 修复编码；抽公共配置与 profile 差异；校验必需 env；建立 migrate/validate/repair 操作说明。
- **接口/类设计：** `@ConfigurationProperties` 取代散落 `@Value`，配置启动时校验。
- **测试与验收：** 空库可一键升级；已有基线库可 baseline 后升级；重复执行幂等；schema validation 通过。
- **指标：** 新环境从启动到 schema ready <2 min（不含镜像下载）。
- **风险/回滚：** 已有库结构漂移；先导出 schema 和备份，迁移只 forward，数据回滚使用备份。
- **建议提交：** `build(db): introduce flyway and validated environment profiles`。

#### S0-04 建立处理状态机、幂等和跨存储补偿

- **状态/阶段/优先级/工作量：** TODO / 0 / P0 / L。
- **目标：** Kafka 重试不重复解析/索引，失败可诊断、可恢复。
- **代码依据：** `FileProcessingConsumer` 顺序执行且无状态；下载异常返回 null；删除跨 ES/MinIO/MySQL 后继续执行。
- **依赖/并行：** S0-03；可与 S0-02 并行。
- **现有文件：** `FileProcessingConsumer.java`、`UploadController.java`、`DocumentService.java`。
- **新增文件：** `ProcessingJob.java`、`ProcessingStage.java`、`ProcessingJobRepository.java`、`ProcessingOrchestrator.java`、`ReconciliationJob.java`。
- **数据库/索引变更：** `processing_job`、outbox（推荐）及唯一键 `(versionId, stage, pipelineVersion)`。
- **实施步骤：** 消息只传 object key/版本 ID；阶段 CAS；异常保留根因；D​​LT 可人工重放；删除改软删+异步清理；定时扫描孤儿。
- **接口/类设计：** `StageHandler.execute(JobContext)`；状态 `PENDING/RUNNING/SUCCEEDED/FAILED/RETRYABLE/CANCELLED`。
- **测试与验收：** 重复消费 5 次仅生成一份产物；任一阶段故障可从断点恢复；删除中断后最终收敛；过期 URL 问题消失。
- **指标：** 重复 chunk/fact/index 文档数 0；失败任务可定位率 100%。
- **风险/回滚：** 状态机扩大改动；先双写状态不改变旧链路，再切换 orchestrator。
- **建议提交：** `feat(pipeline): add idempotent processing state and reconciliation`。

#### S0-05 校正文档表述并建立真实性能基线

- **状态/阶段/优先级/工作量：** TODO / 0 / P0 / S。
- **目标：** Kafka/模型/性能描述与代码及实测一致。
- **代码依据：** 实际 Kafka 3.2.1；性能测试仅做 3 ms 算术模拟；模型版本写错。
- **依赖/并行：** S0-01；可与 S0-03 并行。
- **现有文件：** `README.md`、路线图、`UploadServicePerformanceTest.java`、`VectorizationService.java`。
- **新增文件：** `docs/benchmarks/baseline.md`、`scripts/benchmark/README.md`、可复现 k6/JMeter 脚本。
- **数据库/索引变更：** ES `embeddingModel/embeddingDimension/pipelineVersion` 正确写入。
- **实施步骤：** 删除伪性能测试；固定硬件、文件、并发、冷/热缓存口径；分别测上传、合并、解析、embedding、索引；统一 Kafka 表述。
- **接口/类设计：** `EmbeddingResponse` 返回 model/dimension/usage，不再写死字符串。
- **测试与验收：** 10 MB/100 MB、1/4 并发至少各 5 次，报告 P50/P95/错误率；README 只引用实测。
- **指标：** 本任务只建基线，不预设提升；后续优化用同脚本对比。
- **风险/回滚：** API 成本/网络波动；分离本地假 Provider 与真实 Provider 报告。
- **建议提交：** `docs(perf): replace simulated claims with reproducible baseline`。

### 7.2 阶段 1：金融年报最小闭环

#### S1-01 建立文档版本与金融元数据骨架

- **状态/阶段/优先级/工作量：** TODO / 1 / P0 / M。
- **目标：** 一份年报可按公司、年份、报告类型、口径和版本唯一识别。
- **代码依据：** 当前只有 `FileUpload(fileMd5,fileName)`，无法表示同一报告版本或财务语义。
- **依赖/并行：** S0-03、S0-04；可与 S1-05 并行。
- **现有文件：** `FileUpload.java`、`FileProcessingTask.java`、repository。
- **新增文件：** `Document.java`、`DocumentVersion.java`、`FinancialReportMetadata.java` 及 repository/service/DTO。
- **数据库/索引变更：** 新表 `document/document_version/financial_report_metadata`；FileUpload 关联 versionId。
- **实施步骤：** 设计自然键与状态；从文件名/封面抽取候选元数据；低置信度进入人工确认；限定支持 PDF 年报。
- **接口/类设计：** `ReportMetadataExtractor`、`ReportMetadataReviewService`；scope 枚举 `CONSOLIDATED/PARENT_COMPANY`。
- **测试与验收：** 2 家公司×2 年报告全部正确识别；错年份/重复上传/修订版有明确行为。
- **指标：** 元数据字段准确率目标 ≥95%，不确定字段必须标记而非猜测。
- **风险/回滚：** 文件命名不规范；封面抽取失败时允许人工传参。旧 FileUpload 查询保持兼容。
- **建议提交：** `feat(finance): add versioned report metadata model`。

#### S1-02 抽象解析 SPI 并保留页级元素

- **状态/阶段/优先级/工作量：** TODO / 1 / P0 / L。
- **目标：** 解析结果从“字符串列表”升级为带页码、类型、顺序、bbox 的元素流。
- **代码依据：** `ParseService` 的 Tika handler 丢失所有结构。
- **依赖/并行：** S1-01；可与 S1-05 并行。
- **现有文件：** `ParseService.java`、`FileProcessingConsumer.java`。
- **新增文件：** `DocumentParser`、`PdfLayoutParser`、`TikaTextFallbackParser`、`ParseManifest`、`DocumentPage/DocumentElement`。
- **数据库/索引变更：** `document_page/document_element`；页图和 manifest 写 MinIO。
- **实施步骤：** 先接 PDF 文本层与坐标；扫描页按阈值启用 OCR；过滤页眉页脚但保留原元素；产物带 parserVersion。
- **接口/类设计：** `supports(MediaType)`、`parse(ParseRequest): ParseResult`；结果不可直接写 ES。
- **测试与验收：** 对数字 PDF、扫描 PDF、混合 PDF golden files 做 snapshot；页数、阅读顺序、bbox 可复现。
- **指标：** 页码准确率 100%；正文字符召回目标 ≥98%；OCR 页单独记录准确率/耗时。
- **风险/回滚：** Java 布局库能力不足；允许独立 Python 解析服务，但 SPI/数据契约不变；Tika 始终为兜底。
- **建议提交：** `feat(parser): introduce page-aware document parser SPI`。

#### S1-03 实现 token/结构感知切块与真实父子关系

- **状态/阶段/优先级/工作量：** TODO / 1 / P0 / M。
- **目标：** 标题、段落、页和表格边界不被随意切断，并可父块回填。
- **代码依据：** 当前 512 字符、无 overlap、父块未持久化。
- **依赖/并行：** S1-02。
- **现有文件：** `ParseService.java`、`DocumentVector.java`。
- **新增文件：** `DocumentChunk.java`、`ChunkRelation.java`、`Chunker`、`TokenCounter`、`StructureAwareChunker.java`。
- **数据库/索引变更：** `document_chunk/chunk_relation`；contentHash、tokenCount、pageRange、elementIds。
- **实施步骤：** 以 heading/paragraph/table 为原子；正文目标 350～600 tokens、适度 overlap；表格独立；建立 PARENT/CHILD/PREV/NEXT。
- **接口/类设计：** `ChunkingPolicy` 配置版本化；字符计数只作为 tokenizer 不可用时的降级。
- **测试与验收：** 重建文本不丢失；chunk 不越文档；父子引用真实存在；中文/英文/表格边界测试。
- **指标：** 不预设最佳 chunk；输出长度分布和边界破坏率，交由 S2-05 选型。
- **风险/回滚：** tokenizer 与模型不一致；Provider 暴露 tokenizerId；保留旧 chunker 作为实验组。
- **建议提交：** `feat(chunking): add versioned structure-aware parent-child chunks`。

#### S1-04 重构索引文档与可追溯 embedding 元数据

- **状态/阶段/优先级/工作量：** TODO / 1 / P0 / M。
- **目标：** 每条 ES 文档能追溯版本、页、结构、ACL、切块和 embedding 模型。
- **代码依据：** 现 `EsDocument` 字段少、随机 UUID、模型写死 `deepseek-embed`。
- **依赖/并行：** S0-02、S1-03。
- **现有文件：** `VectorizationService.java`、`EsDocument.java`、mapping。
- **新增文件：** `IndexDocument.java`、`IndexDocumentMapper.java`、`EmbeddingMetadata.java`、mapping v2。
- **数据库/索引变更：** 新物理索引；稳定 ID；增加 version/page/type/parent/metric/ACL/pipeline 字段。
- **实施步骤：** 批量 embedding 前校验维度；响应数必须等于输入数；记录模型；bulk 逐项检查错误；成功后更新状态。
- **接口/类设计：** `IndexWriter.upsert/deleteByVersion`，业务层不直接依赖 ES Client。
- **测试与验收：** 固定 chunk 重试不增加文档数；模型/维度不匹配 fail-fast；bulk 部分失败可恢复。
- **指标：** 索引完整率 100%；重复 ID 0；记录 chunks/s 和 embedding tokens/s。
- **风险/回滚：** 新 mapping 需重建；使用独立索引和别名灰度。
- **建议提交：** `refactor(index): make chunks and embedding metadata traceable`。

#### S1-05 建立 50 题金融 Golden Set 与评测骨架

- **状态/阶段/优先级/工作量：** TODO / 1 / P0 / M。
- **目标：** 在优化前先固定问题、标准答案、证据页和权限身份。
- **代码依据：** 当前无评测数据/运行模型。
- **依赖/并行：** S1-01；可与 S1-02 并行。
- **现有文件：** 路线图中的意图与数据建议。
- **新增文件：** `eval/datasets/finance-mvp-v1.jsonl`、Evaluation 四类模型、`EvaluationRunner`、标注规范。
- **数据库/索引变更：** `evaluation_case/run/result/bad_case`；大结果可存 JSON。
- **实施步骤：** 选 2 公司×2 年；覆盖事实、期间比较、比率、政策、风险、证据不足；双人/二次复核数字和页码；记录数据许可。
- **接口/类设计：** case 必含 `question/expected/goldEvidence/category/aclPersona`；run 冻结 gitSha/config。
- **测试与验收：** 50 题无空证据；同一评测可重复运行；至少包含 10 个应拒答和 5 个 ACL 用例。
- **指标：** 标注复核一致率目标 ≥95%；不把 LLM judge 作为唯一裁判。
- **风险/回滚：** 年报版权和答案歧义；只保存公开报告引用/页码，争议题标记 exclude。
- **建议提交：** `feat(eval): add versioned finance golden set and run model`。

### 7.3 阶段 2：可评测混合检索、排序与证据生成

#### S2-01 拆分 BM25、Vector、Fact Retriever

- **状态/阶段/优先级/工作量：** TODO / 2 / P0 / M。
- **目标：** 每路召回独立、同 ACL、可计时和单独评测。
- **代码依据：** 当前 `HybridSearchService` 把 query/knn/rescore 混在一次 ES 请求中。
- **依赖/并行：** S0-02、S1-04；与 S2-05 框架可并行。
- **现有文件：** `HybridSearchService.java`。
- **新增文件：** `Retriever`、`Bm25Retriever`、`VectorRetriever`、`FinancialFactRetriever`、`RetrievalCandidate`。
- **数据库/索引变更：** ES 为 metricCode/company/year/scope 等加 keyword 字段。
- **实施步骤：** 各路返回 rank/rawScore/source；统一 ACL；embedding 失败只降级 Vector 路并显式记录。
- **接口/类设计：** `RetrievalContext(query, filters, accessScope, topK, traceId)`。
- **测试与验收：** 三路可单独开关；权限矩阵复用；外部 embedding 失败仍有 BM25 且 response 标注 degraded。
- **指标：** 分别输出 Recall@5/10/20、MRR、NDCG、P50/P95。
- **风险/回滚：** 多请求增加延迟；先并行执行并设置独立 timeout，保留旧链路 feature flag。
- **建议提交：** `refactor(retrieval): split lexical vector and fact retrievers`。

#### S2-02 实现 RRF、去重和元数据过滤

- **状态/阶段/优先级/工作量：** TODO / 2 / P0 / S。
- **目标：** 以 rank 融合异构分数，避免同一证据重复占位。
- **代码依据：** 当前用固定 queryWeight/rescoreWeight，跨路分数不可比。
- **依赖/并行：** S2-01。
- **现有文件：** 新 RetrievalCandidate。
- **新增文件：** `FusionStrategy`、`RrfFusionStrategy`、`CandidateDeduplicator`、`QueryFilterExtractor`。
- **数据库/索引变更：** 无新增表；使用现有公司/年份/报告/口径字段。
- **实施步骤：** RRF k 可配置；按 chunkId/contentHash/fact source 去重；显式问题抽公司、年份和 scope，歧义不强行过滤。
- **接口/类设计：** `FusionResult` 保留每路 rank 与贡献，便于解释和评测。
- **测试与验收：** 单路缺失仍工作；顺序确定；重复证据只留一份；融合可解释。
- **指标：** 相比最佳单路 Recall@10/NDCG@10 的增益由 Golden Set 实测，目标仅作为门槛建议：Recall@10 不下降。
- **风险/回滚：** 过滤器误杀；低置信度 filter 改 boost，feature flag 切回 concat。
- **建议提交：** `feat(retrieval): add explainable RRF fusion and deduplication`。

#### S2-03 增加 Reranker 与降级策略

- **状态/阶段/优先级/工作量：** TODO / 2 / P1 / M。
- **目标：** 对融合候选精排，并可证明收益大于延迟/成本。
- **代码依据：** 当前 BM25 rescore 不是语义 cross-encoder rerank。
- **依赖/并行：** S2-02；Provider 接口可为 S5-01 的先行最小版。
- **现有文件：** 无可复用 reranker。
- **新增文件：** `Reranker`、`NoopReranker`、`ApiReranker`、`RerankResult`。
- **数据库/索引变更：** EvaluationResult 保存 rerank score/model/latency。
- **实施步骤：** Top50 融合→Top10 精排；批量、timeout、熔断；失败使用 RRF 顺序；记录模型版本。
- **接口/类设计：** `rerank(query,candidates,topN,context)`，不得绕过 ACL。
- **测试与验收：** 超时/429/空响应稳定降级；分数和模型可追溯；结果仍是授权子集。
- **指标：** NDCG@10/MRR 改善与 P95 增量同时报告；若收益不显著则默认关闭。
- **风险/回滚：** 外部服务成本与波动；Noop/feature flag 即时回滚。
- **建议提交：** `feat(rerank): add pluggable reranking with safe fallback`。

#### S2-04 重构上下文、拒答、证据和引用协议

- **状态/阶段/优先级/工作量：** TODO / 2 / P0 / L。
- **目标：** 回答只基于冻结证据，证据不足时确定性拒答，每个引用可定位。
- **代码依据：** 当前固定 Top5/300 字、仅靠 Prompt 声明规则。
- **依赖/并行：** S2-03、S1-03。
- **现有文件：** `ChatHandler.java`、`DeepSeekClient.java`、`AiProperties.java`。
- **新增文件：** `EvidenceAssembler`、`TokenBudgetPolicy`、`AnswerStatus`、`CitationVerifier`、`RagResponse`。
- **数据库/索引变更：** Evidence/Citation 最小表；返回 pageNo/bbox/chunkId/factId/versionId。
- **实施步骤：** 基于 token 分配；父块/相邻块按需回填；阈值先用开发集标定；生成 JSON schema；后验检查 citation marker、quote、数字；输出五种证据状态。
- **接口/类设计：** `VERIFIED/SUPPORTED/PARTIAL/INSUFFICIENT_EVIDENCE/CONFLICTING_EVIDENCE`；拒答由服务规则决定，不交给模型自由发挥。
- **测试与验收：** 无证据、冲突、越权、伪引用、超长表格、prompt injection 用例全过；所有引用能打开原页。
- **指标：** Citation Precision/Recall、Faithfulness、拒答 precision/recall、token 与延迟。
- **风险/回滚：** 阈值过高导致过度拒答；配置化阈值，按类别校准，不允许关闭引用校验来“提升回答率”。
- **建议提交：** `feat(rag): enforce evidence budget refusal and verified citations`。

#### S2-05 完成检索/生成评测、消融与 Bad Case 闭环

- **状态/阶段/优先级/工作量：** TODO / 2 / P0 / M。
- **目标：** 回答 TopK、chunk、RRF、rerank、阈值选择为何有效。
- **代码依据：** S1-05 只有骨架，当前项目无可对比报告。
- **依赖/并行：** S1-05、S2-01；实现中持续接入 S2-02～S2-04。
- **现有文件：** Golden Set 与路线图指标。
- **新增文件：** `eval/report/`、`MetricCalculator`、`ExperimentMatrix`、Markdown/CSV 报告生成器。
- **数据库/索引变更：** EvaluationResult metrics JSON；BadCase reason 枚举。
- **实施步骤：** baseline→单路→RRF→rerank→上下文/阈值逐项消融；固定随机性；按意图切片；Bad Case 分到解析/召回/排序/生成/引用/权限。
- **接口/类设计：** metric 插件至少含 Recall@K、MRR、NDCG、EM/F1、数值容差、citation、faithfulness、latency/cost。
- **测试与验收：** 一条命令复现报告；run 含 gitSha/config/dataset/model；两个 run 可自动 diff。
- **指标：** 不提前承诺提升值；MVP 门禁：ACL 0 泄漏、关键财务事实 Recall@10 达到项目设定阈值、引用正确率与拒答效果均有报告。
- **风险/回滚：** LLM judge 漂移；规则/人工指标为主，judge 固定模型/Prompt 并抽检。
- **建议提交：** `feat(eval): add reproducible ablation and bad-case workflow`。

### 7.4 阶段 3：表格、财务事实与确定性计算

#### S3-01 解析表格、图片与跨页结构

- **状态/阶段/优先级/工作量：** TODO / 3 / P0 / L。
- **目标：** 保留年报表格标题、单位、行列、合并单元格、跨页关系和源 bbox。
- **代码依据：** 当前 Tika 纯文本无法支持财务数字核验。
- **依赖/并行：** S1-02；可与 S3-03 规则骨架并行。
- **现有文件：** DocumentElement/Page。
- **新增文件：** `TableModel`、`TableCell`、`TableNormalizer`、`CrossPageTableMerger`、OCR Provider。
- **数据库/索引变更：** table/cell 表或 element JSON；MinIO 存 table JSON/Markdown/page crop。
- **实施步骤：** 数字 PDF 结构提取；扫描表 OCR；重复表头和跨页合并；标题/单位邻域绑定；低置信度保留原图供复核。
- **接口/类设计：** cell 含 row/col/span/text/bbox/confidence；table 含 page range/unit/title。
- **测试与验收：** 至少 30 张表 golden snapshot；资产负债表、利润表、现金流量表覆盖；任一 cell 可回源。
- **指标：** 表检测 F1、cell text exact/numeric accuracy、row/column structure accuracy、跨页合并准确率。
- **风险/回滚：** OCR/布局模型复杂；优先数字 PDF 主路径，扫描件明确标 beta；原始元素不覆盖。
- **建议提交：** `feat(parser): preserve financial tables images and cross-page layout`。

#### S3-02 指标标准化并抽取 FinancialFact

- **状态/阶段/优先级/工作量：** TODO / 3 / P0 / L。
- **目标：** 将表格数字映射为统一指标、期间、单位、币种和合并口径。
- **代码依据：** 当前无财务领域模型。
- **依赖/并行：** S1-01、S3-01。
- **现有文件：** FinancialReportMetadata、DocumentElement。
- **新增文件：** `FinancialMetric`、`FinancialFact`、`MetricDictionary`、`FactExtractor`、`UnitNormalizer`、review API。
- **数据库/索引变更：** `financial_metric/financial_metric_alias/financial_fact`；数值用 Decimal，禁止 double。
- **实施步骤：** 先覆盖 20～30 个高频指标；结合表名/行名/列名识别；单位换算保留 rawValue/rawUnit；区分期末/本期/上期和合并/母公司。
- **接口/类设计：** 抽取结果含 evidence、confidence、extractorVersion；低置信度待复核。
- **测试与验收：** 选定报告的关键三表事实双重核验；重复抽取幂等；负数、括号、破折号、万元/百万元正确。
- **指标：** 数值准确率、指标映射 F1、单位/期间/scope 准确率分别报告，目标关键指标数值准确率 ≥98%。
- **风险/回滚：** 别名和列头多样；字典版本化、允许人工修正，修正不改原始证据。
- **建议提交：** `feat(finance): normalize metrics and extract traceable facts`。

#### S3-03 实现确定性财务计算引擎

- **状态/阶段/优先级/工作量：** TODO / 3 / P0 / M。
- **目标：** 增长率、利润率、资产负债率等由代码计算，不让 LLM 心算。
- **代码依据：** 路线图要求数字防幻觉，当前只有生成模型。
- **依赖/并行：** S3-02；计算 DSL 可先与 S3-01 并行设计。
- **现有文件：** FinancialMetric/Fact。
- **新增文件：** `FinancialCalculator`、`FormulaRegistry`、`CalculationTrace`、`DecimalPolicy`。
- **数据库/索引变更：** 可选 `calculation_result` 缓存；公式版本随结果记录。
- **实施步骤：** 定义公式输入、期间、scope、单位；BigDecimal 精度与舍入；缺值/除零/冲突不计算；输出公式、代入值、结果和证据。
- **接口/类设计：** `calculate(metricCode, dimensions): CalculationResult`；状态 `CALCULATED/NOT_APPLICABLE/INSUFFICIENT/CONFLICT`。
- **测试与验收：** 属性测试+手工样例；跨单位、负值、零分母、不同 scope 全覆盖；LLM 只能解释结果。
- **指标：** 对标人工答案数值准确率目标 100%（容差按指标定义）；所有结果 trace 完整率 100%。
- **风险/回滚：** 公式定义争议；公式版本化并显示口径，不自动覆盖旧结果。
- **建议提交：** `feat(finance): add deterministic decimal calculation engine`。

#### S3-04 构建数字一致性和引用核验器

- **状态/阶段/优先级/工作量：** TODO / 3 / P0 / M。
- **目标：** 生成答案中的公司、年份、金额、单位、比例和引用与事实/计算结果一致。
- **代码依据：** S2-04 只有通用 CitationVerifier。
- **依赖/并行：** S2-04、S3-03。
- **现有文件：** Evidence、Citation、CalculationResult。
- **新增文件：** `FinancialAnswerVerifier`、`NumericClaimExtractor`、`VerificationIssue`。
- **数据库/索引变更：** Citation 增 verificationReason；EvaluationResult 保存 issue。
- **实施步骤：** 从结构化回答取 claim；标准化数值单位；对照 fact/calculation；检查 period/scope；失败降级 PARTIAL/CONFLICT 或拒答。
- **接口/类设计：** `verify(DraftAnswer,EvidenceBundle): VerificationReport`，高风险错误阻断发送。
- **测试与验收：** 故意改数字、年份、单位、scope、citation 的变异测试全部捕获。
- **指标：** 数字错误检出 recall/precision、引用正确率；高风险假数字漏检目标 0（限测试集）。
- **风险/回滚：** 自然语言 claim 难抽取；生成阶段强制结构化 JSON，无法解析则不输出 VERIFIED。
- **建议提交：** `feat(finance): verify numeric claims scopes and citations`。

#### S3-05 扩展金融专项评测至 300 题

- **状态/阶段/优先级/工作量：** TODO / 3 / P1 / M。
- **目标：** 覆盖 6～10 家公司、3 年、结构化/非结构化/拒答/冲突问题。
- **代码依据：** MVP 50 题不足以支持稳健结论。
- **依赖/并行：** S3-02～S3-04；可持续滚动。
- **现有文件：** finance-mvp-v1、评测框架。
- **新增文件：** `finance-v2.jsonl`、标注审计日志、数据卡。
- **数据库/索引变更：** datasetVersion 与 case lineage。
- **实施步骤：** 分层抽样；纳入 FinanceBench/FinQA/TAT-QA 可合法使用的样例或映射；至少 20% 拒答/冲突；留出 test 集不调参。
- **接口/类设计：** 数据卡记录来源、许可、适用范围和泄漏风险。
- **测试与验收：** 300 题完成复核；train/dev/test 隔离；所有图表可由 run 自动生成。
- **指标：** 分类别 EM/F1/数值准确率/citation/refusal，附 95% bootstrap CI。
- **风险/回滚：** 标注耗时最大；先保证 100 题高质量，再扩容，低质题不纳入门禁。
- **建议提交：** `data(eval): expand audited finance benchmark to v2`。

### 7.5 阶段 4：增量索引与知识生命周期

#### S4-01 实现版本 diff、chunk hash 与增量计划

- **状态/阶段/优先级/工作量：** TODO / 4 / P1 / M。
- **目标：** 同一报告修订或 pipeline 升级时只处理变化内容。
- **代码依据：** 当前以 fileMd5 全量解析且随机 ES ID。
- **依赖/并行：** S1-03、S1-04。
- **现有文件：** DocumentVersion/Chunk。
- **新增文件：** `VersionDiffService`、`IndexingPlan`、`ChunkFingerprint`。
- **数据库/索引变更：** chunk contentHash、sourceElementHash、pipelineVersion；version parentId。
- **实施步骤：** 区分文件变更、解析器变更、chunker 变更、embedding 变更；生成 ADD/UPDATE/DELETE/REUSE 计划。
- **接口/类设计：** `plan(oldVersion,newVersion,targetPipeline): IndexingPlan`。
- **测试与验收：** 未变 chunk 不调用 embedding；删除/移动/轻微修改正确识别；计划可重放。
- **指标：** embedding 节省比例、更新耗时、误复用率；正确性优先于节省率。
- **风险/回滚：** hash 复用错位；hash 纳入关键上下文和版本，必要时强制 full rebuild。
- **建议提交：** `feat(index): plan hash-based incremental reprocessing`。

#### S4-02 建立版本化索引、别名切换和回填

- **状态/阶段/优先级/工作量：** TODO / 4 / P1 / M。
- **目标：** mapping/模型升级不停机，可灰度验证和秒级回滚。
- **代码依据：** `EsIndexInitializer` 固定 `knowledge_base` 且启动失败阻断上下文。
- **依赖/并行：** S4-01。
- **现有文件：** `EsIndexInitializer.java`、mapping、ElasticsearchService。
- **新增文件：** `IndexLifecycleService`、`ReindexCommand`、`IndexManifest`。
- **数据库/索引变更：** `knowledge_read/knowledge_write` alias；manifest 保存 schema/model/checksum。
- **实施步骤：** 创建新索引→回填→计数/抽样/ACL/评测校验→原子切别名→保留上一版。
- **接口/类设计：** 初始化不再因 ES 暂时不可用阻断全部单测；生产健康状态显示 DEGRADED。
- **测试与验收：** 切换期间查询无错误；失败自动不切换；30 分钟内可回退上一索引。
- **指标：** 回填吞吐、切换错误率 0、源/目标计数和抽样 checksum 一致。
- **风险/回滚：** 双索引占磁盘；容量检查为前置，回滚即切回 alias。
- **建议提交：** `feat(es): add validated blue-green indices and aliases`。

#### S4-03 完善删除、保留策略和一致性巡检

- **状态/阶段/优先级/工作量：** TODO / 4 / P1 / M。
- **目标：** 软删、恢复、最终物理清理、孤儿检测形成闭环。
- **代码依据：** `DocumentService` 捕获外部删除错误后继续删 MySQL。
- **依赖/并行：** S0-04、S4-02。
- **现有文件：** `DocumentService.java`、ReconciliationJob。
- **新增文件：** `DocumentLifecycleService`、`RetentionPolicy`、一致性报告。
- **数据库/索引变更：** deletedAt/purgeAfter/tombstone；处理审计日志。
- **实施步骤：** API 先鉴权软删；检索立即排除；保留期内恢复；purge job 清 ES/MinIO/DB；巡检反向比对。
- **接口/类设计：** lifecycle command 幂等；每个资源记录删除结果。
- **测试与验收：** 删除后各检索路不可见；恢复成功；注入任意存储故障最终收敛；无越权恢复/删除。
- **指标：** orphan count、reconciliation lag、purge success rate。
- **风险/回滚：** 误删；默认 7 天保留，物理 purge 需要显式策略；恢复只在 purge 前。
- **建议提交：** `feat(lifecycle): add recoverable deletion and consistency sweeps`。

### 7.6 阶段 5：多模型与在线工程

#### S5-01 抽象 LLM/Embedding/Reranker Provider 与注册表

- **状态/阶段/优先级/工作量：** TODO / 5 / P1 / L。
- **目标：** 业务代码不感知 DeepSeek/DashScope 协议，可按能力和配置切换。
- **代码依据：** `DeepSeekClient`、`EmbeddingClient` 与业务强耦合。
- **依赖/并行：** S2-03、S2-04；可与 S5-03 并行。
- **现有文件：** 两个 client、WebClientConfig。
- **新增文件：** `LlmProvider`、`EmbeddingProvider`、`RerankProvider`、`ModelRegistry`、capability/usage DTO。
- **数据库/索引变更：** model invocation audit（不存敏感原文或先脱敏）；ES embeddingModel 可区分。
- **实施步骤：** 先把现有实现适配；再接第二个兼容 Provider/本地模型；启动时校验维度/上下文/流式能力。
- **接口/类设计：** Provider 返回 modelId/version/usage/finishReason；流式有显式 onComplete/onError/cancel。
- **测试与验收：** contract test 对所有 Provider 一致；切配置无需改业务代码；维度不同不能写入同一索引。
- **指标：** 各模型效果/延迟/token/成本四维报告，而非“支持 N 个模型”。
- **风险/回滚：** 过度抽象；只抽当前用到的共同能力，保留 vendorOptions；默认回到现有 Provider。
- **建议提交：** `refactor(ai): introduce capability-aware model provider registry`。

#### S5-02 路由、熔断、限流、缓存与可靠流式响应

- **状态/阶段/优先级/工作量：** TODO / 5 / P1 / L。
- **目标：** 外部模型失败可控，流式完成/取消有明确信号，不再依赖 sleep。
- **代码依据：** ChatHandler 裸线程和“2 秒无增长”；EmbeddingClient 只做固定重试。
- **依赖/并行：** S5-01。
- **现有文件：** `ChatHandler.java`、clients、Redis。
- **新增文件：** `ModelRouter`、`ResiliencePolicy`、`StreamSessionManager`、`CostQuotaService`。
- **数据库/索引变更：** Redis 限流/短缓存 key；调用统计表或 metrics。
- **实施步骤：** Resilience4j timeout/retry/circuit breaker/bulkhead；按任务/成本路由；显式 completion；断线取消；embedding cache key 含 model+textHash。
- **接口/类设计：** 路由输入含 capability/dataSensitivity/budget；fallback 不得降低数据安全级别。
- **测试与验收：** 429/5xx/超时/半流/断线/并发压测；无裸线程；重复 completion 0；缓存仍执行 ACL。
- **指标：** 可用率、P95、熔断次数、fallback rate、cost/query、线程数稳定性。
- **风险/回滚：** 重试放大流量；只对幂等请求且指数退避，feature flag 关闭智能路由。
- **建议提交：** `feat(ai): add resilient routing quotas and cancellable streaming`。

#### S5-03 可观测性、CI 门禁和发布证据

- **状态/阶段/优先级/工作量：** TODO / 5 / P0 / M。
- **目标：** 每次提交自动验证编译、测试、ACL、评测回归，并可定位在线慢点。
- **代码依据：** 现有 LogUtils 无标准 metrics/trace，仓库无 CI。
- **依赖/并行：** S0-01、S2-05；可与 S5-01 并行。
- **现有文件：** `logback-spring.xml`、LogUtils、pom。
- **新增文件：** GitHub Actions workflow、Micrometer metrics、dashboard JSON、`docs/release-checklist.md`。
- **数据库/索引变更：** 无业务表；必要时 OTLP/Prometheus 外部存储。
- **实施步骤：** 统一 traceId/versionId/runId；各阶段 timer/counter；CI 执行 unit→integration→ACL→小评测；报告作为 artifact。
- **接口/类设计：** 指标名低基数；禁止 company/question/fileName 作为 metrics label。
- **测试与验收：** PR 自动门禁；一次问答可串起 retrieval/rerank/LLM；错误日志脱敏；评测下降超阈值阻断。
- **指标：** pipeline stage P50/P95、error rate、queue lag、retrieval/LLM latency、token/cost、index consistency。
- **风险/回滚：** 可观测开销；采样和异步导出，必要时关闭 trace exporter 但保留核心 metrics。
- **建议提交：** `ci: gate changes with tests ACL evaluation and observability`。

### 7.7 阶段 6：可选研究项

#### S6-01 GraphRAG 多跳关系实验

- **状态/阶段/优先级/工作量：** TODO / 6 / P3 / M。
- **目标：** 只验证公司—指标—期间—风险关系的多跳题是否优于阶段 3 基线。
- **代码依据：** 路线图明确 GraphRAG 不是主线。
- **依赖/并行：** S3-05、S5-01；可与 S6-02 并行。
- **现有文件：** FinancialFact/Metric 与评测框架。
- **新增文件：** `GraphRetriever`、图构建实验、multi-hop 子集。
- **数据库/索引变更：** 优先用内存/ES 邻接实验；收益成立后再决策图数据库。
- **实施步骤：** 定义关系和 30+ 多跳题；对比 standard RAG；记录构图成本/新鲜度。
- **接口/类设计：** feature flag，输出仍转 Evidence 并走统一 ACL/Verifier。
- **测试与验收：** ACL 0 泄漏；多跳效果与单跳退化同时报告；无收益则归档实验。
- **指标：** multi-hop EM/F1、证据路径准确率、延迟/存储开销。
- **风险/回滚：** 为追热点扩大范围；限定两周盒，默认关闭即可回滚。
- **建议提交：** `experiment(graph): evaluate GraphRAG on finance multi-hop cases`。

#### S6-02 Query Decomposition/HyDE/Agentic RAG 实验

- **状态/阶段/优先级/工作量：** TODO / 6 / P3 / M。
- **目标：** 验证复杂尽调问题的拆解收益及额外成本，不构建开放式自治 Agent。
- **代码依据：** 路线图要求先完成可评测主线。
- **依赖/并行：** S2-05、S5-02；可与 S6-01 并行。
- **现有文件：** QueryClassifier、RetrievalPipeline。
- **新增文件：** `QueryPlanner`、`HydeRewriter`、实验配置和复杂问题子集。
- **数据库/索引变更：** EvaluationResult 保存子查询/规划轨迹和成本。
- **实施步骤：** 仅对分类器判定复杂的问题启用；限制步骤/时间/token；每个子查询继承 ACL；最终证据去重核验。
- **接口/类设计：** `QueryPlan` 是有界 DAG，不允许任意工具执行。
- **测试与验收：** 死循环/预算/注入/越权测试；收益、成本、延迟完整对照。
- **指标：** complex-query Recall/answer score、平均步骤、token/cost、超时率。
- **风险/回滚：** 成本和不确定性增加；默认关闭，超预算立即回普通 RAG。
- **建议提交：** `experiment(rag): benchmark bounded decomposition and HyDE`。

## 8. 测试与评测计划

### 8.1 测试金字塔

| 层级 | 内容 | 运行时机 | 门禁 |
|---|---|---|---|
| 单元测试 | chunk、RRF、公式、单位、ACL policy、verifier、状态机 | 每次提交 | 必须全绿 |
| 契约测试 | LLM/Embedding/Reranker、Parser SPI、ES mapping | 每次 PR | schema/错误语义一致 |
| 集成测试 | Testcontainers MySQL/Redis/Kafka/ES + MinIO | 每次 PR/每日 | 幂等、DLT、索引、删除 |
| 安全测试 | 五类身份×所有检索/引用/缓存/导出路径 | 每次 PR | 泄漏数必须 0 |
| Golden/Snapshot | PDF 页元素、表格、chunk、引用定位 | 解析变更 | 非预期 diff 阻断 |
| 离线 RAG 评测 | 50 题 smoke，300 题 full | PR smoke/每周 full | 不得越过设定回归阈值 |
| 性能测试 | 上传、解析、索引、检索、首 token/完整响应 | 里程碑 | 固定环境报告 P50/P95 |
| 故障测试 | API timeout/429、Kafka 重放、存储失败、断线 | 阶段验收 | 最终一致且明确降级 |

### 8.2 指标定义

- 检索：Recall@5/10/20、MRR、NDCG@10、ACL leak count、各路覆盖率。
- 生成：EM/F1、数值容差准确率、Faithfulness、Answer Relevance。
- 证据：Citation Precision/Recall、page/bbox 定位准确率、无效引用率。
- 拒答：precision、recall、F1；同时观察过度拒答率。
- 财务：metric mapping F1、value/unit/period/scope accuracy、calculation accuracy。
- 工程：P50/P95/P99、首 token、错误率、Kafka lag、重复处理率、orphan count、token/cost。
- 所有指标必须给出数据集版本、样本数、配置、git SHA；小样本给置信区间，不只报单点。

## 9. 数据准备计划

1. **MVP（W1～W2）：** 从巨潮资讯等公开来源选 2 家公司×2 个年度年报；保留来源 URL、下载日期、SHA-256、许可说明。
2. **解析集：** 选择 10 份数字 PDF、3 份扫描/混合 PDF；标注 30 张关键表的 bbox/行列/单位。
3. **50 题集：** FACT_LOOKUP、PERIOD_COMPARE、RATIO_CALCULATION、ACCOUNTING_POLICY、RISK_SUMMARY、EVIDENCE_VERIFICATION；至少 10 个拒答、5 个 ACL。
4. **完整集（W5～W6）：** 扩至 6～10 家、3 年、300 题；公司/行业/年份分层，避免同模板泄漏。
5. **外部基准：** FinQA、TAT-QA、Tab-CQA、FinanceBench 仅在许可与格式适配后引用；分别报告外部基准和自建中文年报集，禁止混成一个指标。
6. **质量控制：** 每题保留答案、公式、页码、bbox、表格单元格和标注人；数字题二次复核；争议题标记而不是强行定 gold。
7. **隐私与仓库：** 仓库不提交密钥、用户私文档和大 PDF；只提交 manifest、小型合法 fixture、下载脚本和 checksum。

## 10. 风险、依赖和技术决策

| 决策/风险 | 当前结论 | 触发复议条件 |
|---|---|---|
| Kafka vs RocketMQ | 保留 Kafka，统一简历和文档 | 只有明确岗位需求且完成真实迁移才改 |
| 单体 vs 微服务 | 保持模块化单体 | 独立解析服务因 Python/GPU 或扩缩容成为硬需求 |
| PDF 解析方案 | Java SPI；数字 PDF 优先，OCR 降级；必要时外接 Python | 表格结构指标达不到验收线 |
| 向量存储 | 继续 ES dense_vector + IK | 数据量/延迟实测证明专用向量库必要 |
| 混合融合 | 独立 BM25/KNN/Fact + RRF | 学习排序在 300 题以上稳定优于 RRF |
| 数据真相源 | MySQL/MinIO 为真相源，ES 可重建 | 无 |
| schema | Flyway + ES 版本索引/别名 | 无 |
| 多模型 | SPI 后至少两个实现做对照，不追求数量 | 单模型已满足且时间不足时停在 SPI |
| GraphRAG/Agent | 研究项，默认关闭 | 多跳子集有显著、稳定、可解释收益 |
| OCR/模型成本 | 记录 usage，扫描页按需 OCR | 成本超预算则限定文件类型/页数 |
| 外部 API 稳定性 | timeout、bulkhead、circuit breaker、degraded response | 无 |

ADR 必须记录：为何选金融年报、ACL 语义、事实源、chunk 策略、RRF 参数、阈值校准、公式口径、Provider 路由和索引版本策略。

## 11. Git 分支、提交和交付规范

- 分支：`main/master` 保持可运行；每个任务一个 `feat/S2-02-rrf-fusion` 或 `fix/S0-02-knn-acl` 短分支。
- 提交：遵循 Conventional Commits；一个提交只表达一个可回滚意图；不得把数据集、重构、格式化和业务功能混成一次提交。
- PR 必含：任务 ID、动机、架构/数据变更、测试命令、评测前后表、风险、回滚、截图/报告链接。
- 数据库变更只新增 Flyway migration，不修改已合并 migration；ES mapping 通过新版本索引发布。
- 每日开发结束前 push；每个阶段打 tag，例如 `rag-mvp-v0.1.0`；阶段报告保存到 `docs/milestones/`。
- 禁止提交 `.env`、密钥、真实用户文档、巨型模型和未经许可的数据；CI 使用 GitHub Secrets。
- 合并门禁：编译、单测、集成 smoke、ACL、50 题 smoke 全绿；若评测回退，PR 必须解释并经显式接受。

## 12. 阶段验收标准

| 阶段 | 必须交付 | 硬验收 |
|---|---|---|
| 0 | 可复现测试、Flyway、统一 ACL、状态机、基线报告 | `mvn test` 全绿；ACL 泄漏 0；无伪性能/错误 MQ 表述 |
| 1 | 4 份报告、结构化页/chunk、金融元数据、50 题 | 每个回答证据可定位到版本/页；模型版本真实；链路可重复跑 |
| 2 | 三路召回、RRF、Rerank、拒答/引用、消融报告 | 各路指标独立；ACL 0；一键复现；阈值有开发集依据 |
| 3 | 30 表、事实、计算、核验、300 题 | 关键事实准确率达约定线；确定性计算 trace 100%；高风险变异测试捕获 |
| 4 | diff、别名发布、软删/恢复/清理 | 未变 chunk 不重算；索引可回滚；故障后最终一致 |
| 5 | Provider、路由可靠性、可观测 CI | 两模型对照；无裸线程猜完成；PR 自动门禁；成本/延迟可追踪 |
| 6 | 实验报告 | 只有数据证明收益才合入默认链路，否则保留负结果 |

阶段完成必须同时满足代码、测试、数据、报告四项，不能以“功能能演示”代替验收。

## 13. 简历数据产出计划

| 简历候选亮点 | 必须采集的证据 | 可写条件 |
|---|---|---|
| 安全混合检索 | ACL 矩阵、BM25/KNN/RRF/Rerank 消融、Recall/NDCG、P95 | 0 泄漏且评测可复现 |
| 布局感知解析 | 页/表检测、cell 数值准确率、跨页表指标、样本数 | 标注集和解析报告完成 |
| 金融事实与计算 | 事实 value/unit/period/scope、公式 trace、变异测试 | 确定性计算与引用核验上线 |
| 增量索引 | 全量 vs 差量 embedding 数、耗时、别名回滚演练 | 至少一次真实修订/模型升级演练 |
| 多模型工程 | 效果、P95、成本、错误率、降级率对照 | 至少两个 Provider 同一数据集 |
| 上传性能 | 100 MB 固定文件、多并发、P50/P95/错误率 | 真实 Redis/MinIO/Kafka 环境，而非模拟 |

推荐写法模板：

> 面向上市公司年报场景构建布局感知 RAG，保留表格行列、单位、页码与 bbox；采用 BM25/向量/财务事实多路召回、RRF 与 Rerank，并通过确定性 BigDecimal 计算和引用核验约束数字回答。在 `[数据集规模]` 上将 `[指标]` 从 `[基线]` 提升至 `[结果]`，ACL 越权用例为 0，P95 为 `[实测]`。

方括号在有自动报告前不得替换为估算数字。

## 14. 推荐的第一批开发任务

### 14.1 立即执行的前三项

1. **S0-01 测试分层与可复现环境**：没有绿色基线，后续任何重构都无法确认是否破坏现有能力。
2. **S0-02 ACL/KNN 安全修复**：这是上线与面试可信度的阻断问题，必须早于所有检索优化。
3. **S0-03 Flyway 与配置收敛**：后续 12 类领域模型都依赖可演进 schema，越晚做迁移成本越高。

S0-01 与 S0-03 可并行思考但建议依次提交；S0-02 在 S0-01 提供 ACL 集成测试骨架后立即开始。完成三项后再做 S0-04，随后进入 S1-01。

### 14.2 八周关键路径

```text
S0-01 → S0-02 → S0-04
                  ↓
S0-03 → S1-01 → S1-02 → S1-03 → S1-04
                                  ↓
S1-05 ───────────────────────→ S2-01 → S2-02 → S2-03 → S2-04 → S2-05
                                                        ↓
S3-01 → S3-02 → S3-03 → S3-04 → S3-05
          ↓
S4-01 → S4-02 → S4-03
                   ↓
S5-01 → S5-02
  └────────→ S5-03
```

并行窗口：

- W1：S0-01、S0-03、S0-05；随后 S0-02、S0-04。
- W2：S1-05 数据标注可与 S1-01～S1-03 开发并行。
- W3～W4：S2-05 评测接入与 S2-01～S2-04 功能滚动并行。
- W5：S3-03 公式骨架可与 S3-01 表格解析并行；S3-05 持续扩充。
- W7：S4-01/S4-02 主线，S4-03 在状态机基础上提前准备。
- W8：S5-01 与 S5-03 并行，最后 S5-02 压测验收。

### 14.3 任务统计与维护检查

- 任务总数：**28 个**（阶段 0：5，阶段 1：5，阶段 2：5，阶段 3：5，阶段 4：3，阶段 5：3，阶段 6：2）。
- 当前 `DONE`：0；所有规划指标均为待实测目标。
- 依赖检查：无循环依赖；阶段 6 不反向阻塞主线。
- 粒度检查：任务均为 S/M/L；开工前若某项实际超过 5 天，必须拆 PR，不允许直接扩成 XL。
- 覆盖检查：解析、表格/图片、评测、Bad Case、多模型、金融适配、ACL、增量生命周期、可观测与 CI 均已映射到任务。
- 场景贯穿检查：金融元数据影响解析和过滤，FinancialFact 进入检索与计算，Evidence/Citation 进入生成与评测，不是额外挂一个 Prompt。

---

## 变更记录

| 日期 | 版本 | 说明 |
|---|---|---|
| 2026-07-27 | v1.0 | 基于当前代码、实跑测试和面试驱动路线图建立首次可执行计划 |

