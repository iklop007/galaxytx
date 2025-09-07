package com.galaxytx.core.resource;

import com.galaxytx.core.annotation.TCCService;
import com.galaxytx.core.exception.TCCException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.stereotype.Component;
import org.springframework.util.ClassUtils;
import org.springframework.util.ReflectionUtils;

import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * TCC服务定位器
 * 负责发现和定位TCC资源
 */
@Component
public class TCCServiceLocator {
    private static final Logger logger = LoggerFactory.getLogger(TCCServiceLocator.class);

    @Autowired
    private ApplicationContext applicationContext;

    // TCC资源缓存
    private final Map<String, TCCResourceManager.TCCResource> resourceCache = new ConcurrentHashMap<>();
    // 方法签名缓存
    private final Map<String, Method> methodSignatureCache = new ConcurrentHashMap<>();
    // 资源扫描状态
    private volatile boolean resourcesScanned = false;

    /**
     * 定位TCC资源
     */
    public TCCResourceManager.TCCResource locateTCCResource(String resourceId) {
        // 首先检查缓存
        TCCResourceManager.TCCResource cachedResource = resourceCache.get(resourceId);
        if (cachedResource != null) {
            return cachedResource;
        }

        // 如果还没有扫描过资源，先进行扫描
        if (!resourcesScanned) {
            scanTCCResources();
        }

        // 再次检查缓存（可能在扫描过程中找到了）
        cachedResource = resourceCache.get(resourceId);
        if (cachedResource != null) {
            return cachedResource;
        }

        // 尝试动态查找
        TCCResourceManager.TCCResource dynamicResource = findTCCResourceDynamically(resourceId);
        if (dynamicResource != null) {
            resourceCache.put(resourceId, dynamicResource);
            return dynamicResource;
        }

        // 资源未找到
        logger.warn("TCC resource not found: {}", resourceId);
        throw TCCException.resourceNotFound(resourceId);
    }

    /**
     * 扫描所有TCC资源
     */
    public synchronized void scanTCCResources() {
        if (resourcesScanned) {
            return;
        }

        logger.info("Scanning TCC resources...");

        try {
            // 查找所有带有@TCCService注解的Bean
            Map<String, Object> tccBeans = applicationContext.getBeansWithAnnotation(TCCService.class);

            for (Map.Entry<String, Object> entry : tccBeans.entrySet()) {
                Object bean = entry.getValue();
                TCCService annotation = AnnotationUtils.findAnnotation(bean.getClass(), TCCService.class);

                if (annotation != null) {
                    String resourceId = annotation.resourceId();
                    String confirmMethod = annotation.confirmMethod();
                    String cancelMethod = annotation.cancelMethod();

                    // 验证方法是否存在
                    validateTCCMethods(bean.getClass(), confirmMethod, cancelMethod, resourceId);

                    // 创建TCC资源并缓存
                    TCCResourceManager.TCCResource resource =
                            new TCCResourceManager.TCCResource(bean, confirmMethod, cancelMethod);

                    resourceCache.put(resourceId, resource);
                    logger.info("Registered TCC resource: {} -> {} (confirm: {}, cancel: {})",
                            resourceId, bean.getClass().getSimpleName(), confirmMethod, cancelMethod);
                }
            }

            resourcesScanned = true;
            logger.info("TCC resource scanning completed. Found {} resources.", resourceCache.size());

        } catch (Exception e) {
            logger.error("Failed to scan TCC resources", e);
            throw new TCCException("TCC resource scanning failed", e);
        }
    }

    /**
     * 动态查找TCC资源
     */
    private TCCResourceManager.TCCResource findTCCResourceDynamically(String resourceId) {
        logger.debug("Dynamically searching for TCC resource: {}", resourceId);

        // 1. 首先尝试按名称查找Bean
        try {
            if (applicationContext.containsBean(resourceId)) {
                Object bean = applicationContext.getBean(resourceId);
                return createTCCResourceFromBean(bean, resourceId);
            }
        } catch (Exception e) {
            logger.debug("Bean not found by name: {}", resourceId, e);
        }

        // 2. 尝试按类型查找
        try {
            String[] beanNames = applicationContext.getBeanNamesForType(Object.class);
            for (String beanName : beanNames) {
                Object bean = applicationContext.getBean(beanName);
                TCCService annotation = AnnotationUtils.findAnnotation(bean.getClass(), TCCService.class);

                if (annotation != null && annotation.resourceId().equals(resourceId)) {
                    return createTCCResourceFromAnnotation(bean, annotation);
                }
            }
        } catch (Exception e) {
            logger.debug("Failed to find TCC resource by type: {}", resourceId, e);
        }

        // 3. 尝试按命名约定查找
        try {
            Object bean = findBeanByNamingConvention(resourceId);
            if (bean != null) {
                return createTCCResourceFromBean(bean, resourceId);
            }
        } catch (Exception e) {
            logger.debug("Failed to find TCC resource by naming convention: {}", resourceId, e);
        }

        return null;
    }

    /**
     * 根据命名约定查找Bean
     */
    private Object findBeanByNamingConvention(String resourceId) {
        // 常见的命名约定模式
        String[] possibleBeanNames = {
                resourceId + "Service",
                resourceId + "ServiceImpl",
                resourceId + "TccService",
                resourceId,
                decapitalize(resourceId + "Service"),
                decapitalize(resourceId)
        };

        for (String beanName : possibleBeanNames) {
            try {
                if (applicationContext.containsBean(beanName)) {
                    return applicationContext.getBean(beanName);
                }
            } catch (Exception e) {
                // 继续尝试下一个名称
            }
        }
        return null;
    }

    /**
     * 从Bean创建TCC资源（无注解情况）
     */
    private TCCResourceManager.TCCResource createTCCResourceFromBean(Object bean, String resourceId) {
        Class<?> beanClass = bean.getClass();

        // 尝试查找默认的TCC方法
        String confirmMethod = findTCCMethod(beanClass, "confirm", "commit", "execute");
        String cancelMethod = findTCCMethod(beanClass, "cancel", "rollback", "compensate");

        if (confirmMethod == null || cancelMethod == null) {
            logger.warn("TCC methods not found in bean: {} for resource: {}", beanClass.getName(), resourceId);
            return null;
        }

        // 验证方法签名
        validateTCCMethods(beanClass, confirmMethod, cancelMethod, resourceId);

        return new TCCResourceManager.TCCResource(bean, confirmMethod, cancelMethod);
    }

    /**
     * 从注解创建TCC资源
     */
    private TCCResourceManager.TCCResource createTCCResourceFromAnnotation(Object bean, TCCService annotation) {
        String resourceId = annotation.resourceId();
        String confirmMethod = annotation.confirmMethod();
        String cancelMethod = annotation.cancelMethod();

        validateTCCMethods(bean.getClass(), confirmMethod, cancelMethod, resourceId);

        return new TCCResourceManager.TCCResource(bean, confirmMethod, cancelMethod);
    }

    /**
     * 查找TCC方法
     */
    private String findTCCMethod(Class<?> clazz, String... methodNameCandidates) {
        for (String methodName : methodNameCandidates) {
            Method method = findMethod(clazz, methodName);
            if (method != null && isValidTCCMethod(method)) {
                return methodName;
            }
        }
        return null;
    }

    /**
     * 查找方法
     */
    private Method findMethod(Class<?> clazz, String methodName) {
        String cacheKey = clazz.getName() + "#" + methodName;
        Method cachedMethod = methodSignatureCache.get(cacheKey);
        if (cachedMethod != null) {
            return cachedMethod;
        }

        Method method = ReflectionUtils.findMethod(clazz, methodName);
        if (method != null) {
            methodSignatureCache.put(cacheKey, method);
        }
        return method;
    }

    /**
     * 验证TCC方法
     */
    private void validateTCCMethods(Class<?> clazz, String confirmMethod, String cancelMethod, String resourceId) {
        // 验证confirm方法
        Method confirm = findMethod(clazz, confirmMethod);
        if (confirm == null) {
            throw TCCException.methodNotFound(resourceId, confirmMethod);
        }
        if (!isValidTCCMethod(confirm)) {
            throw new TCCException("Invalid TCC confirm method signature: " + confirmMethod, resourceId, confirmMethod);
        }

        // 验证cancel方法
        Method cancel = findMethod(clazz, cancelMethod);
        if (cancel == null) {
            throw TCCException.methodNotFound(resourceId, cancelMethod);
        }
        if (!isValidTCCMethod(cancel)) {
            throw new TCCException("Invalid TCC cancel method signature: " + cancelMethod, resourceId, cancelMethod);
        }
    }

    /**
     * 检查是否是有效的TCC方法
     */
    private boolean isValidTCCMethod(Method method) {
        Class<?>[] paramTypes = method.getParameterTypes();

        // 支持的方法签名：
        // 1. 无参数
        // 2. 单个String参数（xid）
        // 3. String和long参数（xid和branchId）
        // 4. BranchTransaction参数

        if (paramTypes.length == 0) {
            return true;
        }

        if (paramTypes.length == 1) {
            return paramTypes[0] == String.class ||
                    paramTypes[0] == com.galaxytx.core.model.BranchTransaction.class;
        }

        if (paramTypes.length == 2) {
            return paramTypes[0] == String.class && paramTypes[1] == Long.TYPE;
        }

        return false;
    }

    /**
     * 获取所有已注册的TCC资源
     */
    public Map<String, TCCResourceManager.TCCResource> getAllTCCResources() {
        if (!resourcesScanned) {
            scanTCCResources();
        }
        return new HashMap<>(resourceCache);
    }

    /**
     * 清空缓存
     */
    public void clearCache() {
        resourceCache.clear();
        methodSignatureCache.clear();
        resourcesScanned = false;
        logger.info("TCC resource cache cleared");
    }

    /**
     * 重新扫描资源
     */
    public void rescanResources() {
        clearCache();
        scanTCCResources();
    }

    /**
     * 检查资源是否存在
     */
    public boolean containsResource(String resourceId) {
        if (resourceCache.containsKey(resourceId)) {
            return true;
        }
        if (!resourcesScanned) {
            scanTCCResources();
        }
        return resourceCache.containsKey(resourceId);
    }

    /**
     * 获取资源数量
     */
    public int getResourceCount() {
        if (!resourcesScanned) {
            scanTCCResources();
        }
        return resourceCache.size();
    }

    /**
     * 工具方法：首字母小写
     */
    private String decapitalize(String name) {
        if (name == null || name.length() == 0) {
            return name;
        }
        if (name.length() > 1 && Character.isUpperCase(name.charAt(1)) &&
                Character.isUpperCase(name.charAt(0))){
            return name;
        }
        char[] chars = name.toCharArray();
        chars[0] = Character.toLowerCase(chars[0]);
        return new String(chars);
    }

    /**
     * 获取方法签名信息
     */
    public String getMethodSignature(String resourceId, String methodName) {
        TCCResourceManager.TCCResource resource = locateTCCResource(resourceId);
        if (resource != null) {
            try {
                Method method = resource.getServiceBean().getClass().getMethod(methodName);
                return method.toString();
            } catch (NoSuchMethodException e) {
                return "Method not found: " + methodName;
            }
        }
        return "Resource not found: " + resourceId;
    }
}