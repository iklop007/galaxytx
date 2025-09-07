package com.galaxytx.core.resource;

import com.galaxytx.core.model.BranchTransaction;
import com.galaxytx.core.model.CommunicationResult;
import com.galaxytx.core.serialization.MessageSerializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import jakarta.jms.*;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 消息队列资源管理器
 * 负责处理消息队列类型资源的确认和取消操作
 *
 * @author 刘志成
 * @date 2023-09-07
 */
@Component
public class MessageQueueManager {
    private static final Logger logger = LoggerFactory.getLogger(MessageQueueManager.class);

    private final Map<String, ConnectionFactory> connectionFactoryMap = new ConcurrentHashMap<>();
    private final Map<String, Session> sessionMap = new ConcurrentHashMap<>();
    private final MessageSerializer messageSerializer;

    @Autowired
    public MessageQueueManager(MessageSerializer messageSerializer) {
        this.messageSerializer = messageSerializer;
    }

    /**
     * 注册消息队列连接工厂
     */
    public void registerConnectionFactory(String resourceId, ConnectionFactory connectionFactory) {
        connectionFactoryMap.put(resourceId, connectionFactory);
        logger.info("Registered message queue connection factory for resource: {}", resourceId);
    }

    /**
     * 确认消息（提交操作）
     */
    public CommunicationResult confirmMessage(BranchTransaction branch) {
        String resourceId = branch.getResourceId();
        String xid = branch.getXid();
        long branchId = branch.getBranchId();

        logger.info("Confirming message: xid={}, branchId={}, resourceId={}",
                xid, branchId, resourceId);

        try {
            ConnectionFactory connectionFactory = getConnectionFactory(resourceId);
            if (connectionFactory == null) {
                return CommunicationResult.failure("ConnectionFactory not found for resource: " + resourceId);
            }

            try (Connection connection = connectionFactory.createConnection();
                 Session session = connection.createSession(true, Session.SESSION_TRANSACTED)) {

                // 根据消息ID查找消息并确认
                String messageId = extractMessageId(branch);
                if (messageId != null) {
                    return confirmMessageById(session, messageId, branch);
                } else {
                    return confirmTransactionalMessage(session, branch);
                }
            }

        } catch (JMSException e) {
            logger.error("Message confirmation failed: xid={}, branchId={}", xid, branchId, e);
            return CommunicationResult.failure("Message confirmation failed: " + e.getMessage());
        } catch (Exception e) {
            logger.error("Unexpected error during message confirmation: xid={}, branchId={}", xid, branchId, e);
            return CommunicationResult.failure("Unexpected error: " + e.getMessage());
        }
    }

    /**
     * 拒绝消息（回滚操作）
     */
    public CommunicationResult rejectMessage(BranchTransaction branch) {
        String resourceId = branch.getResourceId();
        String xid = branch.getXid();
        long branchId = branch.getBranchId();

        logger.info("Rejecting message: xid={}, branchId={}, resourceId={}",
                xid, branchId, resourceId);

        try {
            ConnectionFactory connectionFactory = getConnectionFactory(resourceId);
            if (connectionFactory == null) {
                return CommunicationResult.failure("ConnectionFactory not found for resource: " + resourceId);
            }

            try (Connection connection = connectionFactory.createConnection();
                 Session session = connection.createSession(true, Session.SESSION_TRANSACTED)) {

                // 根据消息ID查找消息并拒绝
                String messageId = extractMessageId(branch);
                if (messageId != null) {
                    return rejectMessageById(session, messageId, branch);
                } else {
                    return rejectTransactionalMessage(session, branch);
                }
            }

        } catch (JMSException e) {
            logger.error("Message rejection failed: xid={}, branchId={}", xid, branchId, e);
            return CommunicationResult.failure("Message rejection failed: " + e.getMessage());
        } catch (Exception e) {
            logger.error("Unexpected error during message rejection: xid={}, branchId={}", xid, branchId, e);
            return CommunicationResult.failure("Unexpected error: " + e.getMessage());
        }
    }

    /**
     * 通过消息ID确认消息
     */
    private CommunicationResult confirmMessageById(Session session, String messageId, BranchTransaction branch)
            throws JMSException {

        // 在实际实现中，需要根据消息系统提供的API来确认消息
        // 这里简化处理
        session.commit();
        logger.debug("Message confirmed by ID: {}", messageId);
        return CommunicationResult.success();
    }

    /**
     * 通过消息ID拒绝消息
     */
    private CommunicationResult rejectMessageById(Session session, String messageId, BranchTransaction branch)
            throws JMSException {

        session.rollback();
        logger.debug("Message rejected by ID: {}", messageId);
        return CommunicationResult.success();
    }

    /**
     * 确认事务性消息
     */
    private CommunicationResult confirmTransactionalMessage(Session session, BranchTransaction branch)
            throws JMSException {

        session.commit();
        logger.debug("Transactional message confirmed");
        return CommunicationResult.success();
    }

    /**
     * 拒绝事务性消息
     */
    private CommunicationResult rejectTransactionalMessage(Session session, BranchTransaction branch)
            throws JMSException {

        session.rollback();
        logger.debug("Transactional message rejected");
        return CommunicationResult.success();
    }

    /**
     * 从分支事务中提取消息ID
     */
    private String extractMessageId(BranchTransaction branch) {
        // 从分支事务的应用数据中提取消息ID
        String applicationData = branch.getApplicationData();
        if (applicationData != null && applicationData.contains("messageId")) {
            // 实际实现中需要解析JSON或特定格式
            return applicationData.replaceAll(".*messageId[=:\"']([^\"']+).*", "$1");
        }
        return null;
    }

    /**
     * 获取连接工厂
     */
    private ConnectionFactory getConnectionFactory(String resourceId) {
        ConnectionFactory factory = connectionFactoryMap.get(resourceId);
        if (factory == null) {
            logger.warn("ConnectionFactory not found for resource: {}", resourceId);
        }
        return factory;
    }

    /**
     * 发送事务消息
     */
    public CommunicationResult sendTransactionalMessage(String resourceId, String destination, Object message,
                                                        String xid, long branchId) {
        try {
            ConnectionFactory connectionFactory = getConnectionFactory(resourceId);
            if (connectionFactory == null) {
                return CommunicationResult.failure("ConnectionFactory not found");
            }

            try (Connection connection = connectionFactory.createConnection();
                 Session session = connection.createSession(true, Session.SESSION_TRANSACTED);
                 MessageProducer producer = session.createProducer(session.createQueue(destination))) {

                Message jmsMessage = messageSerializer.toMessage(session, message);
                jmsMessage.setStringProperty("XID", xid);
                jmsMessage.setLongProperty("BranchID", branchId);

                producer.send(jmsMessage);
                // 不立即提交，等待全局事务提交

                return CommunicationResult.success();
            }

        } catch (JMSException e) {
            logger.error("Failed to send transactional message: xid={}, branchId={}", xid, branchId, e);
            return CommunicationResult.failure("Failed to send message: " + e.getMessage());
        }
    }

    /**
     * 获取所有注册的连接工厂
     */
    public Map<String, ConnectionFactory> getConnectionFactories() {
        return new ConcurrentHashMap<>(connectionFactoryMap);
    }

    /**
     * 移除连接工厂
     */
    public void removeConnectionFactory(String resourceId) {
        connectionFactoryMap.remove(resourceId);
        logger.info("Removed connection factory for resource: {}", resourceId);
    }

    /**
     * 清理所有资源
     */
    public void cleanup() {
        connectionFactoryMap.clear();
        // 清理会话
        sessionMap.values().forEach(session -> {
            try {
                session.close();
            } catch (JMSException e) {
                logger.warn("Failed to close session", e);
            }
        });
        sessionMap.clear();
        logger.info("Cleaned up all message queue resources");
    }
}