package io.github.hiant.common.utils;

import com.baomidou.dynamic.datasource.toolkit.DynamicDataSourceContextHolder;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.*;
import org.springframework.jdbc.support.rowset.SqlRowSet;
import org.springframework.lang.Nullable;
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

    /**
     * Private constructor to prevent instantiation of this utility class.
     */
    private QueryUtils() {
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
     * Wrap a database exception with a basic error message.
     *
     * @param original The original exception thrown during database operation.
     * @return An IllegalStateException wrapping the root cause with a basic message.
     */
    private static IllegalStateException wrapException(Exception original) {
        Throwable rootCause = findRootCause(original);
        return new IllegalStateException("Database query failed.", rootCause);
    }

    /**
     * Wrap a database exception with a message including the SQL.
     *
     * @param original The original exception thrown during database operation.
     * @param sql      The SQL statement being executed.
     * @return An IllegalStateException wrapping the root cause with a message including the SQL.
     */
    private static IllegalStateException wrapException(Exception original, String sql) {
        Throwable rootCause = findRootCause(original);
        String message = String.format("Database query failed. SQL: [%s]", sql);
        return new IllegalStateException(message, rootCause);
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

    /**
     * Query the database using the given JdbcTemplate, database key, SQL, and ResultSetExtractor.
     *
     * @param jdbcTemplate The JdbcTemplate instance to use for the query.
     * @param databaseKey  The identifier of the target data source. If null or empty, the default data source will be used.
     * @param sql          The SQL query to execute.
     * @param rse          The ResultSetExtractor to extract data from the result set.
     * @param <T>          The type of the result object.
     * @return The result of the query, or null if no result is found.
     * @throws DataAccessException If an error occurs during the query execution.
     */
    @Nullable
    public static <T> T query(JdbcTemplate jdbcTemplate, String databaseKey, String sql, ResultSetExtractor<T> rse) throws DataAccessException {
        return executeInDataSource(jdbcTemplate, databaseKey, template -> {
            try {
                return template.query(sql, rse);
            } catch (Exception e) {
                throw wrapException(e, sql);
            }
        });
    }

    /**
     * Query the database using the given JdbcTemplate, database key, SQL, and RowMapper.
     *
     * @param jdbcTemplate The JdbcTemplate instance to use for the query.
     * @param databaseKey  The identifier of the target data source. If null or empty, the default data source will be used.
     * @param sql          The SQL query to execute.
     * @param rowMapper    The RowMapper to map each row to a result object.
     * @param <T>          The type of the result objects.
     * @return A list of result objects mapped from the query results.
     * @throws DataAccessException If an error occurs during the query execution.
     */
    public static <T> List<T> query(JdbcTemplate jdbcTemplate, String databaseKey, String sql, RowMapper<T> rowMapper) throws DataAccessException {
        return executeInDataSource(jdbcTemplate, databaseKey, template -> {
            try {
                return template.query(sql, rowMapper);
            } catch (Exception e) {
                throw wrapException(e, sql);
            }
        });
    }

    /**
     * Query the database for a single row and return it as a map.
     *
     * @param jdbcTemplate The JdbcTemplate instance to use for the query.
     * @param databaseKey  The identifier of the target data source. If null or empty, the default data source will be used.
     * @param sql          The SQL query to execute.
     * @return A map representing the single row of the query results.
     * @throws DataAccessException If an error occurs during the query execution.
     */
    public static Map<String, Object> queryForMap(JdbcTemplate jdbcTemplate, String databaseKey, String sql) throws DataAccessException {
        return executeInDataSource(jdbcTemplate, databaseKey, template -> {
            try {
                return template.queryForMap(sql);
            } catch (Exception e) {
                throw wrapException(e, sql);
            }
        });
    }

    /**
     * Query the database for a single object using the given RowMapper.
     *
     * @param jdbcTemplate The JdbcTemplate instance to use for the query.
     * @param databaseKey  The identifier of the target data source. If null or empty, the default data source will be used.
     * @param sql          The SQL query to execute.
     * @param rowMapper    The RowMapper to map the single row to a result object.
     * @param <T>          The type of the result object.
     * @return The single result object, or null if no result is found.
     * @throws DataAccessException If an error occurs during the query execution.
     */
    @Nullable
    public static <T> T queryForObject(JdbcTemplate jdbcTemplate, String databaseKey, String sql, RowMapper<T> rowMapper) throws DataAccessException {
        return executeInDataSource(jdbcTemplate, databaseKey, template -> {
            try {
                return template.queryForObject(sql, rowMapper);
            } catch (Exception e) {
                throw wrapException(e, sql);
            }
        });
    }

    /**
     * Query the database for a single object of the specified type.
     *
     * @param jdbcTemplate The JdbcTemplate instance to use for the query.
     * @param databaseKey  The identifier of the target data source. If null or empty, the default data source will be used.
     * @param sql          The SQL query to execute.
     * @param requiredType The required type of the result object.
     * @param <T>          The type of the result object.
     * @return The single result object, or null if no result is found.
     * @throws DataAccessException If an error occurs during the query execution.
     */
    @Nullable
    public static <T> T queryForObject(JdbcTemplate jdbcTemplate, String databaseKey, String sql, Class<T> requiredType) throws DataAccessException {
        return executeInDataSource(jdbcTemplate, databaseKey, template -> {
            try {
                return template.queryForObject(sql, requiredType);
            } catch (Exception e) {
                throw wrapException(e, sql);
            }
        });
    }

    /**
     * Query the database for a list of objects of the specified type.
     *
     * @param jdbcTemplate The JdbcTemplate instance to use for the query.
     * @param databaseKey  The identifier of the target data source. If null or empty, the default data source will be used.
     * @param sql          The SQL query to execute.
     * @param elementType  The type of the elements in the list.
     * @param <T>          The type of the result objects.
     * @return A list of result objects of the specified type.
     * @throws DataAccessException If an error occurs during the query execution.
     */
    public static <T> List<T> queryForList(JdbcTemplate jdbcTemplate, String databaseKey, String sql, Class<T> elementType) throws DataAccessException {
        return executeInDataSource(jdbcTemplate, databaseKey, template -> {
            try {
                return template.queryForList(sql, elementType);
            } catch (Exception e) {
                throw wrapException(e, sql);
            }
        });
    }

    /**
     * Query the database for a list of maps, where each map represents a row with column names as keys.
     *
     * @param jdbcTemplate The JdbcTemplate instance to use for the query.
     * @param databaseKey  The identifier of the target data source. If null or empty, the default data source will be used.
     * @param sql          The SQL query to execute.
     * @return A list of maps, where each map represents a row of the query results.
     * @throws DataAccessException If an error occurs during the query execution.
     */
    public static List<Map<String, Object>> queryForList(JdbcTemplate jdbcTemplate, String databaseKey, String sql) throws DataAccessException {
        return executeInDataSource(jdbcTemplate, databaseKey, template -> {
            try {
                return template.queryForList(sql);
            } catch (Exception e) {
                throw wrapException(e, sql);
            }
        });
    }

    /**
     * Query the database for a SqlRowSet.
     *
     * @param jdbcTemplate The JdbcTemplate instance to use for the query.
     * @param databaseKey  The identifier of the target data source. If null or empty, the default data source will be used.
     * @param sql          The SQL query to execute.
     * @return A SqlRowSet representing the query results.
     * @throws DataAccessException If an error occurs during the query execution.
     */
    public static SqlRowSet queryForRowSet(JdbcTemplate jdbcTemplate, String databaseKey, String sql) throws DataAccessException {
        return executeInDataSource(jdbcTemplate, databaseKey, template -> {
            try {
                return template.queryForRowSet(sql);
            } catch (Exception e) {
                throw wrapException(e, sql);
            }
        });
    }


    /**
     * Query the database using a PreparedStatementCreator and a ResultSetExtractor.
     *
     * @param jdbcTemplate The JdbcTemplate instance to use for the query.
     * @param databaseKey  The identifier of the target data source. If null or empty, the default data source will be used.
     * @param psc          The PreparedStatementCreator to create the prepared statement.
     * @param rse          The ResultSetExtractor to extract data from the result set.
     * @param <T>          The type of the result object.
     * @return The result of the query, or null if no result is found.
     * @throws DataAccessException If an error occurs during the query execution.
     */
    @Nullable
    public static <T> T query(JdbcTemplate jdbcTemplate, String databaseKey, PreparedStatementCreator psc, ResultSetExtractor<T> rse) throws DataAccessException {

        return executeInDataSource(jdbcTemplate, databaseKey, template -> {
            try {
                return template.query(psc, rse);
            } catch (Exception e) {
                throw wrapException(e);
            }
        });
    }

    /**
     * Query the database using a SQL statement, a PreparedStatementSetter, and a ResultSetExtractor.
     *
     * @param jdbcTemplate The JdbcTemplate instance to use for the query.
     * @param databaseKey  The identifier of the target data source. If null or empty, the default data source will be used.
     * @param sql          The SQL query to execute.
     * @param pss          The PreparedStatementSetter to set the parameters of the prepared statement.
     * @param rse          The ResultSetExtractor to extract data from the result set.
     * @param <T>          The type of the result object.
     * @return The result of the query, or null if no result is found.
     * @throws DataAccessException If an error occurs during the query execution.
     */
    @Nullable
    public static <T> T query(JdbcTemplate jdbcTemplate, String databaseKey, String sql, PreparedStatementSetter pss, ResultSetExtractor<T> rse) throws DataAccessException {
        return executeInDataSource(jdbcTemplate, databaseKey, template -> {
            try {
                return template.query(sql, pss, rse);
            } catch (Exception e) {
                throw wrapException(e, sql);
            }
        });
    }

    /**
     * Query the database using a SQL statement, an array of arguments, an array of argument types, and a ResultSetExtractor.
     *
     * @param jdbcTemplate The JdbcTemplate instance to use for the query.
     * @param databaseKey  The identifier of the target data source. If null or empty, the default data source will be used.
     * @param sql          The SQL query to execute.
     * @param args         The arguments to bind to the query.
     * @param argTypes     The types of the arguments.
     * @param rse          The ResultSetExtractor to extract data from the result set.
     * @param <T>          The type of the result object.
     * @return The result of the query, or null if no result is found.
     * @throws DataAccessException If an error occurs during the query execution.
     */
    @Nullable
    public static <T> T query(JdbcTemplate jdbcTemplate, String databaseKey, String sql, Object[] args, int[] argTypes, ResultSetExtractor<T> rse) throws DataAccessException {
        return executeInDataSource(jdbcTemplate, databaseKey, template -> {
            try {
                return template.query(sql, args, argTypes, rse);
            } catch (Exception e) {
                throw wrapException(e, sql, args);
            }
        });
    }

    /**
     * Query the database using a SQL statement, an array of arguments, and a ResultSetExtractor.
     *
     * @param jdbcTemplate The JdbcTemplate instance to use for the query.
     * @param databaseKey  The identifier of the target data source. If null or empty, the default data source will be used.
     * @param sql          The SQL query to execute.
     * @param args         The arguments to bind to the query.
     * @param rse          The ResultSetExtractor to extract data from the result set.
     * @param <T>          The type of the result object.
     * @return The result of the query, or null if no result is found.
     * @throws DataAccessException If an error occurs during the query execution.
     */
    @Nullable
    public static <T> T query(JdbcTemplate jdbcTemplate, String databaseKey, String sql, Object[] args, ResultSetExtractor<T> rse) throws DataAccessException {
        return executeInDataSource(jdbcTemplate, databaseKey, template -> {
            try {
                return template.query(sql, args, rse);
            } catch (Exception e) {
                throw wrapException(e, sql, args);
            }
        });
    }

    /**
     * Query the database using a SQL statement, a ResultSetExtractor, and an array of arguments.
     *
     * @param jdbcTemplate The JdbcTemplate instance to use for the query.
     * @param databaseKey  The identifier of the target data source. If null or empty, the default data source will be used.
     * @param sql          The SQL query to execute.
     * @param rse          The ResultSetExtractor to extract data from the result set.
     * @param args         The arguments to bind to the query.
     * @param <T>          The type of the result object.
     * @return The result of the query, or null if no result is found.
     * @throws DataAccessException If an error occurs during the query execution.
     */
    @Nullable
    public static <T> T query(JdbcTemplate jdbcTemplate, String databaseKey, String sql, ResultSetExtractor<T> rse, Object... args) throws DataAccessException {
        return executeInDataSource(jdbcTemplate, databaseKey, template -> {
            try {
                return template.query(sql, rse, args);
            } catch (Exception e) {
                throw wrapException(e, sql, args);
            }
        });
    }

    /**
     * Query the database using a SQL statement, a PreparedStatementSetter, and a RowMapper.
     *
     * @param jdbcTemplate The JdbcTemplate instance to use for the query.
     * @param databaseKey  The identifier of the target data source. If null or empty, the default data source will be used.
     * @param sql          The SQL query to execute.
     * @param pss          The PreparedStatementSetter to set the parameters of the prepared statement.
     * @param rowMapper    The RowMapper to map each row to a result object.
     * @param <T>          The type of the result objects.
     * @return A list of result objects mapped from the query results.
     * @throws DataAccessException If an error occurs during the query execution.
     */
    public static <T> List<T> query(JdbcTemplate jdbcTemplate, String databaseKey, String sql, PreparedStatementSetter pss, RowMapper<T> rowMapper) throws DataAccessException {
        return executeInDataSource(jdbcTemplate, databaseKey, template -> {
            try {
                return template.query(sql, pss, rowMapper);
            } catch (Exception e) {
                throw wrapException(e, sql);
            }
        });
    }

    /**
     * Query the database using a SQL statement, an array of arguments, an array of argument types, and a RowMapper.
     *
     * @param jdbcTemplate The JdbcTemplate instance to use for the query.
     * @param databaseKey  The identifier of the target data source. If null or empty, the default data source will be used.
     * @param sql          The SQL query to execute.
     * @param args         The arguments to bind to the query.
     * @param argTypes     The types of the arguments.
     * @param rowMapper    The RowMapper to map each row to a result object.
     * @param <T>          The type of the result objects.
     * @return A list of result objects mapped from the query results.
     * @throws DataAccessException If an error occurs during the query execution.
     */
    public static <T> List<T> query(JdbcTemplate jdbcTemplate, String databaseKey, String sql, Object[] args, int[] argTypes, RowMapper<T> rowMapper) throws DataAccessException {
        return executeInDataSource(jdbcTemplate, databaseKey, template -> {
            try {
                return template.query(sql, args, argTypes, rowMapper);
            } catch (Exception e) {
                throw wrapException(e, sql, args);
            }
        });
    }

    /**
     * Query the database using a SQL statement, an array of arguments, and a RowMapper.
     *
     * @param jdbcTemplate The JdbcTemplate instance to use for the query.
     * @param databaseKey  The identifier of the target data source. If null or empty, the default data source will be used.
     * @param sql          The SQL query to execute.
     * @param args         The arguments to bind to the query.
     * @param rowMapper    The RowMapper to map each row to a result object.
     * @param <T>          The type of the result objects.
     * @return A list of result objects mapped from the query results.
     * @throws DataAccessException If an error occurs during the query execution.
     */
    public static <T> List<T> query(JdbcTemplate jdbcTemplate, String databaseKey, String sql, Object[] args, RowMapper<T> rowMapper) throws DataAccessException {
        return executeInDataSource(jdbcTemplate, databaseKey, template -> {
            try {
                return template.query(sql, args, rowMapper);
            } catch (Exception e) {
                throw wrapException(e, sql, args);
            }
        });
    }

    /**
     * Query the database using a SQL statement, a RowMapper, and an array of arguments.
     *
     * @param jdbcTemplate The JdbcTemplate instance to use for the query.
     * @param databaseKey  The identifier of the target data source. If null or empty, the default data source will be used.
     * @param sql          The SQL query to execute.
     * @param rowMapper    The RowMapper to map each row to a result object.
     * @param args         The arguments to bind to the query.
     * @param <T>          The type of the result objects.
     * @return A list of result objects mapped from the query results.
     * @throws DataAccessException If an error occurs during the query execution.
     */
    public static <T> List<T> query(JdbcTemplate jdbcTemplate, String databaseKey, String sql, RowMapper<T> rowMapper, Object... args) throws DataAccessException {
        return executeInDataSource(jdbcTemplate, databaseKey, template -> {
            try {
                return template.query(sql, rowMapper, args);
            } catch (Exception e) {
                throw wrapException(e, sql, args);
            }
        });
    }

    /**
     * Query the database for a single object using a SQL statement, an array of arguments, an array of argument types, and a RowMapper.
     *
     * @param jdbcTemplate The JdbcTemplate instance to use for the query.
     * @param databaseKey  The identifier of the target data source. If null or empty, the default data source will be used.
     * @param sql          The SQL query to execute.
     * @param args         The arguments to bind to the query.
     * @param argTypes     The types of the arguments.
     * @param rowMapper    The RowMapper to map the single row to a result object.
     * @param <T>          The type of the result object.
     * @return The single result object, or null if no result is found.
     * @throws DataAccessException If an error occurs during the query execution.
     */
    @Nullable
    public static <T> T queryForObject(JdbcTemplate jdbcTemplate, String databaseKey, String sql, Object[] args, int[] argTypes, RowMapper<T> rowMapper) throws DataAccessException {
        return executeInDataSource(jdbcTemplate, databaseKey, template -> {
            try {
                return template.queryForObject(sql, args, argTypes, rowMapper);
            } catch (Exception e) {
                throw wrapException(e, sql, args);
            }
        });
    }

    /**
     * Query the database for a single object using a SQL statement, an array of arguments, and a RowMapper.
     *
     * @param jdbcTemplate The JdbcTemplate instance to use for the query.
     * @param databaseKey  The identifier of the target data source. If null or empty, the default data source will be used.
     * @param sql          The SQL query to execute.
     * @param args         The arguments to bind to the query.
     * @param rowMapper    The RowMapper to map the single row to a result object.
     * @param <T>          The type of the result object.
     * @return The single result object, or null if no result is found.
     * @throws DataAccessException If an error occurs during the query execution.
     */
    @Nullable
    public static <T> T queryForObject(JdbcTemplate jdbcTemplate, String databaseKey, String sql, Object[] args, RowMapper<T> rowMapper) throws DataAccessException {
        return executeInDataSource(jdbcTemplate, databaseKey, template -> {
            try {
                return template.queryForObject(sql, args, rowMapper);
            } catch (Exception e) {
                throw wrapException(e, sql, args);
            }
        });
    }

    /**
     * Query the database for a single object using a SQL statement, a RowMapper, and an array of arguments.
     *
     * @param jdbcTemplate The JdbcTemplate instance to use for the query.
     * @param databaseKey  The identifier of the target data source. If null or empty, the default data source will be used.
     * @param sql          The SQL query to execute.
     * @param rowMapper    The RowMapper to map the single row to a result object.
     * @param args         The arguments to bind to the query.
     * @param <T>          The type of the result object.
     * @return The single result object, or null if no result is found.
     * @throws DataAccessException If an error occurs during the query execution.
     */
    @Nullable
    public static <T> T queryForObject(JdbcTemplate jdbcTemplate, String databaseKey, String sql, RowMapper<T> rowMapper, Object... args) throws DataAccessException {
        return executeInDataSource(jdbcTemplate, databaseKey, template -> {
            try {
                return template.queryForObject(sql, rowMapper, args);
            } catch (Exception e) {
                throw wrapException(e, sql, args);
            }
        });
    }

    /**
     * Query the database for a single object of the specified type using a SQL statement, an array of arguments, and an array of argument types.
     *
     * @param jdbcTemplate The JdbcTemplate instance to use for the query.
     * @param databaseKey  The identifier of the target data source. If null or empty, the default data source will be used.
     * @param sql          The SQL query to execute.
     * @param args         The arguments to bind to the query.
     * @param argTypes     The types of the arguments.
     * @param requiredType The required type of the result object.
     * @param <T>          The type of the result object.
     * @return The single result object, or null if no result is found.
     * @throws DataAccessException If an error occurs during the query execution.
     */
    @Nullable
    public static <T> T queryForObject(JdbcTemplate jdbcTemplate, String databaseKey, String sql, Object[] args, int[] argTypes, Class<T> requiredType) throws DataAccessException {
        return executeInDataSource(jdbcTemplate, databaseKey, template -> {
            try {
                return template.queryForObject(sql, args, argTypes, requiredType);
            } catch (Exception e) {
                throw wrapException(e, sql, args);
            }
        });
    }

    /**
     * Query the database for a single object of the specified type using a SQL statement and an array of arguments.
     *
     * @param jdbcTemplate The JdbcTemplate instance to use for the query.
     * @param databaseKey  The identifier of the target data source. If null or empty, the default data source will be used.
     * @param sql          The SQL query to execute.
     * @param args         The arguments to bind to the query.
     * @param requiredType The required type of the result object.
     * @param <T>          The type of the result object.
     * @return The single result object, or null if no result is found.
     * @throws DataAccessException If an error occurs during the query execution.
     */
    public static <T> T queryForObject(JdbcTemplate jdbcTemplate, String databaseKey, String sql, Object[] args, Class<T> requiredType) throws DataAccessException {
        return executeInDataSource(jdbcTemplate, databaseKey, template -> {
            try {
                return template.queryForObject(sql, args, requiredType);
            } catch (Exception e) {
                throw wrapException(e, sql, args);
            }
        });
    }

    /**
     * Query the database for a single object of the specified type using a SQL statement, a required type, and an array of arguments.
     *
     * @param jdbcTemplate The JdbcTemplate instance to use for the query.
     * @param databaseKey  The identifier of the target data source. If null or empty, the default data source will be used.
     * @param sql          The SQL query to execute.
     * @param requiredType The required type of the result object.
     * @param args         The arguments to bind to the query.
     * @param <T>          The type of the result object.
     * @return The single result object, or null if no result is found.
     * @throws DataAccessException If an error occurs during the query execution.
     */
    public static <T> T queryForObject(JdbcTemplate jdbcTemplate, String databaseKey, String sql, Class<T> requiredType, Object... args) throws DataAccessException {
        return executeInDataSource(jdbcTemplate, databaseKey, template -> {
            try {
                return template.queryForObject(sql, requiredType, args);
            } catch (Exception e) {
                throw wrapException(e, sql, args);
            }
        });
    }

    /**
     * Query the database for a single row and return it as a map using a SQL statement, an array of arguments, and an array of argument types.
     *
     * @param jdbcTemplate The JdbcTemplate instance to use for the query.
     * @param databaseKey  The identifier of the target data source. If null or empty, the default data source will be used.
     * @param sql          The SQL query to execute.
     * @param args         The arguments to bind to the query.
     * @param argTypes     The types of the arguments.
     * @return A map representing the single row of the query results.
     * @throws DataAccessException If an error occurs during the query execution.
     */
    public static Map<String, Object> queryForMap(JdbcTemplate jdbcTemplate, String databaseKey, String sql, Object[] args, int[] argTypes) throws DataAccessException {
        return executeInDataSource(jdbcTemplate, databaseKey, template -> {
            try {
                return template.queryForMap(sql, args, argTypes);
            } catch (Exception e) {
                throw wrapException(e, sql, args);
            }
        });
    }

    /**
     * Query the database for a single row and return it as a map using a SQL statement and an array of arguments.
     *
     * @param jdbcTemplate The JdbcTemplate instance to use for the query.
     * @param databaseKey  The identifier of the target data source. If null or empty, the default data source will be used.
     * @param sql          The SQL query to execute.
     * @param args         The arguments to bind to the query.
     * @return A map representing the single row of the query results.
     * @throws DataAccessException If an error occurs during the query execution.
     */
    public static Map<String, Object> queryForMap(JdbcTemplate jdbcTemplate, String databaseKey, String sql, Object... args) throws DataAccessException {
        return executeInDataSource(jdbcTemplate, databaseKey, template -> {
            try {
                return template.queryForMap(sql, args);
            } catch (Exception e) {
                throw wrapException(e, sql, args);
            }
        });
    }

    /**
     * Query the database for a list of objects of the specified type using a SQL statement, an array of arguments, an array of argument types, and an element type.
     *
     * @param jdbcTemplate The JdbcTemplate instance to use for the query.
     * @param databaseKey  The identifier of the target data source. If null or empty, the default data source will be used.
     * @param sql          The SQL query to execute.
     * @param args         The arguments to bind to the query.
     * @param argTypes     The types of the arguments.
     * @param elementType  The type of the elements in the list.
     * @param <T>          The type of the result objects.
     * @return A list of result objects of the specified type.
     * @throws DataAccessException If an error occurs during the query execution.
     */
    public static <T> List<T> queryForList(JdbcTemplate jdbcTemplate, String databaseKey, String sql, Object[] args, int[] argTypes, Class<T> elementType) throws DataAccessException {
        return executeInDataSource(jdbcTemplate, databaseKey, template -> {
            try {
                return template.queryForList(sql, args, argTypes, elementType);
            } catch (Exception e) {
                throw wrapException(e, sql, args);
            }
        });
    }

    /**
     * Query the database for a list of objects of the specified type using a SQL statement, an array of arguments, and an element type.
     *
     * @param jdbcTemplate The JdbcTemplate instance to use for the query.
     * @param databaseKey  The identifier of the target data source. If null or empty, the default data source will be used.
     * @param sql          The SQL query to execute.
     * @param args         The arguments to bind to the query.
     * @param elementType  The type of the elements in the list.
     * @param <T>          The type of the result objects.
     * @return A list of result objects of the specified type.
     * @throws DataAccessException If an error occurs during the query execution.
     */
    public static <T> List<T> queryForList(JdbcTemplate jdbcTemplate, String databaseKey, String sql, Object[] args, Class<T> elementType) throws DataAccessException {
        return executeInDataSource(jdbcTemplate, databaseKey, template -> {
            try {
                return template.queryForList(sql, args, elementType);
            } catch (Exception e) {
                throw wrapException(e, sql, args);
            }
        });
    }

    /**
     * Query the database for a list of objects of the specified type using a SQL statement, an element type, and an array of arguments.
     *
     * @param jdbcTemplate The JdbcTemplate instance to use for the query.
     * @param databaseKey  The identifier of the target data source. If null or empty, the default data source will be used.
     * @param sql          The SQL query to execute.
     * @param elementType  The type of the elements in the list.
     * @param args         The arguments to bind to the query.
     * @param <T>          The type of the result objects.
     * @return A list of result objects of the specified type.
     * @throws DataAccessException If an error occurs during the query execution.
     */
    public static <T> List<T> queryForList(JdbcTemplate jdbcTemplate, String databaseKey, String sql, Class<T> elementType, Object... args) throws DataAccessException {
        return executeInDataSource(jdbcTemplate, databaseKey, template -> {
            try {
                return template.queryForList(sql, elementType, args);
            } catch (Exception e) {
                throw wrapException(e, sql, args);
            }
        });
    }

    /**
     * Query the database for a list of maps, where each map represents a row with column names as keys, using a SQL statement, an array of arguments, and an array of argument types.
     *
     * @param jdbcTemplate The JdbcTemplate instance to use for the query.
     * @param databaseKey  The identifier of the target data source. If null or empty, the default data source will be used.
     * @param sql          The SQL query to execute.
     * @param args         The arguments to bind to the query.
     * @param argTypes     The types of the arguments.
     * @return A list of maps, where each map represents a row of the query results.
     * @throws DataAccessException If an error occurs during the query execution.
     */
    public static List<Map<String, Object>> queryForList(JdbcTemplate jdbcTemplate, String databaseKey, String sql, Object[] args, int[] argTypes) throws DataAccessException {
        return executeInDataSource(jdbcTemplate, databaseKey, template -> {
            try {
                return template.queryForList(sql, args, argTypes);
            } catch (Exception e) {
                throw wrapException(e, sql, args);
            }
        });
    }

    /**
     * Query the database for a list of maps, where each map represents a row with column names as keys, using a SQL statement and an array of arguments.
     *
     * @param jdbcTemplate The JdbcTemplate instance to use for the query.
     * @param databaseKey  The identifier of the target data source. If null or empty, the default data source will be used.
     * @param sql          The SQL query to execute.
     * @param args         The arguments to bind to the query.
     * @return A list of maps, where each map represents a row of the query results.
     * @throws DataAccessException If an error occurs during the query execution.
     */
    public static List<Map<String, Object>> queryForList(JdbcTemplate jdbcTemplate, String databaseKey, String sql, Object... args) throws DataAccessException {
        return executeInDataSource(jdbcTemplate, databaseKey, template -> {
            try {
                return template.queryForList(sql, args);
            } catch (Exception e) {
                throw wrapException(e, sql, args);
            }
        });
    }

    /**
     * Query the database for a SqlRowSet using a SQL statement, an array of arguments, and an array of argument types.
     *
     * @param jdbcTemplate The JdbcTemplate instance to use for the query.
     * @param databaseKey  The identifier of the target data source. If null or empty, the default data source will be used.
     * @param sql          The SQL query to execute.
     * @param args         The arguments to bind to the query.
     * @param argTypes     The types of the arguments.
     * @return A SqlRowSet representing the query results.
     * @throws DataAccessException If an error occurs during the query execution.
     */
    public static SqlRowSet queryForRowSet(JdbcTemplate jdbcTemplate, String databaseKey, String sql, Object[] args, int[] argTypes) throws DataAccessException {
        return executeInDataSource(jdbcTemplate, databaseKey, template -> {
            try {
                return template.queryForRowSet(sql, args, argTypes);
            } catch (Exception e) {
                throw wrapException(e, sql, args);
            }
        });
    }

    /**
     * Query the database for a SqlRowSet using a SQL statement and an array of arguments.
     *
     * @param jdbcTemplate The JdbcTemplate instance to use for the query.
     * @param databaseKey  The identifier of the target data source. If null or empty, the default data source will be used.
     * @param sql          The SQL query to execute.
     * @param args         The arguments to bind to the query.
     * @return A SqlRowSet representing the query results.
     * @throws DataAccessException If an error occurs during the query execution.
     */
    public static SqlRowSet queryForRowSet(JdbcTemplate jdbcTemplate, String databaseKey, String sql, Object... args) throws DataAccessException {
        return executeInDataSource(jdbcTemplate, databaseKey, template -> {
            try {
                return template.queryForRowSet(sql, args);
            } catch (Exception e) {
                throw wrapException(e, sql, args);
            }
        });
    }
}
