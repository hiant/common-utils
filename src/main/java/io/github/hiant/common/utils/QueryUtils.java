package io.github.hiant.common.utils;

import com.baomidou.dynamic.datasource.toolkit.DynamicDataSourceContextHolder;
import org.springframework.jdbc.core.BeanPropertyRowMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.util.StringUtils;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * Utility class for executing database queries with dynamic data source switching support.
 * This class provides static methods to execute various SQL queries while automatically
 * managing data source context based on the provided database key.
 */
public class QueryUtils {

    private QueryUtils() {
    }

    /**
     * Execute a query and return a list of results mapped by a RowMapper.
     *
     * @param jdbcTemplate The JdbcTemplate instance to use for the query.
     * @param databaseKey  The identifier of the target data source. If null or empty,
     *                     the default data source will be used.
     * @param sql          The SQL query to execute.
     * @param rowMapper    The RowMapper to map each row to a result object.
     * @param args         The arguments to bind to the query.
     * @param <T>          The type of the result objects.
     * @return A list of result objects mapped from the query results.
     * @throws IllegalStateException If any error occurs during the query execution,
     *                               wrapping the root cause with SQL and parameter details.
     */
    public static <T> List<T> select(JdbcTemplate jdbcTemplate, String databaseKey, String sql, RowMapper<T> rowMapper, Object... args) {
        return executeInDataSource(jdbcTemplate, databaseKey, template -> {
            try {
                return template.query(sql, rowMapper, args);
            } catch (Exception e) {
                throw wrapException(e, sql, args);
            }
        });
    }

    /**
     * Execute a query and return a single result object.
     *
     * @param jdbcTemplate The JdbcTemplate instance to use for the query.
     * @param databaseKey  The identifier of the target data source. If null or empty,
     *                     the default data source will be used.
     * @param clazz        The class of the result object.
     * @param sql          The SQL query to execute.
     * @param args         The arguments to bind to the query.
     * @param <T>          The type of the result object.
     * @return A single result object, or null if no result is found.
     * @throws IllegalStateException If any error occurs during the query execution,
     *                               wrapping the root cause with SQL and parameter details.
     */
    public static <T> T selectOne(JdbcTemplate jdbcTemplate, String databaseKey, Class<T> clazz, String sql, Object... args) {
        return executeInDataSource(jdbcTemplate, databaseKey, template -> {
            try {
                return template.queryForObject(sql, BeanPropertyRowMapper.newInstance(clazz), args);
            } catch (Exception e) {
                throw wrapException(e, sql, args);
            }
        });
    }

    /**
     * Execute a query and return a list of maps, where each map represents a row with column names as keys.
     *
     * @param jdbcTemplate The JdbcTemplate instance to use for the query.
     * @param databaseKey  The identifier of the target data source. If null or empty,
     *                     the default data source will be used.
     * @param sql          The SQL query to execute.
     * @param args         The arguments to bind to the query.
     * @return A list of maps, where each map represents a row of the query results.
     * @throws IllegalStateException If any error occurs during the query execution,
     *                               wrapping the root cause with SQL and parameter details.
     */
    public static List<Map<String, Object>> selectForList(JdbcTemplate jdbcTemplate, String databaseKey, String sql, Object... args) {
        return executeInDataSource(jdbcTemplate, databaseKey, template -> {
            try {
                return template.queryForList(sql, args);
            } catch (Exception e) {
                throw wrapException(e, sql, args);
            }
        });
    }

    /**
     * Execute a query that returns a single value (e.g., a count or aggregate function).
     *
     * @param jdbcTemplate The JdbcTemplate instance to use for the query.
     * @param databaseKey  The identifier of the target data source. If null or empty,
     *                     the default data source will be used.
     * @param sql          The SQL query to execute.
     * @param requiredType The required type of the result.
     * @param args         The arguments to bind to the query.
     * @param <T>          The type of the result value.
     * @return The single value result of the query.
     * @throws IllegalStateException If any error occurs during the query execution,
     *                               wrapping the root cause with SQL and parameter details.
     */
    public static <T> T selectForObject(JdbcTemplate jdbcTemplate, String databaseKey, String sql, Class<T> requiredType, Object... args) {
        return executeInDataSource(jdbcTemplate, databaseKey, template -> {
            try {
                return template.queryForObject(sql, args, requiredType);
            } catch (Exception e) {
                throw wrapException(e, sql, args);
            }
        });
    }

    // Private helper methods

    /**
     * Execute a database operation within the context of a specified data source.
     * If the databaseKey is null or empty, the operation will use the default data source
     * without switching the context.
     *
     * @param jdbcTemplate The JdbcTemplate instance to use for the operation.
     * @param databaseKey  The identifier of the target data source.
     * @param operation    The database operation to execute.
     * @param <T>          The type of the operation result.
     * @return The result of the database operation.
     */
    private static <T> T executeInDataSource(JdbcTemplate jdbcTemplate, String databaseKey, Function<JdbcTemplate, T> operation) {
        String originalDataSource = null;
        boolean contextSwitched = false;

        try {
            // Only switch data source if databaseKey is provided
            if (StringUtils.hasText(databaseKey)) {
                // Save current data source context
                originalDataSource = DynamicDataSourceContextHolder.peek();
                // Switch to the specified data source
                DynamicDataSourceContextHolder.push(databaseKey);
                contextSwitched = true;
            }

            // Execute the database operation using the provided JdbcTemplate
            return operation.apply(jdbcTemplate);
        } finally {
            // Restore the original data source context only if it was switched
            if (contextSwitched) {
                if (originalDataSource != null) {
                    // Restore to the original data source
                    DynamicDataSourceContextHolder.push(originalDataSource);
                } else {
                    // Clear the context if there was no original data source
                    DynamicDataSourceContextHolder.poll();
                }
            }
        }
    }

    /**
     * Wrap a database exception with a more informative message including the SQL and parameters.
     *
     * @param original The original exception thrown during database operation.
     * @param sql      The SQL statement being executed.
     * @param args     The arguments bound to the SQL statement.
     * @return An IllegalStateException wrapping the root cause with detailed message.
     */
    private static IllegalStateException wrapException(Exception original, String sql, Object... args) {
        Throwable rootCause = findRootCause(original);
        String message = String.format("Database query failed. SQL: [%s], Parameters: [%s]",
                sql, Arrays.toString(args));
        return new IllegalStateException(message, rootCause);
    }

    /**
     * Find the root cause of a Throwable by traversing the cause chain.
     *
     * @param t The Throwable to analyze.
     * @return The root cause Throwable.
     */
    private static Throwable findRootCause(Throwable t) {
        Throwable root = t;
        while (root.getCause() != null && root.getCause() != root) {
            root = root.getCause();
        }
        return root;
    }
}
