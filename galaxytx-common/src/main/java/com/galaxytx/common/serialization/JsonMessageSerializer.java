package com.galaxytx.common.serialization;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

/**
 * JSON 消息序列化器
 */
public class JsonMessageSerializer extends AbstractMessageSerializer {

    private final ObjectMapper objectMapper;

    public JsonMessageSerializer() {
        this.objectMapper = new ObjectMapper();
        // 可以在这里配置 ObjectMapper
         objectMapper.configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false);
         objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }

    public JsonMessageSerializer(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    protected byte[] serializeObject(Object obj) throws Exception {
        return objectMapper.writeValueAsBytes(obj);
    }

    @Override
    protected <T> T deserializeBytes(byte[] data, Class<T> targetType) throws Exception {
        return objectMapper.readValue(data, targetType);
    }

    @Override
    protected <T> T deserializeString(String data, Class<T> targetType) throws Exception {
        return objectMapper.readValue(data, targetType);
    }

    @Override
    public String getFormat() {
        return "JSON";
    }

    @Override
    protected String getContentType() {
        return "application/json";
    }
}
