package io.github.hiant.common.utils;

import lombok.Getter;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.config.AutowireCapableBeanFactory;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationEvent;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.MessageSourceResolvable;
import org.springframework.context.NoSuchMessageException;
import org.springframework.core.ResolvableType;
import org.springframework.core.env.Environment;
import org.springframework.core.io.Resource;
import org.springframework.lang.Nullable;

import java.io.IOException;
import java.lang.annotation.Annotation;
import java.util.Locale;
import java.util.Map;

/**
 * Utility class for accessing Spring application context.
 * <p>
 * This class provides static methods to access and manipulate the Spring ApplicationContext,
 * including retrieving beans, publishing events, getting bean definitions, etc.
 * </p>
 *
 * @author liudong.work@gmail.com
 * Created at: 2025/6/11 10:44
 */
public class SpringContextUtils {

    /**
     * Holds the current application context instance.
     */
    @Getter
    private static ApplicationContext applicationContext;

    /**
     * Sets the application context.
     *
     * @param applicationContext The ApplicationContext to set.
     */
    /**
     * Sets the Spring ApplicationContext.
     *
     * @param applicationContext The ApplicationContext to set.
     */
    public static void setApplicationContext(ApplicationContext applicationContext) {
        SpringContextUtils.applicationContext = applicationContext;
    }

    /**
     * Returns the ID of this application context.
     *
     * @return The ID of this application context, or {@code null} if not available.
     */
    @Nullable
    public static String getId() {
        return applicationContext.getId();
    }

    /**
     * Checks if this context contains a bean definition or a bean instance,
     * which might be registered by a post-processor or obtained by an {@link org.springframework.beans.factory.ObjectFactory}.
     *
     * @param s The name of the bean to query.
     * @return {@code true} if this context contains a bean with the given name, {@code false} otherwise.
     */
    public static boolean containsLocalBean(String s) {
        return applicationContext.containsLocalBean(s);
    }

    /**
     * Return the timestamp when this context was first loaded.
     *
     * @return The startup date in milliseconds since the epoch.
     */
    public static long getStartupDate() {
        return applicationContext.getStartupDate();
    }

    /**
     * Determine whether the bean with the given name matches the specified type.
     *
     * @param s              The name of the bean to query.
     * @param resolvableType The type to check against.
     * @return {@code true} if the bean matches the specified type, {@code false} otherwise.
     * @throws NoSuchBeanDefinitionException If there is no bean with the given name.
     */
    public static boolean isTypeMatch(String s, ResolvableType resolvableType) throws NoSuchBeanDefinitionException {
        return applicationContext.isTypeMatch(s, resolvableType);
    }

    /**
     * Return the bean instance that uniquely matches the given object type, if any.
     *
     * @param aClass The type of the bean to retrieve.
     * @param <T>    The type of the bean.
     * @return The bean instance.
     * @throws BeansException If the bean could not be found or created.
     */
    public static <T> T getBean(Class<T> aClass) throws BeansException {
        return applicationContext.getBean(aClass);
    }

    /**
     * Return the name of the application that this context is part of.
     *
     * @return The application name, or an empty string if none.
     */
    public static String getApplicationName() {
        return applicationContext.getApplicationName();
    }

    /**
     * Return the bean instances that match the given object type (including subclasses),
     * optionally excluding non-singletons and/or lazily initialized ones.
     *
     * @param aClass The type of the bean to retrieve.
     * @param b      Whether to include non-singletons.
     * @param b1     Whether to include lazily initialized beans.
     * @param <T>    The type of the bean.
     * @return A map of bean names to bean instances.
     * @throws BeansException If the beans could not be found or created.
     */
    public static <T> Map<String, T> getBeansOfType(Class<T> aClass, boolean b, boolean b1) throws BeansException {
        return applicationContext.getBeansOfType(aClass, b, b1);
    }

    /**
     * Determine whether the bean with the given name matches the specified type.
     *
     * @param s      The name of the bean to query.
     * @param aClass The type to check against.
     * @return {@code true} if the bean matches the specified type, {@code false} otherwise.
     * @throws NoSuchBeanDefinitionException If there is no bean with the given name.
     */
    public static boolean isTypeMatch(String s, Class<?> aClass) throws NoSuchBeanDefinitionException {
        return applicationContext.isTypeMatch(s, aClass);
    }

    /**
     * Return the ClassLoader used by this context.
     *
     * @return The ClassLoader, or {@code null} if none.
     */
    @Nullable
    public static ClassLoader getClassLoader() {
        return applicationContext.getClassLoader();
    }

    /**
     * Return the bean instance that uniquely matches the given object type, if any.
     *
     * @param aClass  The type of the bean to retrieve.
     * @param objects Arguments to use when creating a bean instance.
     * @param <T>     The type of the bean.
     * @return The bean instance.
     * @throws BeansException If the bean could not be found or created.
     */
    public static <T> T getBean(Class<T> aClass, Object... objects) throws BeansException {
        return applicationContext.getBean(aClass, objects);
    }

    /**
     * Return a friendly name for this context.
     *
     * @return The display name of this context.
     */
    public static String getDisplayName() {
        return applicationContext.getDisplayName();
    }

    /**
     * Publish an event to all registered listeners.
     *
     * @param event The event to publish.
     */
    public static void publishEvent(ApplicationEvent event) {
        applicationContext.publishEvent(event);
    }

    /**
     * Return a Resource handle for the specified resource location.
     *
     * @param s The resource location.
     * @return A Resource handle.
     * @throws IOException If the resource cannot be resolved.
     */
    public static Resource[] getResources(String s) throws IOException {
        return applicationContext.getResources(s);
    }

    /**
     * Determine the type of the bean with the given name.
     *
     * @param s The name of the bean to query.
     * @return The type of the bean, or {@code null} if not determinable.
     * @throws NoSuchBeanDefinitionException If there is no bean with the given name.
     */
    @Nullable
    public static Class<?> getType(String s) throws NoSuchBeanDefinitionException {
        return applicationContext.getType(s);
    }

    /**
     * Return the names of beans that are annotated with the given annotation type.
     *
     * @param aClass The annotation type to look for.
     * @return An array of bean names.
     */
    public static String[] getBeanNamesForAnnotation(Class<? extends Annotation> aClass) {
        return applicationContext.getBeanNamesForAnnotation(aClass);
    }

    /**
     * Return the parent context, or {@code null} if there is no parent context.
     *
     * @return The parent ApplicationContext, or {@code null}.
     */
    @Nullable
    public static ApplicationContext getParent() {
        return applicationContext.getParent();
    }

    /**
     * Try to retrieve the given message, using the given parameters and default.
     *
     * @param s       The code of the message to retrieve.
     * @param objects Arguments for the message.
     * @param s1      The default message to return if no message is found.
     * @param locale  The locale to use.
     * @return The resolved message.
     */
    @Nullable
    public static String getMessage(String s, Object[] objects, String s1, Locale locale) {
        return applicationContext.getMessage(s, objects, s1, locale);
    }

    /**
     * Publish an event to all registered listeners.
     *
     * @param o The event to publish.
     */
    public static void publishEvent(Object o) {
        applicationContext.publishEvent(o);
    }

    /**
     * Return an {@link ObjectProvider} that provides access to the bean instance that uniquely matches the given object type.
     *
     * @param aClass The type of the bean to retrieve.
     * @param <T>    The type of the bean.
     * @return An ObjectProvider for the bean.
     */
    public static <T> ObjectProvider<T> getBeanProvider(Class<T> aClass) {
        return applicationContext.getBeanProvider(aClass);
    }

    /**
     * Return the parent BeanFactory, or {@code null} if there is no parent BeanFactory.
     *
     * @return The parent BeanFactory, or {@code null}.
     */
    @Nullable
    public static BeanFactory getParentBeanFactory() {
        return applicationContext.getParentBeanFactory();
    }

    /**
     * Return the AutowireCapableBeanFactory for this context.
     *
     * @return The AutowireCapableBeanFactory.
     * @throws IllegalStateException If the context does not support autowiring.
     */
    public static AutowireCapableBeanFactory getAutowireCapableBeanFactory() throws IllegalStateException {
        return applicationContext.getAutowireCapableBeanFactory();
    }

    /**
     * Return the bean instances that are annotated with the given annotation type.
     *
     * @param aClass The annotation type to look for.
     * @return A map of bean names to bean instances.
     * @throws BeansException If the beans could not be found or created.
     */
    public static Map<String, Object> getBeansWithAnnotation(Class<? extends Annotation> aClass) throws BeansException {
        return applicationContext.getBeansWithAnnotation(aClass);
    }

    /**
     * Determine the type of the bean with the given name.
     *
     * @param s The name of the bean to query.
     * @param b Whether to allow resolution of a bean definition name to a corresponding class.
     * @return The type of the bean, or {@code null} if not determinable.
     * @throws NoSuchBeanDefinitionException If there is no bean with the given name.
     */
    @Nullable
    public static Class<?> getType(String s, boolean b) throws NoSuchBeanDefinitionException {
        return applicationContext.getType(s, b);
    }

    /**
     * Return the names of beans matching the given type (including subclasses),
     * optionally excluding non-singletons and/or lazily initialized ones.
     *
     * @param resolvableType The type to look for.
     * @param b              Whether to include non-singletons.
     * @param b1             Whether to include lazily initialized beans.
     * @return An array of bean names.
     */
    public static String[] getBeanNamesForType(ResolvableType resolvableType, boolean b, boolean b1) {
        return applicationContext.getBeanNamesForType(resolvableType, b, b1);
    }

    /**
     * Return the aliases for the given bean name, if any.
     *
     * @param s The name of the bean to query.
     * @return An array of aliases.
     */
    public static String[] getAliases(String s) {
        return applicationContext.getAliases(s);
    }

    /**
     * Return an {@link ObjectProvider} that provides access to the bean instance that uniquely matches the given object type.
     *
     * @param resolvableType The type of the bean to retrieve.
     * @param <T>            The type of the bean.
     * @return An ObjectProvider for the bean.
     */
    public static <T> ObjectProvider<T> getBeanProvider(ResolvableType resolvableType) {
        return applicationContext.getBeanProvider(resolvableType);
    }

    /**
     * Find a {@link Annotation} of {@code annotationType} on the specified bean, traversing its annotations and superclasses.
     *
     * @param s      The name of the bean to query.
     * @param aClass The annotation type to look for.
     * @param <A>    The annotation type.
     * @return The annotation found, or {@code null} if none.
     * @throws NoSuchBeanDefinitionException If there is no bean with the given name.
     */
    @Nullable
    public static <A extends Annotation> A findAnnotationOnBean(String s, Class<A> aClass) throws NoSuchBeanDefinitionException {
        return applicationContext.findAnnotationOnBean(s, aClass);
    }

    /**
     * Return the names of beans matching the given type (including subclasses).
     *
     * @param resolvableType The type to look for.
     * @return An array of bean names.
     */
    public static String[] getBeanNamesForType(ResolvableType resolvableType) {
        return applicationContext.getBeanNamesForType(resolvableType);
    }

    /**
     * Return the bean instance that uniquely matches the given object type, if any.
     *
     * @param s The name of the bean to retrieve.
     * @return The bean instance.
     * @throws BeansException If the bean could not be found or created.
     */
    public static Object getBean(String s) throws BeansException {
        return applicationContext.getBean(s);
    }

    /**
     * Check if this context contains a bean definition or a bean instance,
     * which might be registered by a post-processor or obtained by an {@link org.springframework.beans.factory.ObjectFactory}.
     *
     * @param s The name of the bean to query.
     * @return {@code true} if this context contains a bean with the given name, {@code false} otherwise.
     */
    public static boolean containsBean(String s) {
        return applicationContext.containsBean(s);
    }

    /**
     * Try to retrieve the given message, using the given parameters.
     *
     * @param s       The code of the message to retrieve.
     * @param objects Arguments for the message.
     * @param locale  The locale to use.
     * @return The resolved message.
     * @throws NoSuchMessageException If no message is found.
     */
    public static String getMessage(String s, Object[] objects, Locale locale) throws NoSuchMessageException {
        return applicationContext.getMessage(s, objects, locale);
    }

    /**
     * Return the names of all beans defined in this factory.
     *
     * @return An array of bean names.
     */
    public static String[] getBeanDefinitionNames() {
        return applicationContext.getBeanDefinitionNames();
    }

    /**
     * Return the names of beans matching the given type (including subclasses).
     *
     * @param aClass The type to look for.
     * @return An array of bean names.
     */
    public static String[] getBeanNamesForType(Class<?> aClass) {
        return applicationContext.getBeanNamesForType(aClass);
    }

    /**
     * Return the {@link Environment} for this application context.
     *
     * @return The Environment.
     */
    public static Environment getEnvironment() {
        return applicationContext.getEnvironment();
    }

    /**
     * Return the number of bean definitions in this factory.
     *
     * @return The number of bean definitions.
     */
    public static int getBeanDefinitionCount() {
        return applicationContext.getBeanDefinitionCount();
    }

    /**
     * Check if the bean with the given name is a singleton.
     *
     * @param s The name of the bean to query.
     * @return {@code true} if the bean is a singleton, {@code false} otherwise.
     * @throws NoSuchBeanDefinitionException If there is no bean with the given name.
     */
    public static boolean isSingleton(String s) throws NoSuchBeanDefinitionException {
        return applicationContext.isSingleton(s);
    }

    /**
     * Return the names of beans matching the given type (including subclasses),
     * optionally excluding non-singletons and/or lazily initialized ones.
     *
     * @param aClass The type to look for.
     * @param b      Whether to include non-singletons.
     * @param b1     Whether to include lazily initialized beans.
     * @return An array of bean names.
     */
    public static String[] getBeanNamesForType(Class<?> aClass, boolean b, boolean b1) {
        return applicationContext.getBeanNamesForType(aClass, b, b1);
    }

    /**
     * Return the bean instance that uniquely matches the given object type, if any.
     *
     * @param s      The name of the bean to retrieve.
     * @param aClass The type of the bean to retrieve.
     * @param <T>    The type of the bean.
     * @return The bean instance.
     * @throws BeansException If the bean could not be found or created.
     */
    public static <T> T getBean(String s, Class<T> aClass) throws BeansException {
        return applicationContext.getBean(s, aClass);
    }

    /**
     * Return a Resource handle for the specified resource location.
     *
     * @param s The resource location.
     * @return A Resource handle.
     */
    public static Resource getResource(String s) {
        return applicationContext.getResource(s);
    }

    /**
     * Check if this factory contains a bean definition with the given name.
     *
     * @param s The name of the bean to query.
     * @return {@code true} if this factory contains a bean definition with the given name, {@code false} otherwise.
     */
    public static boolean containsBeanDefinition(String s) {
        return applicationContext.containsBeanDefinition(s);
    }

    /**
     * Check if the bean with the given name is a prototype.
     *
     * @param s The name of the bean to query.
     * @return {@code true} if the bean is a prototype, {@code false} otherwise.
     * @throws NoSuchBeanDefinitionException If there is no bean with the given name.
     */
    public static boolean isPrototype(String s) throws NoSuchBeanDefinitionException {
        return applicationContext.isPrototype(s);
    }

    /**
     * Try to retrieve the given message, using the given {@link MessageSourceResolvable}.
     *
     * @param messageSourceResolvable The MessageSourceResolvable to resolve.
     * @param locale                  The locale to use.
     * @return The resolved message.
     * @throws NoSuchMessageException If no message is found.
     */
    public static String getMessage(MessageSourceResolvable messageSourceResolvable, Locale locale) throws NoSuchMessageException {
        return applicationContext.getMessage(messageSourceResolvable, locale);
    }

    /**
     * Return the bean instance that uniquely matches the given object type, if any.
     *
     * @param s       The name of the bean to retrieve.
     * @param objects Arguments to use when creating a bean instance.
     * @return The bean instance.
     * @throws BeansException If the bean could not be found or created.
     */
    public static Object getBean(String s, Object... objects) throws BeansException {
        return applicationContext.getBean(s, objects);
    }

    /**
     * Return the bean instances that match the given object type (including subclasses).
     *
     * @param aClass The type of the bean to retrieve.
     * @param <T>    The type of the bean.
     * @return A map of bean names to bean instances.
     * @throws BeansException If the beans could not be found or created.
     */
    public static <T> Map<String, T> getBeansOfType(Class<T> aClass) throws BeansException {
        return applicationContext.getBeansOfType(aClass);
    }

    /**
     * Updates or registers a bean definition in the Spring context.
     * If a bean with the given name already exists, its definition will be removed and re-registered.
     *
     * @param beanName       The name of the bean.
     * @param beanDefinition The BeanDefinition to register or update.
     */
    public static void updateBeanDefinition(String beanName, BeanDefinition beanDefinition) {
        if (applicationContext instanceof BeanDefinitionRegistry) {
            BeanDefinitionRegistry registry = (BeanDefinitionRegistry) applicationContext;
            if (registry.containsBeanDefinition(beanName)) {
                registry.removeBeanDefinition(beanName);
            }
            registry.registerBeanDefinition(beanName, beanDefinition);
        } else {
            throw new IllegalStateException("ApplicationContext is not a BeanDefinitionRegistry.");
        }
    }

    /**
     * register bean
     *
     * @param beanName The name of the bean.
     * @param bean     The bean to register.
     */
    public static void registerBean(String beanName, Object bean) {
        ConfigurableApplicationContext context = (ConfigurableApplicationContext) applicationContext;
        context.getBeanFactory().registerSingleton(beanName, bean);
    }

}
