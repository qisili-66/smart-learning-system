# Smart Learning System

智能学习系统由学生端、管理端、Spring Boot 后端和 FastAPI AI 服务组成，覆盖智能答疑、学习计划、学情画像、错题管理、学习资源和测评等学习流程。

## 功能概览

- 学生注册、登录、JWT 鉴权和个人信息管理
- AI 文本答疑、图片 OCR 答疑和语音答疑
- 学情画像与学习行为分析
- 个性化学习计划和学习任务管理
- 错题收集、复习计划、统计与导出
- 学习资源检索与后台资源管理
- 测评生成、在线答题、自动评分、报告和趋势分析
  - 学生发起测评时按年级、学科和知识范围实时 AI 组卷；后台题库仍保留录入、导入和维护能力。
- 管理端用户、题库、资源、AI 配置和系统状态管理

## 技术栈

| 模块 | 技术 |
| --- | --- |
| 前端 | Vue 3、Vite、Vue Router、Pinia、Element Plus、Axios、ECharts |
| 后端 | Java 17、Spring Boot 3、Spring Security、MyBatis-Plus、JWT、MySQL、Redis |
| AI 服务 | Python、FastAPI、LangChain 消息模型、OpenAI 兼容 API、PaddleOCR |

## 项目结构

```text
smart-learning-system/
├── ai/          # FastAPI AI 服务
├── backend/     # Spring Boot 后端
├── docs/sql/    # 数据库结构与演示数据脚本
└── frontend/    # Vue 3 前端
```

## 环境要求

- JDK 17
- Node.js 20.19 或更高版本
- Python 3.12
- MySQL 8.x
- Redis 6.x 或更高版本
- OpenAI 兼容 API Key

## 快速开始

### 1. 初始化数据库

创建 `smart_learning_system` 数据库，然后执行：

```bash
mysql --default-character-set=utf8mb4 -u root -p < docs/sql/initial_schema.sql
```

可选演示数据：

```bash
mysql --default-character-set=utf8mb4 -u root -p smart_learning_system < docs/sql/f6_f7_demo_learning_loop_seed.sql
```

后台题库演示数据仍可按需导入，但学生端测评不再依赖预置题库；创建测评时会调用 AI 服务按年级和知识范围生成本次专用试卷题目。

### 2. 启动后端

后端通过环境变量读取数据库和 JWT 配置，避免把凭据写入仓库：

| 环境变量 | 默认值 | 说明 |
| --- | --- | --- |
| `DB_USERNAME` | `root` | MySQL 用户名 |
| `DB_PASSWORD` | `root` | MySQL 密码，本地默认值；服务器部署时必须通过环境变量覆盖 |
| `JWT_SECRET` | 本地开发默认值 | JWT 签名密钥，部署时必须修改 |
| `JWT_ACCESS_EXPIRE_MS` | `86400000` | Access Token 有效期（毫秒） |
| `JWT_REFRESH_EXPIRE_MS` | `604800000` | Refresh Token 有效期（毫秒） |

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

后端默认地址：`http://127.0.0.1:8080/api`

### 3. 启动 AI 服务

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

AI 服务统一调用 OpenAI 兼容 API。不要把真实密钥写入仓库，部署时通过服务器环境变量注入：

| 环境变量 | 说明 |
| --- | --- |
| `EXTERNAL_LLM_BASE_URL` | OpenAI 兼容接口地址 |
| `EXTERNAL_LLM_MODEL` | 默认 `qwen3.7-max` |
| `EXTERNAL_LLM_API_KEY` | OpenAI 兼容 API Key，也可使用 `OPENAI_API_KEY` |

也可以复制根目录 `auth.example.json` 为 `auth.json`，再填写本地密钥。`auth.json` 已被 Git 忽略，请勿提交真实密钥。

AI 服务默认地址：`http://127.0.0.1:8000`，健康检查：`GET /health`。

### 4. 启动前端

```bash
cd frontend
npm install
npm run dev
```

前端默认地址：`http://127.0.0.1:5173`。开发服务器会把 `/api` 请求代理到后端。

## 项目验证

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

## 安全说明

- 不要提交 `auth.json`、`.env`、证书、私钥、日志或构建产物。
- 示例配置仅用于本地开发，部署时应通过环境变量或安全的密钥管理服务提供凭据。
- 初始化脚本中的演示账号仅用于本地测试，部署前请替换并修改密码。

## License

本项目暂未声明开源许可证。未经授权，请勿用于商业分发。
