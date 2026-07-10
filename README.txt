Smart Learning System 项目说明
更新时间：2026-07-08

一、项目简介

本项目是一个智能学习系统，包含 Vue 学生端/管理端、Spring Boot 后端、FastAPI AI 服务和 MySQL 数据库。当前重点完成了学生学习闭环：学情画像、AI 答疑、学习计划、错题本、资源库、测评与题库。

二、当前已完成功能模块

1. 基础认证与用户
- 学生注册、登录、退出登录。
- JWT 鉴权、当前用户信息查询和修改。
- 管理员用户管理入口。
- 初始化脚本内置管理员账号：admin，初始密码：123456。

2. F1 个性化学习画像
- 支持学习行为、测评、错题、资源浏览、答疑交互等数据刷新画像。
- 支持能力分、知识掌握度、薄弱点、学习习惯和偏好展示。
- 支持画像手动修正和修正日志。

3. F2 AI 智能答疑
- 支持文本答疑、图片 OCR 答疑、语音答疑。
- 支持浏览器 ASR、识别文字修正、语音回放。
- 支持后端持久化会话列表、会话详情和删除。
- 支持作业/考试原题二次确认保护，避免直接输出标准答案。
- 支持近 7 天答疑数量、响应时间、语音/图片/文本分布等基础验收指标。

4. F3 个性化学习方案
- 支持创建、编辑、删除学习计划。
- 支持根据当前分、目标分、每天可学时间、学生画像、错题和测评结果生成 AI 学习路径。
- 支持外部 OpenAI 兼容 API、Ollama 本地模型和规则兜底三种路径生成模式。
- 支持诊断测评、专项练习、错题复盘、资源学习和阶段测评组成闭环。
- 支持每个步骤跳转到测评中心、错题本或资源库等独立页面执行，避免把所有功能堆在同一页面。
- 支持智能调整计划和推荐学习资源。

5. F4 智能错题本
- 支持测评错题自动入本、单题采集和批量采集。
- 支持错题本首页顶部筛选、按每次测评生成长方形错题书册、双击书册进入测评错题册详情页、二级错题列表页、独立错题详情页、统计分析和同类题推荐。
- 支持个人备注、复盘周期、下次复盘时间和掌握状态。
- 支持真实导出错题本文件。
- 支持单条删除错题和按当前筛选条件一键删除错题。

6. F6 学习资源库
- 学生端支持资源列表、搜索、分类聚合和资源详情。
- 管理端支持资源新增、修改、上架、下架、删除。
- 资源浏览可参与画像刷新和后续推荐。

7. F7 测评与题库
- 支持按学科、知识点、难度生成测评。
- 支持独立答题页、提交测评、自动批改、测评报告。
- 支持答题明细、每题得分、AI 主观题语义评分、评分要点配置、单题用时记录。
- 支持独立成绩趋势页，对比同学科历史成绩变化。
- 支持人工复核单题分数并回算总分。
- 支持测评错题入本和画像刷新。
- 支持单条删除测评记录和按学科一键删除测评记录。

8. 文档与数据库
- docs/api.md：接口说明。
- docs/用户体验测试指南.md：不使用接口、直接打开网站的手动体验测试流程。
- docs/项目结构与需求实现核查.md：项目结构和 F1/F2/F3/F4/F6/F7 实现核查。
- docs/本科毕设答辩PPT逐页内容.md：12 页答辩 PPT 文字版逐页内容，包含正文、配图建议和口述稿。
- docs/答辩辅助技术问答.md：按功能模块整理技术实现思路问答。
- docs/sql/initial_schema.sql：统一结构脚本，支持空库初始化和旧库幂等补表补列。
- docs/sql/f4_question_bank_seed.sql、docs/sql/f6_f7_demo_learning_loop_seed.sql：题库/资源演示种子数据脚本。

三、运行环境

1. 基础环境
- Windows 10/11。
- JDK 17。
- MySQL 8.x，数据库名建议为 smart_learning_system。
- Node.js 18 或更高版本。
- Python 3.12，建议使用：
  C:\Users\zhangmei\AppData\Local\Programs\Python\Python312\python.exe
- Ollama，本地模型建议：
  qwen2.5:3b-instruct-q4_0
- 可选外部 API：默认使用 OpenAI 兼容地址 https://apihub.agnes-ai.com/v1，默认模型 agnes-2.0-flash

2. 默认服务地址
- 前端页面：http://127.0.0.1:5173
- 后端 API：http://127.0.0.1:8080/api
- AI 服务：http://127.0.0.1:8000
- Ollama：http://127.0.0.1:11434

3. 外部 API 密钥配置

项目不会把 API Key 写进代码。需要使用外部 API 生成学习路径时，可任选一种方式：

- 设置环境变量：EXTERNAL_LLM_API_KEY 或 OPENAI_API_KEY
- 复制 ai/auth.example.json 为 ai/auth.json，并在 auth.json 中填写 OPENAI_API_KEY

ai/auth.json 已加入 .gitignore，不要提交真实密钥。

四、数据库初始化

空库首次部署建议执行：

cd /d D:\zuoye\dasanxia\xiaoxueqi\smart-learning-system
cmd /c "mysql --default-character-set=utf8mb4 -uroot -proot < docs\sql\initial_schema.sql"

旧库升级同样执行 unified schema：

cmd /c "mysql --default-character-set=utf8mb4 -uroot -proot < docs\sql\initial_schema.sql"

需要演示题库/资源数据时再单独执行种子脚本：

cmd /c "mysql --default-character-set=utf8mb4 -uroot -proot smart_learning_system < docs\sql\f4_question_bank_seed.sql"
cmd /c "mysql --default-character-set=utf8mb4 -uroot -proot smart_learning_system < docs\sql\f6_f7_demo_learning_loop_seed.sql"

如果 MySQL 密码不是 root，请把命令中的 -proot 改成实际密码。

初始化脚本会写入默认管理员账号：

账号：admin
密码：123456

如果数据库里已经存在 admin 账号，重复执行初始化脚本会把该账号更新为管理员角色并重置为上述初始密码。

五、启动方式

1. 启动 MySQL

确认 MySQL 服务已启动，并且 smart_learning_system 数据库已创建或已通过 initial_schema.sql 初始化。

2. 启动 Ollama

确认 Ollama 已启动，并已拉取模型：

ollama pull qwen2.5:3b-instruct-q4_0

3. 启动 AI 服务

推荐直接双击：

D:\zuoye\dasanxia\xiaoxueqi\smart-learning-system\ai\一键启动服务.bat

或手动启动：

cd /d D:\zuoye\dasanxia\xiaoxueqi\smart-learning-system\ai
C:\Users\zhangmei\AppData\Local\Programs\Python\Python312\python.exe -m uvicorn main:app --host 127.0.0.1 --port 8000

4. 启动后端服务

cd /d D:\zuoye\dasanxia\xiaoxueqi\smart-learning-system\backend
mvnw.cmd spring-boot:run

后端默认端口为 8080，接口前缀为 /api。

5. 启动前端服务

cd /d D:\zuoye\dasanxia\xiaoxueqi\smart-learning-system\frontend
npm install
npm run dev

启动成功后浏览器打开：

http://127.0.0.1:5173

六、验证命令

后端测试：

cd /d D:\zuoye\dasanxia\xiaoxueqi\smart-learning-system\backend
mvnw.cmd test

前端构建：

cd /d D:\zuoye\dasanxia\xiaoxueqi\smart-learning-system\frontend
npm run build

AI 语法检查：

cd /d D:\zuoye\dasanxia\xiaoxueqi\smart-learning-system
C:\Users\zhangmei\AppData\Local\Programs\Python\Python312\python.exe -m compileall -q ai

七、体验入口

学生端：
- 打开 http://127.0.0.1:5173 后注册学生账号即可体验。
- 推荐按 docs/用户体验测试指南.md 的顺序测试。

管理端：
- 默认管理员账号：admin，密码：123456。
- 管理功能包括用户管理、资源管理、题库管理、AI 配置和系统状态。

八、注意事项

- 当前默认 python 可能指向 Python 3.14，AI 服务建议固定使用 Python 3.12。
- F3 学习路径默认优先使用外部 API；没有配置密钥或外部 API 不可用时，可在页面选择 Ollama 或 auto 模式降级。
- 图片 OCR 首次加载可能较慢，请等待 1-3 分钟。
- 语音识别依赖浏览器能力，建议使用 Chrome 或 Edge，并允许麦克风权限。
- 前端构建可能出现第三方 @vueuse/core pure annotation 警告和 chunk 体积警告，不影响当前构建通过。
- 当前自动化测试主要证明工程可构建，完整业务验收建议按 docs/用户体验测试指南.md 手动体验。
- 答辩 PPT 文字版逐页内容见 docs/本科毕设答辩PPT逐页内容.md。
