package com.galaxytx.common.model;

import java.util.concurrent.TimeUnit;

/**
 * 分支事务实体类-核心模型定义
 * 分支事务由资源管理器创建，负责维护分支事务的状态和属性
 * 分支事务包含唯一标识branchId、所属全局事务xid、资源标识resourceId、资源组标识resourceGroupId、锁定的资源key lockKey和分支事务状态status等属性
 *
 * @author: 刘志成
 * @date: 2025年5月18日
 */
public class BranchTransaction {

    private long branchId;
    private String xid;
    private String resourceGroupId;
    private String resourceId;
    private String lockKey;
    private BranchStatus status;
    private String applicationData;
    private long beginTime;
    private long endTime;
    private long timeoutMillis; // 分支事务超时时间

    // 默认超时时间（30秒）
    public static final long DEFAULT_TIMEOUT_MILLIS = 30000;
    // 最大超时时间（5分钟）
    public static final long MAX_TIMEOUT_MILLIS = 300000;
    // 最小超时时间（1秒）
    public static final long MIN_TIMEOUT_MILLIS = 1000;

    // 构造函数
    public BranchTransaction() {
        this.beginTime = System.currentTimeMillis();
        this.status = BranchStatus.REGISTERED;
        this.timeoutMillis = DEFAULT_TIMEOUT_MILLIS;
    }

    public BranchTransaction(String xid, String resourceGroupId, String resourceId) {
        this();
        this.xid = xid;
        this.resourceGroupId = resourceGroupId;
        this.resourceId = resourceId;
    }

    public BranchTransaction(String xid, String resourceGroupId, String resourceId, long timeoutMillis) {
        this(xid, resourceGroupId, resourceId);
        setTimeoutMillis(timeoutMillis);
    }

    // Getters and Setters
    public long getBranchId() { return branchId; }
    public void setBranchId(long branchId) { this.branchId = branchId; }

    public String getXid() { return xid; }
    public void setXid(String xid) { this.xid = xid; }

    public String getResourceGroupId() { return resourceGroupId; }
    public void setResourceGroupId(String resourceGroupId) { this.resourceGroupId = resourceGroupId; }

    public String getResourceId() { return resourceId; }
    public void setResourceId(String resourceId) { this.resourceId = resourceId; }

    public String getLockKey() { return lockKey; }
    public void setLockKey(String lockKey) { this.lockKey = lockKey; }

    public BranchStatus getStatus() { return status; }
    public void setStatus(BranchStatus status) {
        this.status = status;
        if (status.isPhaseTwoCompleted()) {
            this.endTime = System.currentTimeMillis();
        }
    }

    public String getApplicationData() { return applicationData; }
    public void setApplicationData(String applicationData) { this.applicationData = applicationData; }

    public long getBeginTime() { return beginTime; }
    public void setBeginTime(long beginTime) { this.beginTime = beginTime; }

    public long getEndTime() { return endTime; }
    public void setEndTime(long endTime) { this.endTime = endTime; }

    public long getTimeoutMillis() { return timeoutMillis; }
    public void setTimeoutMillis(long timeoutMillis) {
        // 确保超时时间在合理范围内
        if (timeoutMillis < MIN_TIMEOUT_MILLIS) {
            this.timeoutMillis = MIN_TIMEOUT_MILLIS;
        } else if (timeoutMillis > MAX_TIMEOUT_MILLIS) {
            this.timeoutMillis = MAX_TIMEOUT_MILLIS;
        } else {
            this.timeoutMillis = timeoutMillis;
        }
    }

    /**
     * 检查分支事务是否超时
     * @return true如果事务已超时，false如果未超时或已完成
     */
    public boolean isTimeout() {
        return isTimeout(this.timeoutMillis);
    }

    /**
     * 检查分支事务是否超时（使用指定超时时间）
     * @param customTimeoutMillis 自定义超时时间（毫秒）
     * @return true如果事务已超时，false如果未超时或已完成
     */
    public boolean isTimeout(long customTimeoutMillis) {
        // 如果事务已经完成，不算超时
        if (status.isFinalStatus()) {
            return false;
        }

        // 如果事务已经超时
        long currentTime = System.currentTimeMillis();
        long elapsedTime = currentTime - beginTime;

        return elapsedTime > customTimeoutMillis;
    }

    /**
     * 检查分支事务是否即将超时（提前警告）
     * @param warningThreshold 警告阈值（毫秒），默认5000ms
     * @return true如果事务即将超时
     */
    public boolean isAboutToTimeout(long warningThreshold) {
        if (status.isFinalStatus()) {
            return false;
        }

        long currentTime = System.currentTimeMillis();
        long elapsedTime = currentTime - beginTime;
        long remainingTime = timeoutMillis - elapsedTime;

        return remainingTime > 0 && remainingTime <= warningThreshold;
    }

    public boolean isAboutToTimeout() {
        return isAboutToTimeout(5000); // 默认5秒警告阈值
    }

    /**
     * 获取分支事务已运行时间（毫秒）
     */
    public long getElapsedTime() {
        if (endTime > 0) {
            return endTime - beginTime;
        }
        return System.currentTimeMillis() - beginTime;
    }

    /**
     * 获取分支事务剩余时间（毫秒）
     * @return 剩余时间，如果已超时返回负数，如果已完成返回0
     */
    public long getRemainingTime() {
        if (status.isFinalStatus()) {
            return 0;
        }

        long currentTime = System.currentTimeMillis();
        long elapsedTime = currentTime - beginTime;
        return timeoutMillis - elapsedTime;
    }

    /**
     * 获取剩余时间百分比
     * @return 0-100之间的百分比，超过100表示已超时
     */
    public int getRemainingTimePercentage() {
        if (status.isFinalStatus()) {
            return 100;
        }

        long elapsedTime = getElapsedTime();
        if (elapsedTime >= timeoutMillis) {
            return 0;
        }

        return (int) ((timeoutMillis - elapsedTime) * 100 / timeoutMillis);
    }

    /**
     * 获取分支事务持续时间（毫秒）
     * @deprecated 使用 getElapsedTime() 代替
     */
    @Deprecated
    public long getDuration() {
        return getElapsedTime();
    }

    /**
     * 重置超时时间（重新开始计时）
     */
    public void resetTimeout() {
        this.beginTime = System.currentTimeMillis();
    }

    /**
     * 延长超时时间
     * @param additionalMillis 要延长的毫秒数
     */
    public void extendTimeout(long additionalMillis) {
        if (additionalMillis > 0) {
            this.timeoutMillis += additionalMillis;
            // 确保不超过最大超时时间
            if (this.timeoutMillis > MAX_TIMEOUT_MILLIS) {
                this.timeoutMillis = MAX_TIMEOUT_MILLIS;
            }
        }
    }

    /**
     * 缩短超时时间
     * @param reduceMillis 要缩短的毫秒数
     */
    public void reduceTimeout(long reduceMillis) {
        if (reduceMillis > 0) {
            this.timeoutMillis -= reduceMillis;
            // 确保不低于最小超时时间
            if (this.timeoutMillis < MIN_TIMEOUT_MILLIS) {
                this.timeoutMillis = MIN_TIMEOUT_MILLIS;
            }
        }
    }

    /**
     * 获取超时时间的人类可读格式
     */
    public String getTimeoutReadable() {
        if (timeoutMillis < 1000) {
            return timeoutMillis + "ms";
        }

        long seconds = TimeUnit.MILLISECONDS.toSeconds(timeoutMillis);
        long millis = timeoutMillis % 1000;

        if (millis == 0) {
            return seconds + "s";
        } else {
            return seconds + "s " + millis + "ms";
        }
    }

    /**
     * 获取已运行时间的人类可读格式
     */
    public String getElapsedTimeReadable() {
        long elapsed = getElapsedTime();

        if (elapsed < 1000) {
            return elapsed + "ms";
        }

        long seconds = TimeUnit.MILLISECONDS.toSeconds(elapsed);
        long millis = elapsed % 1000;

        if (millis == 0) {
            return seconds + "s";
        } else {
            return seconds + "s " + millis + "ms";
        }
    }

    /**
     * 获取剩余时间的人类可读格式
     */
    public String getRemainingTimeReadable() {
        long remaining = getRemainingTime();

        if (remaining <= 0) {
            return "0ms (timeout)";
        }

        if (remaining < 1000) {
            return remaining + "ms";
        }

        long seconds = TimeUnit.MILLISECONDS.toSeconds(remaining);
        long millis = remaining % 1000;

        if (millis == 0) {
            return seconds + "s";
        } else {
            return seconds + "s " + millis + "ms";
        }
    }

    /**
     * 创建分支事务的深拷贝
     */
    public BranchTransaction copy() {
        BranchTransaction copy = new BranchTransaction();
        copy.branchId = this.branchId;
        copy.xid = this.xid;
        copy.resourceGroupId = this.resourceGroupId;
        copy.resourceId = this.resourceId;
        copy.lockKey = this.lockKey;
        copy.status = this.status;
        copy.applicationData = this.applicationData;
        copy.beginTime = this.beginTime;
        copy.endTime = this.endTime;
        copy.timeoutMillis = this.timeoutMillis;
        return copy;
    }

    @Override
    public String toString() {
        return "BranchTransaction{" +
                "branchId=" + branchId +
                ", xid='" + xid + '\'' +
                ", resourceGroupId='" + resourceGroupId + '\'' +
                ", resourceId='" + resourceId + '\'' +
                ", status=" + status +
                ", elapsedTime=" + getElapsedTimeReadable() +
                ", timeout=" + getTimeoutReadable() +
                ", remaining=" + getRemainingTimeReadable() +
                ", remainingPercentage=" + getRemainingTimePercentage() + "%" +
                '}';
    }
}
