# AGENTS.md - Agent Instructions for pgcheck-mcp

This file provides guidance for AI agents (like Claude, ChatGPT, etc.) on how to use `pgcheck-mcp` effectively.

## 🚀 Overview
`pgcheck-mcp` is a **Model Context Protocol (MCP)** server that allows you to interact with a PostgreSQL database. It is designed for **safety**, **determinism**, and **speed**.

## 🛡️ Safety & Mutation Policy
The server implements a strict safety policy:
- **Read-Only by Default**: Most operations should be `SELECT` or `WITH`.
- **DML (INSERT, UPDATE, DELETE)**: Only allowed if the server is started with `-Dpgcheck.allow-writes=true`.
- **DDL (CREATE, DROP, ALTER, TRUNCATE)**: **Strictly blocked** at the application level.

## 🛠️ Available Tools

### 1. `get_schema()`
- **When to use**: Always call this first when you connect to a new database or when you need to understand the structure.
- **What it returns**: A JSON object listing all tables, views, and their columns (with types).

### 2. `execute_query(sql)`
- **When to use**: To fetch data or (if allowed) modify records.
- **Best Practices**:
    - **Prefer explicit columns**: Avoid `SELECT *`.
    - **Limit results**: Use `LIMIT` to avoid overwhelming the context window.
    - **Single Statement**: While the server may support multiple statements, it's safer to send one at a time.

## 📊 Deterministic Output
The server returns **JSON**. You do not need to parse ASCII tables.
- **`status`**: `SUCCESS` or `ERROR`.
- **`columns`**: Array of column names.
- **`rows`**: Array of objects, where keys are column names.
- **`row_count`**: Number of rows returned/affected.

## 💡 Strategy for Success
1. **Discovery**: Call `get_schema()` to map the database.
2. **Plan**: Formulate your SQL based on the schema.
3. **Execute**: Run `execute_query`.
4. **Iterate**: If a query fails, the error message will be in the JSON response. Use it to fix your SQL.

## ⚙️ Configuration for Agents
If you are configuring this server in your own settings (e.g., Claude Desktop `config.json`):
```json
{
  "mcpServers": {
    "pgcheck-mcp": {
      "command": "jbang",
      "args": [
        "PgcheckMcpServer.java"
      ],
      "env": {
        "QUARKUS_DATASOURCE_JDBC_URL": "jdbc:postgresql://localhost:5432/postgres",
        "QUARKUS_DATASOURCE_USERNAME": "postgres",
        "QUARKUS_DATASOURCE_PASSWORD": "postgres"
      }
    }
  }
}
```
