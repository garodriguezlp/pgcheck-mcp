# pgcheck-mcp: System Specification

## 1. Overview
`pgcheck-mcp` is a single-file, Model Context Protocol (MCP) server designed to provide AI agents with a safe, predictable, and deterministic interface to PostgreSQL databases. It blends the JSON-first, non-interactive database querying functionality of [pgcheck](https://github.com/garodriguezlp/pgcheck) with the Quarkus-based MCP stdio server architecture of [mcp-greeting-server](https://github.com/garodriguezlp/mcp-greeting-server).

The ultimate goal is to allow AI assistants (like Claude) to connect to a local or remote PostgreSQL database, execute queries, and inspect schemas, directly via the MCP protocol over Standard I/O.

## 2. Architecture & Technologies
- **Language**: Java 17+
- **Execution**: [JBang](https://jbang.dev/) (allows running a single `.java` file with declared dependencies)
- **Framework**: [Quarkus](https://quarkus.io/)
- **Protocol**: Model Context Protocol (MCP) via `quarkus-mcp-server-stdio`
- **Database Driver**: PostgreSQL JDBC (`quarkus-jdbc-postgresql` / `quarkus-agroal`)
- **Connection Pooling**: [Agroal](https://quarkus.io/guides/datasource) (enables efficient reuse of database connections across multiple MCP tool calls)

### 2.1. Performance & Pooling (The "Why")
Unlike a traditional CLI tool (like the original `pgcheck`) that opens and closes a connection for every execution, `pgcheck-mcp` remains a long-running process. This allows it to maintain a **persistent connection pool**.
- **Benefit**: Eliminates the handshake and authentication overhead for every query.
- **Optimization**: The pool size can be tuned to balance memory usage and concurrency, ensuring the AI agent receives rapid responses.


## 3. Core Requirements
1. **Single-File Deployment**: The entire application logic must reside within a single `.java` file (e.g., `PgcheckMcpServer.java`) utilizing JBang's `//DEPS` and `//Q:CONFIG` comment directives.
2. **MCP Stdio Transport**: The server must communicate via `stdin` and `stdout` using JSON-RPC as dictated by the MCP specification.
3. **Log Redirection (Critical)**: Because MCP uses standard I/O for communication, **all Quarkus logging must be redirected to a file** (e.g., `pgcheck-mcp.log`) to prevent protocol corruption. Console logging must be strictly disabled.
4. **Safety Defaults**:
   - Queries must be read-only (SELECT) by default.
   - DML (INSERT, UPDATE, DELETE) is only permitted if the server is started with the `pgcheck.allow-writes=true` property.
   - DDL (CREATE, DROP, ALTER) should be strictly blocked.
5. **Deterministic Output**: SQL execution must return structured, deterministic JSON results, eliminating the need for AI to parse human-centric ASCII tables.

## 4. Implementation Details

### 4.1. JBang Directives
The file must start with the following directives (versions can be updated to latest stable):
```java
//DEPS io.quarkiverse.mcp:quarkus-mcp-server-stdio:1.1.0
//DEPS io.quarkus:quarkus-jdbc-postgresql
//DEPS io.quarkus:quarkus-agroal
//DEPS com.fasterxml.jackson.core:jackson-databind
```

### 4.2. Quarkus Configuration (`//Q:CONFIG`)
The file must contain default configurations that target the **Docker Playground** (localhost:5432) out of the box.
```java
//Q:CONFIG quarkus.banner.enabled=false
//Q:CONFIG quarkus.log.level=INFO
//Q:CONFIG quarkus.log.console.enable=false
//Q:CONFIG quarkus.log.file.enable=true
//Q:CONFIG quarkus.log.file.path=pgcheck-mcp.log
//Q:CONFIG quarkus.log.file.format=%d{yyyy-MM-dd HH:mm:ss,SSS} %-5p [%c{3.}] %s%e%n

// Default Connection to Docker Playground
//Q:CONFIG quarkus.datasource.db-kind=postgresql
//Q:CONFIG quarkus.datasource.jdbc.url=jdbc:postgresql://localhost:5432/postgres
//Q:CONFIG quarkus.datasource.username=postgres
//Q:CONFIG quarkus.datasource.password=postgres
```

### 4.3. Configuration
The server follows a "Convention over Configuration" approach to ensure a zero-install experience:
1. **Defaults (`//Q:CONFIG`)**: Hardcoded in the script to work immediately with the provided `support/docker-compose.yml`.
2. **Environment Variables (Preferred Override)**: Leveraging Quarkus conventions, users can tweak any setting (e.g., `QUARKUS_DATASOURCE_JDBC_URL`) without modifying the code or rebuilding.
3. **System Properties**: Use `-D` flags (e.g., `-Dpgcheck.allow-writes=true`) for runtime overrides.

### 4.4. Class Structure
The single Java file should contain the following inner classes or package-private classes:

1. **`PgcheckMcpServer` (Main)**
   - Implements `QuarkusApplication`.
   - Calls `Quarkus.waitForExit()` to block the main thread and keep the MCP server alive.

2. **`PgcheckTools` (MCP Tools)**
   - An `@ApplicationScoped` bean.
   - Defines methods annotated with `@Tool` that AI agents can call.

3. **`DatabaseExecutor` (Logic)**
   - Handles the JDBC connection utilizing the injected `DataSource`.
   - Injects `@ConfigProperty(name = "pgcheck.allow-writes")` for safety checks.
   - Implements the core logic: validating queries, executing them, and mapping `ResultSet` objects to structured JSON output.

### 4.5. MCP Tools Specification
The server should expose the following tools to the AI:

- **`execute_query`**
  - **Description**: Executes a SQL query against the connected PostgreSQL database.
  - **Arguments**: `sql` (String, required) - The SQL query to execute.
  - **Behavior**: Validates safety (blocks DDL, checks `pgcheck.allow-writes` for DML).
  - **Returns**: A JSON string containing `status`, `row_count`, `columns`, and `rows`.

- **`get_schema`**
  - **Description**: Retrieves the database schema (tables and views).
  - **Returns**: A JSON string describing available tables and their columns.

### 4.6. Coding Standards & Style
The implementation must adhere to strict clean code principles:

1.  **SOLID Principles**: Every class and method should have a single responsibility.
2.  **Small, Single-Purpose Abstractions**: Favor small classes and methods.
3.  **Single Level of Abstraction (SLA)**: Shared level of abstraction within methods.
4.  **Top-Down Storytelling**: Code readable from top to bottom.
5.  **Declarative Style**: Descriptive names revealing intent.
6.  **Resource Management**: Strictly use try-with-resources for JDBC.

## 5. Execution & Testing
The resulting script should be executable directly via JBang:
```bash
jbang -Dpgcheck.allow-writes=true PgcheckMcpServer.java
```
Or via an AI agent's configuration file referencing the JBang command and environment variables.

## 6. Convenience & Developer Experience (DX)

### 6.1. Docker Playground
A `docker-compose.yml` file and accompanying `init-db.sql` are provided in `support/`.

### 6.2. Helper Scripts
- `support/scripts/up.sh`: Starts the Docker environment.
- `support/scripts/down.sh`: Shuts down the environment.

### 6.3. MCP Configuration Defaults
Pre-configured settings for VS Code (`.vscode/mcp.json`) using environment variables for database connection.

### 6.4. JBang Catalog
A `jbang-catalog.json` file registering the tool locally.

### 6.5. Testing Tools
Test with MCP Inspector:
```bash
npx @modelcontextprotocol/inspector jbang PgcheckMcpServer.java
```
