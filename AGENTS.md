# AGENTS.md

This repository contains `pgcheck-mcp`, a single-file MCP server for PostgreSQL built with Quarkus and JBang. This guide is for coding agents working on the repository and for runtime agents using the server's tools.

## What This Repo Is
- `pgcheck-mcp` exposes PostgreSQL through MCP over stdio.
- The implementation is intentionally concentrated in `PgcheckMcpServer.java`.
- The project favors safe defaults, deterministic JSON responses, and low setup friction.

## Key Files
- `PgcheckMcpServer.java`: entire server implementation, including tool definitions, SQL validation, schema inspection, and JSON serialization.
- `README.md`: user-facing setup and usage guide.
- `support/docker-compose.yml`: local PostgreSQL playground.
- `support/init-db.sql`: sample schema and seed data for the playground.
- `support/scripts/up.sh`: starts the local playground and waits for health.
- `support/scripts/down.sh`: stops the local playground and removes volumes.
- `jbang-catalog.json`: local JBang alias definition.

## Architecture Snapshot
- Entry point: `PgcheckMcpServer` keeps the Quarkus app alive with `Quarkus.waitForExit()`.
- MCP tools: `PgcheckTools` exposes `execute_query` and `get_schema`.
- SQL safety: `SqlValidator` blocks DDL and blocks DML unless `pgcheck.allow-writes=true`.
- Output formatting: `ResultSerializer` returns JSON for both result sets and update counts.
- Schema discovery: `SchemaInspector` reads JDBC metadata and skips PostgreSQL system schemas.
- Execution: `DatabaseExecutor` coordinates validation, JDBC execution, and error handling.

## Ground Truth for Agents
- Trust the code over stale prose. Check `PgcheckMcpServer.java` before making behavior claims.
- Keep the single-file design unless the user explicitly asks for a refactor.
- Preserve MCP-safe logging behavior. Stdio transport must not be polluted by normal output.
- Keep responses deterministic and JSON-first.
- Maintain the safety contract:
  - Read-only queries should work by default.
  - DML requires `-Dpgcheck.allow-writes=true`.
  - DDL and administrative statements must stay blocked unless the user explicitly changes that contract.

## How To Work In This Repo

### When changing behavior
- Read the relevant section of `PgcheckMcpServer.java` first.
- Update `README.md` if setup, logging, safety, or commands change.
- Prefer small, targeted edits. This repo is simple enough that over-abstraction usually makes it worse.

### When validating changes
- Start the playground database if needed:
  - `bash support/scripts/up.sh`
- Run the server:
  - `jbang PgcheckMcpServer.java`
- Exercise it with MCP Inspector:
  - `npx @modelcontextprotocol/inspector jbang PgcheckMcpServer.java`

### When working on SQL-facing behavior
- Test both allowed and blocked statements.
- Verify error payloads remain structured JSON.
- Prefer explicit examples with `LIMIT` when documenting queries.

## Runtime Guidance For Agents Using The Server

### Available tools
- `get_schema()`: inspect tables, views, and columns before writing non-trivial SQL.
- `execute_query(sql)`: run one SQL statement and receive JSON output.

### Recommended workflow
1. Call `get_schema()` first for unfamiliar databases.
2. Write one SQL statement at a time.
3. Prefer explicit columns instead of `SELECT *`.
4. Use `LIMIT` unless the user truly needs a large result set.
5. Treat `status: "error"` as recoverable feedback and revise the query.

### Output shape
- Query success returns JSON with `status`, `columns`, `rows`, and `row_count`.
- Non-query success returns JSON with `status` and `row_count`.
- Failures return JSON with `status: "error"` and a `message`.

## Configuration Reference
- Default database target is the local playground at `jdbc:postgresql://localhost:5432/postgres`.
- Override connection settings through Quarkus config, typically:
  - `QUARKUS_DATASOURCE_JDBC_URL`
  - `QUARKUS_DATASOURCE_USERNAME`
  - `QUARKUS_DATASOURCE_PASSWORD`
- Enable writes only when needed:
  - `-Dpgcheck.allow-writes=true`

## Good Agent Habits Here
- Verify whether docs still match code before editing either.
- Do not introduce extra project-planning docs unless the user asks for them.
- Do not add framework ceremony for a repo this small.
- If you change the safety model, logging path, or developer workflow, document it immediately in `README.md`.
