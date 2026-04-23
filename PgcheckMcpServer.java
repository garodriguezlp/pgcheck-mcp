///usr/bin/env jbang "$0" "$@" ; exit $?

//JAVA 17+

//COMPILE_OPTIONS -encoding UTF-8
//RUNTIME_OPTIONS -Dfile.encoding=UTF-8 -Dstdout.encoding=UTF-8 -Djava.util.logging.manager=org.jboss.logmanager.LogManager

//DEPS io.quarkus:quarkus-bom:3.30.8@pom
//DEPS io.quarkiverse.mcp:quarkus-mcp-server-stdio:1.12.0
//DEPS io.quarkus:quarkus-jdbc-postgresql
//DEPS io.quarkus:quarkus-agroal
//DEPS com.fasterxml.jackson.core:jackson-databind:2.20.2

//Q:CONFIG quarkus.banner.enabled=false
//Q:CONFIG quarkus.log.level=INFO
//Q:CONFIG quarkus.log.console.enable=true
//Q:CONFIG quarkus.log.console.stderr=true
//Q:CONFIG quarkus.log.console.format=%d{yyyy-MM-dd HH:mm:ss,SSS} %-5p [%c{3.}] %s%e%n
//Q:CONFIG quarkus.log.file.enable=false

// Default Connection to Docker Playground
//Q:CONFIG quarkus.datasource.db-kind=postgresql
//Q:CONFIG quarkus.datasource.jdbc.url=jdbc:postgresql://localhost:5432/postgres
//Q:CONFIG quarkus.datasource.username=postgres
//Q:CONFIG quarkus.datasource.password=postgres

import io.quarkiverse.mcp.server.Tool;
import io.quarkus.runtime.Quarkus;
import io.quarkus.runtime.QuarkusApplication;
import io.quarkus.runtime.annotations.QuarkusMain;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Optional;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

@QuarkusMain
public class PgcheckMcpServer implements QuarkusApplication {

    private static final Logger LOG = Logger.getLogger(PgcheckMcpServer.class);

    @Override
    public int run(String... args) throws Exception {
        LOG.info("pgcheck-mcp server starting...");
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

    private static final Logger LOG = Logger.getLogger(PgcheckTools.class);

    @Inject
    DatabaseExecutor executor;

    @Tool(description = "Executes a SQL query against the connected PostgreSQL database.")
    public String execute_query(String sql) {
        LOG.infof("Tool execute_query called with SQL: %s", sql);
        return executor.executeQuery(sql);
    }

    @Tool(description = "Retrieves the database schema (tables and views).")
    public String get_schema() {
        LOG.info("Tool get_schema called");
        return executor.getSchema();
    }
}

/**
 * Handles SQL security and validation rules.
 */
@ApplicationScoped
class SqlValidator {

    private static final Logger LOG = Logger.getLogger(SqlValidator.class);

    @ConfigProperty(name = "pgcheck.allow-writes", defaultValue = "false")
    boolean allowWrites;

    public Optional<String> validate(String sql) {
        if (sql == null || sql.trim().isEmpty()) {
            return Optional.of("SQL query cannot be empty");
        }

        String cleanedSql = cleanSql(sql);
        String normalizedSql = cleanedSql.toUpperCase();

        if (isForbidden(normalizedSql)) {
            LOG.warnf("Blocked forbidden operation (DDL/Admin): %s", cleanedSql);
            return Optional.of("Forbidden operation: DDL or administrative commands are strictly blocked.");
        }

        if (isDml(normalizedSql) && !allowWrites) {
            LOG.warnf("Blocked DML operation (writes disabled): %s", cleanedSql);
            return Optional.of("DML operation (INSERT, UPDATE, DELETE) is blocked. Set 'pgcheck.allow-writes=true' to enable.");
        }

        return Optional.empty();
    }

    private String cleanSql(String sql) {
        // Remove multi-line comments /* ... */
        String noMultiLine = sql.replaceAll("/\\*[\\s\\S]*?\\*/", "");
        // Remove single-line comments -- ...
        String noSingleLine = noMultiLine.replaceAll("--.*", "");
        return noSingleLine.trim();
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
}

/**
 * Converts Database results and metadata into JSON strings.
 */
@ApplicationScoped
class ResultSerializer {

    private final ObjectMapper mapper = new ObjectMapper();

    public String toJson(ResultSet rs) throws SQLException {
        ResultSetMetaData metaData = rs.getMetaData();
        ObjectNode root = mapper.createObjectNode();
        root.put("status", "success");

        ArrayNode columns = root.putArray("columns");
        int columnCount = metaData.getColumnCount();
        for (int i = 1; i <= columnCount; i++) {
            ObjectNode col = columns.addObject();
            col.put("name", metaData.getColumnLabel(i));
            col.put("type", metaData.getColumnTypeName(i));
        }

        ArrayNode rows = root.putArray("rows");
        while (rs.next()) {
            ObjectNode row = rows.addObject();
            for (int i = 1; i <= columnCount; i++) {
                String colLabel = metaData.getColumnLabel(i);
                Object value = rs.getObject(i);
                if (value == null) {
                    row.putNull(colLabel);
                } else if (value instanceof Boolean b) {
                    row.put(colLabel, b);
                } else if (value instanceof Number n) {
                    if (value instanceof Integer iVal) row.put(colLabel, iVal);
                    else if (value instanceof Long lVal) row.put(colLabel, lVal);
                    else if (value instanceof Double dVal) row.put(colLabel, dVal);
                    else if (value instanceof java.math.BigDecimal bdVal) row.put(colLabel, bdVal);
                    else row.put(colLabel, n.toString());
                } else {
                    row.put(colLabel, value.toString());
                }
            }
        }

        root.put("row_count", rows.size());
        return root.toString();
    }

    public String toJson(int updateCount) {
        ObjectNode result = mapper.createObjectNode();
        result.put("status", "success");
        result.put("row_count", updateCount);
        return result.toString();
    }

    public String error(String message) {
        ObjectNode error = mapper.createObjectNode();
        error.put("status", "error");
        error.put("message", message);
        return error.toString();
    }

    public ObjectMapper mapper() {
        return mapper;
    }
}

/**
 * Inspects database metadata to build schema definitions.
 */
@ApplicationScoped
class SchemaInspector {

    private static final Logger LOG = Logger.getLogger(SchemaInspector.class);

    @Inject
    ResultSerializer serializer;

    public String inspect(DatabaseMetaData metaData) throws SQLException {
        LOG.debug("Starting schema inspection");
        ObjectNode root = serializer.mapper().createObjectNode();
        ArrayNode tablesNode = root.putArray("tables");

        try (ResultSet rs = metaData.getTables(null, null, "%", new String[]{"TABLE", "VIEW"})) {
            while (rs.next()) {
                String schemaName = rs.getString("TABLE_SCHEM");
                if (isSystemSchema(schemaName)) continue;

                String tableName = rs.getString("TABLE_NAME");
                ObjectNode tableNode = tablesNode.addObject();
                tableNode.put("schema", schemaName);
                tableNode.put("name", tableName);
                tableNode.put("type", rs.getString("TABLE_TYPE"));

                appendColumns(metaData, schemaName, tableName, tableNode.putArray("columns"));
            }
        }
        LOG.info("Schema inspection completed successfully");
        return root.toString();
    }

    private void appendColumns(DatabaseMetaData metaData, String schema, String table, ArrayNode columnsNode) throws SQLException {
        try (ResultSet rsCols = metaData.getColumns(null, schema, table, "%")) {
            while (rsCols.next()) {
                ObjectNode colNode = columnsNode.addObject();
                colNode.put("name", rsCols.getString("COLUMN_NAME"));
                colNode.put("type", rsCols.getString("TYPE_NAME"));
                colNode.put("nullable", rsCols.getString("IS_NULLABLE"));
            }
        }
    }

    private boolean isSystemSchema(String schemaName) {
        return schemaName == null || 
               "information_schema".equals(schemaName) || 
               "pg_catalog".equals(schemaName) ||
               schemaName.startsWith("pg_toast") ||
               schemaName.startsWith("pg_temp");
    }
}

/**
 * Orchestrates the execution of database operations.
 */
@ApplicationScoped
class DatabaseExecutor {

    private static final Logger LOG = Logger.getLogger(DatabaseExecutor.class);

    @Inject
    DataSource dataSource;

    @Inject
    SqlValidator validator;

    @Inject
    ResultSerializer serializer;

    @Inject
    SchemaInspector inspector;

    public String executeQuery(String sql) {
        return validator.validate(sql)
                .map(serializer::error)
                .orElseGet(() -> {
                    try (Connection conn = dataSource.getConnection();
                         Statement stmt = conn.createStatement()) {
                        LOG.debugf("Executing SQL: %s", sql);
                        if (stmt.execute(sql)) {
                            try (ResultSet rs = stmt.getResultSet()) {
                                String json = serializer.toJson(rs);
                                LOG.infof("Query executed successfully. Result rows: %d", rs.getRow()); // This might not work as expected with all drivers
                                return json;
                            }
                        }
                        int updateCount = stmt.getUpdateCount();
                        LOG.infof("Statement executed successfully. Update count: %d", updateCount);
                        return serializer.toJson(updateCount);
                    } catch (SQLException e) {
                        LOG.errorf(e, "SQL Execution failed: %s", e.getMessage());
                        return serializer.error("SQL Error: " + e.getMessage());
                    }
                });
    }

    public String getSchema() {
        try (Connection conn = dataSource.getConnection()) {
            return inspector.inspect(conn.getMetaData());
        } catch (SQLException e) {
            LOG.errorf(e, "Failed to fetch schema: %s", e.getMessage());
            return serializer.error("Error fetching schema: " + e.getMessage());
        }
    }
}
