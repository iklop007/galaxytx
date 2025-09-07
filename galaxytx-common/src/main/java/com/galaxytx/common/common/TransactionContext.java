package com.galaxytx.common.common;

import com.galaxytx.common.client.TcClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 事务上下文管理器
 * 用于在当前线程中存储和传递分布式事务相关信息
 *
 * @author: 刘志成
 * @date: 2023/7/27
 */
public class TransactionContext {
    private static final Logger logger = LoggerFactory.getLogger(TransactionContext.class);

    private static final ThreadLocal<Context> CONTEXT_HOLDER = new ThreadLocal<>();

    // 用于存储全局事务ID与TcClient的映射（用于跨线程传递）
    private static final Map<String, TcClient> XID_CLIENT_MAP = new ConcurrentHashMap<>();

    /**
     * 上下文信息
     */
    private static class Context {
        private String xid;              // 全局事务ID
        private Long branchId;           // 分支事务ID
        private TcClient tcClient;       // TC客户端实例
        private String resourceGroupId;  // 资源组ID
        private boolean isGlobal;        // 是否全局事务
        private long timeout;            // 事务超时时间
        private String transactionName;  // 事务名称

        private Context(String xid, Long branchId, TcClient tcClient,
                        String resourceGroupId, boolean isGlobal,
                        long timeout, String transactionName) {
            this.xid = xid;
            this.branchId = branchId;
            this.tcClient = tcClient;
            this.resourceGroupId = resourceGroupId;
            this.isGlobal = isGlobal;
            this.timeout = timeout;
            this.transactionName = transactionName;
        }
    }

    private TransactionContext() {
        // 工具类，防止实例化
    }

    /**
     * 绑定全局事务上下文（完整版本）
     */
    public static void bindGlobal(String xid, TcClient tcClient, String resourceGroupId,
                                  long timeout, String transactionName) {
        Context context = new Context(xid, null, tcClient, resourceGroupId, true, timeout, transactionName);
        CONTEXT_HOLDER.set(context);

        // 注册到全局映射，用于跨线程访问
        if (xid != null && tcClient != null) {
            XID_CLIENT_MAP.put(xid, tcClient);
        }

        logger.debug("Bind global transaction context: xid={}, timeout={}, name={}",
                xid, timeout, transactionName);
    }

    /**
     * 绑定全局事务上下文（简化版本）
     */
    public static void bindGlobal(String xid, TcClient tcClient, String resourceGroupId) {
        bindGlobal(xid, tcClient, resourceGroupId, 60000, "default");
    }

    /**
     * 绑定全局事务ID（最简版本）
     * 用于跨线程或手动绑定事务上下文
     */
    public static void bind(String xid) {
        if (xid == null || xid.trim().isEmpty()) {
            throw new IllegalArgumentException("XID cannot be null or empty");
        }

        // 从全局映射中获取对应的TcClient
        TcClient tcClient = XID_CLIENT_MAP.get(xid);
        if (tcClient == null) {
            logger.warn("No TcClient found for xid: {}, creating minimal context", xid);
            // 创建最小化的上下文（只有xid，没有TcClient）
            Context context = new Context(xid, null, null, "default", true, 60000, "manual");
            CONTEXT_HOLDER.set(context);
        } else {
            // 使用已有的TcClient创建完整上下文
            Context context = new Context(xid, null, tcClient, "default", true, 60000, "manual");
            CONTEXT_HOLDER.set(context);
        }

        logger.debug("Bind transaction context with xid: {}", xid);
    }

    /**
     * 绑定分支事务上下文
     */
    public static void bindBranch(String xid, Long branchId, TcClient tcClient,
                                  String resourceGroupId) {
        Context context = new Context(xid, branchId, tcClient, resourceGroupId, false, 0, null);
        CONTEXT_HOLDER.set(context);
        logger.debug("Bind branch transaction context: xid={}, branchId={}", xid, branchId);
    }

    /**
     * 解绑事务上下文
     */
    public static void unbind() {
        Context context = CONTEXT_HOLDER.get();
        if (context != null) {
            logger.debug("Unbind transaction context: xid={}, branchId={}",
                    context.xid, context.branchId);

            // 如果是全局事务且没有分支事务，从映射中移除
            if (context.isGlobal && context.branchId == null) {
                XID_CLIENT_MAP.remove(context.xid);
            }
        }
        CONTEXT_HOLDER.remove();
    }

    /**
     * 获取全局事务ID
     */
    public static String getXid() {
        Context context = CONTEXT_HOLDER.get();
        return context != null ? context.xid : null;
    }

    /**
     * 获取分支事务ID
     */
    public static Long getBranchId() {
        Context context = CONTEXT_HOLDER.get();
        return context != null ? context.branchId : null;
    }

    /**
     * 获取TC客户端实例
     */
    public static TcClient getTcClient() {
        Context context = CONTEXT_HOLDER.get();
        if (context != null && context.tcClient != null) {
            return context.tcClient;
        }

        // 如果当前上下文没有TcClient，尝试从全局映射中获取
        if (context != null && context.xid != null) {
            return XID_CLIENT_MAP.get(context.xid);
        }

        return null;
    }

    /**
     * 获取资源组ID
     */
    public static String getResourceGroupId() {
        Context context = CONTEXT_HOLDER.get();
        return context != null ? context.resourceGroupId : null;
    }

    /**
     * 获取事务超时时间
     */
    public static long getTimeout() {
        Context context = CONTEXT_HOLDER.get();
        return context != null ? context.timeout : 60000;
    }

    /**
     * 获取事务名称
     */
    public static String getTransactionName() {
        Context context = CONTEXT_HOLDER.get();
        return context != null ? context.transactionName : null;
    }

    /**
     * 是否在全局事务中
     */
    public static boolean isInGlobalTransaction() {
        Context context = CONTEXT_HOLDER.get();
        return context != null && context.isGlobal;
    }

    /**
     * 是否在分支事务中
     */
    public static boolean isInBranchTransaction() {
        Context context = CONTEXT_HOLDER.get();
        return context != null && context.branchId != null;
    }

    /**
     * 是否有有效的上下文
     */
    public static boolean hasContext() {
        return CONTEXT_HOLDER.get() != null;
    }

    /**
     * 注册全局事务ID和TcClient的映射
     * 用于跨线程传递事务上下文
     */
    public static void registerXidClientMapping(String xid, TcClient tcClient) {
        if (xid != null && tcClient != null) {
            XID_CLIENT_MAP.put(xid, tcClient);
            logger.debug("Registered xid-client mapping: xid={}", xid);
        }
    }

    /**
     * 移除全局事务ID和TcClient的映射
     */
    public static void removeXidClientMapping(String xid) {
        if (xid != null) {
            XID_CLIENT_MAP.remove(xid);
            logger.debug("Removed xid-client mapping: xid={}", xid);
        }
    }

    /**
     * 获取指定xid的TcClient
     */
    public static TcClient getTcClientByXid(String xid) {
        return xid != null ? XID_CLIENT_MAP.get(xid) : null;
    }

    /**
     * 清除上下文（用于异常恢复）
     */
    public static void clear() {
        Context context = CONTEXT_HOLDER.get();
        if (context != null && context.xid != null) {
            XID_CLIENT_MAP.remove(context.xid);
        }
        CONTEXT_HOLDER.remove();
        logger.warn("Transaction context cleared forcibly");
    }

    /**
     * 获取当前上下文信息的字符串表示（用于调试）
     */
    public static String getContextInfo() {
        Context context = CONTEXT_HOLDER.get();
        if (context == null) {
            return "No transaction context";
        }
        return String.format("xid=%s, branchId=%s, isGlobal=%s, resourceGroup=%s, timeout=%d, name=%s",
                context.xid, context.branchId, context.isGlobal, context.resourceGroupId,
                context.timeout, context.transactionName);
    }

    /**
     * 跨线程传递事务上下文
     * 用于异步操作或线程池场景
     */
    public static Runnable wrapWithContext(Runnable task) {
        final String xid = getXid();
        final TcClient tcClient = getTcClient();

        return () -> {
            if (xid != null) {
                // 在新线程中绑定事务上下文
                bind(xid);
                if (tcClient != null) {
                    registerXidClientMapping(xid, tcClient);
                }

                try {
                    task.run();
                } finally {
                    unbind();
                    if (tcClient != null) {
                        removeXidClientMapping(xid);
                    }
                }
            } else {
                task.run();
            }
        };
    }

    /**
     * 创建子上下文（用于嵌套事务场景）
     */
    public static Context createChildContext() {
        Context parent = CONTEXT_HOLDER.get();
        if (parent == null) {
            return null;
        }

        return new Context(
                parent.xid,
                parent.branchId,
                parent.tcClient,
                parent.resourceGroupId,
                parent.isGlobal,
                parent.timeout,
                parent.transactionName
        );
    }

    /**
     * 恢复上下文
     */
    public static void restoreContext(Context context) {
        if (context != null) {
            CONTEXT_HOLDER.set(context);
        }
    }
}