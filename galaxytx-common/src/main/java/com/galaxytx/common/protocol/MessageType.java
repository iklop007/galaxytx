package com.galaxytx.common.protocol;

import java.util.HashMap;
import java.util.Map;

/**
 * 消息类型枚举
 * 定义了不同类型的消息及其对应的代码
 * 消息类型包括全局事务操作、分支事务操作和响应结果
 *
 * @date 2025-09-06
 * @author 刘志成
 */
public enum MessageType {
    // Global Transaction
    GLOBAL_BEGIN(10),
    GLOBAL_COMMIT(11),
    GLOBAL_ROLLBACK(12),
    GLOBAL_STATUS(13),

    // Branch Transaction
    BRANCH_REGISTER(20),
    BRANCH_STATUS_REPORT(21), // 新增分支状态报告消息类型

    // Response
    RESULT(100);

    private final int code;
    private static final Map<Integer, MessageType> CODE_MAP = new HashMap<>();

    static {
        for (MessageType type : MessageType.values()) {
            CODE_MAP.put(type.code, type);
        }
    }
    MessageType(int code) {
        this.code = code;
    }

    public int getCode() {
        return code;
    }
    /**
     * 根据code获取消息类型
     *
     * @param code 消息类型代码
     * @return 消息类型枚举
     */
    public static MessageType getByCode(int code) {
        return CODE_MAP.get(code);
    }
}
