# RAG 大厂面试问题驱动的二开路线

> 调研快照：2026-07-27。目标不是收集“八股答案”，而是从真实追问反推项目必须能演示、能测量、能解释的能力。

## 1. 调研口径

来源按可信度分级：

- **A 类——真实面经**：候选人以第一人称记录具体公司、岗位和面试问题，是本文提炼公司侧重点的主要依据。
- **B 类——公司岗位 JD**：只能说明团队真实需求，不能当作面试原题。
- **C 类——题库、培训或二次整理**：只用于补充关键词，不用于声称“某公司问过”。

本轮已检索字节、百度、阿里、腾讯、小红书、滴滴、美团、快手、京东、华为、小米、vivo、B 站等公司。公开可检索的小红书 RAG 专项一手面经不足，本文明确保留这个证据空白，不把二次整理内容包装成小红书真题。

## 2. 分公司真实问题

### 2.1 字节跳动：效果归因、知识运营、多模态和生产兜底

真实追问：

- RAG 的召回、粗排、精排链路如何设计？Embedding 召回有哪些方案？
- 从纯向量或 BM25 演进到混合检索后，实际解决了什么问题？仍未召回的数据如何归因？
- 召回 10 个 Chunk，其中一半是噪声，怎样阻止生成器使用噪声？
- OCR 有噪声时怎么纠错？图像和文本向量不在同一空间时如何对齐？
- Chunk 为什么要有 Overlap？TopK 和 Rerank 截断值如何确定？
- 文档局部更新时如何增量建索引？
- 没有召回到知识时如何拒答？大模型高并发时如何限流、降级和控成本？
- 知识如何完成来源、清洗、打标、上线、更新、过期和清退？
- 置信度与意图识别怎样组合做分级路由？

A 类来源：

- [4 轮拿下字节 Offer：LLM 面试题合集](https://www.nowcoder.com/discuss/746382064101908480)
- [字节机审策略与工具运营一面](https://www.nowcoder.com/discuss/857726328517169152)
- [字节 Agent 面试：召回结果中有一半噪声怎么办](https://www.nowcoder.com/feed/main/detail/f87f6eda15cf477fa9758d0bf7612b98)
- [字节番茄 AI Agent 开发二面](https://www.nowcoder.com/feed/main/detail/27ceb2d68d90494382e53d2f821f99e3)
- [字节一面：混合检索、Chunk、Rerank 追问](https://www.nowcoder.com/feed/main/detail/abc736612a744daba3a849b3d8b132a1)

### 2.2 阿里 / 阿里云：量化评测、检索参数、系统规模和数据隐私

真实追问：

- RAG 全链路和多路召回怎么做？除 MRR 外还有哪些评价指标？
- 为什么使用 Elasticsearch？数据量、索引分片和集群高可用如何设计？
- Chunk Size 和 Overlap 如何选？有没有 Query Rewrite？
- 是否构建过自己的知识库和对比实验？知识质量怎样评估？
- 一次回答要多久？检索为什么慢？各阶段耗时分别是多少？
- 召回 K 值如何选？排序和重排序大概增加多少延迟？
- RAG 与微调怎样取舍？私有数据如何保护？
- 如何评估完整 AI 应用，而不是只评估模型？

A 类来源：

- [阿里云 AI 平台研发一面](https://www.nowcoder.com/feed/main/detail/7fa8ae2f1c2e42909e664dce6cbc8190)
- [阿里 4.9 一面：Chunk、Query Rewrite、评测](https://www.nowcoder.com/feed/main/detail/82df44bd2b44458d90c9c18921254b2c)
- [阿里国际研发工程师 Java 二面](https://www.nowcoder.com/feed/main/detail/cc4c23cdb5824ec3898ffa1a38c217cf)
- [阿里国际 AI 应用开发一面](https://www.nowcoder.com/discuss/872069472310288384)
- [阿里 AI 应用开发一面：K 值与 Rerank 延迟](https://www.nowcoder.com/feed/main/detail/21b63ca5299a48aca4cbea099bfbc0b7)

B 类来源：

- [阿里巴巴 RAG 引擎研发工程师 JD](https://www.nowcoder.com/jobs/detail/439513)：多模态检索、稀疏/稠密融合、Embedding 和索引优化、端到端评估与 A/B 实验。

### 2.3 百度：Retriever 与多路召回融合

真实追问：

- 完整 RAG 流程是什么？
- Retriever 模型如何使用？
- Rerank 如何融合多路召回结果？
- 如何评估基座模型推理能力，并区分检索问题和生成问题？

A 类来源：

- [百度提前批大模型算法二面](https://www.nowcoder.com/feed/main/detail/88bd58fecce848559c6a15428cf07371)

### 2.4 腾讯 / 微信 / 混元：Embedding、向量索引、GraphRAG 与长上下文

真实追问：

- 推荐或搜索系统为什么引入 RAG？知识库的数据从哪里来，如何更新？
- 为什么选 BGE？FAISS 索引怎样构建和优化？
- Chunk 策略如何平衡信息完整性和噪声？
- GraphRAG 适合解决哪些传统 RAG 难以处理的问题？
- 输入超过上下文窗口时，滑动窗口、压缩和摘要如何选择？
- LangGraph 多工具编排相较纯 Prompt 有什么优势？

A 类来源：

- [腾讯混元大模型算法一面](https://www.nowcoder.com/feed/main/detail/7d254fabbe4d4d00a97a2a6fbd05b634)
- [腾讯微信大模型算法实习一面](https://www.nowcoder.com/feed/main/detail/6d63ce890e6b44f89e58c8c1f6332bf6)

B 类来源：

- [腾讯 AI 搜索算法（LLM 方向）JD](https://www.nowcoder.com/jobs/detail/389420)：Query 意图理解、Embedding、混合检索、排序、对话式搜索和实验评估。

### 2.5 滴滴：场景化系统设计、准确率统计、长文本与 Bad Case

真实追问：

- RAG 检索准确率怎样统计？知识库全链路怎么设计？
- Agent 给出的排障建议与事实不符时怎么办？
- 从零设计一套用于长文本摘要理解的 RAG。
- 长上下文能否击败极致优化的 RAG？
- RAG 在摘要理解方面如何提升？
- 系统上线后有哪些 Bad Case，怎样定位和修复？

A 类来源：

- [滴滴 AI Agent 开发实习面经](https://www.nowcoder.com/feed/main/detail/9b2042a59f2e4d7cb0b2b43ceda5cdbb)
- [滴滴提前批大模型算法五轮面经](https://www.nowcoder.com/discuss/658076659777564672)

### 2.6 快手：参数依据、向量索引、Rerank 和上下文工程

真实追问：

- 为什么需要父子索引？为什么还要 BM25？BM25 和向量检索怎样组合，比例依据是什么？
- Rerank 后取几个 Chunk？TopK 截断为何选这个数，是否做过实验？
- 既然向量检索已有相似度，为什么还需要 Cross-Encoder？
- 如何评估 Rerank 的有效性？
- IVF_FLAT 和 HNSW 有什么区别，分别适合什么规模和延迟要求？
- RAG 各阶段怎样降低延迟？上下文窗口怎样统一管理？
- 复杂推理问题何时需要知识图谱？如何从文本抽取节点与边？
- 如何验证 RAG 系统真正有效，而不是“看起来能回答”？

A 类来源：

- [快手 AI Agent 实习一面：父子索引、混合检索与 Rerank](https://www.nowcoder.com/feed/main/detail/330b980c0dc545b3a209bd51a09c12e8)
- [快手大模型应用开发三面：Cross-Encoder 与 Rerank 评估](https://www.nowcoder.com/feed/main/detail/4ff132d0d0fb450398341f4ec9828def)
- [快手大模型开发算法实习：GraphRAG、延迟和上下文](https://www.nowcoder.com/feed/main/detail/8907ac1fa3f64b928ff104bbacfa6fba)
- [快手 AI 应用开发面经汇总](https://www.nowcoder.com/discuss/882573284426932224)

### 2.7 美团：RAG 与 Memory / Agent 的边界及业务价值

真实追问：

- Memory 与 RAG 的区别、优势和适用场景分别是什么？
- Workflow、Agent 和 RAG 应如何分工？
- 垂类大模型或 RAG 项目上线后用什么业务指标证明价值？

A 类来源：

- [美团大模型算法岗一面与二面](https://www.nowcoder.com/feed/main/detail/b264abf14d4d4c44bc022c9ea1dce981)
- [美团大模型产品转正实习面经](https://www.nowcoder.com/feed/main/detail/932d399ab5f846c29ebb8d7272b2ee90)

### 2.8 小米：Embedding 原理、分阶段评测与数据规模

真实追问：

- Embedding 模型的结构、输出维度是什么？为什么选择这个维度？
- RAG 的数据集怎样构建？召回准确率是多少？
- 是否拆开评估 RAG、工具调用和最终回答？
- 剩余误判如何做阶段归因？
- Bad Case 持续入库导致数据库膨胀、检索质量下降时怎么办？
- RAG 效果差时，数据处理、检索排序和 Query Rewrite 如何优化？

A 类来源：

- [小米大模型算法实习二面](https://www.nowcoder.com/feed/main/detail/6e4fa662d94d4023b8ca88bd30e4e4a7)
- [小米大模型算法实习一面：分阶段评测与 Bad Case](https://www.nowcoder.com/feed/main/detail/69d56315acea489c8afcdb65d754147b)
- [小米测开实习一面：RAG 优化与指标来源](https://www.nowcoder.com/feed/main/detail/577d996958024035810c0a7e89a864fe)

B 类来源：

- [小米 RAG 大模型算法研究员 JD](https://www.nowcoder.com/jobs/detail/408752)：音频、视频、文本多模态 RAG，Self-RAG / DeepRAG 及 RAG 与强化学习结合。

### 2.9 华为：歧义处理、边缘部署和技术路线选择

真实追问：

- RAG 怎样搭建？分块和检索策略是什么？
- Query 歧义与知识库同词歧义如何解决？
- 为什么选择当前框架和模型？边缘设备能承载多大的模型？
- 项目是否只能使用 RAG？换成 Agent 有何得失？

A 类来源：

- [华为实习 AI 工程师面经](https://www.nowcoder.com/feed/main/detail/3bd15a280c334ccfa51c2d181c4b587c)

B 类来源：

- [华为云大模型应用 JD](https://www.nowcoder.com/jobs/detail/405945)：模型微调、部署、RAG、数据流程、效果评估和持续优化。

### 2.10 京东及其他公司：数据处理和召回优化

京东真实追问：

- RAG 切片如何实现和优化？
- Embedding 召回怎样兼顾效果和效率？
- Agent 与 RAG 的原理和使用边界是什么？

来源：

- [京东大模型算法面经：切片与 Embedding 召回](https://www.nowcoder.com/discuss/696466506158268416)
- [京东大模型算法实习面经](https://www.nowcoder.com/feed/main/detail/9ce9749030b24f6a9a7afcaa62b27f46)

其他可验证信号：

- [vivo 大模型算法实习面经](https://www.nowcoder.com/feed/main/detail/4e1aefbef3a04234a3def7b20f07810a)：RAG 项目重点追问数据处理。
- [B 站商业大模型算法 JD](https://www.nowcoder.com/feed/main/detail/0e602f13e3b44830b8e2c74d842126e1)：RAG、多轮对话和图片/视频多模态内容理解。

### 2.11 小红书：公开证据不足

本轮没有检索到足够可靠、同时明确标注小红书岗位与 RAG 具体问题的一手面经。能够搜到的主要是推荐算法面经或第三方“面试题整理”，因此不据此虚构公司侧重点。

可以合理准备但不能声称“小红书问过”的方向是：社区搜索的 Query 意图、时效性、图文多模态检索、内容安全、作者/笔记关系和推荐场景下的 GraphRAG。后续若获得面试录音、截图或一手帖子，再升级为 A 类证据。

## 3. 跨公司高频考点

| 优先级 | 高频考点 | 面试官真正想确认 | 本项目必须提供的证据 |
| --- | --- | --- | --- |
| S | 如何评测、指标为何这样选 | 是否做过真实实验，而不是调用 API | 固定评测集、Baseline、对照实验、可复现报告 |
| S | Chunk / Overlap / 父子索引 | 是否理解数据结构决定检索上限 | 多种切块策略的 Recall、nDCG、Token 和延迟对比 |
| S | BM25 + KNN + Rerank | 是否理解召回、粗排、精排的职责 | 独立双路召回、RRF、Cross-Encoder 消融实验 |
| S | TopK、阈值和拒答 | 参数是否来自数据而非拍脑袋 | 阈值校准曲线、拒答准确率、错误接受率 |
| S | Bad Case 分层归因 | 能否定位解析、召回、排序或生成问题 | Trace、错误标签和自动归因报告 |
| A | Embedding 与索引选型 | 是否理解维度、语言、成本和 ANN 原理 | 多 Embedding / 维度对比，HNSW 参数实验 |
| A | 多模态文档解析 | 是否能处理表格、图片、扫描件和布局 | 结构化中间表示、OCR/表格专项评测 |
| A | 增量更新和知识清退 | 是否考虑真实知识库持续变化 | 文档版本、差异更新、索引 Alias 与回滚 |
| A | 长上下文、Memory、RAG 边界 | 是否理解上下文不是越长越好 | Token Budget、上下文压缩、父块回填实验 |
| A | 延迟、成本、高并发和降级 | 是否具备线上工程意识 | 分阶段 P50/P95/P99、Token/成本、Fallback |
| A | 权限与 Prompt Injection | 企业数据是否可能越权或被恶意文档操纵 | ACL 泄漏率为 0、安全测试集、引用校验 |
| B | GraphRAG / Agentic RAG | 是否能判断适用边界，而非追热点 | 只在多跳关系题上做可开关对照实验 |

结论：跨公司最稳定的主线是 **评测 → 切块 → 召回融合 → Rerank → 拒答 → Bad Case 归因**。多模型和 GraphRAG 是加分项，但不能替代这条主线。

## 4. 当前代码审计

| 维度 | 当前实现 | 风险 / 缺口 |
| --- | --- | --- |
| 解析 | `ParseService` 用 Tika `BodyContentHandler` 抽取纯文本，按字符数、段落、句子和 HanLP 切块 | 丢失页码、标题层级、表格结构、图片、坐标和阅读顺序；没有 OCR 和 Overlap |
| 父子块 | 1MB 缓冲区被命名为 Parent Chunk，但只负责分批落库 | 没有持久化 Parent ID，检索后无法父块回填，不是真正父子索引 |
| 向量化 | 单一 DashScope `text-embedding-v4`，固定 2048 维 | Provider 与索引耦合；模型迁移方案缺失；`modelVersion` 仍错误写成 `deepseek-embed` |
| 混合检索 | Elasticsearch 顶层 KNN + `match`，再做 BM25 rescore | 不是两个独立召回列表；没有 RRF、真正的 Cross-Encoder、MMR 和消融实验 |
| 权限 | ACL 仅在顶层关键词 `query` 中 | **P0：顶层 `knn` 与 `query` 按 OR 融合，KNN 分支没有 `knn.filter`，存在越权召回风险** |
| 字段 | Mapping 是 `isPublic`，查询代码使用 `public` | 字段语义不一致，可能依赖动态映射，权限逻辑难以验证 |
| 置信度 | 固定 Top 5，直接进入 Prompt | 没有相似度下限、Rerank 阈值、校准或无答案检测 |
| 上下文 | 每块截断 300 字符后拼接 | 可能截断句子和表格；没有 Token Budget、去重、冲突处理、父块/邻块扩展 |
| 引用 | Prompt 要求输出来源编号 | 没有后端验证引用是否真实支撑回答，模型可生成不存在的引用 |
| 增量索引 | 以文件为单位解析、向量化和删除 | 没有版本号、Chunk Diff、Alias 切换、双写、回滚和孤儿数据清理 |
| 评测 | 没有 RAG 评测集和流水线 | 无法回答 TopK、Chunk、权重、模型为什么这样选 |
| 性能 | 上传测试以假设的 3ms 网络耗时做算术模拟 | 不能支撑“100MB 8s → 2s”等简历数据 |
| 模型 | 单一 `DeepSeekClient` 和 `EmbeddingClient` | 没有 Chat / Embedding / Rerank / Vision 统一 Provider、路由和降级 |
| 可观测性 | 普通业务日志 | 无 Query 级 Trace、阶段耗时、Token、成本、模型版本和召回快照 |
| 消息队列 | 代码实际使用 Kafka | 简历若写 RocketMQ 会被项目深挖直接识破 |

Elastic 官方说明顶层 `knn` 与 `query` 的结果按 disjunction（OR）组合；ACL 必须同时写进 `knn.filter`：

- [Elasticsearch filtered kNN 官方文档](https://www.elastic.co/guide/en/elasticsearch/reference/8.18/knn-search.html)

## 5. 选定垂直场景：上市公司年报智能尽调与财务指标核验

### 5.1 为什么选择这个场景

项目不再定位为泛化“企业知识库”，而是：

> 面向投研、审计、风控和企业财务人员，对上市公司年报、季报、审计报告及更正公告进行证据可追溯的事实查询、跨期比较、财务指标计算和风险信息核验。

选择该场景的原因：

1. **不是换 Prompt 就能完成**：财务报告包含多级表头、合并单元格、跨页表格、脚注、单位和会计口径，必须改造解析、索引、检索、计算和验证链路。
2. **公开数据可获得**：巨潮资讯是深交所法定信息披露平台，可获取上市公司定期报告和公告。
3. **有公开评测资源**：FinQA、TAT-QA、中文 Tab-CQA 和 FinanceBench 可用于验证表格检索、证据抽取和数值推理。
4. **能放大现有项目优势**：文档密级、组织权限、MinIO、异步解析和 Elasticsearch 都可以自然映射到尽调项目与内部工作底稿。
5. **面试可深挖**：能够回答表格解析、元数据过滤、跨年度检索、精确计算、引用核验、权限隔离和高风险拒答等问题。

数据与评测依据：

- [巨潮资讯网：上市公司法定信息披露平台](https://www.cninfo.com.cn/)
- [FinQA：财务报告数值推理数据集](https://aclanthology.org/2021.emnlp-main.300/)
- [TAT-QA：表格与文本混合财务问答](https://aclanthology.org/2021.acl-long.254/)
- [Tab-CQA：基于中文上市公司财务报告的表格对话问答](https://aclanthology.org/2023.acl-industry.20/)
- [FinanceBench：开放式财务文档问答评测](https://arxiv.org/abs/2311.11944)

### 5.2 用户与核心任务

| 用户 | 可见数据 | 典型任务 |
| --- | --- | --- |
| 投研分析师 | 公开报告、被授权的内部尽调材料 | 跨年度指标比较、经营变化和风险因素归纳 |
| 审计 / 财务人员 | 财务报表、附注、审计底稿 | 数字勾稽、会计口径核验、异常差异定位 |
| 风控 / 合规人员 | 公开披露、内部风险记录 | 关联交易、担保、诉讼、审计意见和更正公告核验 |
| 项目管理员 | 指定项目的全部文档 | 文档版本、权限、索引状态和评测报告管理 |

必须支持的五类问题：

1. **事实定位**：“2024 年合并口径营业收入是多少？位于哪一页？”
2. **跨期比较**：“2023—2025 年经营活动现金流净额如何变化？”
3. **确定性计算**：“2025 年研发费用占营业收入的比例是多少？”
4. **定性核验**：“审计意见是否为标准无保留意见？主要风险是什么？”
5. **多证据归纳**：“收入增长但经营现金流下降，报告中披露了哪些可能原因？”

明确不做：

- 不预测股价，不输出买卖建议，不替代持牌投顾。
- 不让 LLM 凭参数记忆回答精确财务数字。
- 不把未经引用核验的生成文本标记为“已核验结论”。

### 5.3 金融文档统一数据模型

通用 `DocumentElement` 之外，增加结构化财务事实：

```text
FinancialFact
├─ companyId / stockCode / companyName
├─ reportType / fiscalYear / periodStart / periodEnd
├─ statementScope          # CONSOLIDATED / PARENT
├─ statementName           # 资产负债表 / 利润表 / 现金流量表 / 附注
├─ metricCode / metricName / metricAliases
├─ value / unit / currency / scale
├─ rowPath / columnPath / tableId
├─ pageNo / bbox / footnoteIds
├─ sourceDocumentId / sourceVersion
└─ auditStatus / effectiveAt
```

金融专项解析规则：

1. 识别“合并”与“母公司”报表，严禁混用口径。
2. 展开多级表头，保留 `rowPath + columnPath`，不能只把表格拍平成文本。
3. 正确处理“万元/亿元”、币种、百分号、括号负数、破折号空值。
4. 表格跨页时根据表名、表头和页码合并；重复表头不作为数据行。
5. 页眉、页脚、目录和审计报告页不能污染正文 Chunk。
6. 脚注与对应指标建立显式关联。
7. 更正公告产生新版本，旧版本保留但不再作为默认有效证据。

建议新增模块：

```text
parser/financial/FinancialReportParser
parser/financial/TableStructureNormalizer
domain/financial/FinancialFact
service/financial/MetricNormalizationService
service/financial/FinancialQueryRouter
service/financial/CalculationService
service/financial/NumericEvidenceVerifier
```

### 5.4 场景专属检索链路

先将问题路由为：

```text
FACT_LOOKUP
PERIOD_COMPARE
RATIO_CALCULATION
RISK_SUMMARY
ACCOUNTING_POLICY
EVIDENCE_VERIFICATION
```

不同意图不能共用同一套检索策略：

| 意图 | 主召回源 | 专项处理 |
| --- | --- | --- |
| FACT_LOOKUP | `FinancialFact` 精确字段 + 表格索引 | 公司、报告期、口径、指标、单位过滤 |
| PERIOD_COMPARE | 多报告期 Fact 查询 | 对齐指标、口径、单位后再比较 |
| RATIO_CALCULATION | 操作数 Fact 查询 | 计算引擎执行公式，LLM 只解释 |
| RISK_SUMMARY | BM25 + KNN + Rerank | 按风险章节、审计意见和附注加权 |
| ACCOUNTING_POLICY | 标题感知文本索引 | 回填完整会计政策父块和关联脚注 |
| EVIDENCE_VERIFICATION | 引用 ID / 页码 / 单元格反查 | 校验答案声明是否有原文支撑 |

金融 Query 处理：

1. 公司名称、简称、股票代码做实体归一化。
2. “营收/营业收入”“净现金流/现金及现金等价物净增加额”等术语映射到标准指标。
3. 从 Query 提取报告期、报表口径、币种、单位和比较维度。
4. 缺少关键口径时先澄清，不能默认把母公司与合并报表混用。
5. 叙述性问题使用 BM25 + KNN + RRF + Rerank；数值问题优先查结构化 Fact。
6. 跨期问题拆成多个受约束子查询，分别取证后再汇总。

### 5.5 确定性计算与数字防幻觉

数值问题采用“检索操作数 → 生成计算计划 → 校验计划 → 程序执行”的方式：

```json
{
  "question": "2025 年研发费用占营业收入比例是多少？",
  "operands": [
    {"metric": "研发费用", "value": 42000000, "unit": "CNY"},
    {"metric": "营业收入", "value": 800000000, "unit": "CNY"}
  ],
  "program": "divide(研发费用, 营业收入) * 100",
  "result": 5.25,
  "resultUnit": "%",
  "citations": ["fact-rd-2025", "fact-revenue-2025"]
}
```

约束：

- 四则运算、同比、占比、均值等交给 `CalculationService`，禁止直接采信 LLM 心算结果。
- 计算前校验公司、报告期、口径、币种和单位完全一致。
- 结果保留原始操作数、公式、舍入规则和证据 ID。
- `NumericEvidenceVerifier` 重新执行公式，并检查引用单元格。
- 若操作数缺失、口径冲突或来源版本过期，必须拒答或要求澄清。

### 5.6 回答协议

每个回答固定返回：

```json
{
  "conclusion": "2025 年研发费用占营业收入 5.25%。",
  "scope": {
    "company": "示例公司",
    "period": "2025",
    "statementScope": "CONSOLIDATED",
    "unit": "%"
  },
  "calculation": "42,000,000 / 800,000,000 × 100%",
  "evidence": [
    {
      "document": "2025年年度报告",
      "page": 128,
      "table": "研发投入情况表",
      "row": "研发费用",
      "value": "42,000,000元"
    }
  ],
  "confidence": "VERIFIED",
  "warning": "结果仅用于文档核验，不构成投资建议。"
}
```

置信度不使用未经校准的单一相似度，而采用状态枚举：

- `VERIFIED`：结构化操作数、公式和引用全部通过校验。
- `SUPPORTED`：定性结论有直接文本证据。
- `PARTIAL`：只有部分证据，回答中必须指出缺口。
- `INSUFFICIENT_EVIDENCE`：拒答。
- `CONFLICTING_EVIDENCE`：不同版本或口径冲突，需要人工处理。

### 5.7 金融专项评测

MVP 数据建议：

- 选择 6～10 家不同行业 A 股公司。
- 每家公司选取连续 3 年年报及相关更正公告。
- 构建 300 条经过人工核验的问题。
- 问题覆盖事实、跨期、计算、表格、风险、无答案、版本冲突和权限场景。
- 外部使用 FinQA、TAT-QA、Tab-CQA 或 FinanceBench 子集验证泛化，内部中文集用于项目验收。

专项指标：

| 层次 | 指标 |
| --- | --- |
| 表格解析 | Cell Exact Match、表头层级准确率、跨页表格合并准确率 |
| 事实检索 | Fact Recall@K、指标归一化准确率、期间/口径过滤准确率 |
| 数值推理 | Program Accuracy、Execution Accuracy、单位准确率、舍入准确率 |
| 证据 | Citation Page Accuracy、Evidence Exact Match、引用覆盖率 |
| 生成 | Faithfulness、答案完整性、无答案拒答准确率 |
| 安全 | ACL 泄漏率、过期版本误用率、Prompt Injection 成功率 |
| 性能 | 解析 P95、检索 P95、Rerank P95、端到端 P95、单问成本 |

建议目标值是开发验收门槛，不是当前已取得的成绩：

- ACL 泄漏率：`0`。
- 期间、单位和报表口径准确率：`≥ 98%`。
- 引用页码准确率：`≥ 95%`。
- 数值 Execution Accuracy：`≥ 90%`。
- 无答案拒答准确率：`≥ 90%`。
- 其余指标先记录 Baseline，再要求每项优化提供显著对照结果。

### 5.8 场景实施顺序

#### F0：最小闭环

1. 选 2 家公司、2 年年报。
2. 实现报表元数据、单位、合并/母公司口径解析。
3. 完成事实查询和页码引用。
4. 人工构建 50 条 Golden Set。

#### F1：表格与计算亮点

1. 多级表头、跨页表格和脚注。
2. `FinancialFact` 双索引。
3. 指标归一化和确定性计算。
4. 数值与引用双重校验。

#### F2：尽调与风险亮点

1. 跨年度比较。
2. 审计意见、关联交易、担保、诉讼和更正公告专项路由。
3. 版本冲突检测与增量索引。
4. 内部尽调材料的项目级 ACL。

#### F3：完整评测

1. 扩展到 6～10 家公司、连续 3 年报告。
2. 300 条中文专项评测集。
3. 接入公开金融 QA 子集。
4. 输出解析、检索、计算、生成、权限和性能六类报告。

完成 F2 后，该项目才应在简历中从“企业 RAG 知识库”改名为“上市公司年报智能尽调与财务指标核验平台”。

## 6. 优化后的目标架构

```text
离线知识链路
文件 → 病毒/类型校验 → 布局解析/OCR → DocumentElement
     ├→ 标题感知父子切块 → 去重/质量评分 → Embedding → Narrative Index
     └→ 财务表格标准化 → FinancialFact → Table/Fact Index
     → 版本化索引 + Alias → 解析、事实和索引评测

在线问答链路
Query → 鉴权 → 金融意图识别 → 公司/期间/口径/指标归一化
      ├→ 数值类 → FinancialFact 受约束查询 → CalculationService
      └→ 叙述类 → BM25 + ACL ───────┐
                   KNN + ACL Filter ─┼→ RRF → 去重/MMR → Rerank
                   可选 Graph 召回 ──┘
      → 置信度校准/拒答 → 父块与脚注回填 → Token Budget
      → Model Router → 带证据生成 → 数字/引用双校验 → 流式响应

评测与观测链路
Golden Set + 线上反馈 → 分阶段 Trace → 错误归因
                       → 实验矩阵 → 回归门禁 → 灰度发布
```

## 7. 最值得做的三个亮点

### 亮点一：布局感知的多模态解析与增量知识生命周期

统一中间表示：

```text
Document
  └─ Page
      ├─ Heading(level, path, bbox)
      ├─ Paragraph(text, bbox)
      ├─ Table(markdown, cells, bbox)
      ├─ Image(ocr, caption, bbox)
      └─ Formula(latex, bbox)
```

必须实现：

1. `DocumentParser` SPI：PDF、Word、PPT、Excel、HTML 分类型解析。
2. 保存 `pageNo / headingPath / elementType / bbox / parentId / version`。
3. 表格同时存 Markdown 和 Cell JSON；扫描件走 OCR；图片生成 Caption。
4. 标题感知父子 Chunk：子块召回，父块或相邻 Element 回填。
5. 文档版本和 Chunk 内容哈希：局部 Diff 后只重建变化块。
6. 解析失败可重试、可人工修正、可追踪 Parser 和模型版本。

验收指标：

- OCR 字符错误率、表格单元格准确率、元素顺序准确率。
- 文本题、表格题、图片题分别统计 Recall@K 和回答正确率。
- 单页解析 P95、单文档失败率、增量更新耗时和全量重建耗时。

进阶实验可比较传统 OCR/文本索引与页面图像多向量检索：

- [ColPali: Efficient Document Retrieval with Vision Language Models](https://arxiv.org/abs/2407.01449)

### 亮点二：安全、可评测的多阶段检索与证据约束生成

必须实现：

1. ACL 同时下推到 BM25 和 `knn.filter`，并统一 `isPublic` 字段。
2. BM25、KNN 独立召回，使用 RRF 融合，不直接混加不可比较的分数。
3. Cross-Encoder Rerank，并按文档、父块去重；可选 MMR 保证证据多样性。
4. 用验证集选择 `candidateK / rerankK / finalK / threshold`。
5. Rerank 分数做校准；证据不足时拒答，不把固定 `_score` 当概率。
6. 生成结果返回结构化 Citation；后端校验引用 ID 存在且确实包含支撑文本。
7. 检索到的文档一律视为不可信数据，与系统指令分隔，增加间接 Prompt Injection 测试。

基础实验矩阵：

| 实验 | 变量 |
| --- | --- |
| Chunk | 256/512/768 Token、0/10%/20% Overlap、固定/标题/父子 |
| 召回 | BM25、KNN、加权融合、RRF |
| 精排 | 无 Rerank、Cross-Encoder，不同 candidateK |
| Query | 原始、规范化、Rewrite、HyDE |
| 上下文 | 固定 TopK、阈值截断、Token Budget、父块回填 |

### 亮点三：分阶段评测、Bad Case 闭环与多模型路由

评测数据格式至少包含：

```json
{
  "id": "acl-no-answer-001",
  "query": "某私密项目的预算是多少？",
  "goldAnswer": null,
  "relevantChunkIds": [],
  "userId": "user-without-permission",
  "type": "permission",
  "shouldRefuse": true
}
```

评测层次：

- **解析**：OCR CER、表格准确率、结构元素 F1。
- **检索**：Recall@K、Precision@K、MRR、nDCG@K、命中率、ACL 泄漏率。
- **生成**：Faithfulness、Answer Relevance、Context Precision/Recall、引用正确率、拒答准确率。
- **系统**：各阶段 P50/P95/P99、QPS、错误率、Token、单问成本、Fallback 率。
- **业务**：用户采纳率、追问率、人工转接率、负反馈率。

每个错误必须归入以下一种或多种标签：

```text
PARSE_ERROR
CHUNK_BOUNDARY_ERROR
EMBEDDING_MISS
LEXICAL_MISS
FUSION_ERROR
RERANK_ERROR
CONTEXT_TRUNCATION
CONTEXT_CONFLICT
GENERATION_UNGROUNDED
CITATION_ERROR
ACL_LEAK
NO_ANSWER_FALSE_ACCEPT
```

模型层抽象：

```java
interface ChatModel {}
interface EmbeddingModel {}
interface RerankModel {}
interface VisionModel {}
```

路由依据不是随机切模型，而是任务类型、效果、延迟、成本、数据敏感度和 Provider 健康状态。Embedding 模型或维度升级时新建版本化索引，通过 Alias 双写、回填、灰度和回滚完成迁移。

评测方法可参考：

- [RAGAS：RAG 自动评估](https://aclanthology.org/2024.eacl-demo.16.pdf)
- [RAGChecker：检索与生成的细粒度诊断](https://arxiv.org/abs/2408.08067)

## 8. 不建议作为主线的功能

### GraphRAG

适合跨文档实体关系、多跳推理和全局主题总结；不适合用来替代普通事实问答检索。建议在基础链路稳定后，用“组织关系、项目依赖、制度引用”一类小数据集做可开关实验。只有多跳题显著提升且延迟、维护成本可接受时，才写入简历。

### Agentic RAG / Self-RAG

可以让模型决定是否检索、改写 Query 或二次检索，但会增加不可控分支、Token 和延迟。先把固定检索链路评测清楚，再加入最大迭代次数、预算、失败回退和轨迹评测。

### 单纯支持很多模型

“接入 10 个模型”本身不是亮点。只有统一 Provider、索引迁移、路由策略、效果—延迟—成本报告和故障降级都存在时，多模型才构成工程亮点。

## 9. 实施顺序与验收门槛

### P0：可信与安全基线

- 修复 KNN ACL、字段不一致和越权测试。
- 删除或修正无实测依据的性能描述；统一 Kafka / RocketMQ 表述。
- 建立 100～200 条最小评测集，覆盖事实、无答案、权限和多跳。
- 为当前实现记录 Baseline。

完成标准：ACL 泄漏率为 0；Baseline 报告可重复运行；构建和核心测试通过。

### P1：第一条可量化亮点——检索与评测

- BM25/KNN 独立召回、RRF、Rerank、阈值拒答。
- Query Trace 和分阶段耗时。
- 完成 Chunk、TopK、Rerank 消融实验。

完成标准：报告能回答“为什么选 512、为什么取 TopK=5、Rerank 提升多少、增加多少延迟”。

### P2：第二条亮点——多模态结构解析

- 文档中间表示、页码/标题/表格。
- OCR、图片 Caption、父子索引。
- 局部更新与版本化索引。

完成标准：表格题和扫描件题有单独指标；演示一次只更新某页而非全量重建。

### P3：第三条亮点——模型与线上工程

- Chat / Embedding / Rerank / Vision Provider。
- 熔断、限流、Fallback、成本统计。
- Alias 双写迁移和灰度。
- 离线评测与线上反馈闭环。

完成标准：至少两组模型在同一评测集上形成效果—延迟—成本 Pareto 报告；主模型故障时可观测地降级。

### P4：可选研究项

- GraphRAG 多跳关系检索。
- Query Decomposition、HyDE、Self-RAG。
- 页面图像多向量检索。

只有通过对照实验才保留，不为了“技术名词多”进入主链路。

## 10. 面试前必须能现场回答的问题

### 数据与切块

1. 为什么按字符而不是 Token 切块？中英文模型下有何影响？
2. Chunk 太大和太小分别会造成什么错误？
3. Overlap 怎样确定？重复内容如何去重？
4. 父子索引和普通滑窗相比提升在哪里？
5. 表格跨页、双栏 PDF 和扫描件怎么处理？

### 检索与排序

6. BM25 和向量召回各自擅长什么？
7. 为什么用 RRF，而不是直接把两个 `_score` 相加？
8. Cross-Encoder 为什么通常比 Bi-Encoder 精排更准？
9. candidateK、rerankK、finalK 怎样通过数据选择？
10. Rerank 只改变排序，为何 Recall@K 可能不变但 MRR/nDCG 上升？
11. HNSW 的 `M / efConstruction / efSearch` 如何影响内存、召回和延迟？

### 评测与归因

12. Golden Set 如何构建，怎样避免数据泄漏？
13. Recall@K、MRR、nDCG 分别回答什么问题？
14. LLM-as-a-Judge 有哪些偏差，如何用人工标注校准？
15. 答错时怎样判断是解析、召回、Rerank 还是生成问题？
16. 如何证明优化没有只对某一类问题过拟合？

### 生成与安全

17. RAG 为什么只能缓解而不能消灭幻觉？
18. 无答案拒答阈值怎么校准？
19. 如何验证引用真的支撑答案？
20. 恶意文档中的 Prompt Injection 怎样防？
21. 多租户 ACL 应在哪几个阶段执行？

### 工程与演进

22. Embedding 模型换维度后如何不停机迁移？
23. 文档更新时如何保证 MySQL、MinIO 和 ES 最终一致？
24. Rerank 或 LLM 超时如何降级？
25. P95 延迟主要花在哪一段，如何用 Trace 证明？
26. 长上下文模型出现后为什么仍需要 RAG？
27. 什么时候应该使用 GraphRAG，什么时候不应该？
28. RAG、Memory、微调和 Agent 的边界是什么？

### 金融场景专项

29. 如何区分合并报表和母公司报表，为什么不能直接混合召回？
30. 多级表头、合并单元格和跨页表格如何恢复成可查询结构？
31. “万元”和“元”、括号负数、百分数如何标准化并避免计算错误？
32. 跨年度比较时怎样确保指标定义、会计政策和统计口径一致？
33. 为什么财务计算不能直接依赖 LLM？计算计划如何校验和执行？
34. 年报与更正公告冲突时应该引用哪个版本，如何支持审计追溯？
35. 如何证明回答中的页码、表格行和数值真正支持最终结论？
36. 为什么本项目是年报尽调平台，而不是投资建议或股价预测系统？

## 11. 最终简历描述模板

> 面向上市公司年报尽调与财务指标核验场景，设计布局感知的财务报告解析管线，恢复多级表头、跨页表格、单位、合并/母公司口径及脚注关系，并基于文档版本和内容哈希实现更正公告与年报的增量索引；构建 `FinancialFact` 结构化事实索引和带 ACL 预过滤的 BM25 + KNN 双路文本召回，通过 RRF 与 Cross-Encoder 精排完成风险信息检索。针对占比、同比和跨期比较设计确定性计算引擎及数字—引用双重校验，避免 LLM 心算和错误口径；建立覆盖事实、表格、计算、跨期、风险、无答案和权限场景的金融评测集，相较 Baseline 将 Fact Recall@5、Execution Accuracy、引用页码准确率分别提升至 `X/Y/Z`，检索 P95 为 `N ms`。

所有 `X/Y/Z/N` 必须由仓库内可复现报告生成。没有实验数据时宁可不写数字，也不要使用模拟测试或主观体验包装结果。
