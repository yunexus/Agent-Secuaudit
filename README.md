## 去中心化安全数据审计系统
多个 AI Agent 共享同一数据平台，会带来很多数据不可信的安全风险。于是我们开发了面向智能体的去中心化安全数据审计系统，从而去确保Agent始终基于可信数据进行分析和决策。
SecuAudit 是一个面向智能体的安全数据审计系统，包含 Spring Boot 后端、内嵌 Web 管理页面、H2 数据库和可选的 MCP Server。
我们通过分布式管理密钥，远程数据可持有证明实现了本系统。

## 一、项目结构

```text
secuaudit-web/
├── backend/                       # Java Spring Boot 主程序
│   ├── pom.xml                    # Maven 配置
│   ├── lib/                       # JPBC 密码学依赖
│   ├── data/                      # H2 数据库文件
│   ├── storage/                   # 上传文件、数据块和标签
│   └── src/main/
│       ├── java/com/secuaudit/    # 后端代码
│       └── resources/static/      # Web 前端页面、样式和脚本
└── mcp-server/                    # 可选的 MCP Server
    ├── package.json
    └── index.js
```

## 二、运行环境

启动主程序需要：

- JDK 17 或更高版本
- Maven 3.8 或更高版本

只有接入 MCP Server 时才需要：

- Node.js 18 或更高版本
- npm

在终端中检查环境：

```powershell
java -version
mvn -version
node -v
npm -v
```

## 三、快速启动

### Windows PowerShell

打开 PowerShell，进入后端目录：

```powershell
cd ".\secuaudit\backend"
mvn spring-boot:run
```

看到下面的信息即表示启动成功：

```text
Tomcat started on port 8080
SecuAudit Web Dashboard Started
```

浏览器访问：

- 系统主页：http://localhost:8080
- 系统状态接口：http://localhost:8080/api/system/status
- H2 控制台：http://localhost:8080/h2-console

运行期间请不要关闭当前终端。需要停止服务时，在该终端按 `Ctrl + C`。

> 请从 `backend` 目录启动。数据库路径 `./data` 和文件存储路径 `./storage` 都是相对于启动目录计算的。

### macOS / Linux

```bash
cd secuaudit-web/backend
mvn spring-boot:run
```

启动后同样访问 http://localhost:8080，按 `Ctrl + C` 停止。

## 四、打包后运行

如果希望先构建 JAR 包再启动：

```powershell
cd ".\secuaudit\backend"
mvn clean package -DskipTests
java -jar target/secuaudit-web-1.0.0.jar
```

## 五、首次使用流程

1. 打开 http://localhost:8080。
2. 进入 **System** 页面，点击 **Initialize (DKG)** 初始化系统。
3. 进入 **Users** 页面注册用户，并选择策略和有效期。
4. 进入 **Files** 页面上传文件并关联用户。
5. 点击 **Audit** 验证文件完整性。
6. 点击 **Inject Fault** 模拟篡改，再次审计以验证检测能力。
7. 在 **MCP Monitor** 页面查看 MCP 调用和实时审计事件。

系统初始化状态保存在内存中，重启应用后需要重新执行 **Initialize (DKG)**。审计记录使用 H2 数据库保存在 `backend/data` 中。

## 六、可选：启用 MCP Server

MCP Server 通过标准输入/输出与 MCP 客户端通信，不需要手工单独常驻启动；客户端调用它之前，Spring Boot 主程序必须已经运行。

先安装 Node.js 依赖：

```powershell
cd ".\secuaudit\mcp-server"
npm ci
```

在 MCP 客户端配置中加入：

```json
{
  "mcpServers": {
    "secuaudit": {
      "command": "node",
      "args": [
        "./secuaudit/mcp-server/index.js"
      ],
      "env": {
        "SECUAUDIT_URL": "http://localhost:8080",
        "SECUAUDIT_USER_ID": "alice"
      }
    }
  }
}
```

重启 MCP 客户端后可以使用：

- `read_file`：审计文件完整性后读取内容
- `query_db`：审计后执行数据库查询
- `audit_status`：查看 MCP 审计统计

## 七、H2 数据库连接参数

在 http://localhost:8080/h2-console 中填写：

| 配置项 | 值 |
| --- | --- |
| Driver Class | `org.h2.Driver` |
| JDBC URL | `jdbc:h2:file:./data/secuaudit` |
| User Name | `sa` |
| Password | 留空 |

## 八、常用接口

| 接口 | 方法 | 说明 |
| --- | --- | --- |
| `/api/system/status` | GET | 系统状态 |
| `/api/system/init` | POST | 初始化 DKG |
| `/api/keyservers` | GET | 密钥服务器列表 |
| `/api/users` | GET | 用户列表 |
| `/api/users/register` | POST | 注册用户 |
| `/api/files` | GET | 文件列表 |
| `/api/files/upload` | POST | 上传、分块并签名文件 |
| `/api/files/{id}/audit` | POST | 审计文件 |
| `/api/files/{id}/tamper` | POST | 模拟篡改 |
| `/api/audit-log` | GET | 审计日志 |
| `/api/audit-log/export` | GET | 导出 CSV |
| `/api/blockchain` | GET | 区块链状态 |
| `/api/mcp/read_file` | POST | MCP 文件读取接口 |
| `/api/mcp/query_db` | POST | MCP 数据库查询接口 |
| `/ws/audit` | WebSocket | 实时审计事件 |

## 九、常见问题

### 1. 提示 `java` 或 `mvn` 不是命令

安装 JDK 17 和 Maven，并将它们的 `bin` 目录加入系统 `PATH`，然后重新打开终端。

### 2. 8080 端口已被占用

临时改用其他端口启动：

```powershell
mvn spring-boot:run "-Dspring-boot.run.arguments=--server.port=8081"
```

然后访问 http://localhost:8081。MCP 配置中的 `SECUAUDIT_URL` 也要同步改成 `http://localhost:8081`。

### 3. Maven 首次启动较慢

第一次运行需要下载 Spring Boot 等依赖，请保持网络连接并等待下载完成。后续启动会使用本地缓存。

### 4. 页面可以打开，但系统显示未初始化

这是正常状态。进入 **System** 页面点击 **Initialize (DKG)** 即可。

### 5. MCP Server 无法连接后端

确认以下事项：

- http://localhost:8080/api/system/status 可以正常打开；
- 已在 `mcp-server` 目录执行 `npm ci`；
- MCP 配置中的 `index.js` 使用正确的绝对路径；
- 修改 MCP 配置后已重启客户端。

## 十、技术栈

- Java 17、Spring Boot 3.2.1
- Spring Data JPA、H2 Database
- JPBC 2.0.0、门限 PDP、Shamir Secret Sharing、DKG
- 原生 HTML/CSS/JavaScript、WebSocket
- Node.js、`@modelcontextprotocol/sdk`
