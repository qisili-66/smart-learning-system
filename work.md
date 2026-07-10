# Smart Learning System 工作日志

更新时间：2026-07-05

## 当前结论

项目当前重点完成了四条可验收链路：

- 基础认证与鉴权：登录、注册、JWT、用户信息接口可用。
- F2 AI 答疑：文本答疑、图片 OCR 答疑、后端转发 FastAPI、Agent 调用链路已接通。
- F1 学习画像：支持学习行为采集、量化分析、画像自动刷新、手动修正日志和画像数据服务接口。
- F3 学习方案：支持资源精准匹配、每日任务生成、动态调整和阶段目标拆解。
- F4 错题本：支持错题自动采集、电子错题本、统计分析和同类题推荐。
- F6/F7 学习资源与测评闭环：资源库、后台资源管理、题库组卷、自动批改、测评报告、画像刷新、错题入本和资源推荐链路已接通。

## 本次已实现

### F1 个性化学习画像

- 自动采集：学习记录、学习计划、测评提交、错题掌握、资源浏览、答疑交互会触发画像数据采集或刷新。
- 量化分析：按学习时长、完成率、测评正确率、错题掌握率、近 14 天活跃天数计算能力分和掌握度。
- 自动更新：`POST /user-profiles/refresh` 可刷新画像并回写 `user_profile`。
- 手动修正：`PUT /user-profiles/corrections` 和 `PUT /user-profiles/my` 可修正画像字段。
- 修正日志：新增 `user_profile_correction_log` 表，接口 `GET /user-profiles/correction-logs` 可查询。
- 对外服务：`GET /user-profiles/my`、`GET /user-profiles/metrics`、`GET /user-profiles/service-summary` 可供其他模块读取画像数据。

### F2 图片答疑修复

- OCR 未识别文字时，AI 服务返回正常答复文案，不再让前端只显示“上传图片”。
- 前端答疑页补充失败消息展示，文本/图片答疑异常时会显示 AI 气泡说明原因。
- 前端兼容嵌套 `data` 返回结构，避免接口结构轻微变化导致页面无输出。
- 图片答疑请求超时时间已调整为 300 秒，适配首次 OCR 模型加载和本地 Ollama 推理较慢的场景。

### F3 个性化学习方案智能推送

- 资源匹配：`GET /study-plans/recommended-resources` 按画像薄弱点、能力水平和资源类型推荐资源。
- 每日任务：`GET /study-plans/daily-tasks` 自动生成学习、练习、复盘、拓展任务，并保存到 `study_task`。
- 任务完成：`PUT /study-plans/tasks/{taskId}/finish` 更新完成状态、记录正确率，低正确率自动追加次日巩固任务。
- 目标适配：`POST /study-plans/targets` 支持单元、期末、备考等阶段目标，并拆解每日安排。
- 动态调整：`POST /study-plans/{planId}/adjustments` 根据近三天完成率和正确率提升难度或追加巩固任务。

### F4 智能错题本

- 自动采集：测评提交 `POST /assessments/{assessmentId}/submit`、单题 `POST /wrong-questions/collect`、批量 `POST /wrong-questions/batch-collect` 会对比题库答案并自动入错题本。
- 错题本：`GET /wrong-questions` 和 `GET /wrong-questions/{wrongId}` 返回题干、选项、正确答案、我的答案、解析、知识点、关联资源和掌握状态。
- 同类题推荐：`GET /wrong-questions/{wrongId}/similar` 同知识点优先，不足时按同学科相近难度从 `question_bank` 补齐巩固题。
- 统计与导出：`GET /wrong-questions/statistics` 返回错误原因和知识点分布，`GET /wrong-questions/export` 返回电子错题本内容。
- 题库来源：不建议直接爬商业题库，容易有版权和反爬风险；当前采用原创种子题库脚本冷启动，后续通过管理员导入、校内授权题库、公开许可题库和 AI 生成变式题扩充。

### F6/F7 资源与测评闭环

- 资源库：`/resources` 已接入 `GET /learning-resources`、`GET /learning-resources/search`、`GET /learning-resources/{resourceId}` 和画像推荐资源。
- 后台同步：后台资源管理改用 `GET /admin/learning-resources` 查询全量资源，学生端只显示上架资源。
- 测评中心：`/assessments` 可创建测评、按题库组卷、答题提交、自动批改、查看报告和历史记录。
- 闭环联动：提交测评后会刷新画像、错误答案进入错题本，资源推荐会根据画像薄弱点优先推荐同知识点资料。
- 题库导入：后台题库批量导入支持 CSV/TSV，字段顺序为 `subject,knowledgePoint,difficulty,questionType,questionText,options,answer,analysis`。

### 接口文档与后端错误处理

- 已同步 `docs/接口文档.txt`：补充 F1 新接口、画像响应结构、图片答疑响应字段，并标注后端暂未实现 `/qa/conversations` 系列接口。
- 已同步 `docs/接口规范文档.txt`：新增接口文档实时更新规则。
- 已修复学习记录 `resourceId` 不存在时触发数据库外键异常的问题，现在返回 400 参数错误。

## 数据库脚本

首次使用 F1 手动修正日志前，需要执行：

```bat
cd /d D:\zuoye\dasanxia\xiaoxueqi\smart-learning-system
cmd /c "mysql -uroot -proot smart_learning_system < docs\sql\f1_user_profile_correction_log.sql"
```

首次使用 F3 每日任务前，需要执行：

```bat
cd /d D:\zuoye\dasanxia\xiaoxueqi\smart-learning-system
cmd /c "mysql -uroot -proot smart_learning_system < docs\sql\f3_study_task.sql"
```

首次使用 F4 推荐和错题采集前，建议先导入原创种子题库：

```bat
cd /d D:\zuoye\dasanxia\xiaoxueqi\smart-learning-system
cmd /c "mysql --default-character-set=utf8mb4 -uroot -proot smart_learning_system < docs\sql\f4_question_bank_seed.sql"
```

演示完整闭环（10 道题、3 个资源）前，建议执行：

```bat
cd /d D:\zuoye\dasanxia\xiaoxueqi\smart-learning-system
cmd /c "mysql --default-character-set=utf8mb4 -uroot -proot smart_learning_system < docs\sql\f6_f7_demo_learning_loop_seed.sql"
```

如果 MySQL 密码不是 `root`，把 `-proot` 改成实际密码，例如 `-p123456`。

## 关键验证

```bash
cd backend
mvn test
```

```bash
cd frontend
npm run build
```

```bash
python -m compileall ai
```

## 待办

- F5 进度监控仍需完善异常识别、提醒和可视化报告。
- F6-F10 仍有资源管理、测评分析、隐私保护、系统运维等增强项。
