package io.github.hiant.common.utils;

import org.springframework.expression.EvaluationContext;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.SimpleEvaluationContext;
import org.springframework.expression.spel.support.StandardEvaluationContext;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Utility class for working with Spring Expression Language (SpEL).
 * Provides methods to create evaluation contexts, evaluate expressions,
 * and retrieve values of different types from SpEL expressions.
 *
 * <p>This class is not thread-safe but can be used in read-only scenarios.
 *
 * @author liudong.work@gmail.com Created at: 2025/6/10 16:19
 */
public class SpelUtils {

    /**
     * Cache for static methods from utility classes.
     */
    private static final Map<Class<?>, Map<String, Method>> STATIC_METHOD_CACHE = new ConcurrentHashMap<>();

    /**
     * Shared instance of SpEL expression parser.
     */
    private static final SpelExpressionParser PARSER = new SpelExpressionParser();

    /**
     * Private constructor to prevent instantiation of this utility class.
     */
    private SpelUtils() {
    }

    /**
     * Creates a standard evaluation context with no root object and no variables.
     *
     * @return a new StandardEvaluationContext instance
     */
    public static EvaluationContext createStandardEvaluationContext() {
        return createStandardEvaluationContext(null, (Map<String, Method>) null);
    }

    /**
     * Creates a standard evaluation context with the specified root object.
     *
     * @param rootObject the root object for the evaluation context
     * @return a new StandardEvaluationContext instance
     */
    public static EvaluationContext createStandardEvaluationContext(Object rootObject) {
        return createStandardEvaluationContext(rootObject, (Map<String, Method>) null);
    }

    /**
     * Creates a standard evaluation context with the specified root object and method variables.
     *
     * @param rootObject the root object for the evaluation context
     * @param methods    map of variable names to methods to be registered
     * @return a new StandardEvaluationContext instance
     */
    public static EvaluationContext createStandardEvaluationContext(Object rootObject, Map<String, Method> methods) {
        StandardEvaluationContext context = new StandardEvaluationContext(rootObject);
        setVariables(context, methods);
        return context;
    }

    /**
     * Creates a standard evaluation context with the specified root object and method variables.
     *
     * @param rootObject the root object for the evaluation context
     * @param clazz      one or more utility classes whose static methods should be registered
     * @return a new StandardEvaluationContext instance
     */
    public static EvaluationContext createStandardEvaluationContext(Object rootObject, Class<?>... clazz) {
        StandardEvaluationContext context = new StandardEvaluationContext(rootObject);

        // Register static methods from utility classes
        if (clazz != null) {
            for (Class<?> toolClass : clazz) {
                registerStaticMethods(context, toolClass);
            }
        }
        return context;
    }

    /**
     * Creates a read-only evaluation context with no root object and no variables.
     *
     * @return a new SimpleEvaluationContext instance configured for read-only use
     */
    public static EvaluationContext createReadonlyEvaluationContext() {
        return createReadonlyEvaluationContext(null, (Map<String, Method>) null);
    }

    /**
     * Creates a read-only evaluation context with the specified root object.
     *
     * @param rootObject the root object for the evaluation context
     * @return a new SimpleEvaluationContext instance configured for read-only use
     */
    public static EvaluationContext createReadonlyEvaluationContext(Object rootObject) {
        return createReadonlyEvaluationContext(rootObject, (Map<String, Method>) null);
    }

    /**
     * Creates a read-only evaluation context with the specified root object and method variables.
     *
     * @param rootObject the root object for the evaluation context
     * @param methods    map of variable names to methods to be registered
     * @return a new SimpleEvaluationContext instance configured for read-only use
     */
    public static EvaluationContext createReadonlyEvaluationContext(Object rootObject, Map<String, Method> methods) {
        SimpleEvaluationContext.Builder builder = SimpleEvaluationContext.forReadOnlyDataBinding().withInstanceMethods();

        if (rootObject != null) {
            builder.withRootObject(rootObject);
        }

        SimpleEvaluationContext context = builder.build();
        setVariables(context, methods);
        return context;
    }

    /**
     * Creates a read-only evaluation context with the given root object and static utility classes.
     * All public static methods from the provided classes will be registered as variables.
     *
     * @param rootObject the root object to bind to (can be null)
     * @param clazz      one or more utility classes whose static methods should be registered
     * @return a configured read-only SimpleEvaluationContext
     */
    public static EvaluationContext createReadonlyEvaluationContext(Object rootObject, Class<?>... clazz) {
        SimpleEvaluationContext.Builder builder = SimpleEvaluationContext.forReadOnlyDataBinding().withInstanceMethods();

        if (rootObject != null) {
            builder.withRootObject(rootObject);
        }

        SimpleEvaluationContext context = builder.build();

        // Register static methods from utility classes
        if (clazz != null) {
            for (Class<?> toolClass : clazz) {
                registerStaticMethods(context, toolClass);
            }
        }

        return context;
    }

    /**
     * Registers all public static methods of a given class as variables in the evaluation context.
     * Method names will be used as variable names.
     *
     * @param context   the evaluation context to configure
     * @param toolClass the class containing static utility methods
     */
    private static void registerStaticMethods(EvaluationContext context, Class<?> toolClass) {
        // Retrieve from cache or scan and cache if first time
        Map<String, Method> staticMethods = STATIC_METHOD_CACHE.computeIfAbsent(toolClass, cls -> {
            Map<String, Method> methods = new HashMap<>();
            for (Method method : cls.getMethods()) {
                if (Modifier.isStatic(method.getModifiers())) {
                    methods.put(method.getName(), method);
                }
            }
            return methods;
        });

        setVariables(context, staticMethods);
    }

    /**
     * Sets the provided methods as variables in the evaluation context.
     *
     * @param context the evaluation context
     * @param methods map of variable names to Method instances
     */
    private static void setVariables(EvaluationContext context, Map<String, Method> methods) {
        if (methods != null) {
            for (Map.Entry<String, Method> entry : methods.entrySet()) {
                context.setVariable(entry.getKey(), entry.getValue());
            }
        }
    }

    /**
     * Evaluates the given SpEL expression in the provided context and checks if the result is false.
     *
     * @param context    the evaluation context
     * @param expression the SpEL expression to evaluate
     * @return true if the evaluated result is Boolean.FALSE; otherwise false
     * @throws IllegalStateException if an error occurs during evaluation
     */
    public static boolean isFalse(EvaluationContext context, String expression) {
        try {
            return Boolean.FALSE.equals(PARSER.parseExpression(expression).getValue(context, Boolean.class));
        } catch (Exception e) {
            throw new IllegalStateException("failed to get Boolean from expression: " + expression, e);
        }
    }

    /**
     * Evaluates the given SpEL expression in the provided context and checks if the result is true.
     *
     * @param context    the evaluation context
     * @param expression the SpEL expression to evaluate
     * @return true if the evaluated result is Boolean.TRUE; otherwise false
     * @throws IllegalStateException if an error occurs during evaluation
     */
    public static boolean isTrue(EvaluationContext context, String expression) {
        try {
            return Boolean.TRUE.equals(PARSER.parseExpression(expression).getValue(context, Boolean.class));
        } catch (Exception e) {
            throw new IllegalStateException("failed to get Boolean from expression: " + expression, e);
        }
    }

    /**
     * Evaluates the given SpEL expression and returns the result as an Object.
     *
     * @param context    the evaluation context
     * @param expression the SpEL expression to evaluate
     * @return the result of the expression evaluation
     * @throws IllegalStateException if an error occurs during evaluation
     */
    public static Object get(EvaluationContext context, String expression) {
        try {
            return PARSER.parseExpression(expression).getValue(context, Object.class);
        } catch (Exception e) {
            throw new IllegalStateException("failed to get Object from expression: " + expression, e);
        }
    }

    /**
     * Evaluates the given SpEL expression and returns the result as a String.
     *
     * @param context    the evaluation context
     * @param expression the SpEL expression to evaluate
     * @return the result of the expression evaluation as a String
     * @throws IllegalStateException if an error occurs during evaluation
     */
    public static String getString(EvaluationContext context, String expression) {
        try {
            return PARSER.parseExpression(expression).getValue(context, String.class);
        } catch (Exception e) {
            throw new IllegalStateException("failed to get String from expression: " + expression, e);
        }
    }

    /**
     * Evaluates the given SpEL expression and returns the result as an int value.
     *
     * @param context    the evaluation context
     * @param expression the SpEL expression to evaluate
     * @return the result of the expression evaluation as an int
     * @throws IllegalStateException if an error occurs during evaluation or the result is null
     */
    public static int getInt(EvaluationContext context, String expression) {
        try {
            Integer result = PARSER.parseExpression(expression).getValue(context, Integer.class);
            if (result == null) {
                throw new IllegalStateException("failed to get Integer from expression: " + expression);
            }
            return result;
        } catch (Exception e) {
            throw new IllegalStateException("failed to get Integer from expression: " + expression, e);
        }
    }

    /**
     * Evaluates the given SpEL expression and returns the result as a long value.
     *
     * @param context    the evaluation context
     * @param expression the SpEL expression to evaluate
     * @return the result of the expression evaluation as a long
     * @throws IllegalStateException if an error occurs during evaluation or the result is null
     */
    public static long getLong(EvaluationContext context, String expression) {
        try {
            Long result = PARSER.parseExpression(expression).getValue(context, Long.class);
            if (result == null) {
                throw new IllegalStateException("failed to get Long from expression: " + expression);
            }
            return result;
        } catch (Exception e) {
            throw new IllegalStateException("failed to get Long from expression: " + expression, e);
        }
    }

    /**
     * Evaluates the given SpEL expression and returns the result as a float value.
     *
     * @param context    the evaluation context
     * @param expression the SpEL expression to evaluate
     * @return the result of the expression evaluation as a float
     * @throws IllegalStateException if an error occurs during evaluation or the result is null
     */
    public static double getFloat(EvaluationContext context, String expression) {
        try {
            Float result = PARSER.parseExpression(expression).getValue(context, Float.class);
            if (result == null) {
                throw new IllegalStateException("failed to get Float from expression: " + expression);
            }
            return result;
        } catch (Exception e) {
            throw new IllegalStateException("failed to get Float from expression: " + expression, e);
        }
    }

    /**
     * Evaluates the given SpEL expression and returns the result as a double value.
     *
     * @param context    the evaluation context
     * @param expression the SpEL expression to evaluate
     * @return the result of the expression evaluation as a long
     * @throws IllegalStateException if an error occurs during evaluation or the result is null
     */
    public static double getDouble(EvaluationContext context, String expression) {
        try {
            Double result = PARSER.parseExpression(expression).getValue(context, Double.class);
            if (result == null) {
                throw new IllegalStateException("failed to get Double from expression: " + expression);
            }
            return result;
        } catch (Exception e) {
            throw new IllegalStateException("failed to get Double from expression: " + expression, e);
        }
    }

    /**
     * Evaluates the given SpEL expression and returns the result as a List.
     *
     * @param context    the evaluation context
     * @param expression the SpEL expression to evaluate
     * @return the result of the expression evaluation as a List
     * @throws IllegalStateException if an error occurs during evaluation or the result is null
     */
    public static List<?> getList(EvaluationContext context, String expression) {
        try {
            List<?> result = PARSER.parseExpression(expression).getValue(context, List.class);
            if (result == null) {
                throw new IllegalStateException("failed to get List from expression: " + expression);
            }
            return result;
        } catch (Exception e) {
            throw new IllegalStateException("failed to get List from expression: " + expression, e);
        }
    }
}
