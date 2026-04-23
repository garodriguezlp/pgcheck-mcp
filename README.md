# pgcheck-mcp

A Model Context Protocol (MCP) server for PostgreSQL, built with Quarkus and JBang. It is designed for AI agents to interact with databases safely, predictably, and with JSON-first responses.

## 🚀 Features
- **MCP Stdio Support**: Communicates with AI agents via standard input/output (JSON-RPC).
- **Agent-Centric Design**: Prioritizes deterministic JSON output over human-friendly tables.
- **Connection Pooling**: Uses [Agroal](https://quarkus.io/guides/datasource) for high-performance connection management.
- **Safety First**: 
    - **Read-only by default**: Most statements are restricted to `SELECT` and `WITH`.
    - **DML Control**: `INSERT`, `UPDATE`, `DELETE` require `-Dpgcheck.allow-writes=true`.
    - **DDL Protection**: `CREATE`, `DROP`, `ALTER` are strictly blocked.
- **Single-file**: The entire server is a single JBang-runnable `PgcheckMcpServer.java`.

## For Agents
If you are an agent working on this repo or calling this server, start with **[AGENTS.md](./AGENTS.md)**. It contains the repository map, safety rules, validation workflow, and usage guidance for `get_schema()` and `execute_query(sql)`.

## 📋 Prerequisites
- **Java 17+**
- **[JBang](https://jbang.dev/)**
- **[Docker](https://www.docker.com/)** (optional, for the playground)

## 🏁 Getting Started

### 1. Start the Database Playground
Use the provided scripts to spin up a PostgreSQL instance with sample data:
```bash
# On Linux/macOS
./support/scripts/up.sh

# On Windows with Git Bash
bash support/scripts/up.sh
```

> [!TIP]
> You can customize the Docker image used for the playground by setting the `PGCHECK_MCP_POSTGRES_IMAGE` environment variable (defaults to `postgres:16.3`).

### 2. Run the MCP Server
Run the server directly using JBang:
```bash
jbang PgcheckMcpServer.java
```

Or with custom database settings:
```bash
jbang -Dquarkus.datasource.jdbc.url="jdbc:postgresql://localhost:5432/postgres" \
      -Dquarkus.datasource.username="postgres" \
      -Dquarkus.datasource.password="postgres" \
      PgcheckMcpServer.java
```

### 3. Test with MCP Inspector
Use the [MCP Inspector](https://github.com/modelcontextprotocol/inspector) to interact with the server's tools:
```bash
npx @modelcontextprotocol/inspector jbang PgcheckMcpServer.java
```

## 🛠️ Available Tools
- **`execute_query(sql)`**: Executes a SQL query and returns **structured JSON** results.
- **`get_schema()`**: Retrieves a comprehensive map of tables, views, and column definitions.

## ⚙️ Configuration
The server can be configured via standard Quarkus mechanisms:
1. **Environment Variables**: `QUARKUS_DATASOURCE_JDBC_URL`, `QUARKUS_DATASOURCE_USERNAME`, etc.
2. **System Properties**: `-Dquarkus.datasource...` and `-Dpgcheck.allow-writes=true`.
3. **Local Properties**: A `.pgcheck-mcp.properties` file in the working directory.

## 📝 Logs
To avoid corrupting the MCP protocol on `stdout`, operational logs should go to `stderr` or a file-backed logger, never normal `stdout`.
