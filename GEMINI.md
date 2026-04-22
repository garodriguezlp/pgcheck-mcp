# GEMINI.md - Project Overview & Instructions

## Project Overview
`pgcheck-mcp` is a single-file **Model Context Protocol (MCP)** server for PostgreSQL. It provides AI agents with a safe, deterministic, and high-performance interface to interact with a database via standard input/output.

### Main Technologies
- **Language**: Java 17+
- **Execution Engine**: [JBang](https://jbang.dev/) (enables running the single-file `.java` application).
- **Framework**: [Quarkus](https://quarkus.io/) with `quarkus-mcp-server-stdio`.
- **Database**: PostgreSQL with JDBC (`quarkus-jdbc-postgresql`, `quarkus-agroal`).
- **Protocol**: MCP (Model Context Protocol).

### Architecture
- **Single-File Server**: The entire logic resides in `PgcheckMcpServer.java`.
- **Connection Pooling**: Uses **Agroal** for efficient connection management, reducing overhead for AI agents.
- **Safety Defaults**:
    - **Read-Only**: Default behavior is read-only (SELECT).
    - **DML (INSERT, UPDATE, DELETE)**: Requires `-Dpgcheck.allow-writes=true`.
    - **DDL (CREATE, DROP, ALTER)**: Strictly blocked.
- **Log Redirection**: Critical for MCP. All logs are redirected to `pgcheck-mcp.log` to prevent protocol corruption on `stdout`.

---

## Building and Running

### 1. Database Playground (Docker)
The project includes a pre-configured PostgreSQL environment.
- **Start**: `./support/scripts/up.sh` (or `.\support\scripts\up.ps1` on Windows).
- **Stop**: `./support/scripts/down.sh`.
- **Initialization**: Sample data is loaded from `support/init-db.sql`.

### 2. Running the Server
Use JBang to run the server directly:
```powershell
jbang PgcheckMcpServer.java
```
Or with custom database settings:
```powershell
jbang -Dquarkus.datasource.jdbc.url="jdbc:postgresql://localhost:5432/postgres" `
      -Dquarkus.datasource.username="postgres" `
      -Dquarkus.datasource.password="postgres" `
      PgcheckMcpServer.java
```

### 3. Testing and Inspection
Use the **MCP Inspector** to interact with the server's tools:
```powershell
npx @modelcontextprotocol/inspector jbang PgcheckMcpServer.java
```

---

## Development Conventions

### Coding Style
- **Single-File Principle**: All application logic must remain in `PgcheckMcpServer.java`.
- **JBang Directives**: Dependencies (`//DEPS`) and Quarkus configurations (`//Q:CONFIG`) are declared at the top of the file.
- **Clean Code**: Prioritize SOLID principles, small single-purpose methods, and descriptive naming.
- **Declarative Style**: Focus on *what* the code does rather than *how* it does it.

### Testing Practices
- **Playground First**: Always test against the local Docker playground before deploying.
- **MCP Inspector**: Use the inspector for interactive tool validation.
- **Log Analysis**: Monitor `pgcheck-mcp.log` for errors, as `stdout` is reserved for the MCP protocol.

---

## Key Files
- `PgcheckMcpServer.java`: The core application file.
- `spec.md`: Detailed system specification and architecture.
- `README.md`: High-level introduction and getting started guide.
- `jbang-catalog.json`: Local JBang alias definitions.
- `support/`: Contains Docker environment and helper scripts.
- `.vscode/mcp.json`: MCP configuration for IDE integration.

## Usage for AI Agents
The server exposes two main tools:
1. `execute_query(sql)`: Executes a SQL query and returns JSON results.
2. `get_schema()`: Retrieves tables, views, and column definitions.
