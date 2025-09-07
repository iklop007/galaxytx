package com.galaxytx.common.serialization;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

/**
 * JSON序列化工具类
 */
public class JsonSerializer {
    private static final Logger logger = LoggerFactory.getLogger(JsonSerializer.class);
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    public static byte[] serialize(Object obj) {
        try {
            return OBJECT_MAPPER.writeValueAsBytes(obj);
        } catch (IOException e) {
            logger.error("JSON serialization failed", e);
            throw new RuntimeException("Serialization failed", e);
        }
    }

    public static <T> T deserialize(byte[] bytes, Class<T> clazz) {
        try {
            return OBJECT_MAPPER.readValue(bytes, clazz);
        } catch (IOException e) {
            logger.error("JSON deserialization failed", e);
            throw new RuntimeException("Deserialization failed", e);
        }
    }

    public static String toJsonString(Object obj) {
        try {
            return OBJECT_MAPPER.writeValueAsString(obj);
        } catch (IOException e) {
            logger.error("JSON serialization to string failed", e);
            return "{}";
        }
    }
}