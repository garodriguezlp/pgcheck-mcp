///usr/bin/env jbang "$0" "$@" ; exit $?

//JAVA 17+

//DEPS io.quarkiverse.mcp:quarkus-mcp-server-stdio:1.1.0
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
import io.quarkus.runtime.Quarkus;
import io.quarkus.runtime.annotations.QuarkusMain;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import javax.sql.DataSource;
import java.sql.*;
import java.util.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import io.quarkus.runtime.QuarkusApplication;

@QuarkusMain
public class PgcheckMcpServer implements QuarkusApplication {

    @Override
    public int run(String... args) throws Exception {
        System.err.println("pgcheck-mcp server starting...");
        Quarkus.waitForExit();
        return 0;
    }

    public static void main(String... args) {
        Quarkus.run(PgcheckMcpServer.class, args);
    }
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

    @ConfigProperty(name = "pgcheck.allow-writes", defaultValue = "false")
    boolean allowWrites;

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

        if (isDml(normalizedSql) && !allowWrites) {
            return errorResponse("DML operation (INSERT, UPDATE, DELETE) is blocked. Set 'pgcheck.allow-writes=true' to enable.");
        }

        try (Connection conn = dataSource.getConnection();
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
        try (Connection conn = dataSource.getConnection()) {
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
