package com.galaxytx.common.serialization;

import jakarta.jms.*;

import java.nio.charset.StandardCharsets;

/**
 * 抽象消息序列化器基类
 */
public abstract class AbstractMessageSerializer implements MessageSerializer {

    protected static final String CONTENT_TYPE_PROPERTY = "Content-Type";
    protected static final String MESSAGE_FORMAT_PROPERTY = "MessageFormat";

    @Override
    public Message toMessage(Session session, Object message) throws JMSException {
        if (message == null) {
            return session.createMessage(); // 空消息
        }

        try {
            byte[] serializedData = serializeObject(message);
            BytesMessage bytesMessage = session.createBytesMessage();
            bytesMessage.writeBytes(serializedData);

            // 设置消息属性
            bytesMessage.setStringProperty(CONTENT_TYPE_PROPERTY, getContentType());
            bytesMessage.setStringProperty(MESSAGE_FORMAT_PROPERTY, getFormat());
            bytesMessage.setStringProperty("OriginalType", message.getClass().getName());

            return bytesMessage;
        } catch (Exception e) {
            throw new JMSException("Failed to serialize message: " + e.getMessage());
        }
    }

    @Override
    public <T> T fromMessage(Message message, Class<T> targetType) throws JMSException {
        if (message == null) {
            return null;
        }

        try {
            if (message instanceof BytesMessage) {
                BytesMessage bytesMessage = (BytesMessage) message;
                byte[] data = new byte[(int) bytesMessage.getBodyLength()];
                bytesMessage.readBytes(data);
                return deserializeBytes(data, targetType);
            } else if (message instanceof TextMessage) {
                TextMessage textMessage = (TextMessage) message;
                String text = textMessage.getText();
                return deserializeString(text, targetType);
            } else {
                throw new JMSException("Unsupported message type: " + message.getClass().getName());
            }
        } catch (Exception e) {
            throw new JMSException("Failed to deserialize message: " + e.getMessage());
        }
    }

    /**
     * 序列化对象为字节数组
     */
    protected abstract byte[] serializeObject(Object obj) throws Exception;

    /**
     * 从字节数组反序列化对象
     */
    protected abstract <T> T deserializeBytes(byte[] data, Class<T> targetType) throws Exception;

    /**
     * 从字符串反序列化对象（可选实现）
     */
    protected <T> T deserializeString(String data, Class<T> targetType) throws Exception {
        return deserializeBytes(data.getBytes(StandardCharsets.UTF_8), targetType);
    }

    /**
     * 获取内容类型
     */
    protected abstract String getContentType();
}
