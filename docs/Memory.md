# Memory.md

> 目的：记录当前项目真实进度、已确认结论、用户偏好、代办事项和下次入口，方便下一次继续开发时不用重新摸索。

## 当前进度

### 已阅读范围

- 当前仓库：`D:\zuoye\dasanxia\xiaoxueqi\smart-learning-system`
- 记忆库：`D:\obsidian\codex长期记忆`
- 项目需求：`D:\zuoye\dasanxia\xiaoxueqi\基于AI Agent的智慧学习辅助系统项目需求.docx`
- 技术方案：`D:\zuoye\dasanxia\xiaoxueqi\基于AI Agent的智慧学习辅助系统技术实现方案.docx`
- 需求说明：`D:\zuoye\dasanxia\xiaoxueqi\需求说明文档.docx`
- 当前代码入口：`README.md`、`backend/`、`frontend/`、`ai/`、`docs/sql/01_schema.sql`、`docs/sql/02_seed_data.sql`

### 当前实现概览

- `backend/`：Spring Boot 后端按模块拆分，包含认证、用户、画像、答疑、错题、学习计划、资源、测评、学习记录、个人数据、管理后台、系统运维等包。
- `frontend/`：Vue 学生端和管理端页面已搭建，覆盖登录、首页、AI 答疑、画像、错题、计划、资源、测评、管理后台等入口；学生端已补项目图标、项目名、PC/平板/手机响应式、学科化画像和资源具体链接。
- `ai/`：FastAPI 服务提供健康检查、文本答疑、图片 OCR 答疑、语音答疑承接、主观题评分、学习路径生成接口；统一通过 OpenAI 兼容 API 调用，默认模型为 `gpt-4o-mini`。调用侧具备有限重试、并发上限、熔断、格式校验和规则兜底。
- `docs/sql/01_schema.sql`：包含用户、画像、资源、学习计划、题库、学习记录、错题、测评、任务、画像修正、答疑会话/消息、个人数据清空审计等核心表；`docs/sql/02_seed_data.sql` 包含统一演示数据。

### 模块状态判断

| 模块 | 当前状态 | 说明 |
| --- | --- | --- |
| 用户注册登录/JWT 鉴权 | 已实现 | 后端认证、密码 BCrypt、JWT 过滤器、前端登录入口存在 |
| 用户信息管理 | 已实现 | `/users/info`、`/users/password` 与前端资料页存在 |
| 学情画像 | 部分实现 | 有画像接口、指标、弱点、修正日志和刷新逻辑；学生端已改为按学科展示画像和 AI 建议；画像算法仍偏规则聚合 |
| AI 答疑 | 部分实现 | 文本/图片/语音入口齐全，会话持久化存在；语音主要依赖浏览器识别文本，不是完整离线 ASR |
| 学习计划 | 部分实现 | 计划、每日任务、目标计划、学习路径和动态调整接口存在；AI 不可用时有规则兜底 |
| 错题管理 | 部分实现 | 错题列表、收集、统计、导出、相似题、复习计划存在；智能分类和二次推送仍可增强 |
| 学习资源 | 部分实现 | 学生端查询、搜索、分类，管理端上传/编辑/上下架存在；学生端会把官方平台笼统入口转换为“初中+学科”具体搜索页；资源智能更新仍是目标能力 |
| 测评中心 | 部分实现 | 创建、提交、报告、趋势、主观题复核存在；学生端创建测评已改为按年级、学科和知识范围实时 AI 组卷，后台题库管理保留；AI 评分稳定性和人工复核链路仍需加强 |
| 学习记录/进度提醒 | 部分实现 | 有新增记录、时长统计、真实进度报告和学习提醒；提醒目前基于任务、错题复习、学习时长和测评表现规则生成 |
| 个人数据管理 | 部分实现 | 有概览、ZIP 导出、短期签名下载、导出审计、过期清理、清空审计与二次确认；个人中心头像上传当前为前端本地预览，画像材料上传用于本地读取薄弱点 |
| 多端交互 | 部分实现 | 学生端主要页面已补 PC/平板/手机响应式布局；仍需浏览器真机截图回归验证 |
| 系统运维 | 部分实现 | 管理端系统状态、日志、故障、备份接口存在；自动告警、高可用、真实备份待验证 |
| 数据安全 | 部分实现 | 已有 JWT、BCrypt、角色鉴权；个人数据导出和清空具备基础能力；AES 全量加密、HTTPS、日志脱敏和文件目录权限仍需按部署要求继续增强 |

## 已确认结论
- 2026-07-23 测评生成口径更新：学生端新建测评不再依赖题库库存，新增年级字段，后端调用 AI 服务 `/assessment/generate-paper` 按年级、学科、知识范围、难度和卷面分区实时生成本次专用题；生成题仍保存到 `question_bank` 并用内部测评标记绑定 `assessmentId`，后台题库录入/导入/维护功能保留。
- 2026-07-21 本轮更新：语音转文字前端增加识别错误提示、手动输入兜底和按学科切换识别语言；英语学科使用 `en-US`，其他学科使用 `zh-CN`。题库生成器已重写为初中试卷风格，每科 240 题、共 2160 题，英语题干/选项为英文。
- 2026-07-21 标准机考试卷更新：测评组卷已按九科发布版卷面规则组织，语文/数学/英语总分 120 分，其余学科总分 100 分；后端按学科板块返回题目、分值和板块说明，前端答题页按标准试卷板块展示。下一步可把题目来源从题库扩展为“AI Agent 按学科 + 知识点 + 卷面规则生成试卷，题库仅作兜底/缓存”。
- 2026-07-21 本轮排错确认：IntelliJ 直接启动后端时出现 `Access denied for user 'root'@'localhost' (using password: NO)`，原因是 `application.yml` 的 `DB_PASSWORD` 默认值为空；已改为本地默认 `root`，与 `docs/wiki.md` 记录的 `root/root` 对齐。服务器部署仍必须通过环境变量设置真实 `DB_PASSWORD`。

- 项目不是单一答疑工具，而是围绕学习画像、资源、计划、错题、测评联动的学习闭环系统。
- 学生端项目名统一为“基于 AI Agent 的智慧学习辅助系统”或短名“智慧学习辅助系统”，项目图标为原创打开书本 SVG，不使用带水印外部生成图。
- 后端统一上下文路径是 `/api`，默认端口 `8080`。
- 前端 Vite 默认端口是 `5173`，开发代理指向后端 `/api`。
- AI 服务默认端口是 `8000`，后端通过 `ai.service.base-url` 调用。
- 当前不是多个独立 Agent 协同编排，而是单个学习 QA Agent：内部按场景承接文本答疑、OCR 后答疑、语音文本答疑、主观题评分和学习路径生成，并使用 `math_calculate`、`ocr_recognize` 工具。
- AI 服务部署使用 OpenAI 兼容 API：`EXTERNAL_LLM_BASE_URL` 指向兼容接口，`EXTERNAL_LLM_MODEL` 默认 `gpt-4o-mini`，`EXTERNAL_LLM_API_KEY` 只在服务器环境变量或私有 `auth.json` 中配置。
- 数据库名是 `smart_learning_system`，初始化脚本是 `docs/sql/01_schema.sql`，演示数据脚本是 `docs/sql/02_seed_data.sql`。
- 角色口径：数据库 `role=1` 为学生，`role=2` 为管理员；Spring Security 使用 `ROLE_STUDENT`、`ROLE_ADMIN`。
- 后端接口文档入口：后端启动后访问 `/api/doc.html`。
- `PersonalDataController.export()` 已生成 `exports/personal-data/personal-data-{userId}-{timestamp}.zip`，写入 `personal_data_export_log`，返回带 `token` 的 `/api/personal-data/export-files/{fileName}?token=...` 短期下载地址。
- 个人数据导出下载策略：token 只明文返回一次，数据库仅保存 SHA-256 hash；下载链接有效期 24 小时，最多下载 3 次；下载成功会递增 `download_count` 并记录 `last_download_time`。
- 过期导出文件由 `PersonalDataExportCleanupTask` 定时清理，同时生成新导出时也会触发一次过期清理；审计记录保留，过期文件会删除。
- `PersonalDataController.clear()` 已迁移到 `PersonalDataClearLogService`：请求必须携带正确密码和 `confirmText`，确认文本为 `CLEAR_PERSONAL_DATA` 或 `清空个人数据`。
- 个人数据清空范围：画像、画像修正日志、学习计划、任务、学习记录、错题、错题复习计划、测评、测评答案、答疑会话、答疑消息、个人导出文件、答疑音频文件。
- 个人数据清空保留范围：账号、密码、题库、学习资源、清空审计日志。数据库删除由 `@Transactional(rollbackFor = Exception.class)` 包裹，审计写入 `personal_data_clear_log`。
- `StudyRecordController.progressReport()` 已由 `StudyProgressService` 聚合学习记录、任务、测评和错题，返回周期摘要、每日趋势、测评趋势、薄弱点和建议。
- `StudyRecordController.reminders()` 已按逾期任务、今日任务、错题复习、近 7 天学习时长和近 30 天测评均分生成学习提醒。
- 前端登录状态唯一来源是 `src/store/auth.js`；重复的 `src/stores/auth.js` 转发壳已删除。
- 学生端资源链接策略：使用 `https://basic.smartedu.cn/search?keyword=初中{学科}` 的具体搜索页；旧资源若仍保存 `/tchMaterial`、`/syncClassroom` 或平台首页，学生端打开时会自动转换。
- 编码巡检未发现 README、docs、前端和后端中存在典型 mojibake 字符；前端集中中文转义已小批量修复 `utils/format.js`、登录页文案和 auth 默认显示名。

## 用户偏好

- 用户希望助手以资深开发工程师身份主动解决问题，不只给建议。
- 文档优先中文，内容要能指导后续开发，不要泛泛模板。
- 用户给出了 Shell 和文件读写权限，能执行就直接执行；不要反复误报“无法访问文件系统”或“没有 Shell 工具”。
- 用户关注“下次从哪里继续”，因此 `Memory.md` 必须写清入口、状态和待办。
- 对已实现和未实现内容要诚实区分，避免把需求目标说成当前能力。
- 用户的长期记忆库在 `D:\obsidian\codex长期记忆`，如后续需要沉淀跨项目偏好，可先读该库入口文档再写入。

## 代办事项

- [x] 实现个人数据真实导出：汇总画像、学习记录、错题、测评、计划、任务、答疑摘要等数据，生成 ZIP 文件，内含 `personal-data.json` 与 `personal-data.md`，并返回有效下载地址。
- [x] 完善个人数据清空：明确清空答疑会话、画像修正日志和任务数据；增加密码校验、二次确认、审计记录和数据库事务边界。
- [x] 为个人数据导出增加审计记录、过期清理、下载次数限制和短期签名 URL。
- [x] 为个人数据导出/清空审计增加前端展示页面和管理员审计入口。
- [x] 继续补齐 F5 进度监控：学习进度报告和提醒系统。
- [x] 完善学习进度报告：按日/周/月统计学习时长、任务完成率、测评趋势、错题变化和建议。
- [x] 完善提醒系统：基于任务截止时间、进度偏差、错题复习和测评表现生成提醒，并在学习首页展示。
- [x] 清理前端重复 auth store，只保留 `frontend/src/store/auth.js` 一个权威状态源。
- [x] 系统性处理中文乱码：已巡检典型 mojibake，确认 README/docs 正常，修复前端集中中文转义和学习记录 Swagger Tag。
- [x] 明确数据安全实现方案：密码已 BCrypt；HTTPS/AES/日志脱敏/文件目录权限已在 `docs/wiki.md` 写清代码实现与部署边界。
- [x] 给 AI 服务补充可观测性：超时、降级、调用日志、模型信息、错误码、失败分类。
- [x] 为主观题 AI 评分增加更稳定的人工复核链路和测试数据。
- [x] 为核心接口补集成测试，尤其是测评提交自动生成错题、学习任务完成生成学习记录、AI 服务不可用降级。（当前为控制器/服务边界行为测试；如需真实 MySQL/Redis 集成测试，下一步引入 Testcontainers）
- [x] 补充部署说明：MySQL、Redis、后端、AI 服务、前端启动顺序和环境变量，当前统一沉淀在 `docs/wiki.md`。
- [x] 学生端界面更新：项目图标和项目名、三端响应式、资源具体链接、个人中心上传、学科化画像和 AI 建议，当前统一沉淀在 `docs/wiki.md`。

## 下次入口

优先从以下入口继续：

1. `docs/CLAUDE.md`
   - 先读全局规则，确认“需求目标”和“当前实现”不要混写。
2. `docs/wiki.md`
   - 项目背景、API、业务口径、部署、数据种子和学生端 UI 口径统一入口。
3. `frontend/src/views/student/profile/index.vue`
   - 学科化数字画像入口；后续若后端提供结构化 subject 字段，应替换前端文本推断逻辑。
4. `frontend/src/utils/resourceLinks.js`
   - 官方资源具体学科搜索页映射；后续如改资源平台链接先改这里。
5. `backend/src/main/java/com/smartlearning/backend/module/personal/controller/PersonalDataController.java`
   - 个人数据导出、清空、学生审计页和管理员审计入口已完成；后续只需按安全要求继续增强管理员审计筛选或导出。
6. `backend/src/main/java/com/smartlearning/backend/module/record/controller/StudyRecordController.java`
   - 进度报告和提醒已接入真实聚合；后续可继续加提醒已读状态、消息推送或单独提醒表。
7. `frontend/src/store/auth.js`
   - 当前是唯一登录状态源；后续新增登录态能力只改这里，不再创建并行 `stores/auth.js`。
8. `docs/learning.md`
   - 每次修 bug 或踩坑后，同步更新复盘、错误用法和优化建议。
9. `backend/src/main/java/com/smartlearning/backend/module/assessment/controller/AssessmentController.java`
   - 测评实时 AI 组卷入口；后续若要调整题量、卷面分区或生成题绑定策略，从这里开始。
10. `ai/services/agent/qa_agent.py`
   - AI 试卷生成 prompt、JSON 规范化和兜底生成器入口。

## 2026-07-18 进度补充：AI 可观测性与主观题复核

### 已完成

- `AiService` 已改为统一 AI 调用封装，覆盖 JSON 和 multipart 调用，记录 operation、endpoint、latencyMs、provider、model、fallback、failureCategory、errorCode、errorMessage。
- 后端 AI 调用失败分类已覆盖 timeout、connection、http_error、invalid_response、ai_service_error、call_error。
- AI 服务主观题评分返回已补充 provider、fallback、failureCategory、errorCode；LLM 调用异常和模型 JSON 解析失败会区分为 `call_error` 与 `invalid_response`。
- 主观题评分链路已避免将 AI fallback/失败结果作为可靠自动评分，相关答案会走待人工复核状态。
- 新增 `backend/src/test/java/com/smartlearning/backend/module/qa/service/AiServiceTests.java`，覆盖成功元数据透传、AI 服务错误分类、异常业务响应分类。
- 主观题复核演示数据已汇总到 `docs/sql/02_seed_data.sql`。

### 验证结果

- 已通过：`cd backend; .\mvnw.cmd test`，结果 `Tests run: 5, Failures: 0, Errors: 0`。
- 已通过：`python` AST 解析检查 `ai/main.py` 与 `ai/services/agent/qa_agent.py`。
- 未执行：前端 build，本轮未修改前端页面。

### 下次入口

1. 如果继续做 AI 运维展示，优先在管理员端增加 AI 调用摘要页，而不是先建复杂日志表。
2. 如果继续强化主观题人工复核，下一步应设计管理员/教师复核入口，目前现有复核接口仍是当前用户自己的测评答案路径。
3. 若要落库 AI 调用审计，再新增独立 `ai_call_log` 表，并明确不保存 prompt/answer 原文。

## 2026-07-18 实时进度：核心测试与部署说明

- [x] 为核心接口补行为测试：测评提交自动生成错题、学习任务完成生成学习记录、AI 服务不可用降级。
  - 新增 `AssessmentSubmitBehaviorTests`，覆盖测评提交后仅自动判错题进入错题收集。
  - 新增 `StudyPlanFinishTaskBehaviorTests`，覆盖任务完成后写入学习记录。
  - 扩展 `AiServiceTests`，覆盖连接失败和超时降级分类。
- [x] 补充部署说明：已合并到 `docs/wiki.md`，覆盖 MySQL、Redis、后端、AI 服务、前端启动顺序和环境变量。
- 验证结果：`cd backend; .\mvnw.cmd test` 通过，`Tests run: 9, Failures: 0, Errors: 0`。
- 下次入口：如果要把行为测试升级为真正数据库集成测试，先补 `test` profile + Testcontainers MySQL/Redis 或维护一份 H2 兼容 schema。

## 2026-07-19 进度补充：初中全科演示题库

- [x] 已将初中九科完整演示题库汇总到 `docs/sql/02_seed_data.sql`。
- [x] 英语题库已改为英文题干和英文选项，覆盖选择、完成句子、阅读与语言运用、简答和写作；不再用中文知识点判断题充当英语题。
- [x] 2026-07-21 按用户要求改为导入前直接替换九科演示题库：先删除九科旧题，再插入新版 2160 道标准试卷风格题；本地库已确认每科 240 道、旧 `【学科】...` 模板题为 0。
- 部署入口：腾讯云服务器执行 `docs/sql/01_schema.sql` 后，再执行 `docs/sql/02_seed_data.sql`。

## 2026-07-19 进度补充：数据库双环境配置

- [x] `backend/src/main/resources/application.yml` 已改为数据库环境变量方案：`DB_HOST`、`DB_PORT`、`DB_NAME`、`DB_USERNAME`、`DB_PASSWORD`。
- [x] 本地默认值：`localhost:3306/smart_learning_system`，账号密码 `root/root`，IntelliJ 不额外配置也能连接本机开发库。
- [x] 服务器部署不改 `application.yml`，在 FinalShell 启动前导出环境变量，例如 `DB_PASSWORD=123456`。
- [x] `docs/wiki.md` 已补 FinalShell / 腾讯云启动示例。
- 验证结果：`cd backend; .\mvnw.cmd test` 通过，`Tests run: 9, Failures: 0, Errors: 0`。

## 2026-07-19 进度补充：初中九科学习资源

- [x] 已将官方正版平台具体学科搜索页资源汇总到 `docs/sql/02_seed_data.sql`。
- [x] 已导入当前本机 MySQL：语文/英语/物理/化学/生物/历史/地理/道德与法治各 3 条资源，数学因原有演示数据共 6 条。
- [x] 学习资源种子覆盖口径、导入命令和链接原则已合并到 `docs/wiki.md`。
- [x] 已将学生端资源分类接口扩展为九科，并把前端 `SUBJECTS` 中的“政治”统一为“道德与法治”。
- 资源链接策略：使用 `https://basic.smartedu.cn/search?keyword=初中{学科}` 具体搜索页，不放未授权 PDF；学生端会把旧的官方平台笼统入口自动转换成具体学科搜索页。
- 验证结果：`cd backend; .\mvnw.cmd test` 通过，`Tests run: 9, Failures: 0, Errors: 0`。

## 2026-07-19 进度补充：学生端 UI 和画像改造

- [x] 登录页、学生端侧边栏、移动端顶栏和 favicon 已统一使用原创打开书本 SVG 图标。
- [x] 项目名已统一为“基于 AI Agent 的智慧学习辅助系统”/“智慧学习辅助系统”。
- [x] 学生端布局已补 PC、平板、手机三端适配：桌面侧边栏、手机顶部品牌栏和底部导航。
- [x] 首页 dashboard 已简化：移除大幅装饰图、今日任务、提醒、下一步和薄弱点卡片，仅保留学习时间、任务完成率、测评均分、错题掌握率和本周进度报告。
- [x] 资源库新增初中九科快捷入口，资源打开会跳转到 `https://basic.smartedu.cn/search?keyword=初中{学科}` 具体搜索页。
- [x] 个人中心新增头像上传本地预览、画像材料上传、本地读取薄弱点、学科化数字画像和 AI 建议入口；没有学习/测评/错题数据时，学科画像显示“待诊断/未评估”，不再把所有学科标成相同高优先级。
- [x] 学生端 UI 更新口径已合并到 `docs/wiki.md`。
- 注意：头像上传当前为前端本地预览，不是后端持久化上传；若要多设备同步，需要新增后端头像字段和上传接口。
