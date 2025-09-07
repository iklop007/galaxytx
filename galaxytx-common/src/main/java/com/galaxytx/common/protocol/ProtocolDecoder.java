package com.galaxytx.common.protocol;

import com.galaxytx.common.model.BranchTransaction;
import com.galaxytx.common.model.GlobalTransaction;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ByteToMessageDecoder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * 协议解码器
 * 负责将字节流解码为 RpcMessage 对象
 *
 * @author 刘志成
 * @date 2023-09-07
 */
public class ProtocolDecoder extends ByteToMessageDecoder {
    private static final Logger logger = LoggerFactory.getLogger(ProtocolDecoder.class);

    /**
     * 协议魔数，用于标识协议开始
     */
    public static final short MAGIC_CODE = (short) 0xCAFE;

    /**
     * 协议版本
     */
    public static final byte VERSION = 1;

    /**
     * 协议头长度
     */
    public static final int HEADER_LENGTH = 12; // 2 + 1 + 1 + 4 + 4

    @Override
    protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) throws Exception {
        // 可读字节数小于协议头长度，等待更多数据
        if (in.readableBytes() < HEADER_LENGTH) {
            return;
        }

        in.markReaderIndex();

        // 读取并验证魔数
        short magic = in.readShort();
        if (magic != MAGIC_CODE) {
            logger.error("Invalid magic code: 0x{}, close connection", Integer.toHexString(magic));
            ctx.close();
            return;
        }

        // 读取版本
        byte version = in.readByte();
        if (version > VERSION) {
            logger.warn("Unsupported protocol version: {}, current version: {}", version, VERSION);
            // 可以处理版本兼容性，这里简单关闭连接
            ctx.close();
            return;
        }

        // 读取消息类型
        byte typeCode = in.readByte();
        MessageType messageType = MessageType.getByCode(typeCode);
        if (messageType == null) {
            logger.error("Unknown message type code: {}", typeCode);
            ctx.close();
            return;
        }

        // 读取消息ID
        int messageId = in.readInt();

        // 读取消息体长度
        int bodyLength = in.readInt();

        // 检查消息体是否完整
        if (in.readableBytes() < bodyLength) {
            in.resetReaderIndex(); // 重置读取位置，等待更多数据
            return;
        }

        // 读取消息体
        byte[] bodyBytes = new byte[bodyLength];
        in.readBytes(bodyBytes);

        try {
            // 反序列化消息体
            Object body = deserializeBody(bodyBytes, messageType);

            // 构建 RpcMessage 对象
            RpcMessage message = new RpcMessage(messageType, body);
            message.setId(messageId);

            out.add(message);

            if (logger.isDebugEnabled()) {
                logger.debug("Decoded message: id={}, type={}, bodyLength={}",
                        messageId, messageType, bodyLength);
            }

        } catch (Exception e) {
            logger.error("Failed to deserialize message body", e);
            // 可以选择发送错误响应或者关闭连接
        }
    }

    /**
     * 反序列化消息体
     */
    private Object deserializeBody(byte[] bodyBytes, MessageType messageType) {
        // 这里使用简单的JSON序列化，实际生产环境可以使用Hessian、Protobuf等
        String json = new String(bodyBytes, java.nio.charset.StandardCharsets.UTF_8);

        // 根据消息类型反序列化为对应的Java对象
        switch (messageType) {
            case GLOBAL_BEGIN:
                return fromJson(json, GlobalTransaction.class);
            case BRANCH_REGISTER:
                return fromJson(json, BranchTransaction.class);
            case BRANCH_STATUS_REPORT:
                return fromJson(json, java.util.Map.class);
            case GLOBAL_COMMIT:
            case GLOBAL_ROLLBACK:
            case GLOBAL_STATUS:
                return json; // 这些消息体通常是String类型的xid
            default:
                return json;
        }
    }

    /**
     * JSON反序列化辅助方法
     */
    private <T> T fromJson(String json, Class<T> clazz) {
        try {
            // 这里使用简单的JSON处理，实际可以使用Jackson、Gson等
            if (clazz == String.class) {
                return clazz.cast(json);
            }
            // 简化实现，实际需要完整的JSON解析
            return clazz.newInstance();
        } catch (Exception e) {
            throw new RuntimeException("JSON deserialization failed", e);
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        logger.error("Protocol decoder exception", cause);
        ctx.close();
    }
}