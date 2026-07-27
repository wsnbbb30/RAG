# RAG 面试驱动的二开路线

> 调研快照：2026-07-27。问题优先采用牛客真实面经；招聘 JD 和论文仅用于交叉验证工程方向，不冒充面试原题。

## 1. 真实面试问题样本

### 字节跳动

- RAG 的召回、排序链路如何设计？Embedding 召回和粗排有哪些方案？
- Prompt Engineering 的效果如何评估？数据 Pipeline 是什么形式？
- 使用哪个 Embedding 模型，为什么选它？误判如何发现和修复？
- 知识的来源、清洗、打标、上线、更新和清退如何形成完整生命周期？
- 置信度和意图识别如何组合并做分级路由？
- 多模态模型误召回时，如何区分输入质量、解析、召回和生成阶段的问题？
- 固定 Token 切片为什么会破坏法律条文、表格或章节语义？如何做结构化父子切片？

来源：

- [4 轮拿下字节 Offer：LLM 面试题合集](https://www.nowcoder.com/discuss/746382064101908480)
- [字节机审策略与工具运营一面](https://www.nowcoder.com/discuss/857726328517169152)
- [字节 AI 应用岗面试复盘](https://www.nowcoder.com/discuss/882634966025175040)

### 百度

- 完整 RAG 流程是什么？
- Retriever 模型如何使用和选择？
- Rerank 如何融合多路召回结果？
- 如何评估大模型基座的推理能力，并区分检索问题和生成问题？

来源：

- [百度提前批大模型算法二面](https://www.nowcoder.com/feed/main/detail/88bd58fecce848559c6a15428cf07371)

### 阿里 / 阿里云

- RAG 全链路是什么？多路召回如何实现？
- 除 MRR 外，还有哪些检索评价指标？
- 为什么使用 Elasticsearch？数据量、分片和集群高可用如何设计？
- 文本切块大小和重叠长度如何选择？
- 如何做 Query Rewrite、上下文记忆和检索质量优化？
- 是否自己构建知识库与评测集做过对比实验？
- 一次回答的端到端耗时是多少？每个阶段分别耗时多少？
- RAG 和微调如何选择？如何处理数据隐私？
- 一个 AI 应用从哪些维度评估？

来源：

- [阿里云 AI 平台研发一面](https://www.nowcoder.com/feed/main/detail/7fa8ae2f1c2e42909e664dce6cbc8190)
- [阿里 4.9 一面](https://www.nowcoder.com/feed/main/detail/82df44bd2b44458d90c9c18921254b2c)
- [阿里国际研发工程师 Java 二面](https://www.nowcoder.com/feed/main/detail/cc4c23cdb5824ec3898ffa1a38c217cf)
- [阿里国际 AI 应用开发一面](https://www.nowcoder.com/discuss/872069472310288384)

### 岗位需求交叉验证

阿里 RAG 引擎岗位明确要求多模态数据检索、Embedding 与索引优化、稀疏和稠密混合检索、端到端评估及 A/B 实验。这说明“多模态解析 + 可量化评测 + 检索精排”比单纯再接一个聊天模型更有区分度。

- [阿里巴巴 RAG 引擎研发工程师岗位](https://www.nowcoder.com/jobs/detail/439513)

## 2. 当前代码审计结论

| 维度 | 当前实现 | 面试风险 / 缺口 |
| --- | --- | --- |
| 文档解析 | Apache Tika `BodyContentHandler` 提取纯文本；按段落、句子、HanLP 分词切成约 512 字符块 | 图片、表格、版面、标题层级、页码、坐标全部丢失；没有 OCR、Chunk overlap 和真正的父子索引 |
| 向量化 | 单一 DashScope `text-embedding-v4`，固定 2048 维 | Provider、模型和维度与索引强耦合；模型升级没有索引版本迁移方案；代码中的 `modelVersion` 仍写成 `deepseek-embed` |
| 混合检索 | Elasticsearch 顶层 KNN + `match`，随后 BM25 rescore | 不是两个独立召回列表的融合；没有 RRF、Cross-Encoder Rerank、Query Rewrite、去重和多样性控制 |
| 权限过滤 | 关键词查询中带用户、公开、组织标签条件 | **P0 安全问题：顶层 `knn` 与 `query` 在 Elasticsearch 中按 OR 融合，而 ACL 没放进 `knn.filter`，向量分支可能返回未授权文档** |
| 置信度 | 固定取 Top 5，直接拼接 | 未实现用户描述中的“置信度阈值过滤”；低相关结果仍可能进入 Prompt |
| 上下文 | 每段机械截断 300 字符，最多 5 段 | 可能截断事实、表格和句子；无 Token 预算、父块回填、邻接块扩展、引用定位和上下文压缩 |
| 生成模型 | `DeepSeekClient` 单实现 | 不支持多 Provider、能力路由、降级、熔断、限流、成本统计和灰度 |
| 评测 | 没有 RAG 数据集、指标或回归流水线 | 无法回答“优化了多少、为什么选这个参数”；现有性能测试只是按假定网络延迟做算术模拟 |
| 可观测性 | 业务日志 | 没有 Parse / Embed / Recall / Rerank / LLM 分阶段 Trace、P50/P95、Token 和成本指标 |
| 消息队列 | 实际为 Kafka | 简历描述写 RocketMQ 会与代码直接冲突，必须统一 |

安全结论的依据：Elastic 官方说明顶层多个 `knn` 和 `query` 结果按 disjunction（OR）组合；需要把元数据约束写进 `knn.filter` 才能限制 KNN 候选集：

- [Elasticsearch：kNN search 与 query 的融合及 filtered kNN](https://www.elastic.co/guide/en/elasticsearch/reference/8.18/knn-search.html)

## 3. 最值得做的三个项目亮点

### 亮点一：布局感知的多模态文档解析

目标不是“调用一次 OCR”，而是建立统一的结构化文档中间表示：

```text
Document
  └─ Page
      ├─ Heading(path, level, bbox)
      ├─ Paragraph(text, bbox)
      ├─ Table(markdown, cells, bbox)
      ├─ Image(ocr, caption, bbox)
      └─ Formula(latex, bbox)
```

实施要点：

1. 为 PDF、Word、PPT、Excel、HTML 建立 `DocumentParser` SPI，按文件类型路由。
2. 文本保留 `pageNo / headingPath / elementType / bbox / parentId` 元数据。
3. 表格同时保存 Markdown（给 LLM）和结构化 Cell JSON（精确查询）。
4. 扫描 PDF 和图片走 OCR；图片增加 Caption，保留与正文的邻接关系。
5. 采用标题感知父子 Chunk：子块用于召回，父块或相邻元素用于生成。
6. 增加解析质量指标：元素召回率、表格单元格准确率、OCR 字符错误率、解析耗时和失败率。

进阶方案可做成对照实验：传统 OCR/文本管线 vs. 页面图像多向量检索。ColPali 论文指出视觉丰富文档中的表格、图形和布局信息会被纯文本检索遗漏，并提出直接对页面图像建立多向量索引：

- [ColPali: Efficient Document Retrieval with Vision Language Models](https://arxiv.org/abs/2407.01449)

### 亮点二：评测驱动的多阶段检索

推荐链路：

```text
Query
  → 意图识别 / Query Rewrite
  → BM25 召回 ─┐
                ├→ RRF 融合 → 去重 → Cross-Encoder Rerank
  → KNN 召回  ─┘
  → 置信度校准 / 无答案判定
  → 父块回填与 Token Budget
  → LLM + 可核验引用
```

关键改动：

1. ACL 条件同时下推到 BM25 和 `knn.filter`，先修复越权风险。
2. BM25 与向量检索独立召回，使用 RRF 融合，避免不同分数空间直接相加。
3. 增加可插拔 `RerankModel`，对融合候选做精排。
4. 用标注集校准阈值，支持“证据不足则拒答”，不要写死某个 `_score`。
5. 支持 Query Rewrite、HyDE、Metadata Filter 作为可开关实验项，不一次性堆满链路。

评测分三层：

- 检索：Recall@K、Precision@K、MRR、nDCG@K、ACL 泄漏率。
- 生成：Faithfulness、Answer Relevance、Context Precision / Recall、引用正确率、拒答准确率。
- 系统：P50/P95/P99 延迟、吞吐、Token、单问成本、失败率。

评测集至少包含：事实题、多跳题、表格题、图片题、时效题、无答案题、权限隔离题和对抗 Prompt 题。每次调整 Chunk、Embedding、TopK、融合权重或模型都产出可对比报告，并设置回归阈值。

可参考：

- [RAGAS：RAG 自动评估论文](https://aclanthology.org/2024.eacl-demo.16.pdf)
- [RAGChecker：检索与生成模块的细粒度诊断](https://arxiv.org/abs/2408.08067)

### 亮点三：多模型 Provider 与自适应路由

先抽象能力，再接模型：

```java
interface ChatModel {}
interface EmbeddingModel {}
interface RerankModel {}
interface VisionModel {}
```

实施要点：

1. 建立 `ModelProvider` 注册表，支持 DeepSeek、Qwen、Ollama 等 OpenAI-compatible 或专用适配器。
2. 模型配置包含 Provider、模型名、维度、超时、重试、限流、价格、能力标签和版本。
3. 根据任务路由：普通问答走低成本模型，复杂推理走强模型，图片/表格走 VLM。
4. 实现超时降级、熔断、Fallback、租户级配额和敏感数据的本地模型路由。
5. Embedding 维度变化时创建新索引，通过 alias 双写、回填、切换，不能在原索引直接混用。
6. 用同一评测集输出“效果—延迟—成本”Pareto 对比，模型选型才有可解释依据。

## 4. 建议开发顺序

### P0：先让现有项目可信

1. 修复 KNN 权限过滤和 `isPublic/public` 字段命名不一致。
2. 补 ACL 自动化测试：私有、公开、同组织、父子组织、越权五类用例。
3. 建立最小评测数据格式和检索指标，记录当前 Baseline。
4. 统一简历与代码：当前使用 Kafka，不是 RocketMQ；删除没有实测证据的性能数字。
5. 将仓库中的数据库、MinIO、JWT、管理员和 Elasticsearch 凭据改为环境变量。

### P1：形成第一条可量化亮点

1. 独立 BM25/KNN 召回 + RRF。
2. 接入 Reranker。
3. 加入置信度校准和无答案拒答。
4. 输出 Baseline、RRF、RRF+Rerank 三组 Recall@K / MRR / nDCG / P95 对照。

### P2：形成多模态亮点

1. 统一文档中间表示。
2. PDF/Word 标题、页码、表格解析。
3. OCR 与图片 Caption。
4. 表格题、图片题专项评测。

### P3：形成平台化亮点

1. 多模型 Provider 抽象。
2. 模型路由、降级与成本追踪。
3. 离线评测 + 在线反馈 + A/B / 灰度闭环。
4. 新前端只做评测看板、链路 Trace、知识管理和模型配置，不重复堆普通 CRUD 页面。

## 5. 面试时可量化的最终交付

完成后，项目描述应以实测数据为主，例如：

> 构建布局感知的多模态解析管线，保留标题层级、页码、表格与图片元数据；设计 BM25 + KNN 双路召回、RRF 融合和 Cross-Encoder 精排，并建立覆盖文本、表格、图片、无答案与权限场景的评测集。相较 Baseline，Recall@5、nDCG@5 和引用正确率分别提升 X%、Y%、Z%，P95 延迟控制在 N ms；通过 Provider SPI 支持多种 Chat / Embedding / Rerank 模型，并基于效果、延迟和成本进行动态路由。

其中所有 `X/Y/Z/N` 必须来自可复现的评测报告，不能先写结论再补实验。
