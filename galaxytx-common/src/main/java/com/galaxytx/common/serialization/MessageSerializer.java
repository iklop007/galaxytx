package com.galaxytx.common.serialization;

import jakarta.jms.JMSException;
import jakarta.jms.Message;
import jakarta.jms.Session;

/**
 * 消息序列化器接口
 * 用于统一消息的序列化格式
 */
public interface MessageSerializer {

    /**
     * 将对象转换为 JMS Message
     * @param session JMS 会话
     * @param message 要序列化的对象
     * @return JMS Message
     * @throws JMSException 如果序列化失败
     */
    Message toMessage(Session session, Object message) throws JMSException;

    /**
     * 从 JMS Message 反序列化为对象
     * @param message JMS 消息
     * @param targetType 目标类型
     * @return 反序列化后的对象
     * @throws JMSException 如果反序列化失败
     */
    <T> T fromMessage(Message message, Class<T> targetType) throws JMSException;

    /**
     * 获取序列化格式名称
     */
    String getFormat();
}