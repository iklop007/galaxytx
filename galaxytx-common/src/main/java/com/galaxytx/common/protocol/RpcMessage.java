package com.galaxytx.common.protocol;

import java.util.concurrent.atomic.AtomicInteger;

public class RpcMessage {
    private static final AtomicInteger ID_GENERATOR = new AtomicInteger(0);

    private int id;

    private MessageType messageType;

    private Object body;

    private byte codec = 0;  // 序列化方式 0: json, 1: hessian, 2: protobuf

    private byte compress = 0; // 压缩方式 0: none, 1: gzip, 2: snappy

    public RpcMessage(MessageType messageType, Object body) {
        this.id = ID_GENERATOR.getAndIncrement();
        this.messageType = messageType;
        this.body = body;
    }

    // Getters
    public int getId() {
        return id;
    }

    public MessageType getMessageType() {
        return messageType;
    }

    public Object getBody() {
        return body;
    }

    public byte getCodec() {
        return codec;
    }

    public byte getCompress() {
        return compress;
    }

    // Setters
    public void setCodec(byte codec) {
        this.codec = codec;
    }
    public void setCompress(byte compress) {
        this.compress = compress;
    }

    public void setBody(Object body) {
        this.body = body;
    }

    public void setId(int id) {
        this.id = id;
    }

    public void setMessageType(MessageType messageType) {
        this.messageType = messageType;
    }

    @Override
    public String toString() {
        return "RpcMessage{" +
                "id=" + id +
                ", messageType=" + messageType +
                ", body=" + body +
                ", codec=" + codec +
                ", compress=" + compress +
                '}';
    }
}
