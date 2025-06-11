package io.github.hiant.common.utils;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.config.AutowireCapableBeanFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationEvent;
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
    private static ApplicationContext applicationContext;

    /**
     * Sets the application context.
     *
     * @param applicationContext The ApplicationContext to set.
     */
    public static void setApplicationContext(ApplicationContext applicationContext) {
        SpringContextUtils.applicationContext = applicationContext;
    }

    @Nullable
    public static String getId() {
        return applicationContext.getId();
    }

    public static boolean containsLocalBean(String s) {
        return applicationContext.containsLocalBean(s);
    }

    public static long getStartupDate() {
        return applicationContext.getStartupDate();
    }

    public static boolean isTypeMatch(String s, ResolvableType resolvableType) throws NoSuchBeanDefinitionException {
        return applicationContext.isTypeMatch(s, resolvableType);
    }

    public static <T> T getBean(Class<T> aClass) throws BeansException {
        return applicationContext.getBean(aClass);
    }

    public static String getApplicationName() {
        return applicationContext.getApplicationName();
    }

    public static <T> Map<String, T> getBeansOfType(Class<T> aClass, boolean b, boolean b1) throws BeansException {
        return applicationContext.getBeansOfType(aClass, b, b1);
    }

    public static boolean isTypeMatch(String s, Class<?> aClass) throws NoSuchBeanDefinitionException {
        return applicationContext.isTypeMatch(s, aClass);
    }

    @Nullable
    public static ClassLoader getClassLoader() {
        return applicationContext.getClassLoader();
    }

    public static <T> T getBean(Class<T> aClass, Object... objects) throws BeansException {
        return applicationContext.getBean(aClass, objects);
    }

    public static String getDisplayName() {
        return applicationContext.getDisplayName();
    }

    public static void publishEvent(ApplicationEvent event) {
        applicationContext.publishEvent(event);
    }

    public static Resource[] getResources(String s) throws IOException {
        return applicationContext.getResources(s);
    }

    @Nullable
    public static Class<?> getType(String s) throws NoSuchBeanDefinitionException {
        return applicationContext.getType(s);
    }

    public static String[] getBeanNamesForAnnotation(Class<? extends Annotation> aClass) {
        return applicationContext.getBeanNamesForAnnotation(aClass);
    }

    @Nullable
    public static ApplicationContext getParent() {
        return applicationContext.getParent();
    }

    @Nullable
    public static String getMessage(String s, Object[] objects, String s1, Locale locale) {
        return applicationContext.getMessage(s, objects, s1, locale);
    }

    public static void publishEvent(Object o) {
        applicationContext.publishEvent(o);
    }

    public static <T> ObjectProvider<T> getBeanProvider(Class<T> aClass) {
        return applicationContext.getBeanProvider(aClass);
    }

    @Nullable
    public static BeanFactory getParentBeanFactory() {
        return applicationContext.getParentBeanFactory();
    }

    public static AutowireCapableBeanFactory getAutowireCapableBeanFactory() throws IllegalStateException {
        return applicationContext.getAutowireCapableBeanFactory();
    }

    public static Map<String, Object> getBeansWithAnnotation(Class<? extends Annotation> aClass) throws BeansException {
        return applicationContext.getBeansWithAnnotation(aClass);
    }

    @Nullable
    public static Class<?> getType(String s, boolean b) throws NoSuchBeanDefinitionException {
        return applicationContext.getType(s, b);
    }

    public static String[] getBeanNamesForType(ResolvableType resolvableType, boolean b, boolean b1) {
        return applicationContext.getBeanNamesForType(resolvableType, b, b1);
    }

    public static String[] getAliases(String s) {
        return applicationContext.getAliases(s);
    }

    public static <T> ObjectProvider<T> getBeanProvider(ResolvableType resolvableType) {
        return applicationContext.getBeanProvider(resolvableType);
    }

    @Nullable
    public static <A extends Annotation> A findAnnotationOnBean(String s, Class<A> aClass) throws NoSuchBeanDefinitionException {
        return applicationContext.findAnnotationOnBean(s, aClass);
    }

    public static String[] getBeanNamesForType(ResolvableType resolvableType) {
        return applicationContext.getBeanNamesForType(resolvableType);
    }

    public static Object getBean(String s) throws BeansException {
        return applicationContext.getBean(s);
    }

    public static boolean containsBean(String s) {
        return applicationContext.containsBean(s);
    }

    public static String getMessage(String s, Object[] objects, Locale locale) throws NoSuchMessageException {
        return applicationContext.getMessage(s, objects, locale);
    }

    public static String[] getBeanDefinitionNames() {
        return applicationContext.getBeanDefinitionNames();
    }

    public static String[] getBeanNamesForType(Class<?> aClass) {
        return applicationContext.getBeanNamesForType(aClass);
    }

    public static Environment getEnvironment() {
        return applicationContext.getEnvironment();
    }

    public static int getBeanDefinitionCount() {
        return applicationContext.getBeanDefinitionCount();
    }

    public static boolean isSingleton(String s) throws NoSuchBeanDefinitionException {
        return applicationContext.isSingleton(s);
    }

    public static String[] getBeanNamesForType(Class<?> aClass, boolean b, boolean b1) {
        return applicationContext.getBeanNamesForType(aClass, b, b1);
    }

    public static <T> T getBean(String s, Class<T> aClass) throws BeansException {
        return applicationContext.getBean(s, aClass);
    }

    public static Resource getResource(String s) {
        return applicationContext.getResource(s);
    }

    public static boolean containsBeanDefinition(String s) {
        return applicationContext.containsBeanDefinition(s);
    }

    public static boolean isPrototype(String s) throws NoSuchBeanDefinitionException {
        return applicationContext.isPrototype(s);
    }

    public static String getMessage(MessageSourceResolvable messageSourceResolvable, Locale locale) throws NoSuchMessageException {
        return applicationContext.getMessage(messageSourceResolvable, locale);
    }

    public static Object getBean(String s, Object... objects) throws BeansException {
        return applicationContext.getBean(s, objects);
    }

    public static <T> Map<String, T> getBeansOfType(Class<T> aClass) throws BeansException {
        return applicationContext.getBeansOfType(aClass);
    }

}
