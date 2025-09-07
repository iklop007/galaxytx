package com.galaxytx.common.protocol;

import com.galaxytx.common.model.BranchTransaction;
import com.galaxytx.common.model.GlobalTransaction;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToByteEncoder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 协议编码器
 * 负责将 RpcMessage 对象编码为字节流
 *
 * @author 刘志成
 * @date 2023-09-07
 */
public class ProtocolEncoder extends MessageToByteEncoder<RpcMessage> {
    private static final Logger logger = LoggerFactory.getLogger(ProtocolEncoder.class);

    @Override
    protected void encode(ChannelHandlerContext ctx, RpcMessage message, ByteBuf out) throws Exception {
        try {
            // 序列化消息体
            byte[] bodyBytes = serializeBody(message.getBody());
            int bodyLength = bodyBytes.length;

            // 写入协议头
            out.writeShort(ProtocolDecoder.MAGIC_CODE);    // 魔数: 2字节
            out.writeByte(ProtocolDecoder.VERSION);        // 版本: 1字节
            out.writeByte(message.getMessageType().getCode()); // 消息类型: 1字节
            out.writeInt(message.getId());                 // 消息ID: 4字节
            out.writeInt(bodyLength);                      // 消息体长度: 4字节

            // 写入消息体
            out.writeBytes(bodyBytes);

            if (logger.isDebugEnabled()) {
                logger.debug("Encoded message: id={}, type={}, bodyLength={}",
                        message.getId(), message.getMessageType(), bodyLength);
            }

        } catch (Exception e) {
            logger.error("Failed to encode message", e);
            throw e;
        }
    }

    /**
     * 序列化消息体
     */
    private byte[] serializeBody(Object body) {
        if (body == null) {
            return new byte[0];
        }

        // 这里使用简单的JSON序列化，实际生产环境可以使用Hessian、Protobuf等
        String json;
        if (body instanceof String) {
            json = (String) body;
        } else {
            json = toJson(body);
        }

        return json.getBytes(java.nio.charset.StandardCharsets.UTF_8);
    }

    /**
     * JSON序列化辅助方法
     */
    private String toJson(Object obj) {
        // 简化实现，实际可以使用Jackson、Gson等
        if (obj instanceof GlobalTransaction) {
            GlobalTransaction tx = (GlobalTransaction) obj;
            return String.format("{\"xid\":\"%s\",\"status\":\"%s\",\"timeout\":%d}",
                    tx.getXid(), tx.getStatus(), tx.getTimeout());
        } else if (obj instanceof BranchTransaction) {
            BranchTransaction branch = (BranchTransaction) obj;
            return String.format("{\"xid\":\"%s\",\"resourceId\":\"%s\",\"status\":%d}",
                    branch.getXid(), branch.getResourceId(), branch.getStatus().getCode());
        } else if (obj instanceof java.util.Map) {
            @SuppressWarnings("unchecked")
            java.util.Map<String, Object> map = (java.util.Map<String, Object>) obj;
            return String.format("{\"branchId\":%d,\"status\":%d}",
                    map.get("branchId"), map.get("status"));
        }

        return obj.toString();
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        logger.error("Protocol encoder exception", cause);
        ctx.close();
    }
}