# 项目：数据库可视化管理工具

## 项目概述

构建一个基于 Web 的数据库可视化管理工具（类似 TablePlus / DBeaver 的轻量版）。用户可管理多个数据库连接，通过图形界面浏览表结构、执行 SQL 查询、编辑数据行、导出数据。支持查询历史、收藏夹和 SQL 片段管理。面向开发者和 DBA 的日常数据库操作场景。

## 技术栈

| 层级 | 技术 |
|------|------|
| 前端 | Vue 3 + TypeScript + Vite + Pinia |
| SQL 编辑器 | CodeMirror 6（SQL 语法高亮 + 自动补全） |
| 表格组件 | 自研虚拟滚动表格（支持编辑、排序、筛选） |
| 后端 | Java 17 + Spring Boot 3.x + Maven |
| 元数据库 | MySQL 8.0（存储连接配置、查询历史等） |
| 目标数据库 | MySQL 8.0（被管理的数据库，后端通过 JDBC 直连） |
| 持久层 | Spring Data JPA (Hibernate) + JDBC Template |

## 核心功能模块

### 1. 连接管理
- 新建/编辑/删除数据库连接配置（Host、Port、用户名、密码、数据库名）
- 连接测试（Test Connection）
- 连接列表树形展示（按分组管理）
- 密码加密存储（AES）

### 2. 数据库浏览
- 左侧树形导航：连接 → 数据库 → 表 / 视图
- 表列表（支持搜索过滤）
- 右键菜单：打开表、查看建表语句、导出、复制表名

### 3. 表数据浏览与编辑
- 数据表格（自研虚拟滚动，支持万行级数据量）
- 分页加载 + 可调每页条数
- 列排序（单击列头）
- 列筛选（文本/数值/日期类型）
- 行内编辑（双击单元格进入编辑态，回车保存）
- 新增行 / 删除行（软删除确认）
- 数据变更自动生成 UPDATE/INSERT/DELETE 语句并执行

### 4. SQL 查询编辑器
- 多 Tab 页查询编辑器（CodeMirror 6，SQL 语法高亮）
- 快捷键执行：`Ctrl+Enter` 执行全部，`Ctrl+Shift+Enter` 执行选中
- 结果面板：表格展示 + 执行耗时 + 影响行数
- 查询历史自动记录
- 收藏查询（命名 + 分类）
- 导出查询结果（CSV / JSON / SQL INSERT）

### 5. 表结构管理
- 查看建表 DDL
- 列信息：名称、类型、是否可空、默认值、注释
- 索引信息：索引名、类型、列
- 表行数估算

### 6. 数据导出
- 整表导出为 CSV / JSON / SQL INSERT 语句
- 查询结果导出
- 导出进度提示

## 数据库核心表设计

```
users                - 用户表
connections          - 数据库连接配置表（加密存储密码）
connection_groups    - 连接分组表
query_history        - SQL 查询历史表
saved_queries       - 收藏查询表
query_folders        - 收藏查询文件夹
export_logs          - 导出记录表
```

## 关键约束

- 后端通过 JDBC Template 动态连接用户配置的目标数据库，不可使用 ORM 管理目标库
- 元数据库（存储连接配置等）使用 Spring Data JPA
- SQL 编辑器使用 CodeMirror 6，禁止使用 Monaco Editor（体积过大）
- 数据表格必须自研虚拟滚动实现，禁止使用 ag-Grid、vxe-table 等第三方表格库
- 连接密码必须 AES 加密存储，密钥通过环境变量注入
- 所有 SQL 执行须在后端完成，前端不直连目标数据库
- 后端 API 必须符合 RESTful 规范
- 所有日期时间字段使用 UTC 存储

## Docker 要求

项目必须提供完整的 Docker 化部署方案，使用 `docker-compose.yml` 编排以下服务：

| 服务 | 说明 |
|------|------|
| `mysql` | MySQL 8.0，数据持久化到 named volume |
| `backend` | Spring Boot 应用，多阶段 Dockerfile 构建 |
| `frontend` | Vue 前端，多阶段构建 + Nginx 托管 |

### docker-compose.yml 结构要求

```
project-root/
├── docker-compose.yml          # 根级编排文件
├── Dockerfile.backend           # 后端多阶段构建
├── Dockerfile.frontend          # 前端多阶段构建
├── nginx/
│   └── default.conf             # 前端 Nginx 配置（API 代理到 backend）
├── mysql/
│   └── init.sql                 # 建库建表
└── .dockerignore
```

### 约束

- 后端 Dockerfile 必须使用多阶段构建（Maven 构建阶段 + JRE 运行阶段）
- 前端 Dockerfile 必须使用多阶段构建（Node 构建阶段 + Nginx 运行阶段）
- MySQL 初始化脚本放在 `docker-entrypoint-initdb.d/` 自动建库建表
- `docker-compose up -d` 一键启动全部服务
- 服务间通过 Docker 内部网络通信，不暴露多余端口到宿主机

## .gitignore

```gitignore
# === IDE ===
.idea/
*.iml
.vscode/
*.swp
*.swo
*~

# === Java / Maven ===
target/
*.class
*.jar
*.war
!.mvn/wrapper/maven-wrapper.jar

# === Node / Vue ===
node_modules/
dist/
.vite/
*.local

# === Environment ===
.env
.env.*
!.env.example

# === Docker ===
.dockerignore

# === OS ===
.DS_Store
Thumbs.db
desktop.ini

# === Logs ===
logs/
*.log

# === Temp ===
tmp/
temp/
*.tmp
```
