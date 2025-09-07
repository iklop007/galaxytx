package com.galaxytx.common.model;

/**
 * 分支事务状态枚举
 * 用于表示AT模式和TCC模式下分支事务的不同状态
 *
 * @author 刘志成
 * @date 2025年9月6日
 */
public enum BranchStatus {

    /**
     * 已注册 - 分支事务已向TC注册但尚未执行完成（AT模式/TCC Try阶段）
     */
    REGISTERED(1, "Registered"),

    /**
     * 一阶段完成 - AT模式本地事务已提交，TCC模式Try方法执行成功
     */
    PHASEONE_DONE(2, "PhaseOne Done"),

    /**
     * 一阶段失败 - AT模式本地事务提交失败，TCC模式Try方法执行失败
     */
    PHASEONE_FAILED(3, "PhaseOne Failed"),

    /**
     * 二阶段提交中 - 收到TC的提交指令，正在执行提交操作
     */
    PHASETWO_COMMITTING(4, "PhaseTwo Committing"),

    /**
     * 二阶段提交完成 - 提交操作成功完成（AT模式删除undo_log成功，TCC Confirm成功）
     */
    PHASETWO_COMMITTED(5, "PhaseTwo Committed"),

    /**
     * 二阶段提交失败 - 提交操作失败
     */
    PHASETWO_COMMIT_FAILED(6, "PhaseTwo Commit Failed"),

    /**
     * 二阶段回滚中 - 收到TC的回滚指令，正在执行回滚操作
     */
    PHASETWO_ROLLBACKING(7, "PhaseTwo Rollbacking"),

    /**
     * 二阶段回滚完成 - 回滚操作成功完成（AT模式执行undo_log成功，TCC Cancel成功）
     */
    PHASETWO_ROLLBACKED(8, "PhaseTwo Rollbacked"),

    /**
     * 二阶段回滚失败 - 回滚操作失败
     */
    PHASETWO_ROLLBACK_FAILED(9, "PhaseTwo Rollback Failed"),

    /**
     * 超时 - 分支事务执行超时
     */
    TIMEOUT(10, "Timeout"),

    /**
     * 未知状态 - 状态不明确，需要人工干预
     */
    UNKNOWN(99, "Unknown");

    private final int code;
    private final String description;

    BranchStatus(int code, String description) {
        this.code = code;
        this.description = description;
    }

    public int getCode() {
        return code;
    }

    public String getDescription() {
        return description;
    }

    /**
     * 根据code获取枚举值
     */
    public static BranchStatus getByCode(int code) {
        for (BranchStatus status : values()) {
            if (status.code == code) {
                return status;
            }
        }
        return UNKNOWN;
    }

    /**
     * 判断是否是一阶段完成状态
     */
    public boolean isPhaseOneDone() {
        return this == PHASEONE_DONE;
    }

    /**
     * 判断是否是一阶段失败状态
     */
    public boolean isPhaseOneFailed() {
        return this == PHASEONE_FAILED;
    }

    /**
     * 判断是否是二阶段完成状态（提交或回滚完成）
     */
    public boolean isPhaseTwoCompleted() {
        return this == PHASETWO_COMMITTED || this == PHASETWO_ROLLBACKED;
    }

    /**
     * 判断是否是最终状态（无需再处理）
     */
    public boolean isFinalStatus() {
        return this == PHASETWO_COMMITTED || this == PHASETWO_ROLLBACKED
                || this == PHASETWO_COMMIT_FAILED || this == PHASETWO_ROLLBACK_FAILED
                || this == TIMEOUT || this == UNKNOWN;
    }

    @Override
    public String toString() {
        return description + "(" + code + ")";
    }
}