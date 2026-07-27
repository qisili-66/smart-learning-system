# Smart Study Agent 智慧学习辅助系统

<p align="center">
  <img src="frontend/public/project-icon.svg" width="88" alt="Smart Study Agent Logo">
</p>

<p align="center">
  <a href="#技术栈"><img src="https://img.shields.io/badge/Vue-3.5-42B883?logo=vuedotjs&logoColor=white" alt="Vue 3"></a>
  <a href="#技术栈"><img src="https://img.shields.io/badge/Vite-8.1-646CFF?logo=vite&logoColor=white" alt="Vite 8"></a>
  <a href="#技术栈"><img src="https://img.shields.io/badge/Spring%20Boot-3.3.8-6DB33F?logo=springboot&logoColor=white" alt="Spring Boot 3.3.8"></a>
  <a href="#技术栈"><img src="https://img.shields.io/badge/Java-17-ED8B00?logo=openjdk&logoColor=white" alt="Java 17"></a>
  <a href="#技术栈"><img src="https://img.shields.io/badge/FastAPI-0.115-009688?logo=fastapi&logoColor=white" alt="FastAPI 0.115"></a>
  <a href="#技术栈"><img src="https://img.shields.io/badge/MySQL-8-4479A1?logo=mysql&logoColor=white" alt="MySQL 8"></a>
  <a href="#技术栈"><img src="https://img.shields.io/badge/Redis-6%2B-DC382D?logo=redis&logoColor=white" alt="Redis 6+"></a>
</p>

Smart Study Agent 是一个面向学生的智能学习辅助系统，提供智能答疑、学习计划、错题复盘、资源检索、在线测评和学习分析能力。

## 项目演示

本地启动后访问：

- 学生端：`http://127.0.0.1:5173/dashboard`
- 管理端：`http://127.0.0.1:5173/admin/dashboard`
- 接口文档：`http://127.0.0.1:8080/api/doc.html`

项目 Logo：

<p align="center">
  <img src="frontend/public/project-icon.svg" width="160" alt="Smart Study Agent 标识">
</p>

## 技术栈

| 层级 | 技术 |
| --- | --- |
| 前端 | Vue 3、Vite、Vue Router、Pinia、Element Plus、Axios、ECharts |
| 后端 | Java 17、Spring Boot 3.3.8、Spring Security、MyBatis-Plus、JWT、Knife4j |
| 数据与缓存 | MySQL 8.x、Redis 6+、Spring AI Redis Vector Store |
| AI 服务 | Python 3.12、FastAPI、Uvicorn、LangChain、PaddleOCR、OpenAI 兼容 API |
| 部署 | Nginx、systemd |

## 功能特性

- 学生注册、登录、JWT 鉴权和个人信息管理。
- 文本答疑、图片 OCR 答疑，以及由浏览器语音识别转写后的语音答疑。
- 学情画像、学习行为记录、学习进度报告与提醒。
- 个性化学习计划、每日任务和 AI 学习路径生成。
- 错题收集、复习计划、统计、相似题推荐与导出。
- 学习资源检索、分类与管理端资源维护。
- AI 实时组卷、在线答题、客观题评分、主观题 AI 评分与人工复核。
- 学生端与管理端的用户、题库、资源、AI 配置和系统状态管理。

## 快速上手

### 环境依赖

- JDK 17
- Node.js 20.19 或更高版本
- Python 3.12
- MySQL 8.x
- Redis 6.x 或更高版本
- OpenAI 兼容 API Key

### 1. 初始化数据库

创建 `smart_learning_system` 数据库，然后执行：

```bash
mysql --default-character-set=utf8mb4 -u root -p < docs/sql/01_schema.sql
```

可选演示数据：

```bash
mysql --default-character-set=utf8mb4 -u root -p smart_learning_system < docs/sql/02_seed_data.sql
```

`02_seed_data.sql` 包含管理员账号、初中题库、学习资源、学习闭环和主观题复核演示数据。

### 2. 启动后端

Windows：

```powershell
cd backend
$env:DB_USERNAME="root"
$env:DB_PASSWORD="your_password"
.\mvnw.cmd spring-boot:run
```

macOS / Linux：

```bash
cd backend
DB_USERNAME=root DB_PASSWORD=your_password ./mvnw spring-boot:run
```

后端默认地址：`http://127.0.0.1:8080/api`，接口文档：`http://127.0.0.1:8080/api/doc.html`。

### 3. 启动 AI 服务

先按[配置说明](#配置说明)设置外部模型凭据，再启动服务。

Windows：

```powershell
cd ai
py -3.12 -m venv .venv
.\.venv\Scripts\python.exe -m pip install -r requirements.txt
.\.venv\Scripts\python.exe -m uvicorn main:app --host 127.0.0.1 --port 8000
```

macOS / Linux：

```bash
cd ai
python3.12 -m venv .venv
./.venv/bin/python -m pip install -r requirements.txt
./.venv/bin/python -m uvicorn main:app --host 127.0.0.1 --port 8000
```

AI 服务默认地址：`http://127.0.0.1:8000`，健康检查：`GET /health`。

### 4. 启动前端

```bash
cd frontend
npm install
npm run dev
```

前端默认地址：`http://127.0.0.1:5173`。开发服务器会把 `/api` 请求代理到后端。

## 目录结构

```text
smart-learning-system/
├── ai/          # FastAPI AI 服务：答疑、OCR、评分、组卷和学习路径
├── backend/     # Spring Boot 后端：REST API、鉴权和业务逻辑
├── docs/        # 项目说明、部署文档和数据库脚本
├── frontend/    # Vue 3 学生端与管理端
└── README.md
```

## 配置说明

### 后端

| 环境变量 | 默认值 | 说明 |
| --- | --- | --- |
| `SERVER_ADDRESS` | `0.0.0.0` | 后端监听地址 |
| `SERVER_PORT` | `8080` | 后端端口 |
| `DB_HOST` | `localhost` | MySQL 主机 |
| `DB_PORT` | `3306` | MySQL 端口 |
| `DB_NAME` | `smart_learning_system` | 数据库名 |
| `DB_USERNAME` | `root` | MySQL 用户名 |
| `DB_PASSWORD` | `root` | 本地开发默认密码；生产环境必须覆盖 |
| `REDIS_HOST` | `localhost` | Redis 主机 |
| `REDIS_PORT` | `6379` | Redis 端口 |
| `AI_SERVICE_BASE_URL` | `http://127.0.0.1:8000` | AI 服务地址 |
| `JWT_SECRET` | 本地开发值 | JWT 签名密钥；生产环境必须替换 |

### AI 服务

AI 服务使用 OpenAI 兼容接口。环境变量优先于根目录或 `ai/` 目录中的本地 `auth.json`。

| 环境变量 | 默认值 | 说明 |
| --- | --- | --- |
| `EXTERNAL_LLM_BASE_URL` | `https://api.openai.com/v1` | OpenAI 兼容接口地址 |
| `EXTERNAL_LLM_MODEL` | `gpt-4o-mini` | 调用的模型名称，可替换为供应商支持的兼容模型 |
| `EXTERNAL_LLM_API_KEY` | 无 | API Key；也可使用 `OPENAI_API_KEY` |
| `OPENAI_API_KEY` | 无 | `EXTERNAL_LLM_API_KEY` 的兼容别名 |
| `OCR_LANG` | `ch` | PaddleOCR 识别语言 |
| `LLM_TIMEOUT_SECONDS` | `60` | 单次外部模型调用超时（秒） |
| `LLM_MAX_RETRIES` | `1` | 可重试故障的额外重试次数，采用指数退避 |
| `LLM_MAX_CONCURRENCY` | `8` | 同时发往外部模型的最大请求数 |
| `LLM_QUEUE_TIMEOUT_SECONDS` | `1` | 并发队列满时的最长等待时间（秒） |
| `LLM_CIRCUIT_FAILURE_THRESHOLD` | `3` | 连续可用性故障后打开熔断器的阈值 |
| `LLM_CIRCUIT_RESET_SECONDS` | `30` | 熔断器恢复尝试前的等待时间（秒） |
| `MEMORY_MAX_SESSIONS` | `1000` | 进程内最多保留的会话数 |
| `MEMORY_TTL_SECONDS` | `3600` | 进程内会话空闲过期时间（秒） |

可将 `auth.example.json` 复制为 `auth.json`，再填入本地密钥。`auth.json` 已被 Git 忽略，切勿提交真实凭据。

### 安全注意事项

- 不要提交 `auth.json`、`.env`、证书、私钥、日志或构建产物。
- 示例配置仅用于本地开发；生产环境应通过环境变量或密钥管理服务提供凭据。
- 初始化脚本中的演示账号仅限本地测试，部署前应替换并修改密码。

## API / 使用文档

后端接口统一以 `/api` 为前缀，AI 服务默认运行在 `http://127.0.0.1:8000`。

| 方法 | 路径 | 用途 |
| --- | --- | --- |
| `POST` | `/api/auth/register` | 学生注册 |
| `POST` | `/api/auth/login` | 用户登录 |
| `GET` | `/api/users/info` | 获取当前用户信息 |
| `POST` | `/api/qa/text` | 文本答疑 |
| `POST` | `/api/qa/image` | 图片 OCR 答疑 |
| `POST` | `/api/qa/voice` | 语音文本答疑 |
| `GET` | `/api/study-plans` | 学习计划列表 |
| `GET` | `/api/wrong-questions` | 错题列表 |
| `POST` | `/api/assessments` | 创建测评 |
| `POST` | `/api/assessments/{assessmentId}/submit` | 提交测评 |
| `GET` | `/api/learning-resources` | 学习资源列表 |
| `GET` | `/api/admin/system/status` | 管理端系统状态 |
| `GET` | `/health` | AI 服务健康检查 |

需要登录的后端接口应携带 `Authorization: Bearer <token>`。详细请求字段可通过 Knife4j 接口文档或对应 Controller、DTO 和前端 API 封装查看。

## 常见问题

### 不配置 AI Key 能运行吗？

可以。系统的学习计划、主观题评分和测评生成会返回本地兜底内容；配置 OpenAI 兼容 API Key 后可使用模型能力。

### 前端无法访问后端怎么办？

确认后端已启动在 `http://127.0.0.1:8080`，并检查前端开发服务器的 `/api` 代理配置。

### 生产环境如何部署？

推荐使用 Nginx 托管前端静态资源并反向代理 `/api`，使用 systemd 托管 Spring Boot 与 FastAPI 服务。数据库密码、JWT 密钥和 AI Key 应通过环境变量配置。

## 验证

```bash
# 前端构建
cd frontend
npm run build

# 后端测试（Windows）
cd backend
.\mvnw.cmd test

# AI 服务语法检查（在项目根目录执行）
py -3.12 -m compileall -q ai
```

## 贡献指南

欢迎通过 Issue 提交问题或建议，也欢迎发起 Pull Request。

1. 从主分支创建功能分支：`git checkout -b feature/your-feature`。
2. 保持改动聚焦，补充必要测试，并执行前端构建或相关测试。
3. 提交信息使用清晰的动词开头，例如 `feat: add learning plan export`。
4. 在 Pull Request 中说明改动目的、测试方式和兼容性影响。

请不要提交环境文件、账号密码、密钥、日志、构建产物或 IDE 本地配置。

## 开源协议

当前仓库尚未附带 `LICENSE` 文件。代码默认保留所有权利；如需开源分发，请在确定许可证后添加对应的 `LICENSE` 文件。

## 作者与联系

- 作者：Smart Study Agent 开发团队
- 项目文档：[docs/wiki.md](docs/wiki.md)
- 部署文档：[docs/deploy-tencent-finalshell.md](docs/deploy-tencent-finalshell.md)
