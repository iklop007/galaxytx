package com.galaxytx.common.resource;

import com.galaxytx.common.annotation.TCCService;
import com.galaxytx.common.exception.TCCException;
import com.galaxytx.common.model.BranchTransaction;
import com.galaxytx.common.model.CommunicationResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * TCC资源管理器
 * 负责处理TCC模式资源的Confirm和Cancel操作
 */
@Component
public class TCCResourceManager {
    private static final Logger logger = LoggerFactory.getLogger(TCCResourceManager.class);

    private final ApplicationContext applicationContext;
    private final Map<String, TCCResource> resourceCache = new ConcurrentHashMap<>();
    private final TCCServiceLocator serviceLocator;

    @Autowired
    public TCCResourceManager(ApplicationContext applicationContext, TCCServiceLocator serviceLocator) {
        this.applicationContext = applicationContext;
        this.serviceLocator = serviceLocator;
    }

    /**
     * 注册TCC资源
     */
    public void registerTCCResource(String resourceId, Object serviceBean, String confirmMethod, String cancelMethod) {
        TCCResource resource = new TCCResource(serviceBean, confirmMethod, cancelMethod);
        resourceCache.put(resourceId, resource);
        logger.info("Registered TCC resource: {}, confirm={}, cancel={}",
                resourceId, confirmMethod, cancelMethod);
    }

    /**
     * 执行Confirm操作
     */
    public CommunicationResult confirm(BranchTransaction branch) {
        String resourceId = branch.getResourceId();
        String xid = branch.getXid();
        long branchId = branch.getBranchId();

        logger.info("Executing TCC confirm: xid={}, branchId={}, resourceId={}",
                xid, branchId, resourceId);

        try {
            TCCResource resource = getTCCResource(resourceId);
            if (resource == null) {
                return CommunicationResult.failure("TCC resource not found: " + resourceId);
            }

            return executeTCCOperation(resource, resource.getConfirmMethod(), branch, "confirm");

        } catch (TCCException e) {
            logger.error("TCC confirm failed: xid={}, branchId={}", xid, branchId, e);
            return CommunicationResult.failure("TCC confirm failed: " + e.getMessage());
        } catch (Exception e) {
            logger.error("Unexpected error during TCC confirm: xid={}, branchId={}", xid, branchId, e);
            return CommunicationResult.failure("Unexpected error: " + e.getMessage());
        }
    }

    /**
     * 执行Cancel操作
     */
    public CommunicationResult cancel(BranchTransaction branch) {
        String resourceId = branch.getResourceId();
        String xid = branch.getXid();
        long branchId = branch.getBranchId();

        logger.info("Executing TCC cancel: xid={}, branchId={}, resourceId={}",
                xid, branchId, resourceId);

        try {
            TCCResource resource = getTCCResource(resourceId);
            if (resource == null) {
                return CommunicationResult.failure("TCC resource not found: " + resourceId);
            }

            return executeTCCOperation(resource, resource.getCancelMethod(), branch, "cancel");

        } catch (TCCException e) {
            logger.error("TCC cancel failed: xid={}, branchId={}", xid, branchId, e);
            return CommunicationResult.failure("TCC cancel failed: " + e.getMessage());
        } catch (Exception e) {
            logger.error("Unexpected error during TCC cancel: xid={}, branchId={}", xid, branchId, e);
            return CommunicationResult.failure("Unexpected error: " + e.getMessage());
        }
    }

    /**
     * 执行TCC操作
     */
    private CommunicationResult executeTCCOperation(TCCResource resource, String methodName,
                                                    BranchTransaction branch, String operation)
            throws TCCException {

        try {
            Object serviceBean = resource.getServiceBean();
            Method method = findMethod(serviceBean.getClass(), methodName, branch);

            if (method == null) {
                throw new TCCException("Method not found: " + methodName);
            }

            Object result = method.invoke(serviceBean, createMethodArguments(method, branch));

            if (isSuccessResult(result)) {
                logger.debug("TCC {} executed successfully: {}", operation, methodName);
                return CommunicationResult.success();
            } else {
                logger.warn("TCC {} returned failure: {}", operation, methodName);
                return CommunicationResult.failure("TCC operation returned failure");
            }

        } catch (Exception e) {
            throw new TCCException("Failed to execute TCC " + operation + " method: " + methodName, e);
        }
    }

    /**
     * 查找方法
     */
    private Method findMethod(Class<?> clazz, String methodName, BranchTransaction branch) {
        try {
            // 首先尝试查找接受BranchTransaction参数的方法
            try {
                return clazz.getMethod(methodName, BranchTransaction.class);
            } catch (NoSuchMethodException e) {
                // 尝试查找接受xid和branchId参数的方法
                try {
                    return clazz.getMethod(methodName, String.class, Long.TYPE);
                } catch (NoSuchMethodException e2) {
                    // 尝试查找无参数方法
                    return clazz.getMethod(methodName);
                }
            }
        } catch (NoSuchMethodException e) {
            logger.warn("Method {} not found in class {}", methodName, clazz.getName());
            return null;
        }
    }

    /**
     * 创建方法参数
     */
    private Object[] createMethodArguments(Method method, BranchTransaction branch) {
        Class<?>[] parameterTypes = method.getParameterTypes();
        if (parameterTypes.length == 0) {
            return new Object[0];
        }

        if (parameterTypes.length == 1 && parameterTypes[0] == BranchTransaction.class) {
            return new Object[]{branch};
        }

        if (parameterTypes.length == 2 &&
                parameterTypes[0] == String.class &&
                parameterTypes[1] == Long.TYPE) {
            return new Object[]{branch.getXid(), branch.getBranchId()};
        }

        throw new TCCException("Unsupported method signature for TCC operation");
    }

    /**
     * 检查操作结果是否成功
     */
    private boolean isSuccessResult(Object result) {
        if (result == null) {
            return true; // 无返回值默认成功
        }

        if (result instanceof Boolean) {
            return (Boolean) result;
        }

        if (result instanceof CommunicationResult) {
            return ((CommunicationResult) result).isSuccess();
        }

        // 其他类型默认成功
        return true;
    }

    /**
     * 获取TCC资源
     */
    private TCCResource getTCCResource(String resourceId) {
        TCCResource resource = resourceCache.get(resourceId);
        if (resource == null) {
            // 尝试从服务定位器查找
            resource = serviceLocator.locateTCCResource(resourceId);
            if (resource != null) {
                resourceCache.put(resourceId, resource);
            }
        }
        return resource;
    }


    /**
     * TCC资源定义
     */
    public static class TCCResource {
        private final Object serviceBean;
        private final String confirmMethod;
        private final String cancelMethod;

        public TCCResource(Object serviceBean, String confirmMethod, String cancelMethod) {
            this.serviceBean = serviceBean;
            this.confirmMethod = confirmMethod;
            this.cancelMethod = cancelMethod;
        }

        public Object getServiceBean() { return serviceBean; }
        public String getConfirmMethod() { return confirmMethod; }
        public String getCancelMethod() { return cancelMethod; }
    }

    /**
     * 获取所有注册的TCC资源
     */
    public Map<String, TCCResource> getTCCResources() {
        return new ConcurrentHashMap<>(resourceCache);
    }

    /**
     * 移除TCC资源
     */
    public void removeTCCResource(String resourceId) {
        resourceCache.remove(resourceId);
        logger.info("Removed TCC resource: {}", resourceId);
    }

    /**
     * 清理所有资源
     */
    public void cleanup() {
        resourceCache.clear();
        logger.info("Cleaned up all TCC resources");
    }

    /**
     * 自动注册TCC服务
     */
    public void autoRegisterTCCServices() {
        Map<String, Object> tccBeans = applicationContext.getBeansWithAnnotation(TCCService.class);
        for (Map.Entry<String, Object> entry : tccBeans.entrySet()) {
            Object bean = entry.getValue();
            TCCService annotation = bean.getClass().getAnnotation(TCCService.class);
            registerTCCResource(annotation.resourceId(), bean, annotation.confirmMethod(), annotation.cancelMethod());
        }
    }
}