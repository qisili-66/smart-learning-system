# wiki.md

> 本 wiki 用于说明项目背景、API、业务口径、目录结构和常见术语。实现状态以当前仓库为准；需求目标来自三份 Word 文档。

## 项目背景

“基于 AI Agent 的智慧学习辅助系统”面向中小学及高校通用学习场景，目标是解决同质化学习资源推荐、日常答疑效率低、知识漏洞难闭环、学习进度不可见等问题。

需求文档将系统定义为围绕 AI Agent 的学习闭环：

1. 感知：采集学习时长、答题正确率、答题速度、错题类型、资源偏好、任务完成率等数据。
2. 决策：分析学习能力、知识掌握度、学习习惯、薄弱点和阶段目标。
3. 执行：输出答疑、资源推荐、每日任务、错题复盘、测评报告、进度提醒。
4. 迭代：基于学习行为和反馈持续优化画像、计划和 AI 输出质量。

当前项目采用前端、后端、AI 服务分离实现，覆盖学生端和管理端。

当前 AI 实现是单个学习 QA Agent 加工具/多端点能力：文本答疑、图片 OCR 后答疑、语音文本答疑、主观题评分和学习路径生成复用同一套 OpenAI 兼容 API 调用与业务兜底；并不是多个独立 Agent 的协同编排，默认模型为 `gpt-4o-mini`，可由环境变量替换。

## 系统模块

| 模块 | 需求目标 | 当前状态 |
| --- | --- | --- |
| 个性化学习画像 | 自动采集行为并生成画像，支持手动修正 | 部分实现：已有画像接口、指标、弱点、行为事件和修正日志 |
| AI Agent 答疑 | 文本、图片、语音，多轮上下文，启发式辅导 | 部分实现：三类入口存在；语音主要依赖前端识别文本 |
| 学习方案推送 | 基于画像和目标生成每日任务，动态调整 | 部分实现：已有计划、任务、AI 路径和规则兜底 |
| 错题闭环 | 自动采集、分类、复盘、导出、同类题推荐 | 部分实现：错题收集、统计、导出、相似题和复习计划存在 |
| 进度监控提醒 | 进度报告、异常识别、系统提醒 | 部分实现：已按学习记录、任务、测评、错题生成进度报告和提醒；提醒已读/推送仍待增强 |
| 学习资源 | 资源库、检索、分类、推荐、管理端维护 | 部分实现：查询/搜索/分类/后台维护存在；学生端已将官方平台笼统入口适配为初中学科具体搜索页；智能更新待增强 |
| 测评分析 | 组卷、提交、评分、报告、趋势、主观题评分 | 部分实现：测评流程存在，AI 评分和人工复核链路需继续强化 |
| 多端交互 | PC/平板/手机响应式、轻量操作 | 部分实现：学生端主要页面已补 PC/平板/手机布局，移动端使用顶部品牌栏和底部导航；仍需真机回归验证 |
| 数据安全 | 加密、鉴权、导出、清空、合规采集 | 部分实现：JWT/BCrypt/角色鉴权存在，个人数据 ZIP 导出已增加短期签名 URL、下载次数限制、导出审计和过期清理；清空已增加密码校验、二次确认、审计记录和事务边界；AES/HTTPS 待实现或部署验证 |
| 稳定运维 | 日志、自检、备份、模型迭代、高可用 | 部分实现：管理端系统接口存在，自动告警和真实备份待验证 |

## API 说明

### 基础约定

- 后端 Base URL：`http://127.0.0.1:8080/api`
- 后端接口文档：`http://127.0.0.1:8080/api/doc.html`
- AI 服务 Base URL：`http://127.0.0.1:8000`
- 认证方式：`Authorization: Bearer <token>`
- 后端统一响应：`Result<T>`，常见业务码见 `Constants`，包括 `200`、`400`、`401`、`403`、`404`、`409`、`500`

### 认证与用户

| 方法 | 路径 | 说明 | 权限 |
| --- | --- | --- | --- |
| POST | `/auth/register` | 注册 | 公开 |
| POST | `/auth/login` | 登录 | 公开 |
| POST | `/auth/refresh` | 刷新 Token | 公开 |
| POST | `/auth/logout` | 退出登录 | 登录用户 |
| GET | `/users/info` | 当前用户信息 | 学生/管理员 |
| PUT | `/users/info` | 修改个人信息 | 学生/管理员 |
| PUT | `/users/password` | 修改密码 | 学生/管理员 |

### 学情画像

| 方法 | 路径 | 说明 | 权限 |
| --- | --- | --- | --- |
| GET | `/user-profiles/my` | 获取我的画像 | 学生 |
| PUT | `/user-profiles/my` | 更新/修正画像 | 学生 |
| GET | `/user-profiles/weak-points` | 获取薄弱点 | 学生 |
| POST | `/user-profiles/refresh` | 刷新画像 | 学生 |
| GET | `/user-profiles/metrics` | 获取画像指标 | 学生 |
| POST | `/user-profiles/behavior-events` | 上报学习行为事件 | 学生 |
| PUT | `/user-profiles/corrections` | 提交画像修正 | 学生 |
| GET | `/user-profiles/correction-logs` | 修正记录 | 学生 |
| GET | `/user-profiles/service-summary` | 画像服务摘要 | 学生 |

### AI 答疑

| 方法 | 路径 | 说明 | 权限 |
| --- | --- | --- | --- |
| POST | `/qa/text` | 文本答疑 | 学生/管理员 |
| POST | `/qa/image` | 图片 OCR 答疑 | 学生/管理员 |
| POST | `/qa/voice` | 语音答疑承接 | 学生/管理员 |
| GET | `/qa/conversations` | 会话列表 | 学生/管理员 |
| GET | `/qa/conversations/{conversationId}` | 会话详情 | 学生/管理员 |
| DELETE | `/qa/conversations/{conversationId}` | 删除会话 | 学生/管理员 |
| GET | `/qa/evaluation` | 答疑质量评估 | 学生/管理员 |
| GET | `/qa/audio/{conversationId}/{fileName}` | 音频文件访问 | 学生/管理员 |

AI 服务内部接口：

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| GET | `/health` | AI 服务健康检查 |
| POST | `/qa/text` | 文本答疑 |
| POST | `/qa/image` | OCR 后答疑 |
| POST | `/qa/voice` | 使用识别文本进行答疑 |
| POST | `/assessment/subjective-score` | 主观题语义评分 |
| POST | `/assessment/generate-paper` | 按年级、学科、知识范围和卷面结构生成原创测评题 |
| POST | `/study-plan/path` | 生成学习路径 |

### 学习计划

| 方法 | 路径 | 说明 | 权限 |
| --- | --- | --- | --- |
| POST | `/study-plans` | 创建计划 | 学生 |
| GET | `/study-plans` | 计划列表 | 学生 |
| GET | `/study-plans/{planId}` | 计划详情 | 学生 |
| PUT | `/study-plans/{planId}` | 更新计划 | 学生 |
| DELETE | `/study-plans/{planId}` | 删除计划 | 学生 |
| GET | `/study-plans/daily-tasks` | 每日任务 | 学生 |
| PUT | `/study-plans/tasks/{taskId}/finish` | 完成任务 | 学生 |
| GET | `/study-plans/recommended-resources` | 推荐资源 | 学生 |
| POST | `/study-plans/targets` | 创建目标计划 | 学生 |
| GET | `/study-plans/{planId}/path` | 学习路径 | 学生 |
| POST | `/study-plans/{planId}/adjustments` | 动态调整计划 | 学生 |

目标计划生成学习路径时，会优先按所选学科和目标描述过滤薄弱点，避免政治/道德与法治计划串到数学等其他学科；AI 返回步骤最多保留 4 个核心步骤，若步骤与当前学科不匹配则使用后端规则兜底。

### 错题管理

| 方法 | 路径 | 说明 | 权限 |
| --- | --- | --- | --- |
| GET | `/wrong-questions` | 错题列表 | 学生 |
| GET | `/wrong-questions/{wrongId}` | 错题详情 | 学生 |
| POST | `/wrong-questions/collect` | 收集错题 | 学生 |
| POST | `/wrong-questions/batch-collect` | 批量收集错题 | 学生 |
| PUT | `/wrong-questions/{wrongId}/mastered` | 标记掌握状态 | 学生 |
| PUT | `/wrong-questions/{wrongId}/review-plan` | 更新复习计划 | 学生 |
| GET | `/wrong-questions/statistics` | 错题统计 | 学生 |
| GET | `/wrong-questions/{wrongId}/similar` | 相似题推荐 | 学生 |
| GET | `/wrong-questions/export` | 导出错题本 | 学生 |
| GET | `/wrong-questions/export-files/{fileName}` | 下载错题本文件 | 学生 |
| DELETE | `/wrong-questions/{wrongId}` | 删除单题 | 学生 |
| DELETE | `/wrong-questions` | 清空/批量删除 | 学生 |

### 学习资源

| 方法 | 路径 | 说明 | 权限 |
| --- | --- | --- | --- |
| GET | `/learning-resources` | 资源列表 | 学生/管理员 |
| GET | `/learning-resources/search` | 资源检索 | 学生/管理员 |
| GET | `/learning-resources/categories` | 资源分类 | 学生/管理员 |
| GET | `/learning-resources/{resourceId}` | 资源详情 | 学生/管理员 |

学生端资源打开规则：若资源 URL 是国家中小学智慧教育平台的笼统入口，如 `/tchMaterial`、`/syncClassroom` 或平台首页，前端会按资源学科转换为 `https://basic.smartedu.cn/search?keyword=初中{学科}` 的具体搜索页。例如初中英语跳转到 `https://basic.smartedu.cn/search?keyword=%E5%88%9D%E4%B8%AD%E8%8B%B1%E8%AF%AD`。

资源地址质量规则：学生端不应展示 `example.com`、`localhost`、`127.0.0.1` 等开发占位资源。历史数据若仍保存占位 URL，后端会在资源列表、搜索、详情和学习计划推荐资源返回前替换为国家中小学智慧教育平台的具体搜索页，前端打开前也会阻止明显占位链接。

资源定位精度规则：如果资源 URL 只是国家中小学智慧教育平台的学科搜索页，学生端和后端会按资源的学科、首个知识点和资源类型生成更精确的搜索关键词，例如“初中数学一次函数课件”，避免只落到“初中数学”。

### 测评中心

| 方法 | 路径 | 说明 | 权限 |
| --- | --- | --- | --- |
| POST | `/assessments` | 创建测评 | 学生 |
| GET | `/assessments/{assessmentId}` | 测评详情与题目 | 学生 |
| POST | `/assessments/{assessmentId}/submit` | 提交测评 | 学生 |
| GET | `/assessments/{assessmentId}/report` | 测评报告 | 学生 |
| GET | `/assessments/{assessmentId}/trend` | 成绩趋势 | 学生 |
| PUT | `/assessments/{assessmentId}/answers/{answerId}/review` | 主观题人工复核 | 学生 |
| GET | `/assessments/history` | 测评历史 | 学生 |
| DELETE | `/assessments/{assessmentId}` | 删除单次测评 | 学生 |
| DELETE | `/assessments` | 清空/批量删除测评 | 学生 |

测评创建口径：学生发起测评时提交测评模式、学科、年级、知识范围和难度；后端保存测评后调用 AI 服务 `/assessment/generate-paper`，由 Agent 按年级、知识范围和标准卷面结构实时生成本次专用题目。生成题会保存到 `question_bank`，并在 `knowledge_point` 中写入内部测评标记，详情和提交时只读取本次测评绑定的题，保证刷新后不变题。
后台题库口径：管理员题库管理、手动录入、CSV 导入和后台维护功能保留；后台题库可作为教学沉淀、人工审核和演示数据来源，但学生端新建测评不再要求管理员先补齐题库库存。旧的题库抽题逻辑仍作为兼容路径存在，用于历史测评或未生成绑定题的测评。
标准机考试卷规则：AI 组卷沿用九科卷面结构。语文为积累与运用、阅读、作文，总分 120 分；数学为选择、填空、分层解答，总分 120 分；英语为听说 + 语法/完形/阅读/读写综合；道德与法治、历史、物理、化学、地理、生物按 100 分发布版结构生成。为了保证实时生成稳定，学生端测评会按模式生成约 8-18 道题，而不是一次生成完整几十题真卷。前端答题页按 `paperSectionTitle`、`paperSectionNote` 和 `maxScore` 展示卷面板块。

### 学习记录与个人数据

| 方法 | 路径 | 说明 | 权限 |
| --- | --- | --- | --- |
| POST | `/study-records` | 新增学习记录 | 学生 |
| GET | `/study-records/duration-statistics` | 学习时长统计 | 学生 |
| GET | `/study-records/progress-report` | 学习进度报告，支持 `period=day/week/month/term` 和 `date=yyyy-MM-dd` | 学生 |
| GET | `/study-records/reminders` | 学习提醒，基于逾期任务、今日任务、错题复习、学习时长和测评表现生成 | 学生 |
| GET | `/personal-data/overview` | 个人数据概览 | 学生 |
| GET | `/personal-data/export` | 生成个人数据 ZIP 导出包，写入导出审计，返回带 `token` 的短期下载地址 | 学生 |
| GET | `/personal-data/export-files/{fileName}?token=...` | 下载个人数据 ZIP 导出包，校验当前用户、短期 token、过期时间和下载次数 | 学生 |
| GET | `/personal-data/export-logs` | 查询当前用户个人数据导出审计记录 | 学生 |
| DELETE | `/personal-data/clear` | 清空个人学习数据；请求体需包含 `password` 和 `confirmText`，确认文本为 `CLEAR_PERSONAL_DATA` 或 `清空个人数据` | 学生 |
| GET | `/personal-data/clear-logs` | 查询当前用户个人数据清空审计记录 | 学生 |

### 管理后台

| 方法 | 路径 | 说明 | 权限 |
| --- | --- | --- | --- |
| GET | `/admin/users` | 用户列表 | 管理员 |
| GET | `/admin/users/{userId}` | 用户详情 | 管理员 |
| PUT | `/admin/users/{userId}/status` | 启用/禁用用户 | 管理员 |
| PUT | `/admin/users/{userId}/reset-password` | 重置密码 | 管理员 |
| GET | `/admin/questions` | 题库列表 | 管理员 |
| GET | `/admin/questions/{questionId}` | 题目详情 | 管理员 |
| POST | `/admin/questions` | 新增题目 | 管理员 |
| PUT | `/admin/questions/{questionId}` | 更新题目 | 管理员 |
| DELETE | `/admin/questions/{questionId}` | 删除题目 | 管理员 |
| POST | `/admin/questions/batch-import` | CSV 批量导入题库 | 管理员 |

管理员题库功能保留：题库列表、新增、编辑、删除、CSV 批量导入和评分要点配置仍用于后台维护题目资源；AI 实时测评生成不会删除或禁用这些后台题库能力。
| GET | `/admin/learning-resources` | 后台资源列表 | 管理员 |
| POST | `/admin/learning-resources` | 上传/创建资源 | 管理员 |
| PUT | `/admin/learning-resources/{resourceId}` | 更新资源 | 管理员 |
| PUT | `/admin/learning-resources/{resourceId}/status` | 资源上下架 | 管理员 |
| DELETE | `/admin/learning-resources/{resourceId}` | 删除资源 | 管理员 |
| GET | `/admin/ai/models` | AI 模型信息 | 管理员 |
| PUT | `/admin/ai/qa-rules` | 更新答疑规则 | 管理员 |
| PUT | `/admin/ai/recommend-config` | 更新推荐配置 | 管理员 |
| GET | `/admin/system/status` | 系统状态 | 管理员 |
| GET | `/admin/system/logs` | 系统日志 | 管理员 |
| GET | `/admin/system/faults` | 故障信息 | 管理员 |
| POST | `/admin/system/backup` | 备份触发 | 管理员 |
| GET | `/admin/audits/personal-data/export-logs` | 查询个人数据导出审计，可按 `userId`、`status` 分页筛选 | 管理员 |
| GET | `/admin/audits/personal-data/clear-logs` | 查询个人数据清空审计，可按 `userId` 分页筛选 | 管理员 |

## 业务口径

### 用户角色

| 值 | 角色 | Spring Security |
| --- | --- | --- |
| `1` | 学生 | `ROLE_STUDENT` |
| `2` | 管理员 | `ROLE_ADMIN` |

### 通用状态

| 值 | 含义 |
| --- | --- |
| `1` | 正常/启用/已完成/已掌握，具体看字段语义 |
| `0` | 禁用/未完成/未掌握，具体看字段语义 |

### 计划状态

| 值 | 含义 |
| --- | --- |
| `1` | 进行中 |
| `2` | 已完成 |
| `3` | 已终止 |

### 任务类型

| 值 | 含义 |
| --- | --- |
| `1` | 学习 |
| `2` | 练习 |
| `3` | 复盘 |
| `4` | 拓展 |

### 学习路径步骤

| 值 | 含义 |
| --- | --- |
| `diagnostic_test` | 诊断测评 |
| `practice` | 专项练习 |
| `wrong_review` | 错题复盘 |
| `resource_study` | 资源学习 |
| `stage_test` | 阶段测评 |

目标计划的学习路径当前最多保留 4 个核心步骤，移动端应按短路径任务流展示，不再假设后端会返回长列表。

### 资源类型

| 值 | 含义 |
| --- | --- |
| `1` | 微课/视频 |
| `2` | 课件 |
| `3` | 练习/真题 |
| `4` | 思维导图 |
| `5` | 考点手册 |

### 题型

| 值 | 含义 |
| --- | --- |
| `1` | 单选题 |
| `2` | 多选题 |
| `3` | 填空/判断类客观题，前端展示需按题库实际确认 |
| `4` | 主观题/解答题 |

### 错题原因

| 值 | 含义 |
| --- | --- |
| `1` | 计算失误 |
| `2` | 概念混淆 |
| `3` | 审题错误 |
| `4` | 思路错误 |

### 测评状态与评分状态

| 字段 | 值 | 含义 |
| --- | --- | --- |
| `assessment_status` | `1` | 进行中 |
| `assessment_status` | `2` | 已提交 |
| `score_status` | `1` | 自动评分 |
| `score_status` | `2` | 待人工复核 |
| `score_status` | `3` | 人工复核完成 |

### 个人数据清空口径

- 请求必须先通过登录鉴权，再提交账号密码校验和二次确认文本。
- 清空范围：`user_profile`、`user_profile_correction_log`、`study_plan`、`study_task`、`study_record`、`wrong_question`、`wrong_question_review_plan`、`assessment`、`assessment_answer`、`qa_conversation`、`qa_message`，以及当前用户个人数据导出 ZIP 和答疑音频文件。
- 保留范围：`sys_user` 账号与密码、`question_bank` 题库、`learning_resource` 学习资源、`personal_data_clear_log` 审计记录。
- 数据库删除和审计写入在服务层事务内执行；文件删除无法被数据库事务自动回滚，后续如要更强一致性可改为事务提交后清理或引入异步补偿任务。

### 个人数据导出口径
- 导出文件保存到 `exports/personal-data/`，文件名格式为 `personal-data-{userId}-{yyyyMMddHHmmssSSS}.zip`。
- 每次导出写入 `personal_data_export_log`，记录文件名、文件路径、文件大小、token hash、过期时间、下载次数、最后下载时间和状态。
- 下载地址带短期 `token`，token 只在导出响应中明文返回一次，数据库仅保存 SHA-256 hash。
- 当前策略：下载链接有效期 24 小时，最多成功下载 3 次；达到次数限制后状态变为 `consumed`，过期后状态变为 `expired` 并清理文件。
- 过期清理由 `PersonalDataExportCleanupTask` 每小时执行，生成新导出时也会触发一次清理。
- 学生端页面入口：`/personal-data`，用于查看个人数据概览、导出审计和清空审计，并可生成新的个人数据导出。
- 管理端页面入口：`/admin/audits`，用于分页查看全局个人数据导出/清空审计，支持按用户 ID 筛选导出和清空记录，导出记录额外支持按状态筛选。

### 学习进度报告口径

- 周期参数：`day` 为当天，`week` 为近 7 天，`month` 为近 30 天，`term` 为近 90 天；`date` 为空时以当天为锚点。
- 报告汇总：学习总时长、学习记录数、任务数、任务完成数、任务完成率、测评数、测评均分、本周期新增错题数、错题掌握率。
- 趋势数据：`dailyTrend` 按天返回学习时长和任务完成率；`assessmentTrend` 返回测评得分趋势；`weakPoints` 按错题原因聚合。
- 学习提醒：逾期任务、今日未完成任务、到期错题复习、近 7 天学习时长偏低、近 30 天测评均分偏低。当前提醒是实时规则生成，没有已读状态。

### 数据安全实现边界

已在代码中落地：

- 密码：注册、登录和重置密码使用 Spring Security `PasswordEncoder`/BCrypt，数据库只保存 BCrypt hash。
- 鉴权：后端使用 JWT Access Token + Refresh Token；学生端接口需要登录，管理端 `/admin/**` 需要管理员角色。
- 个人数据导出：导出 ZIP 写入 `personal_data_export_log`；下载 URL 使用短期 token，数据库只保存 SHA-256 hash；链接 24 小时过期，最多下载 3 次。
- 个人数据清空：必须登录、校验当前密码、提交二次确认文本；清空范围和保留范围明确，清空结果写入 `personal_data_clear_log`。
- 审计入口：学生可查看自己的导出/清空审计，管理员可在 `/admin/audits` 查看全局个人数据审计。
- 密钥配置：数据库密码、JWT 密钥和外部模型密钥通过环境变量或本地 `auth.json` 提供，`auth.json` 不应提交。

部署或待实现边界：

- HTTPS：当前代码不内置 TLS 终止；生产环境应在 Nginx、网关或云负载均衡层启用 HTTPS，并强制 HTTP 跳转 HTTPS。
- AES/字段级加密：当前未对学习记录、答疑内容、画像等业务字段做 AES 加密存储；如验收要求必须加密，应先定义敏感字段清单、密钥管理方式和迁移脚本。
- 日志脱敏：当前应避免业务代码输出真实用户隐私；如接入集中日志，需要统一脱敏手机号、token、答疑内容、导出 token。
- 文件安全：导出 ZIP 当前保存在本地 `exports/personal-data/`；生产环境需限制目录权限、禁止 Web 服务器直接暴露该目录，并定期清理过期文件。

### 学生端界面更新口径

- 项目图标：使用原创打开书本 SVG，文件为 `frontend/src/assets/project-icon.svg` 与 `frontend/public/project-icon.svg`；不使用带水印外部 PNG。
- 项目名称：登录页、学生端导航和浏览器标题使用“基于 AI Agent 的智慧学习辅助系统”或短名“智慧学习辅助系统”。
- 首页 dashboard：只保留学习时间、任务完成率、测评均分、错题掌握率和本周进度报告；不再放大幅装饰图、今日任务、提醒、下一步和薄弱点卡片。
- 三端适配：桌面端保留侧边栏，平板端压缩网格，手机端隐藏侧边栏并使用顶部品牌栏和底部导航。
- 个人中心上传：头像上传当前为前端本地预览；画像材料上传支持本地读取 `.txt` / `.csv` 并填入薄弱点，保存后走现有画像修正接口。
- 数字画像：学生端按九个学科展示掌握度、优先级、薄弱点和 AI 建议；后端尚未提供结构化 subject 画像字段，当前前端根据薄弱点文本推断学科。无学习/测评/错题数据时显示“待诊断/未评估”。

## 目录结构

```text
smart-learning-system/
├── ai/                         # FastAPI AI 服务
│   ├── main.py                  # AI 服务入口
│   ├── config/settings.py       # AI 配置
│   ├── models/                  # Pydantic 请求/响应模型
│   ├── services/                # OCR、Agent、记忆等服务
│   ├── agent_tools/             # Agent 工具，如 OCR、计算器
│   └── requirements.txt         # Python 依赖
├── backend/                     # Spring Boot 后端
│   ├── pom.xml                  # Maven 依赖
│   └── src/main/java/com/smartlearning/backend/
│       ├── common/              # Result、PageVO、异常、常量
│       ├── config/              # Security、MyBatis、RestTemplate 配置
│       ├── security/            # JWT 过滤器和安全工具
│       ├── utils/               # 通用工具
│       └── module/              # 业务模块
│           ├── auth/            # 注册、登录、刷新 token
│           ├── user/            # 用户信息与后台用户管理
│           ├── profile/         # 学情画像
│           ├── qa/              # AI 答疑与后端调用 AI 服务
│           ├── plan/            # 学习计划和每日任务
│           ├── wrong/           # 错题管理
│           ├── resource/        # 学习资源
│           ├── assessment/      # 测评与评分
│           ├── record/          # 学习记录与进度
│           ├── personal/        # 个人数据查看、导出、清空
│           ├── question/        # 题库管理
│           └── system/          # 系统运维接口
├── docs/
│   ├── sql/                     # 数据库脚本与种子数据
│   ├── CLAUDE.md                # 全局规则和开发规范
│   ├── Memory.md                # 项目记忆和下次入口
│   ├── learning.md              # 踩坑与复盘
│   └── wiki.md                  # 项目 wiki、API、业务口径、部署与数据种子
├── frontend/                    # Vue 3 前端
│   ├── package.json
│   └── src/
│       ├── api/                 # 前端 API 封装
│       ├── router/              # 路由和守卫
│       ├── store/               # 当前路由实际使用的 auth store
│       ├── views/student/       # 学生端页面
│       └── views/admin/         # 管理端页面
├── auth.example.json            # AI/外部模型密钥示例
├── auth.json                    # 本地私密配置，应保持 git ignore
└── README.md
```

## 常见术语

| 术语 | 说明 |
| --- | --- |
| AI Agent | 具备感知、决策、执行、迭代能力的学习智能体，是需求定义的核心引擎 |
| 学情画像 | 用户学习能力、知识掌握度、学习习惯、学习偏好、薄弱点等数据集合 |
| 薄弱点 | 学生掌握度较低或错题频次较高的知识点 |
| 错题闭环 | 错题采集、分类、复盘、巩固、再次推荐资源的完整流程 |
| 学习路径 | 围绕目标和薄弱点生成的诊断、练习、复盘、资源学习、阶段测评步骤 |
| 每日任务 | 系统根据计划、画像和完成情况生成的轻量化学习任务 |
| 主观题 AI 评分 | AI 根据题干、参考答案、评分点和学生答案给出语义评分，低置信度需人工复核 |
| OCR | 图片文字识别，用于题目图片答疑 |
| ASR | 语音识别；当前主要由前端 Web Speech API 提供识别文本 |
| 个人数据导出 | 用户自主导出个人学习数据；当前生成 ZIP 包，包含结构化 JSON 和 Markdown 摘要，下载需登录且校验短期 token、过期时间和下载次数 |
| 个人数据清空 | 用户自主清空个人学习痕迹；当前需要密码和二次确认，清空答疑、画像修正、任务、记录、错题、测评等数据并写入审计日志 |
| 规则兜底 | AI 服务不可用或返回无效时，后端使用固定规则生成可用结果 |

## 本地启动摘要

1. 初始化数据库：执行 `docs/sql/01_schema.sql`，再按需执行 `docs/sql/02_seed_data.sql`。
2. 启动后端：`cd backend; .\mvnw.cmd spring-boot:run`。
3. 启动 AI：`cd ai; py -3.12 -m uvicorn main:app --host 127.0.0.1 --port 8000`。
4. 启动前端：`cd frontend; npm install; npm run dev`。

常用验证：

```powershell
cd backend
.\mvnw.cmd test

cd ..\frontend
npm run build

cd ..
py -3.12 -m compileall -q ai
```

## 2026-07-18 补充：AI 调用可观测性与主观题复核口径

### AI 观测字段

后端通过 `AiService` 调用 FastAPI 时统一补充以下字段：

| 字段 | 含义 |
| --- | --- |
| `operation` | 后端定义的调用场景，如 `qa_text`、`subjective_score`、`learning_path` |
| `endpoint` | AI 服务内部路径，如 `/assessment/subjective-score` |
| `latencyMs` | 本次调用耗时，单位毫秒 |
| `provider` | 调用来源或模型提供方，如 `openai-compatible`、`rule_fallback`、`spring-backend` |
| `model` | 模型名称，如 `gpt-4o-mini` |
| `fallback` | 是否使用兜底结果 |
| `failureCategory` | 失败分类：`timeout`、`connection`、`http_error`、`invalid_response`、`ai_service_error`、`call_error` 等 |
| `errorCode` | 机器可读错误码，如 `AI_TIMEOUT`、`MODEL_JSON_PARSE_FAILED`、`AI_SERVICE_ERROR` |
| `errorMessage` | 简短错误说明，不包含题干、答案或对话原文 |

### 主观题复核口径

- 主观题 AI 评分结果包含 `score`、`confidence`、`matchedPoints`、`missingPoints`、`scoringMode`、`provider`、`model`、`fallback`、`failureCategory`、`errorCode`。
- 若 AI 服务不可用、模型返回非法 JSON、后端 Java 兜底、Python 启发式兜底、低置信度或存在错误码，答案应保持 `score_status=2` 待人工复核。
- 人工复核仍使用现有接口 `PUT /assessments/{assessmentId}/answers/{answerId}/review`，复核后状态为 `score_status=3`、`review_status=1`，并重新计算测评总分。
- 演示数据脚本：`docs/sql/02_seed_data.sql`。

## 部署说明入口

- 部署说明统一保存在本节，不再拆分独立 Markdown。
- 启动顺序：MySQL -> 初始化 SQL -> Redis -> AI 服务 -> 后端 -> 前端。
- 组件端口：MySQL `127.0.0.1:3306`，Redis `127.0.0.1:6379`，AI 服务 `127.0.0.1:8000`，后端 `127.0.0.1:8080/api`，前端 `127.0.0.1:5173`。
- 数据库初始化：执行 `docs/sql/01_schema.sql` 创建数据库和表；执行 `docs/sql/02_seed_data.sql` 导入管理员账号、题库、资源和演示数据。
- 后端环境变量：`DB_HOST`、`DB_PORT`、`DB_NAME`、`DB_USERNAME`、`DB_PASSWORD`、`JWT_SECRET`；本地默认连接 `localhost:3306/smart_learning_system`，账号密码 `root/root`。服务器部署不改 `application.yml`，启动前导出环境变量。
- AI 环境变量：`EXTERNAL_LLM_BASE_URL`、`EXTERNAL_LLM_MODEL`、`EXTERNAL_LLM_API_KEY`；密钥只能放服务器环境变量或本地私有 `auth.json`，不得提交。
- 前端部署：开发使用 `npm run dev`，生产构建使用 `npm run build`，建议用 Nginx 托管 `dist/` 并将 `/api` 反向代理到后端。
- 生产边界：HTTPS 建议在网关层终止；JWT 密钥、外部模型密钥、数据库密码必须由环境变量或密钥管理服务提供；日志不得记录 prompt、学生答案、答疑正文或上传文件内容。

## 数据种子口径

### 初中演示题库

- 完整题库已汇总在 `docs/sql/02_seed_data.sql`。
- 学生端测评已改为 AI 实时组卷，导入演示题库不是发起测评的前置条件；演示题库主要用于管理员题库维护、人工审核和兼容历史抽题逻辑。
- 覆盖 9 个学科，每科 240 道原创演示题，共 2160 道；每科 12 个知识点，每个知识点 20 道。
- 题目按初中试卷常见排版生成：选择题、填空题/完成句子、多选题、解答题/材料分析题/写作题。英语题干和选项使用英文，覆盖语法选择、完成句子、阅读与语言运用、简答和写作，不再使用中文知识点说明题替代英语试题。
- 替换策略：按用户要求，导入前会直接删除九科旧题，再插入新版 2160 道标准试卷风格题，避免旧模板题继续进入测评。`question_bank` 外键为 `ON DELETE CASCADE`，因此旧题关联的演示答题/错题记录会随旧题清理。
- 本地导入口径：重新执行 `docs/sql/02_seed_data.sql` 后，九科题库应为每科 240 道、共 2160 道；本地库已验证旧 `【学科】...` 模板题数量为 0。

### 初中学习资源

- 主资源、学习闭环资源和测评演示数据已汇总在 `docs/sql/02_seed_data.sql`。
- 覆盖 9 个学科，每科 3 类资源：课件、练习、考点手册。资源 URL 使用国家中小学智慧教育平台的知识点级搜索页，不保存未授权 PDF、第三方网盘或来源不明扫描件。
- 链接原则：优先使用“初中 + 学科 + 首个知识点 + 资源类型”的搜索关键词；不要使用平台首页、笼统学科搜索页、`example.com`、`localhost`、`127.0.0.1` 作为学生端直接跳转目标。
- 幂等策略：主资源脚本按 `resource_name + subject` 更新旧记录，再插入缺失记录；演示闭环脚本包含修复旧 `example.com` 链接的 `UPDATE`。
- 本地导入结果：主资源不再保存纯“初中数学/初中英语”等宽泛搜索 URL；旧 `https://example.com/%` 资源数量已确认为 0。
