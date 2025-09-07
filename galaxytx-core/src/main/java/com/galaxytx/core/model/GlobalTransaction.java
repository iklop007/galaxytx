package com.galaxytx.core.model;

import java.util.concurrent.atomic.AtomicLong;

/**
 * 全局事务实体类-核心模型定义
 * 全局事务由事务管理器创建，负责维护全局事务的状态和属性
 * 全局事务包含唯一标识xid、状态、超时时间、开始时间、应用标识和事务名称等属性
 * 全局事务的状态由TransactionStatus枚举定义，包括BEGIN、COMMITTED、ROLLBACKED等状态
 *
 * @date 2025-09-06
 * @version 1.0
 * @author 刘志成
 */
public class GlobalTransaction {
    private static final AtomicLong ID_GENERATOR = new AtomicLong(0);

    private String xid;
    private TransactionStatus status;
    private long timeout;
    private long beginTime;
    private String applicationId;
    private String transactionName;

    public GlobalTransaction(String applicationId, String transactionName, long timeout) {
        this.xid = generateXid(applicationId);
        this.status = TransactionStatus.BEGIN;
        this.timeout = 60000; // default timeout 60 seconds
        this.beginTime = System.currentTimeMillis();
        this.applicationId = applicationId;
        this.transactionName = transactionName;
    }

    private String generateXid(String applicationId) {
        return applicationId + ":" + System.currentTimeMillis() + ":" + ID_GENERATOR.incrementAndGet();
    }

    public String getXid() {
        return xid;
    }

    public void setXid(String xid) {
        this.xid = xid;
    }

    public TransactionStatus getStatus() {
        return status;
    }

    public void setStatus(TransactionStatus status) {
        this.status = status;
    }

    public long getTimeout() {
        return timeout;
    }

    public void setTimeout(long timeout) {
        this.timeout = timeout;
    }

    public long getBeginTime() {
        return beginTime;
    }

    public void setBeginTime(long beginTime) {
        this.beginTime = beginTime;
    }

    public String getApplicationId() {
        return applicationId;
    }

    public void setApplicationId(String applicationId) {
        this.applicationId = applicationId;
    }

    public String getTransactionName() {
        return transactionName;
    }

    public void setTransactionName(String transactionName) {
        this.transactionName = transactionName;
    }
}
