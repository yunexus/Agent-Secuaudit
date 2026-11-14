# AI Agent Data Security Auditing System

When multiple AI agents share the same data platform, untrusted data can introduce significant security risks. To address these risks, we developed an AI Agent Data Security Auditing System using distributed key management and remote provable data possession (PDP), helping ensure that agents analyze data and make decisions based on trustworthy data.

The system implements the core ideas of our published SCI-indexed paper (JCR Q3), with further engineering enhancements and optimizations. An application for software copyright registration has been submitted.

## 1. Project Structure

```text
secuaudit-web/
├── backend/                       # Main Java Spring Boot application
│   ├── pom.xml                    # Maven configuration
│   ├── lib/                       # JPBC cryptographic dependencies
│   ├── data/                      # H2 database files
│   ├── storage/                   # Uploaded files, data blocks, and tags
│   └── src/main/
│       ├── java/com/secuaudit/    # Backend source code
│       └── resources/static/      # Web frontend pages, styles, and scripts
└── mcp-server/                    # Optional MCP server
    ├── package.json
    └── index.js
```

## 2. Requirements

To run the main application:

- JDK 17 or later
- Maven 3.8 or later

The following are required only when integrating the MCP server:

- Node.js 18 or later
- npm

Check your environment in a terminal:

```powershell
java -version
mvn -version
node -v
npm -v
```

## 3. Quick Start

### Windows PowerShell

Open PowerShell and navigate to the backend directory:

```powershell
cd ".\secuaudit\backend"
mvn spring-boot:run
```

The following messages indicate that the application has started successfully:

```text
Tomcat started on port 8080
SecuAudit Web Dashboard Started
```

Open the following URLs in your browser:

- Home page: http://localhost:8080
- System status API: http://localhost:8080/api/system/status
- H2 console: http://localhost:8080/h2-console

Keep the terminal open while the application is running. To stop the service, press `Ctrl + C` in that terminal.

> Start the application from the `backend` directory. The database path `./data` and file storage path `./storage` are resolved relative to the directory from which the application is started.

### macOS / Linux

```bash
cd secuaudit-web/backend
mvn spring-boot:run
```

Once the application has started, visit http://localhost:8080. Press `Ctrl + C` to stop it.

## 4. Build and Run a JAR

To build a JAR package before starting the application:

```powershell
cd ".\secuaudit\backend"
mvn clean package -DskipTests
java -jar target/secuaudit-web-1.0.0.jar
```

## 5. First-Time Workflow

1. Open http://localhost:8080.
2. Go to the **System** page and click **Initialize (DKG)** to initialize the system.
3. Go to the **Users** page to register users and select their policies and validity periods.
4. Go to the **Files** page to upload files and associate them with users.
5. Click **Audit** to verify file integrity.
6. Click **Inject Fault** to simulate tampering, then run another audit to verify the system's detection capability.
7. View MCP calls and real-time audit events on the **MCP Monitor** page.

The system's initialization state is held in memory. After restarting the application, you must run **Initialize (DKG)** again. Audit records are persisted in the H2 database under `backend/data`.

## 6. Optional: Enable the MCP Server

The MCP server communicates with MCP clients through standard input/output (stdio). You do not need to start it manually as a separate persistent process. The Spring Boot application must already be running before an MCP client invokes the server.

First, install the Node.js dependencies:

```powershell
cd ".\secuaudit\mcp-server"
npm ci
```

Add the following to your MCP client configuration:

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

Restart the MCP client to use the following tools:

- `read_file`: Reads file contents after auditing file integrity.
- `query_db`: Executes database queries after auditing.
- `audit_status`: Displays MCP audit statistics.

## 7. H2 Database Connection Settings

Enter the following settings at http://localhost:8080/h2-console:

| Setting | Value |
| --- | --- |
| Driver Class | `org.h2.Driver` |
| JDBC URL | `jdbc:h2:file:./data/secuaudit` |
| User Name | `sa` |
| Password | Leave blank |

## 8. Common Endpoints

| Endpoint | Method | Description |
| --- | --- | --- |
| `/api/system/status` | GET | Get system status |
| `/api/system/init` | POST | Initialize DKG |
| `/api/keyservers` | GET | List key servers |
| `/api/users` | GET | List users |
| `/api/users/register` | POST | Register a user |
| `/api/files` | GET | List files |
| `/api/files/upload` | POST | Upload a file, split it into blocks, and generate signatures |
| `/api/files/{id}/audit` | POST | Audit a file |
| `/api/files/{id}/tamper` | POST | Simulate tampering |
| `/api/audit-log` | GET | Get audit logs |
| `/api/audit-log/export` | GET | Export audit logs as CSV |
| `/api/blockchain` | GET | Get blockchain status |
| `/api/mcp/read_file` | POST | MCP file reading endpoint |
| `/api/mcp/query_db` | POST | MCP database query endpoint |
| `/ws/audit` | WebSocket | Receive real-time audit events |

## 9. Troubleshooting

### 1. `java` or `mvn` Is Not Recognized as a Command

Install JDK 17 and Maven, add their `bin` directories to the system `PATH`, and then open a new terminal.

### 2. Port 8080 Is Already in Use

Temporarily start the application on a different port:

```powershell
mvn spring-boot:run "-Dspring-boot.run.arguments=--server.port=8081"
```

Then visit http://localhost:8081. Update `SECUAUDIT_URL` in the MCP configuration to `http://localhost:8081` as well.

### 3. The First Maven Startup Is Slow

The first run downloads Spring Boot and other dependencies. Keep your internet connection active and wait for the downloads to finish. Subsequent runs use the local cache.

### 4. The Page Loads, but the System Is Not Initialized

This is expected. Go to the **System** page and click **Initialize (DKG)**.

### 5. The MCP Server Cannot Connect to the Backend

Check the following:

- http://localhost:8080/api/system/status is accessible.
- You have run `npm ci` in the `mcp-server` directory.
- The MCP configuration uses the correct absolute path to `index.js`.
- You have restarted the client after changing the MCP configuration.

## 10. Technology Stack

- Java 17 and Spring Boot 3.2.1
- Spring Data JPA and H2 Database
- JPBC 2.0.0, threshold PDP, Shamir Secret Sharing, and DKG
- Vanilla HTML/CSS/JavaScript and WebSocket
- Node.js and `@modelcontextprotocol/sdk`
