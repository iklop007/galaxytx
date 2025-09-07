package com.galaxytx.common.serialization;

/**
 * 序列化器工厂
 */
public class MessageSerializerFactory {

    public static MessageSerializer createJsonSerializer() {
        return new JsonMessageSerializer();
    }

    public static MessageSerializer createJsonSerializer(com.fasterxml.jackson.databind.ObjectMapper objectMapper) {
        return new JsonMessageSerializer(objectMapper);
    }

    // 可以添加其他格式的工厂方法
    // public static MessageSerializer createProtobufSerializer() {...}
    // public static MessageSerializer createAvroSerializer() {...}
}
