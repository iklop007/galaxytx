package com.galaxytx.server;

import com.galaxytx.core.client.external.ExternalServiceClient;
import com.galaxytx.core.client.external.ExternalServiceClientFactory;
import com.galaxytx.core.client.external.ServiceAddressResolver;
import com.galaxytx.core.exception.NetworkException;
import com.galaxytx.core.exception.NetworkExceptionUtils;
import com.galaxytx.core.model.BranchStatus;
import com.galaxytx.core.model.BranchTransaction;
import com.galaxytx.core.model.CommunicationResult;
import com.galaxytx.core.model.GlobalTransaction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.concurrent.*;
import java.util.stream.Collectors;

/**
 * 事务协调器
 * 负责管理全局事务和分支事务的生命周期
 * 1. 管理全局事务和分支事务的生命周期
 * 2. 提交全局事务
 * 3. 回滚全局事务
 * 4. 获取全局事务和分支事务的状态
 *
 * @author 刘志成
 * @date 2023-09-07
 */
public class TransactionCoordinator {
    private static final Logger logger = LoggerFactory.getLogger(TransactionCoordinator.class);

    private final Map<String, GlobalTransaction> globalTransactions = new ConcurrentHashMap<>();
    private final Map<Long, BranchTransaction> branchTransactions = new ConcurrentHashMap<>();
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);


    public TransactionCoordinator() {
        // 启动定时任务，检查超时事务
        scheduler.scheduleAtFixedRate(this::checkTimeouts, 1, 1, TimeUnit.MINUTES);
    }

    public String beginGlobalTransaction(GlobalTransaction globalTransaction) {
        globalTransactions.put(globalTransaction.getXid(), globalTransaction);
        logger.info("Begin global transaction: {}", globalTransaction.getXid());
        return globalTransaction.getXid();
    }

    public Long registerBranch(BranchTransaction branchTransaction) {
        long branchId = generateBranchId();
        branchTransaction.setBranchId(branchId);
        branchTransaction.setStatus(BranchStatus.REGISTERED);

        branchTransactions.put(branchId, branchTransaction);
        logger.info("Register branch transaction: {} for global: {}", branchId, branchTransaction.getXid());

        return branchId;
    }

    public boolean commitGlobalTransaction(String xid) {
        GlobalTransaction globalTransaction = globalTransactions.get(xid);
        if (globalTransaction == null) {
            logger.warn("Global transaction not found: {}", xid);
            return false;
        }

        logger.info("Committing global transaction: {}", xid);

        // 获取该全局事务的所有分支
        List<BranchTransaction> branches = getBranchesByXid(xid);

        for (BranchTransaction branch : branches) {
            if (branch.getStatus().isPhaseOneDone()) {
                // 更新分支状态为提交中
                branch.setStatus(BranchStatus.PHASETWO_COMMITTING);

                try {
                    // 这里应该发送RPC请求到对应的RM执行提交
                    boolean success = sendCommitToRM(branch);

                    if (success) {
                        branch.setStatus(BranchStatus.PHASETWO_COMMITTED);
                        logger.info("Branch {} committed successfully", branch.getBranchId());
                    } else {
                        branch.setStatus(BranchStatus.PHASETWO_COMMIT_FAILED);
                        logger.error("Branch {} commit failed", branch.getBranchId());
                    }
                } catch (Exception e) {
                    branch.setStatus(BranchStatus.PHASETWO_COMMIT_FAILED);
                    logger.error("Branch {} commit error", branch.getBranchId(), e);
                }
            }
        }

        return true;
    }

    public boolean rollbackGlobalTransaction(String xid) {
        GlobalTransaction globalTransaction = globalTransactions.get(xid);
        if (globalTransaction == null) {
            logger.warn("Global transaction not found: {}", xid);
            return false;
        }

        logger.info("Rolling back global transaction: {}", xid);

        // 获取该全局事务的所有分支
        List<BranchTransaction> branches = getBranchesByXid(xid);

        for (BranchTransaction branch : branches) {
            if (branch.getStatus() == BranchStatus.REGISTERED ||
                    branch.getStatus() == BranchStatus.PHASEONE_DONE) {

                // 更新分支状态为回滚中
                branch.setStatus(BranchStatus.PHASETWO_ROLLBACKING);

                try {
                    // 这里应该发送RPC请求到对应的RM执行回滚
                    boolean success = sendRollbackToRM(branch);

                    if (success) {
                        branch.setStatus(BranchStatus.PHASETWO_ROLLBACKED);
                        logger.info("Branch {} rolled back successfully", branch.getBranchId());
                    } else {
                        branch.setStatus(BranchStatus.PHASETWO_ROLLBACK_FAILED);
                        logger.error("Branch {} rollback failed", branch.getBranchId());
                    }
                } catch (Exception e) {
                    branch.setStatus(BranchStatus.PHASETWO_ROLLBACK_FAILED);
                    logger.error("Branch {} rollback error", branch.getBranchId(), e);
                }
            }
        }

        return true;
    }

    /**
     * 检查超时事务
     */
    private void checkTimeouts() {
        long now = System.currentTimeMillis();

        for (GlobalTransaction globalTx : globalTransactions.values()) {
            // 检查全局事务超时
            if (now - globalTx.getBeginTime() > globalTx.getTimeout()) {
                logger.warn("Global transaction timeout: {}", globalTx.getXid());
                rollbackGlobalTransaction(globalTx.getXid());
            }
        }

        for (BranchTransaction branchTx : branchTransactions.values()) {
            // 检查分支事务超时（假设分支事务超时时间为5分钟）
            if (branchTx.isTimeout(5 * 60 * 1000)) {
                logger.warn("Branch transaction timeout: {}", branchTx.getBranchId());
                branchTx.setStatus(BranchStatus.TIMEOUT);
            }
        }
    }

    private long generateBranchId() {
        return System.currentTimeMillis() + branchTransactions.size();
    }

    private List<BranchTransaction> getBranchesByXid(String xid) {
        return branchTransactions.values().stream()
                .filter(branch -> xid.equals(branch.getXid()))
                .collect(Collectors.toList());
    }

    /**
     * 向资源管理器发送提交指令
     * 支持多种通信方式和故障处理策略
     */
    private boolean sendCommitToRM(BranchTransaction branch) {
        if (branch == null) {
            logger.warn("Cannot send commit to null branch");
            return false;
        }

        final String xid = branch.getXid();
        final long branchId = branch.getBranchId();
        final String resourceId = branch.getResourceId();

        logger.info("Sending commit instruction to RM: xid={}, branchId={}, resourceId={}",
                xid, branchId, resourceId);

        int attempt = 0;
        int maxAttempts = getMaxRetryAttempts(branch);
        long retryInterval = getInitialRetryInterval();

        while (attempt < maxAttempts) {
            attempt++;

            try {
                // 根据资源类型选择不同的通信方式
                CommunicationResult result = communicateWithRM(branch, "commit");

                switch (result.getStatus()) {
                    case SUCCESS:
                        logger.info("Commit successful: xid={}, branchId={}, attempt={}",
                                xid, branchId, attempt);
                        return true;

                    case FAILURE:
                        logger.warn("Commit failed: xid={}, branchId={}, attempt={}, error={}",
                                xid, branchId, attempt, result.getError());
                        break;

                    case TIMEOUT:
                        logger.warn("Commit timeout: xid={}, branchId={}, attempt={}",
                                xid, branchId, attempt);
                        break;

                    case NETWORK_ERROR:
                        logger.warn("Commit network error: xid={}, branchId={}, attempt={}",
                                xid, branchId, attempt);
                        break;
                }

                // 如果最后一次尝试仍然失败，直接返回
                if (attempt >= maxAttempts) {
                    logger.error("All commit attempts failed: xid={}, branchId={}, totalAttempts={}",
                            xid, branchId, maxAttempts);
                    return false;
                }

                // 等待重试
                waitForRetry(retryInterval, attempt);
                retryInterval = calculateNextRetryInterval(retryInterval, attempt);

            } catch (Exception e) {
                logger.error("Unexpected error during commit: xid={}, branchId={}, attempt={}",
                        xid, branchId, attempt, e);

                if (attempt >= maxAttempts) {
                    return false;
                }

                waitForRetry(retryInterval, attempt);
                retryInterval = calculateNextRetryInterval(retryInterval, attempt);
            }
        }

        return false;
    }

    /**
     * 向资源管理器发送回滚指令
     * 支持多种通信方式和故障处理策略
     */
    private boolean sendRollbackToRM(BranchTransaction branch) {
        if (branch == null) {
            logger.warn("Cannot send rollback to null branch");
            return false;
        }

        final String xid = branch.getXid();
        final long branchId = branch.getBranchId();
        final String resourceId = branch.getResourceId();

        logger.info("Sending rollback instruction to RM: xid={}, branchId={}, resourceId={}",
                xid, branchId, resourceId);

        int attempt = 0;
        int maxAttempts = getMaxRetryAttempts(branch);
        long retryInterval = getInitialRetryInterval();

        while (attempt < maxAttempts) {
            attempt++;

            try {
                // 根据资源类型选择不同的通信方式
                CommunicationResult result = communicateWithRM(branch, "rollback");

                switch (result.getStatus()) {
                    case SUCCESS:
                        logger.info("Rollback successful: xid={}, branchId={}, attempt={}",
                                xid, branchId, attempt);
                        return true;

                    case FAILURE:
                        logger.warn("Rollback failed: xid={}, branchId={}, attempt={}, error={}",
                                xid, branchId, attempt, result.getError());
                        break;

                    case TIMEOUT:
                        logger.warn("Rollback timeout: xid={}, branchId={}, attempt={}",
                                xid, branchId, attempt);
                        break;

                    case NETWORK_ERROR:
                        logger.warn("Rollback network error: xid={}, branchId={}, attempt={}",
                                xid, branchId, attempt);
                        break;
                }

                // 如果最后一次尝试仍然失败，直接返回
                if (attempt >= maxAttempts) {
                    logger.error("All rollback attempts failed: xid={}, branchId={}, totalAttempts={}",
                            xid, branchId, maxAttempts);
                    return false;
                }

                // 等待重试
                waitForRetry(retryInterval, attempt);
                retryInterval = calculateNextRetryInterval(retryInterval, attempt);

            } catch (Exception e) {
                logger.error("Unexpected error during rollback: xid={}, branchId={}, attempt={}",
                        xid, branchId, attempt, e);

                if (attempt >= maxAttempts) {
                    return false;
                }

                waitForRetry(retryInterval, attempt);
                retryInterval = calculateNextRetryInterval(retryInterval, attempt);
            }
        }

        return false;
    }

    /**
     * 与资源管理器通信的通用方法
     */
    private CommunicationResult communicateWithRM(BranchTransaction branch, String operation) {
        final String resourceId = branch.getResourceId();
        final String xid = branch.getXid();
        final long branchId = branch.getBranchId();

        try {
            // 根据资源类型选择通信方式
            if (isDatabaseResource(resourceId)) {
                return communicateWithDatabaseRM(branch, operation);
            } else if (isMessageQueueResource(resourceId)) {
                return communicateWithMessageQueueRM(branch, operation);
            } else if (isExternalServiceResource(resourceId)) {
                return communicateWithExternalServiceRM(branch, operation);
            } else if (isTCCResource(resourceId)) {
                return communicateWithTCCRM(branch, operation);
            } else {
                logger.warn("Unknown resource type: {}", resourceId);
                return CommunicationResult.failure("Unknown resource type: " + resourceId);
            }

        } catch (NetworkException e) {
            return CommunicationResult.networkError(e.getMessage());
        } catch (Exception e) {
            return CommunicationResult.failure(e.getMessage());
        }
    }

    /**
     * 与数据库资源管理器通信
     */
    private CommunicationResult communicateWithDatabaseRM(BranchTransaction branch, String operation) {
        // 数据库资源的提交/回滚通常通过JDBC驱动完成
        // 这里可以集成各种数据库的特定逻辑

        /*String resourceId = branch.getResourceId();

        try {
            if ("commit".equals(operation)) {
                // 执行数据库提交
                boolean success = databaseResourceManager.commit(branch);
                return success ? CommunicationResult.success() :
                        CommunicationResult.failure("Database commit failed");
            } else {
                // 执行数据库回滚
                boolean success = databaseResourceManager.rollback(branch);
                return success ? CommunicationResult.success() :
                        CommunicationResult.failure("Database rollback failed");
            }
        } catch (SQLException e) {
            return handleDatabaseException(e, resourceId, operation);
        }*/
        return CommunicationResult.success();
    }

    /**
     * 与消息队列资源管理器通信
     */
    private CommunicationResult communicateWithMessageQueueRM(BranchTransaction branch, String operation) {
        // 消息队列的提交/回滚可能涉及消息确认或重投递

        /*try {
            if ("commit".equals(operation)) {
                return messageQueueManager.confirmMessage(branch);
            } else {
                return messageQueueManager.rejectMessage(branch);
            }
        } catch (MessageQueueException e) {
            return CommunicationResult.failure("Message queue error: " + e.getMessage());
        }*/
        return CommunicationResult.success();
    }

    /**
     * 与外部服务资源管理器通信
     */
    private CommunicationResult communicateWithExternalServiceRM(BranchTransaction branch, String operation) {
        String remoteAddress = ServiceAddressResolver.getServiceAddress(branch.getResourceId());

        try {
            ExternalServiceClient client = ExternalServiceClientFactory.getExternalServiceClient(branch.getResourceId());

            if ("commit".equals(operation)) {
                return client.confirmTransaction(branch.getXid(), branch.getBranchId());
            } else {
                return client.cancelTransaction(branch.getXid(), branch.getBranchId());
            }
        } catch (Exception e) {
            return CommunicationResult.failure("Unexpected error: " + e.getMessage());
        }
    }


    /**
     * 处理网络异常的重试逻辑
     */
    private boolean shouldRetryOnNetworkException(NetworkException e, int attempt, int maxAttempts) {
        if (!e.isRetryable()) {
            logger.warn("Non-retryable network exception: {}", e.getReadableDescription());
            return false;
        }

        NetworkExceptionUtils.Severity severity = NetworkExceptionUtils.getExceptionSeverity(e);

        // 根据异常严重级别决定重试策略
        switch (severity) {
            case CRITICAL:
                // 严重异常，只重试1次
                return attempt < Math.min(1, maxAttempts);
            case ERROR:
                // 错误级别，重试次数减半
                return attempt < Math.max(1, maxAttempts / 2);
            case WARNING:
            case INFO:
                // 警告和信息级别，正常重试
                return attempt < maxAttempts;
            default:
                return attempt < maxAttempts;
        }
    }

    /**
     * 与TCC资源管理器通信
     */
    private CommunicationResult communicateWithTCCRM(BranchTransaction branch, String operation) {
        // TCC模式的Confirm/Cancel操作

//        try {
//            TCCResourceManager tccManager = getTCCResourceManager(branch.getResourceId());
//
//            if ("commit".equals(operation)) {
//                return tccManager.confirm(branch.getXid(), branch.getBranchId());
//            } else {
//                return tccManager.cancel(branch.getXid(), branch.getBranchId());
//            }
//        } catch (TCCException e) {
//            return CommunicationResult.failure("TCC operation failed: " + e.getMessage());
//        }
        return null;
    }

    /**
     * 处理数据库异常
     */
//    private CommunicationResult handleDatabaseException(SQLException e, String resourceId, String operation) {
//        String sqlState = e.getSQLState();
//        int errorCode = e.getErrorCode();
//
//        // 根据不同的数据库错误码进行处理
//        if (isConnectionError(sqlState, errorCode)) {
//            return CommunicationResult.networkError("Database connection error: " + e.getMessage());
//        } else if (isTimeoutError(sqlState, errorCode)) {
//            return CommunicationResult.timeout("Database operation timeout: " + e.getMessage());
//        } else if (isDeadlockError(sqlState, errorCode)) {
//            return CommunicationResult.failure("Database deadlock: " + e.getMessage());
//        } else {
//            return CommunicationResult.failure("Database error: " + e.getMessage());
//        }
//    }

    /**
     * 获取最大重试次数（根据资源类型和操作类型动态调整）
     */
    private int getMaxRetryAttempts(BranchTransaction branch) {
        String resourceId = branch.getResourceId();

        // 数据库操作通常可以多重试几次
        if (isDatabaseResource(resourceId)) {
            return 5;
        }
        // 外部服务可能重试次数较少
        else if (isExternalServiceResource(resourceId)) {
            return 3;
        }
        // TCC操作通常需要精确控制重试
        else if (isTCCResource(resourceId)) {
            return 5; // TCC需要保证最终一致性
        }

        return 3; // 默认重试3次
    }

    /**
     * 获取初始重试间隔
     */
    private long getInitialRetryInterval() {
        return 1000; // 1秒
    }

    /**
     * 计算下一次重试间隔（指数退避算法）
     */
    private long calculateNextRetryInterval(long currentInterval, int attempt) {
        long nextInterval = (long) (currentInterval * 1.5); // 指数增长
        return Math.min(nextInterval, 30000); // 最大不超过30秒
    }

    /**
     * 等待重试
     */
    private void waitForRetry(long interval, int attempt) {
        try {
            logger.debug("Waiting for retry, attempt: {}, interval: {}ms", attempt, interval);
            Thread.sleep(interval);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.warn("Retry wait interrupted", e);
        }
    }

    /**
     * 资源类型判断辅助方法
     */
    private boolean isDatabaseResource(String resourceId) {
        return resourceId != null &&
                (resourceId.startsWith("jdbc:") || resourceId.contains("database"));
    }

    private boolean isMessageQueueResource(String resourceId) {
        return resourceId != null &&
                (resourceId.contains("mq") || resourceId.contains("queue") ||
                        resourceId.contains("kafka") || resourceId.contains("rabbitmq"));
    }

    private boolean isExternalServiceResource(String resourceId) {
        return resourceId != null &&
                (resourceId.startsWith("http") || resourceId.startsWith("https") ||
                        resourceId.contains("service") || resourceId.contains("api"));
    }

    private boolean isTCCResource(String resourceId) {
        return resourceId != null && resourceId.startsWith("tcc:");
    }

    /**
     * 通信结果封装类
     */
    /*private static class CommunicationResult {
        enum Status { SUCCESS, FAILURE, TIMEOUT, NETWORK_ERROR }

        private final Status status;
        private final String error;
        private final long timestamp;

        private CommunicationResult(Status status, String error) {
            this.status = status;
            this.error = error;
            this.timestamp = System.currentTimeMillis();
        }

        public static CommunicationResult success() {
            return new CommunicationResult(Status.SUCCESS, null);
        }

        public static CommunicationResult failure(String error) {
            return new CommunicationResult(Status.FAILURE, error);
        }

        public static CommunicationResult timeout(String error) {
            return new CommunicationResult(Status.TIMEOUT, error);
        }

        public static CommunicationResult networkError(String error) {
            return new CommunicationResult(Status.NETWORK_ERROR, error);
        }

        public Status getStatus() { return status; }
        public String getError() { return error; }
        public long getTimestamp() { return timestamp; }
        public boolean isSuccess() { return status == Status.SUCCESS; }
    }*/
}