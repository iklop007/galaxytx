package com.galaxytx.core.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.lang.annotation.Annotation;
import java.util.Map;

/**
 * Spring工具类
 * 用于获取Spring容器中的Bean实例和环境信息
 */
@Component
public class SpringUtil implements ApplicationContextAware {
    private static final Logger logger = LoggerFactory.getLogger(SpringUtil.class);

    private static ApplicationContext applicationContext;
    private static ConfigurableApplicationContext configurableApplicationContext;

    /**
     * 设置ApplicationContext
     */
    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        if (SpringUtil.applicationContext == null) {
            SpringUtil.applicationContext = applicationContext;
            if (applicationContext instanceof ConfigurableApplicationContext) {
                SpringUtil.configurableApplicationContext = (ConfigurableApplicationContext) applicationContext;
            }
            logger.info("SpringUtil initialized successfully");
        }
    }

    /**
     * 获取ApplicationContext
     */
    public static ApplicationContext getApplicationContext() {
        checkApplicationContext();
        return applicationContext;
    }

    /**
     * 获取ConfigurableApplicationContext
     */
    public static ConfigurableApplicationContext getConfigurableApplicationContext() {
        checkApplicationContext();
        if (configurableApplicationContext == null) {
            throw new IllegalStateException("ConfigurableApplicationContext is not available");
        }
        return configurableApplicationContext;
    }

    /**
     * 通过名称获取Bean
     */
    public static Object getBean(String name) {
        checkApplicationContext();
        try {
            return applicationContext.getBean(name);
        } catch (BeansException e) {
            logger.warn("Bean not found by name: {}", name);
            return null;
        }
    }

    /**
     * 通过类型获取Bean
     */
    public static <T> T getBean(Class<T> clazz) {
        checkApplicationContext();
        try {
            return applicationContext.getBean(clazz);
        } catch (BeansException e) {
            logger.warn("Bean not found by type: {}", clazz.getName());
            return null;
        }
    }

    /**
     * 通过名称和类型获取Bean
     */
    public static <T> T getBean(String name, Class<T> clazz) {
        checkApplicationContext();
        try {
            return applicationContext.getBean(name, clazz);
        } catch (BeansException e) {
            logger.warn("Bean not found by name and type: {}, {}", name, clazz.getName());
            return null;
        }
    }

    /**
     * 获取指定类型的所有Bean
     */
    public static <T> Map<String, T> getBeansOfType(Class<T> type) {
        checkApplicationContext();
        try {
            return applicationContext.getBeansOfType(type);
        } catch (BeansException e) {
            logger.warn("No beans found for type: {}", type.getName());
            return null;
        }
    }

    /**
     * 获取带有指定注解的所有Bean
     */
    public static Map<String, Object> getBeansWithAnnotation(Class<? extends Annotation> annotationType) {
        checkApplicationContext();
        try {
            return applicationContext.getBeansWithAnnotation(annotationType);
        } catch (BeansException e) {
            logger.warn("No beans found with annotation: {}", annotationType.getName());
            return null;
        }
    }

    /**
     * 获取环境信息
     */
    public static Environment getEnvironment() {
        checkApplicationContext();
        return applicationContext.getEnvironment();
    }

    /**
     * 获取配置属性
     */
    public static String getProperty(String key) {
        checkApplicationContext();
        return applicationContext.getEnvironment().getProperty(key);
    }

    /**
     * 获取配置属性（带默认值）
     */
    public static String getProperty(String key, String defaultValue) {
        checkApplicationContext();
        return applicationContext.getEnvironment().getProperty(key, defaultValue);
    }

    /**
     * 获取配置属性（指定类型）
     */
    public static <T> T getProperty(String key, Class<T> targetType) {
        checkApplicationContext();
        return applicationContext.getEnvironment().getProperty(key, targetType);
    }

    /**
     * 获取配置属性（指定类型，带默认值）
     */
    public static <T> T getProperty(String key, Class<T> targetType, T defaultValue) {
        checkApplicationContext();
        return applicationContext.getEnvironment().getProperty(key, targetType, defaultValue);
    }

    /**
     * 获取活跃的Profile
     */
    public static String[] getActiveProfiles() {
        checkApplicationContext();
        return applicationContext.getEnvironment().getActiveProfiles();
    }

    /**
     * 获取默认的Profile
     */
    public static String[] getDefaultProfiles() {
        checkApplicationContext();
        return applicationContext.getEnvironment().getDefaultProfiles();
    }

    /**
     * 检查是否包含某个Profile
     */
    public static boolean hasProfile(String profile) {
        checkApplicationContext();
        return applicationContext.getEnvironment().acceptsProfiles(profile);
    }

    /**
     * 检查Bean是否存在
     */
    public static boolean containsBean(String name) {
        checkApplicationContext();
        return applicationContext.containsBean(name);
    }

    /**
     * 检查Bean是否存在（按类型）
     */
    public static boolean containsBean(Class<?> type) {
        checkApplicationContext();
        return !applicationContext.getBeansOfType(type).isEmpty();
    }

    /**
     * 获取Bean的类型
     */
    public static Class<?> getType(String name) {
        checkApplicationContext();
        try {
            return applicationContext.getType(name);
        } catch (BeansException e) {
            logger.warn("Cannot get type for bean: {}", name);
            return null;
        }
    }

    /**
     * 获取Bean的别名
     */
    public static String[] getAliases(String name) {
        checkApplicationContext();
        try {
            return applicationContext.getAliases(name);
        } catch (BeansException e) {
            logger.warn("Cannot get aliases for bean: {}", name);
            return new String[0];
        }
    }

    /**
     * 发布应用事件
     */
    public static void publishEvent(Object event) {
        checkApplicationContext();
        applicationContext.publishEvent(event);
    }

    /**
     * 获取应用名称
     */
    public static String getApplicationName() {
        checkApplicationContext();
        return applicationContext.getApplicationName();
    }

    /**
     * 获取Bean定义数量
     */
    public static int getBeanDefinitionCount() {
        checkApplicationContext();
        return applicationContext.getBeanDefinitionCount();
    }

    /**
     * 获取所有Bean定义名称
     */
    public static String[] getBeanDefinitionNames() {
        checkApplicationContext();
        return applicationContext.getBeanDefinitionNames();
    }

    /**
     * 检查ApplicationContext是否已初始化
     */
    public static boolean isInitialized() {
        return applicationContext != null;
    }

    /**
     * 安全地获取Bean（如果不存在返回null）
     */
    public static <T> T getBeanSafely(Class<T> clazz) {
        if (!isInitialized()) {
            return null;
        }
        try {
            return applicationContext.getBean(clazz);
        } catch (BeansException e) {
            return null;
        }
    }

    /**
     * 安全地获取Bean（如果不存在返回null）
     */
    public static Object getBeanSafely(String name) {
        if (!isInitialized()) {
            return null;
        }
        try {
            return applicationContext.getBean(name);
        } catch (BeansException e) {
            return null;
        }
    }

    /**
     * 获取第一个匹配类型的Bean
     */
    public static <T> T getFirstBeanOfType(Class<T> type) {
        Map<String, T> beans = getBeansOfType(type);
        if (beans != null && !beans.isEmpty()) {
            return beans.values().iterator().next();
        }
        return null;
    }

    /**
     * 获取指定注解的第一个Bean
     */
    public static <T> T getFirstBeanWithAnnotation(Class<? extends Annotation> annotationType, Class<T> beanType) {
        Map<String, Object> beans = getBeansWithAnnotation(annotationType);
        if (beans != null && !beans.isEmpty()) {
            for (Object bean : beans.values()) {
                if (beanType.isInstance(bean)) {
                    return beanType.cast(bean);
                }
            }
        }
        return null;
    }

    /**
     * 检查ApplicationContext
     */
    private static void checkApplicationContext() {
        if (applicationContext == null) {
            throw new IllegalStateException("ApplicationContext is not initialized, please check if SpringUtil is configured correctly");
        }
    }

    /**
     * 重新初始化ApplicationContext（用于测试）
     */
    public static void reinitialize(ApplicationContext newApplicationContext) {
        applicationContext = newApplicationContext;
        if (newApplicationContext instanceof ConfigurableApplicationContext) {
            configurableApplicationContext = (ConfigurableApplicationContext) newApplicationContext;
        }
        logger.info("SpringUtil reinitialized with new ApplicationContext");
    }

    /**
     * 重置ApplicationContext（用于测试）
     */
    public static void reset() {
        applicationContext = null;
        configurableApplicationContext = null;
        logger.info("SpringUtil reset");
    }

    /**
     * 获取Spring Util的版本信息
     */
    public static String getVersion() {
        return "1.0.0";
    }

    /**
     * 获取Spring Util的状态信息
     */
    public static String getStatus() {
        if (!isInitialized()) {
            return "NOT_INITIALIZED";
        }

        StringBuilder status = new StringBuilder();
        status.append("Initialized | ");
        status.append("Beans: ").append(getBeanDefinitionCount()).append(" | ");
        status.append("Active Profiles: ").append(String.join(", ", getActiveProfiles()));

        return status.toString();
    }
}