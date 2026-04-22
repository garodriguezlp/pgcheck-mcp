# pgcheck-mcp: System Specification

## 1. Overview
`pgcheck-mcp` is a single-file, Model Context Protocol (MCP) server designed to provide AI agents with a safe, predictable, and deterministic interface to PostgreSQL databases. It blends the JSON-first, non-interactive database querying functionality of [pgcheck](https://github.com/garodriguezlp/pgcheck) with the Quarkus-based MCP stdio server architecture of [mcp-greeting-server](https://github.com/garodriguezlp/mcp-greeting-server).

The ultimate goal is to allow AI assistants (like Claude) to connect to a local or remote PostgreSQL database, execute queries, and inspect schemas, directly via the MCP protocol over Standard I/O.

## 2. Architecture & Technologies
- **Language**: Java 17+
- **Execution**: [JBang](https://jbang.dev/) (allows running a single `.java` file with declared dependencies)
- **Framework**: [Quarkus](https://quarkus.io/)
- **Protocol**: Model Context Protocol (MCP) via `quarkus-mcp-server-stdio`
- **CLI Parsing**: Picocli (`quarkus-picocli`)
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
   - DML (INSERT, UPDATE, DELETE) is only permitted if the server is started with an `--allow-writes=true` flag.
   - DDL (CREATE, DROP, ALTER) should be strictly blocked.
5. **Deterministic Output**: SQL execution must return structured, deterministic JSON results, eliminating the need for AI to parse human-centric ASCII tables.

## 4. Implementation Details

### 4.1. JBang Directives
The file must start with the following directives (versions can be updated to latest stable):
```java
//DEPS io.quarkus:quarkus-bom:3.18.0@pom
//DEPS io.quarkiverse.mcp:quarkus-mcp-server-stdio:1.1.0
//DEPS io.quarkus:quarkus-picocli
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

### 4.3. Configuration & State Management
The server follows a "Convention over Configuration" approach to ensure a zero-install experience:
1. **Defaults (`//Q:CONFIG`)**: Hardcoded in the script to work immediately with the provided `support/docker-compose.yml`.
2. **Environment Variables (Preferred Override)**: Leveraging Quarkus conventions, users can tweak any setting (e.g., `QUARKUS_DATASOURCE_JDBC_URL`) without modifying the code or rebuilding. This is the "cheapest" and fastest way to adapt the server to different environments.
3. **CLI Arguments**: `--db-url`, `--db-user`, etc., handled by Picocli for explicit overrides.
4. **Properties File**: A local `.pgcheck-mcp.properties` for persistent local tweaks.

### 4.4. Class Structure
The single Java file should contain the following inner classes or package-private classes:

1. **`PgcheckMcpServer` (Main CLI)**
   - Annotated with `@Command(name = "pgcheck-mcp", mixinStandardHelpOptions = true)`.
   - Defines Picocli options: `--db-url`, `--db-user`, `--db-pass`, `--allow-writes`.
   - Populates a global `ServerSettings` thread-safe configuration object.
   - **Logic**: Should attempt to load configuration from the `.properties` file and environment variables if CLI flags are missing.
   - Calls `Quarkus.waitForExit()` to block the main thread and keep the MCP server alive.

2. **`ServerSettings` (State Management)**
   - A thread-safe utility class to share the CLI-configured database settings with the Quarkus CDI context.

3. **`PgcheckTools` (MCP Tools)**
   - An `@ApplicationScoped` bean.
   - Defines methods annotated with `@Tool` that AI agents can call.

4. **`DatabaseExecutor` (Logic)**
   - Handles the JDBC connection utilizing settings from `ServerSettings`.
   - Implements the core logic of `pgcheck`: validating queries, executing them, and mapping `ResultSet` objects to structured JSON output (e.g., extracting column names, types, and row data).

### 4.5. MCP Tools Specification
The server should expose the following tools to the AI:

- **`execute_query`**
  - **Description**: Executes a SQL query against the connected PostgreSQL database.
  - **Arguments**: `sql` (String, required) - The SQL query to execute.
  - **Behavior**: Validates safety (blocks DDL, checks `--allow-writes` for DML).
  - **Returns**: A JSON string containing `status`, `row_count`, `columns`, and `rows`.

- **`get_schema`** (Optional but recommended)
  - **Description**: Retrieves the database schema (tables and views).
  - **Returns**: A JSON string describing available tables and their columns.

### 4.6. Coding Standards & Style
The implementation must adhere to strict clean code principles to ensure high maintainability and readability:

1.  **SOLID Principles**: Every class and method should have a single, well-defined responsibility.
2.  **Small, Single-Purpose Abstractions**: Favor small classes and methods. If a method exceeds 10-15 lines, it is likely doing too much.
3.  **Single Level of Abstraction (SLA)**: All statements within a method should share the same level of abstraction. High-level logic should not be mixed with low-level implementation details (e.g., raw JDBC handling vs. high-level query execution flow).
4.  **Top-Down Storytelling**: Code should be readable from top to bottom. Public "entry point" methods should appear first, delegating specific tasks to well-named private methods that handle the "details."
5.  **Declarative Style**: Use descriptive names for variables and methods that reveal intent. The code should "explain itself" without needing excessive comments.
6.  **Resource Management**: Strictly use try-with-resources for all JDBC objects (Connections, Statements, ResultSets).

## 5. Execution & Testing
The resulting script should be executable directly via JBang:
```bash
jbang PgcheckMcpServer.java --db-url="jdbc:postgresql://localhost:5432/mydb" --db-user="user" --db-pass="pass"
```
Or via an AI agent's configuration file referencing the JBang command.

## 6. Convenience & Developer Experience (DX)
To ensure the project is immediately usable, the following convenience tools and defaults must be included:

### 6.1. Docker Playground
A `docker-compose.yml` file and accompanying `init-db.sql` should be provided in a `support/` directory to quickly spin up a PostgreSQL instance with sample data for testing.
- **Postgres Version**: 16.3
- **Healthcheck**: Included to ensure the DB is ready before the server starts.
- **Seeding**: Automatically seeds a sample schema (e.g., `store` with `customers` and `orders`).

### 6.2. Helper Scripts
- `support/scripts/up.sh`: Automates starting the Docker environment and waiting for the database to be healthy.
- `support/scripts/down.sh`: Shuts down the environment and cleans up volumes.
- **JBang Wrappers**: Local `jbang`, `jbang.cmd`, and `jbang.ps1` files to allow users to run the server without pre-installing JBang.

### 6.3. MCP Configuration Defaults
Pre-configured settings for common MCP clients:
- **VS Code (GitHub Copilot Chat)**: A `.vscode/mcp.json` file.
  ```json
  {
    "mcpServers": {
      "pgcheck-mcp": {
        "command": "jbang",
        "args": ["PgcheckMcpServer.java"],
        "env": {
          "PG_URL": "jdbc:postgresql://localhost:5432/postgres",
          "PG_USER": "postgres",
          "PG_PASS": "postgres"
        }
      }
    }
  }
  ```
- **Claude Desktop**: Instructions or a sample `claude_desktop_config.json` snippet.

### 6.4. JBang Catalog
A `jbang-catalog.json` file should be provided to register the tool locally:
```json
{
  "aliases": {
    "pgcheck-mcp": {
      "script-ref": "PgcheckMcpServer.java",
      "description": "PostgreSQL MCP Server with connection pooling"
    }
  }
}
```

### 6.5. Testing Tools
A recommended one-liner for the **MCP Inspector** should be documented to allow interactive testing of the server's tools:
```bash
npx @modelcontextprotocol/inspector jbang PgcheckMcpServer.java [FLAGS]
```

## 7. LLM Instructions for Code Generation
When generating the source code for this spec, the LLM should:
1. **Source Code**: Ensure all logic is contained within one `PgcheckMcpServer.java` file.
2. **Quality**: Produce clean, idiomatically correct Java 17 code with proper resource management (try-with-resources).
3. **Safety**: Explicitly handle SQL validation and block dangerous operations (DDL) by default.
4. **Error Handling**: Return SQL errors as structured JSON instead of crashing the process.
5. **Artifacts**: Provide the following supporting files alongside the main Java file:
   - JBang wrappers (`jbang`, `jbang.cmd`, `jbang.ps1`).
   - `jbang-catalog.json` with an alias for the server.
   - `support/docker-compose.yml` and `support/init-db.sql`.
   - `support/scripts/up.sh` and `support/scripts/down.sh`.
   - `.vscode/mcp.json` for VSCode integration.
   - `.pgcheck-mcp.properties.example` with default connection settings.
   - A `README.md` summarizing how to get started using the convenience scripts.