package io.github.hiant.common.utils;

import org.springframework.expression.EvaluationContext;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.SimpleEvaluationContext;
import org.springframework.expression.spel.support.StandardEvaluationContext;

import java.lang.reflect.Method;
import java.util.Map;

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
        return createStandardEvaluationContext(null, null);
    }

    /**
     * Creates a standard evaluation context with the specified root object.
     *
     * @param rootObject the root object for the evaluation context
     * @return a new StandardEvaluationContext instance
     */
    public static EvaluationContext createStandardEvaluationContext(Object rootObject) {
        return createStandardEvaluationContext(rootObject, null);
    }

    /**
     * Creates a standard evaluation context with the specified root object and method variables.
     *
     * @param rootObject the root object for the evaluation context
     * @param methods    map of variable names to methods to be registered
     * @return a new StandardEvaluationContext instance
     */
    public static EvaluationContext createStandardEvaluationContext(Object rootObject, Map<String, Method> methods) {
        StandardEvaluationContext evaluationContext = new StandardEvaluationContext(rootObject);
        setVariables(evaluationContext, methods);
        return evaluationContext;
    }

    /**
     * Creates a read-only evaluation context with no root object and no variables.
     *
     * @return a new SimpleEvaluationContext instance configured for read-only use
     */
    public static EvaluationContext createReadonlyEvaluationContext() {
        return createReadonlyEvaluationContext(null, null);
    }

    /**
     * Creates a read-only evaluation context with the specified root object.
     *
     * @param rootObject the root object for the evaluation context
     * @return a new SimpleEvaluationContext instance configured for read-only use
     */
    public static EvaluationContext createReadonlyEvaluationContext(Object rootObject) {
        return createReadonlyEvaluationContext(rootObject, null);
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

        SimpleEvaluationContext evaluationContext = builder.build();
        setVariables(evaluationContext, methods);
        return evaluationContext;
    }

    /**
     * Sets variables into the given evaluation context.
     *
     * @param evaluationContext the context to set variables into
     * @param methods           map of variable names to methods to register
     * @return the same evaluation context with variables added
     */
    private static EvaluationContext setVariables(EvaluationContext evaluationContext, Map<String, Method> methods) {
        if (methods != null) {
            for (Map.Entry<String, Method> entry : methods.entrySet()) {
                String name = entry.getKey();
                Method method = entry.getValue();
                evaluationContext.setVariable(name, method);
            }
        }
        return evaluationContext;
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
}
