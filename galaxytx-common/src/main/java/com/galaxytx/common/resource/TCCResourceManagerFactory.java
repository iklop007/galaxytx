package com.galaxytx.common.resource;

import com.galaxytx.common.exception.TCCException;
import com.galaxytx.common.util.SpringUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationContext;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * TCC资源管理器工厂
 * 负责创建和管理TCC资源管理器实例
 */
public class TCCResourceManagerFactory {
    private static final Logger logger = LoggerFactory.getLogger(TCCResourceManagerFactory.class);

    // TCC资源管理器缓存
    private static final Map<String, TCCResourceManager> resourceManagerCache = new ConcurrentHashMap<>();

    // 默认资源管理器（用于资源ID无法匹配时）
    private static TCCResourceManager defaultResourceManager;

    // 资源ID到管理器类型的映射配置
    private static final Map<String, String> resourceManagerMapping = new ConcurrentHashMap<>();

    static {
        // 初始化默认映射配置
        initializeDefaultMappings();
    }

    /**
     * 初始化默认的资源管理器映射
     */
    private static void initializeDefaultMappings() {
        // 可以根据资源ID的前缀或模式进行映射
        resourceManagerMapping.put("order-", "orderTCCResourceManager");
        resourceManagerMapping.put("payment-", "paymentTCCResourceManager");
        resourceManagerMapping.put("inventory-", "inventoryTCCResourceManager");
        resourceManagerMapping.put("user-", "userTCCResourceManager");
        resourceManagerMapping.put("account-", "accountTCCResourceManager");

        // 默认的通配符映射
        resourceManagerMapping.put("*", "defaultTCCResourceManager");
    }

    /**
     * 获取TCC资源管理器
     */
    public static TCCResourceManager getTCCResourceManager(String resourceId) {
        if (resourceId == null || resourceId.trim().isEmpty()) {
            throw new TCCException("Resource ID cannot be null or empty");
        }

        // 首先检查缓存
        TCCResourceManager cachedManager = resourceManagerCache.get(resourceId);
        if (cachedManager != null) {
            return cachedManager;
        }

        // 根据资源ID查找对应的管理器Bean名称
        String managerBeanName = resolveManagerBeanName(resourceId);

        try {
            // 从Spring容器中获取管理器实例
            TCCResourceManager manager = getManagerFromSpring(managerBeanName);

            if (manager != null) {
                // 缓存找到的管理器
                resourceManagerCache.put(resourceId, manager);
                logger.debug("TCC resource manager found for {}: {}", resourceId, managerBeanName);
                return manager;
            }

            // 如果找不到指定的管理器，使用默认管理器
            TCCResourceManager defaultManager = getDefaultResourceManager();
            resourceManagerCache.put(resourceId, defaultManager);
            logger.debug("Using default TCC resource manager for: {}", resourceId);
            return defaultManager;

        } catch (Exception e) {
            logger.error("Failed to get TCC resource manager for: {}", resourceId, e);
            throw new TCCException("Failed to get TCC resource manager for: " + resourceId, resourceId, e);
        }
    }

    /**
     * 根据资源ID解析管理器Bean名称
     */
    private static String resolveManagerBeanName(String resourceId) {
        // 首先尝试精确匹配
        for (Map.Entry<String, String> entry : resourceManagerMapping.entrySet()) {
            String pattern = entry.getKey();
            String beanName = entry.getValue();

            // 如果是通配符，直接返回
            if ("*".equals(pattern)) {
                return beanName;
            }

            // 检查资源ID是否以模式开头
            if (resourceId.startsWith(pattern)) {
                return beanName;
            }

            // 检查资源ID是否包含模式（去掉后缀）
            if (resourceId.contains(pattern)) {
                return beanName;
            }
        }

        // 如果没有找到匹配的映射，使用默认管理器
        return resourceManagerMapping.get("*");
    }

    /**
     * 从Spring容器中获取管理器实例
     */
    private static TCCResourceManager getManagerFromSpring(String beanName) {
        if (!SpringUtil.isInitialized()) {
            throw new TCCException("Spring context is not initialized");
        }

        try {
            // 首先按名称查找
            TCCResourceManager manager = SpringUtil.getBean(beanName, TCCResourceManager.class);
            if (manager != null) {
                return manager;
            }

            // 如果按名称找不到，尝试按类型查找第一个
            Map<String, TCCResourceManager> managers = SpringUtil.getBeansOfType(TCCResourceManager.class);
            if (managers != null && !managers.isEmpty()) {
                return managers.values().iterator().next();
            }

            return null;

        } catch (Exception e) {
            logger.warn("Failed to get TCC manager bean: {}", beanName, e);
            return null;
        }
    }

    /**
     * 获取默认的资源管理器
     */
    private static synchronized TCCResourceManager getDefaultResourceManager() {
        if (defaultResourceManager == null) {
            if (SpringUtil.isInitialized()) {
                // 尝试从Spring容器获取默认管理器
                defaultResourceManager = SpringUtil.getBean("defaultTCCResourceManager", TCCResourceManager.class);

                if (defaultResourceManager == null) {
                    // 如果容器中没有默认管理器，创建一個默认实例
                    defaultResourceManager = createDefaultResourceManager();
                    logger.info("Created default TCC resource manager instance");
                }
            } else {
                // 非Spring环境，创建默认实例
                defaultResourceManager = createDefaultResourceManager();
                logger.info("Created default TCC resource manager instance (non-Spring environment)");
            }
        }
        return defaultResourceManager;
    }

    /**
     * 创建默认的资源管理器
     */
    private static TCCResourceManager createDefaultResourceManager() {
        TCCServiceLocator serviceLocator = null;

        // 尝试从Spring容器获取TCCServiceLocator
        if (SpringUtil.isInitialized()) {
            serviceLocator = SpringUtil.getBean(TCCServiceLocator.class);
        }

        // 如果容器中没有，创建新的实例
        if (serviceLocator == null) {
            serviceLocator = new TCCServiceLocator();
            // 如果需要，可以在这里初始化serviceLocator
        }

        ApplicationContext applicationContext = null;
        if (SpringUtil.isInitialized()) {
            applicationContext = SpringUtil.getApplicationContext();
        }
        return new TCCResourceManager(applicationContext, serviceLocator);
    }

    /**
     * 注册自定义的资源管理器映射
     */
    public static void registerResourceMapping(String resourcePattern, String managerBeanName) {
        if (resourcePattern == null || managerBeanName == null) {
            throw new IllegalArgumentException("Resource pattern and manager bean name cannot be null");
        }

        resourceManagerMapping.put(resourcePattern, managerBeanName);
        logger.info("Registered TCC resource mapping: {} -> {}", resourcePattern, managerBeanName);

        // 清除缓存，因为映射关系发生了变化
        clearCache();
    }

    /**
     * 注册资源管理器实例
     */
    public static void registerResourceManager(String resourceId, TCCResourceManager manager) {
        if (resourceId == null || manager == null) {
            throw new IllegalArgumentException("Resource ID and manager cannot be null");
        }

        resourceManagerCache.put(resourceId, manager);
        logger.info("Registered TCC resource manager for: {}", resourceId);
    }

    /**
     * 清除缓存
     */
    public static void clearCache() {
        resourceManagerCache.clear();
        defaultResourceManager = null;
        logger.info("TCC resource manager cache cleared");
    }

    /**
     * 清除特定资源的缓存
     */
    public static void clearCache(String resourceId) {
        resourceManagerCache.remove(resourceId);
        logger.debug("Cleared TCC resource manager cache for: {}", resourceId);
    }

    /**
     * 获取所有缓存的资源管理器
     */
    public static Map<String, TCCResourceManager> getCachedManagers() {
        return new ConcurrentHashMap<>(resourceManagerCache);
    }

    /**
     * 获取资源管理器映射配置
     */
    public static Map<String, String> getResourceMappings() {
        return new ConcurrentHashMap<>(resourceManagerMapping);
    }

    /**
     * 检查是否有资源管理器可用于指定的资源ID
     */
    public static boolean hasResourceManager(String resourceId) {
        if (resourceId == null) {
            return false;
        }

        // 检查缓存
        if (resourceManagerCache.containsKey(resourceId)) {
            return true;
        }

        // 检查是否有对应的映射配置
        String beanName = resolveManagerBeanName(resourceId);
        if (beanName != null) {
            if (SpringUtil.isInitialized()) {
                return SpringUtil.containsBean(beanName) ||
                        !SpringUtil.getBeansOfType(TCCResourceManager.class).isEmpty();
            }
            return true; // 非Spring环境，假设总是可用
        }

        return false;
    }

    /**
     * 重新加载映射配置
     */
    public static void reloadMappings() {
        resourceManagerMapping.clear();
        initializeDefaultMappings();
        clearCache();
        logger.info("TCC resource manager mappings reloaded");
    }
}