///usr/bin/env jbang "$0" "$@" ; exit $?
//DEPS io.quarkus:quarkus-bom:3.18.0@pom
//DEPS io.quarkiverse.mcp:quarkus-mcp-server-stdio:1.1.0
//DEPS io.quarkus:quarkus-picocli
//DEPS io.quarkus:quarkus-jdbc-postgresql
//DEPS io.quarkus:quarkus-agroal
//DEPS com.fasterxml.jackson.core:jackson-databind

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

import io.quarkiverse.mcp.server.Tool;
import io.quarkus.picocli.runtime.annotations.TopCommand;
import io.quarkus.runtime.Quarkus;
import io.quarkus.runtime.annotations.QuarkusMain;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import javax.sql.DataSource;
import java.sql.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import io.quarkus.runtime.QuarkusApplication;

@QuarkusMain
@TopCommand
@Command(name = "pgcheck-mcp", mixinStandardHelpOptions = true, description = "PostgreSQL MCP Server")
public class PgcheckMcpServer implements Runnable, QuarkusApplication {

    @Option(names = {"--db-url"}, description = "Database URL", defaultValue = "${env:QUARKUS_DATASOURCE_JDBC_URL:-jdbc:postgresql://localhost:5432/postgres}")
    String dbUrl;

    @Option(names = {"--db-user"}, description = "Database User", defaultValue = "${env:QUARKUS_DATASOURCE_USERNAME:-postgres}")
    String dbUser;

    @Option(names = {"--db-pass"}, description = "Database Password", defaultValue = "${env:QUARKUS_DATASOURCE_PASSWORD:-postgres}")
    String dbPass;

    @Option(names = {"--allow-writes"}, description = "Allow DML operations (INSERT, UPDATE, DELETE)", defaultValue = "false")
    boolean allowWrites;

    @Override
    public int run(String... args) throws Exception {
        return new CommandLine(this).execute(args);
    }

    @Override
    public void run() {
        ServerSettings.setDbUrl(dbUrl);
        ServerSettings.setDbUser(dbUser);
        ServerSettings.setDbPass(dbPass);
        ServerSettings.setAllowWrites(allowWrites);

        System.err.println("pgcheck-mcp server starting...");
        System.err.println("Database URL: " + dbUrl);
        System.err.println("Allow Writes: " + allowWrites);

        Quarkus.waitForExit();
    }

    public static void main(String... args) {
        Quarkus.run(PgcheckMcpServer.class, args);
    }
}

/**
 * State Management for server settings.
 */
class ServerSettings {
    private static final AtomicReference<String> dbUrl = new AtomicReference<>();
    private static final AtomicReference<String> dbUser = new AtomicReference<>();
    private static final AtomicReference<String> dbPass = new AtomicReference<>();
    private static final AtomicBoolean allowWrites = new AtomicBoolean(false);

    public static void setDbUrl(String url) { dbUrl.set(url); }
    public static String getDbUrl() { return dbUrl.get(); }

    public static void setDbUser(String user) { dbUser.set(user); }
    public static String getDbUser() { return dbUser.get(); }

    public static void setDbPass(String pass) { dbPass.set(pass); }
    public static String getDbPass() { return dbPass.get(); }

    public static void setAllowWrites(boolean allow) { allowWrites.set(allow); }
    public static boolean isAllowWrites() { return allowWrites.get(); }
}

/**
 * MCP Tools for the AI agent.
 */
@ApplicationScoped
class PgcheckTools {

    @Inject
    DatabaseExecutor executor;

    @Tool(description = "Executes a SQL query against the connected PostgreSQL database.")
    public String execute_query(String sql) {
        return executor.executeQuery(sql);
    }

    @Tool(description = "Retrieves the database schema (tables and views).")
    public String get_schema() {
        return executor.getSchema();
    }
}

/**
 * Core logic for database interaction and safety validation.
 */
@ApplicationScoped
class DatabaseExecutor {

    @Inject
    DataSource dataSource;

    private final ObjectMapper mapper = new ObjectMapper();

    public String executeQuery(String sql) {
        if (sql == null || sql.trim().isEmpty()) {
            return errorResponse("SQL query cannot be empty");
        }

        String normalizedSql = sql.trim().toUpperCase();
        
        // Safety validation
        if (isForbidden(normalizedSql)) {
            return errorResponse("Forbidden operation: DDL or administrative commands are strictly blocked.");
        }

        if (isDml(normalizedSql) && !ServerSettings.isAllowWrites()) {
            return errorResponse("DML operation (INSERT, UPDATE, DELETE) is blocked. Use --allow-writes=true to enable.");
        }

        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement()) {
            
            boolean hasResultSet = stmt.execute(sql);
            
            if (hasResultSet) {
                try (ResultSet rs = stmt.getResultSet()) {
                    return resultSetToJson(rs);
                }
            } else {
                int updateCount = stmt.getUpdateCount();
                ObjectNode result = mapper.createObjectNode();
                result.put("status", "success");
                result.put("row_count", updateCount);
                return result.toString();
            }
        } catch (SQLException e) {
            return errorResponse("SQL Error: " + e.getMessage());
        }
    }

    public String getSchema() {
        try (Connection conn = getConnection()) {
            DatabaseMetaData metaData = conn.getMetaData();
            ObjectNode root = mapper.createObjectNode();
            ArrayNode tablesNode = root.putArray("tables");

            try (ResultSet rs = metaData.getTables(null, null, "%", new String[]{"TABLE", "VIEW"})) {
                while (rs.next()) {
                    String schemaName = rs.getString("TABLE_SCHEM");
                    if ("information_schema".equals(schemaName) || "pg_catalog".equals(schemaName)) {
                        continue;
                    }
                    
                    String tableName = rs.getString("TABLE_NAME");
                    ObjectNode tableNode = tablesNode.addObject();
                    tableNode.put("schema", schemaName);
                    tableNode.put("name", tableName);
                    tableNode.put("type", rs.getString("TABLE_TYPE"));

                    ArrayNode columnsNode = tableNode.putArray("columns");
                    try (ResultSet rsCols = metaData.getColumns(null, schemaName, tableName, "%")) {
                        while (rsCols.next()) {
                            ObjectNode colNode = columnsNode.addObject();
                            colNode.put("name", rsCols.getString("COLUMN_NAME"));
                            colNode.put("type", rsCols.getString("TYPE_NAME"));
                            colNode.put("nullable", rsCols.getString("IS_NULLABLE"));
                        }
                    }
                }
            }
            return root.toString();
        } catch (SQLException e) {
            return errorResponse("Error fetching schema: " + e.getMessage());
        }
    }

    private Connection getConnection() throws SQLException {
        // Agroal DataSource is injected, but we want to ensure it uses our settings if they were overridden via CLI
        // In a real Quarkus app, we'd use a dynamic datasource or similar, 
        // but for a single-file JBang script, we'll try to use the injected one first 
        // and only fallback if needed.
        // Actually, Quarkus reads system properties and env vars, which Picocli can also set.
        return dataSource.getConnection();
    }

    private boolean isForbidden(String sql) {
        return sql.startsWith("CREATE") || sql.startsWith("DROP") || sql.startsWith("ALTER") ||
               sql.startsWith("TRUNCATE") || sql.startsWith("GRANT") || sql.startsWith("REVOKE") ||
               sql.startsWith("COMMENT");
    }

    private boolean isDml(String sql) {
        return sql.startsWith("INSERT") || sql.startsWith("UPDATE") || sql.startsWith("DELETE") ||
               sql.startsWith("MERGE");
    }

    private String resultSetToJson(ResultSet rs) throws SQLException {
        ResultSetMetaData metaData = rs.getMetaData();
        int columnCount = metaData.getColumnCount();

        ObjectNode root = mapper.createObjectNode();
        root.put("status", "success");
        
        ArrayNode columns = root.putArray("columns");
        for (int i = 1; i <= columnCount; i++) {
            ObjectNode col = columns.addObject();
            col.put("name", metaData.getColumnName(i));
            col.put("type", metaData.getColumnTypeName(i));
        }

        ArrayNode rows = root.putArray("rows");
        while (rs.next()) {
            ObjectNode row = rows.addObject();
            for (int i = 1; i <= columnCount; i++) {
                String colName = metaData.getColumnName(i);
                Object value = rs.getObject(i);
                if (value == null) {
                    row.putNull(colName);
                } else {
                    row.put(colName, value.toString());
                }
            }
        }
        root.put("row_count", rows.size());

        return root.toString();
    }

    private String errorResponse(String message) {
        ObjectNode error = mapper.createObjectNode();
        error.put("status", "error");
        error.put("message", message);
        return error.toString();
    }
}
