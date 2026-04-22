# pgcheck-mcp

A Model Context Protocol (MCP) server for PostgreSQL, based on Quarkus and JBang.

## Features
- **MCP Stdio Support**: Communicates with AI agents via standard input/output.
- **Connection Pooling**: Uses Agroal for efficient database connection management.
- **Safety First**: 
    - Read-only by default.
    - DML (INSERT, UPDATE, DELETE) requires `--allow-writes=true`.
    - DDL (CREATE, DROP, ALTER) is strictly blocked.
- **Single-file**: The entire server is contained in `PgcheckMcpServer.java`.

## Prerequisites
- Java 17+
- [JBang](https://jbang.dev/)
- [Docker](https://www.docker.com/) (for the playground)

## Getting Started

### 1. Start the Database Playground
Use the provided scripts to spin up a PostgreSQL instance with sample data:
```bash
# On Linux/macOS
./support/scripts/up.sh

# On Windows (PowerShell)
.\support\scripts\up.ps1
```

### 2. Run the MCP Server
You can run the server directly using JBang:
```bash
jbang PgcheckMcpServer.java
```

Or with custom database settings:
```bash
jbang PgcheckMcpServer.java --db-url="jdbc:postgresql://localhost:5432/postgres" --db-user="postgres" --db-pass="postgres"
```

### 3. Test with MCP Inspector
Use the MCP Inspector to interact with the server's tools:
```bash
npx @modelcontextprotocol/inspector jbang PgcheckMcpServer.java
```

## Available Tools
- `execute_query(sql)`: Executes a SQL query and returns JSON results.
- `get_schema()`: Retrieves tables, views, and column definitions.

## Configuration
The server can be configured via:
1. CLI Arguments (`--db-url`, `--db-user`, etc.)
2. Environment Variables (`QUARKUS_DATASOURCE_JDBC_URL`, etc.)
3. A local `.pgcheck-mcp.properties` file.

## Logs
Logs are redirected to `pgcheck-mcp.log` to avoid corrupting the MCP protocol on `stdout`.
