# CLAUDE.md

> 本文件是本项目后续由 AI 助手或开发者继续开发时必须先读的全局规则。项目事实以当前仓库代码为准；需求目标来自三份文档：《基于AI Agent的智慧学习辅助系统项目需求》《基于AI Agent的智慧学习辅助系统技术实现方案》《需求说明文档》。

## 项目定位

本项目是“基于 AI Agent 的智慧学习辅助系统”，面向中小学及高校通用学习场景，目标是形成“感知-决策-执行-迭代”的学习辅助闭环。

当前仓库采用三段式架构：

- `frontend/`：Vue 3 + Vite + Vue Router + Pinia + Element Plus，包含学生端与管理端页面。
- `backend/`：Spring Boot 3.3.8 + Java 17 + Spring Security + MyBatis-Plus + MySQL + Redis 配置，统一后端入口为 `/api`。
- `ai/`：FastAPI + LangChain 消息模型 + OpenAI 兼容 API + PaddleOCR，提供文本答疑、图片 OCR 答疑、语音答疑承接、主观题评分、学习路径生成等 AI 能力。当前是单个学习 QA Agent 加工具/多端点能力，不是多个独立 Agent 编排。

需求文档定义的 10 个核心模块是：学习画像、AI 答疑、学习方案推送、错题闭环、进度监控提醒、学习资源、测评分析、多端交互、数据安全、系统稳定迭代。

## 行为准则

- 先读代码，再下结论。禁止只根据需求文档、文件名或上次记忆判断当前实现状态。
- 区分“需求目标”和“当前实现”。例如 AES 全量加密、HTTPS 部署、千人并发、模型微调、完整 ASR、自动告警等属于目标或方案，当前代码未落地时必须标为待实现或待验证。
- 当前工作区可读写 `D:\zuoye\dasanxia\xiaoxueqi\smart-learning-system`。常规项目读写应直接执行；涉及仓库外路径、网络、破坏性操作时按权限规则处理。
- 如果 PowerShell 显示中文乱码，不等于文件不存在或内容损坏。优先用 `Get-Content -Encoding UTF8`、Python OOXML 读取、编辑器确认或二进制方式核验。
- 任何接口变更都要同步前端 API 封装、页面调用、`docs/wiki.md` 和必要测试说明。
- 涉及用户数据、答疑内容、学习记录、错题、画像时，按隐私数据处理，不在日志或文档中暴露真实样本。

## 代码风格

### 后端 Java

- Controller 统一返回 `Result<T>`，分页返回 `PageVO<T>`。
- Controller 只处理入参、鉴权上下文、响应封装；复杂业务放入 Service。
- Entity 字段与 `docs/sql/01_schema.sql` 保持一致，使用 MyBatis-Plus 注解。
- Service 接口优先继承 `IService<T>`，实现类优先继承 `ServiceImpl<M, T>`。
- 业务错误使用 `BusinessException` + `Constants`，由 `GlobalExceptionHandler` 统一处理。
- 当前登录用户使用 `SecurityUtils.currentUserId()` 获取，不信任前端传入的 `userId`。
- 管理端接口放在 `/admin/**`；学生端学习接口按模块放在 `/qa`、`/study-plans`、`/wrong-questions`、`/assessments`、`/study-records`、`/personal-data` 等路径。
- 密码必须使用 `PasswordEncoder`/BCrypt，禁止新增 MD5、明文或可逆密码存储。

### 前端 Vue

- API 请求集中在 `frontend/src/api/**`，页面不要散落硬编码接口路径。
- 路由鉴权以 `frontend/src/router/index.js` 为准：登录页为 guest，学生端需要 `requiresAuth`，管理端需要 `requiresAdmin`。
- 当前唯一权威登录状态源是 `frontend/src/store/auth.js`；禁止再新增 `frontend/src/stores/auth.js` 这类并行状态源。
- 页面文案、状态值、错误提示要与后端业务口径一致，避免前后端各自定义枚举。

### AI 服务 Python

- FastAPI 入口是 `ai/main.py`。
- AI 服务对后端暴露 `/health`、`/qa/text`、`/qa/image`、`/qa/voice`、`/assessment/subjective-score`、`/study-plan/path`。
- 当前语音答疑主要依赖前端 Web Speech API 识别后的文本，后端/AI 服务承接音频保存和文本答疑；不要写成已经内置完整离线 ASR。
- AI 服务统一调用 OpenAI 兼容 API，答疑和学习路径生成都走同一套外部 API 配置。
- 模型配置通过 `EXTERNAL_LLM_BASE_URL`、`EXTERNAL_LLM_MODEL`、`EXTERNAL_LLM_API_KEY` 或本地私有 `auth.json` 读取，禁止提交真实密钥。默认模型为 `gpt-4o-mini`；可通过环境变量替换为任意兼容供应商支持的模型。

## 测试要求

- 后端改动后至少运行：`cd backend; .\mvnw.cmd test`。
- 前端改动后至少运行：`cd frontend; npm run build`。
- AI 服务改动后至少运行：`py -3.12 -m compileall -q ai`，并尽量启动验证 `GET /health`。
- 涉及接口契约变化时，核对前端 `frontend/src/api/**` 与后端 Controller 双向一致。
- 涉及数据库字段变化时，同步更新 Entity、SQL、种子数据和文档。
- 高风险功能必须补测试或明确验证说明：登录鉴权、个人数据清空/导出、错题自动收集、测评评分、AI 降级、学习计划动态调整。

## 禁止事项

- 禁止提交 `auth.json`、`.env`、真实密钥、真实用户数据、日志文件、构建产物。
- 禁止在 Controller 中写大量业务规则或直接拼接复杂查询。
- 禁止绕过 `SecurityConfig` 新增未鉴权的学习数据接口。
- 禁止把 AI 返回结果直接当作可信事实覆盖关键数据；必须保留后端校验、兜底或人工复核路径。
- 禁止把需求文档中的未实现能力写成“已完成”，尤其是 AES 全量加密、HTTPS 部署、千人并发、模型微调、自动告警、完整 ASR。
- 禁止为了解决乱码而无差别重编码整个仓库；先定位具体文件和影响范围。
- 禁止删除用户现有未跟踪文件或工作区改动，除非用户明确要求。

## 输出标准

## 文档结构

- `docs/CLAUDE.md`：全局规则、开发规范、行为准则、代码风格、测试要求、禁止事项、输出标准。
- `docs/Memory.md`：当前进度、已确认结论、用户偏好、代办事项、下次入口。
- `docs/learning.md`：bug 复盘、踩坑记录、错误用法、反直觉结论、优化建议。
- `docs/wiki.md`：项目背景、API 说明、业务口径、目录结构、常见术语、部署与数据种子口径。
- 不再新增独立说明类 Markdown；新信息按以上职责合并到四份核心文档。`docs/sql/` 下 SQL 和生成脚本属于可执行数据资产，可以保留。

- 文档输出使用中文，必要时保留英文技术名词。
- 实现状态统一使用：`已实现`、`部分实现`、`待实现`、`待验证`。
- API 文档以真实 Controller 路径为准；不能确认的接口标 `待确认`。
- 交付说明包含修改文件、验证命令和未验证原因。
- 新增 Markdown 保持结构清晰，优先写后续开发能直接执行的规则。

## 2026-07-18 更新：AI 服务可观测性与主观题复核

- AI 调用必须通过 `backend/module/qa/service/AiService.java` 统一封装，不要在业务代码里直接拼 `RestTemplate` 调 FastAPI。
- AI 调用日志只能记录 operation、endpoint、latencyMs、provider、model、fallback、failureCategory、errorCode，不记录题干、学生答案、对话内容、token 或文件原文。
- 主观题 AI 评分只要出现 fallback、failureCategory、errorCode 或低置信度，必须进入 `score_status=2` 待人工复核，不能作为可靠自动评分直接闭环。
- `scoring_detail` 可以保存 AI 评分摘要与 `aiObservation`，但应保持短文本，不把完整学生答案或提示词写入审计/日志。
- 主观题复核演示数据已汇总在 `docs/sql/02_seed_data.sql`；生产环境按需选择数据脚本，不要导入演示数据。
