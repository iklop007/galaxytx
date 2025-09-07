package com.galaxytx.core.util;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fasterxml.jackson.datatype.jsr310.deser.LocalDateDeserializer;
import com.fasterxml.jackson.datatype.jsr310.deser.LocalDateTimeDeserializer;
import com.fasterxml.jackson.datatype.jsr310.deser.LocalTimeDeserializer;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalDateSerializer;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalDateTimeSerializer;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalTimeSerializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * JSON工具类
 * 基于Jackson的高性能JSON序列化和反序列化工具
 */
public class JsonUtils {
    private static final Logger logger = LoggerFactory.getLogger(JsonUtils.class);

    // 默认日期时间格式
    private static final String DEFAULT_DATE_TIME_FORMAT = "yyyy-MM-dd HH:mm:ss";
    private static final String DEFAULT_DATE_FORMAT = "yyyy-MM-dd";
    private static final String DEFAULT_TIME_FORMAT = "HH:mm:ss";

    // 对象映射器缓存（支持不同配置）
    private static final Map<String, ObjectMapper> OBJECT_MAPPER_CACHE = new ConcurrentHashMap<>();

    // 默认对象映射器
    private static volatile ObjectMapper defaultObjectMapper;

    static {
        // 初始化默认配置
        initializeDefaultObjectMapper();
    }

    /**
     * 初始化默认ObjectMapper
     */
    private static void initializeDefaultObjectMapper() {
        defaultObjectMapper = new ObjectMapper();

        // 基本配置
        defaultObjectMapper.configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false);
        defaultObjectMapper.configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false);
        defaultObjectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        defaultObjectMapper.configure(DeserializationFeature.ACCEPT_SINGLE_VALUE_AS_ARRAY, true);
        defaultObjectMapper.configure(DeserializationFeature.READ_UNKNOWN_ENUM_VALUES_AS_NULL, true);

        // 设置日期格式
        defaultObjectMapper.setDateFormat(new SimpleDateFormat(DEFAULT_DATE_TIME_FORMAT));

        // 设置null值不序列化
        defaultObjectMapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);

        // 注册Java 8时间模块
        JavaTimeModule javaTimeModule = new JavaTimeModule();

        // 配置LocalDateTime序列化和反序列化
        javaTimeModule.addSerializer(LocalDateTime.class,
                new LocalDateTimeSerializer(DateTimeFormatter.ofPattern(DEFAULT_DATE_TIME_FORMAT)));
        javaTimeModule.addDeserializer(LocalDateTime.class,
                new LocalDateTimeDeserializer(DateTimeFormatter.ofPattern(DEFAULT_DATE_TIME_FORMAT)));

        // 配置LocalDate序列化和反序列化
        javaTimeModule.addSerializer(LocalDate.class,
                new LocalDateSerializer(DateTimeFormatter.ofPattern(DEFAULT_DATE_FORMAT)));
        javaTimeModule.addDeserializer(LocalDate.class,
                new LocalDateDeserializer(DateTimeFormatter.ofPattern(DEFAULT_DATE_FORMAT)));

        // 配置LocalTime序列化和反序列化
        javaTimeModule.addSerializer(LocalTime.class,
                new LocalTimeSerializer(DateTimeFormatter.ofPattern(DEFAULT_TIME_FORMAT)));
        javaTimeModule.addDeserializer(LocalTime.class,
                new LocalTimeDeserializer(DateTimeFormatter.ofPattern(DEFAULT_TIME_FORMAT)));

        // Long类型转为String（避免JavaScript精度丢失）
        SimpleModule longModule = new SimpleModule();
        longModule.addSerializer(Long.class, ToStringSerializer.instance);
        longModule.addSerializer(Long.TYPE, ToStringSerializer.instance);

        defaultObjectMapper.registerModules(javaTimeModule, longModule);
    }

    /**
     * 获取默认ObjectMapper
     */
    public static ObjectMapper getDefaultObjectMapper() {
        return defaultObjectMapper;
    }

    /**
     * 获取指定配置的ObjectMapper
     */
    public static ObjectMapper getObjectMapper(String configKey) {
        return OBJECT_MAPPER_CACHE.computeIfAbsent(configKey, key -> {
            ObjectMapper mapper = defaultObjectMapper.copy();
            // 可以根据configKey进行特殊配置
            switch (key) {
                case "pretty":
                    mapper.enable(SerializationFeature.INDENT_OUTPUT);
                    break;
                case "include_null":
                    mapper.setSerializationInclusion(JsonInclude.Include.ALWAYS);
                    break;
                case "strict":
                    mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, true);
                    break;
            }
            return mapper;
        });
    }

    /**
     * 对象转换为JSON字符串（使用默认配置）
     */
    public static String toJson(Object object) {
        return toJson(object, defaultObjectMapper);
    }

    /**
     * 对象转换为JSON字符串（指定ObjectMapper）
     */
    public static String toJson(Object object, ObjectMapper objectMapper) {
        if (object == null) {
            return null;
        }

        try {
            return objectMapper.writeValueAsString(object);
        } catch (JsonProcessingException e) {
            logger.error("Object to JSON conversion failed", e);
            throw new JsonConversionException("Failed to convert object to JSON", e);
        }
    }

    /**
     * 对象转换为格式化的JSON字符串
     */
    public static String toPrettyJson(Object object) {
        try {
            return getObjectMapper("pretty").writeValueAsString(object);
        } catch (JsonProcessingException e) {
            logger.error("Object to pretty JSON conversion failed", e);
            throw new JsonConversionException("Failed to convert object to pretty JSON", e);
        }
    }

    /**
     * JSON字符串转换为对象
     */
    public static <T> T fromJson(String json, Class<T> clazz) {
        return fromJson(json, clazz, defaultObjectMapper);
    }

    /**
     * JSON字符串转换为对象（指定ObjectMapper）
     */
    public static <T> T fromJson(String json, Class<T> clazz, ObjectMapper objectMapper) {
        if (json == null || json.trim().isEmpty()) {
            return null;
        }

        try {
            return objectMapper.readValue(json, clazz);
        } catch (JsonProcessingException e) {
            logger.error("JSON to object conversion failed. JSON: {}, Class: {}", json, clazz.getName(), e);
            throw new JsonConversionException("Failed to convert JSON to object: " + clazz.getName(), e);
        }
    }

    /**
     * JSON字符串转换为复杂类型对象（支持泛型）
     */
    public static <T> T fromJson(String json, TypeReference<T> typeReference) {
        return fromJson(json, typeReference, defaultObjectMapper);
    }

    public static <T> T fromJson(String json, TypeReference<T> typeReference, ObjectMapper objectMapper) {
        if (json == null || json.trim().isEmpty()) {
            return null;
        }

        try {
            return objectMapper.readValue(json, typeReference);
        } catch (JsonProcessingException e) {
            logger.error("JSON to generic object conversion failed. JSON: {}, Type: {}", json, typeReference.getType(), e);
            throw new JsonConversionException("Failed to convert JSON to generic object", e);
        }
    }

    /**
     * JSON字符串转换为List
     */
    public static <T> List<T> fromJsonToList(String json, Class<T> elementClass) {
        if (json == null || json.trim().isEmpty()) {
            return Collections.emptyList();
        }

        try {
            JavaType javaType = defaultObjectMapper.getTypeFactory()
                    .constructCollectionType(List.class, elementClass);
            return defaultObjectMapper.readValue(json, javaType);
        } catch (JsonProcessingException e) {
            logger.error("JSON to List conversion failed. JSON: {}, ElementClass: {}", json, elementClass.getName(), e);
            throw new JsonConversionException("Failed to convert JSON to List: " + elementClass.getName(), e);
        }
    }

    /**
     * JSON字符串转换为Map
     */
    public static <K, V> Map<K, V> fromJsonToMap(String json, Class<K> keyClass, Class<V> valueClass) {
        if (json == null || json.trim().isEmpty()) {
            return Collections.emptyMap();
        }

        try {
            JavaType javaType = defaultObjectMapper.getTypeFactory()
                    .constructMapType(Map.class, keyClass, valueClass);
            return defaultObjectMapper.readValue(json, javaType);
        } catch (JsonProcessingException e) {
            logger.error("JSON to Map conversion failed. JSON: {}", json, e);
            throw new JsonConversionException("Failed to convert JSON to Map", e);
        }
    }

    /**
     * JSON字符串转换为Set
     */
    public static <T> Set<T> fromJsonToSet(String json, Class<T> elementClass) {
        if (json == null || json.trim().isEmpty()) {
            return Collections.emptySet();
        }

        try {
            JavaType javaType = defaultObjectMapper.getTypeFactory()
                    .constructCollectionType(Set.class, elementClass);
            return defaultObjectMapper.readValue(json, javaType);
        } catch (JsonProcessingException e) {
            logger.error("JSON to Set conversion failed. JSON: {}, ElementClass: {}", json, elementClass.getName(), e);
            throw new JsonConversionException("Failed to convert JSON to Set: " + elementClass.getName(), e);
        }
    }

    /**
     * 从输入流读取JSON并转换为对象
     */
    public static <T> T fromJson(InputStream inputStream, Class<T> clazz) {
        try {
            return defaultObjectMapper.readValue(inputStream, clazz);
        } catch (IOException e) {
            logger.error("InputStream to object conversion failed", e);
            throw new JsonConversionException("Failed to convert InputStream to object", e);
        }
    }

    /**
     * 将对象写入输出流
     */
    public static void writeJson(OutputStream outputStream, Object object) {
        try {
            defaultObjectMapper.writeValue(outputStream, object);
        } catch (IOException e) {
            logger.error("Object to OutputStream conversion failed", e);
            throw new JsonConversionException("Failed to convert object to OutputStream", e);
        }
    }

    /**
     * 将对象转换为字节数组
     */
    public static byte[] toJsonBytes(Object object) {
        try {
            return defaultObjectMapper.writeValueAsBytes(object);
        } catch (JsonProcessingException e) {
            logger.error("Object to byte array conversion failed", e);
            throw new JsonConversionException("Failed to convert object to byte array", e);
        }
    }

    /**
     * 从字节数组转换为对象
     */
    public static <T> T fromJsonBytes(byte[] bytes, Class<T> clazz) {
        try {
            return defaultObjectMapper.readValue(bytes, clazz);
        } catch (IOException e) {
            logger.error("Byte array to object conversion failed", e);
            throw new JsonConversionException("Failed to convert byte array to object", e);
        }
    }

    /**
     * 格式化JSON字符串
     */
    public static String formatJson(String json) {
        try {
            Object jsonObject = defaultObjectMapper.readValue(json, Object.class);
            return getObjectMapper("pretty").writeValueAsString(jsonObject);
        } catch (JsonProcessingException e) {
            logger.warn("JSON formatting failed, returning original string", e);
            return json;
        }
    }

    /**
     * 压缩JSON字符串（移除不必要的空格）
     */
    public static String compressJson(String json) {
        try {
            Object jsonObject = defaultObjectMapper.readValue(json, Object.class);
            return defaultObjectMapper.writeValueAsString(jsonObject);
        } catch (JsonProcessingException e) {
            logger.warn("JSON compression failed, returning original string", e);
            return json;
        }
    }

    /**
     * 验证JSON字符串是否有效
     */
    public static boolean isValidJson(String json) {
        if (json == null || json.trim().isEmpty()) {
            return false;
        }

        try {
            defaultObjectMapper.readTree(json);
            return true;
        } catch (JsonProcessingException e) {
            return false;
        }
    }

    /**
     * 获取JSON节点的值
     */
    public static String getJsonValue(String json, String path) {
        try {
            JsonNode rootNode = defaultObjectMapper.readTree(json);
            JsonNode targetNode = rootNode.at(path);

            if (targetNode.isMissingNode()) {
                return null;
            }

            if (targetNode.isTextual()) {
                return targetNode.textValue();
            } else {
                return targetNode.toString();
            }
        } catch (JsonProcessingException e) {
            logger.warn("Failed to get JSON value at path: {}", path, e);
            return null;
        }
    }

    /**
     * 合并两个JSON对象
     */
    public static String mergeJson(String json1, String json2) {
        try {
            JsonNode node1 = defaultObjectMapper.readTree(json1);
            JsonNode node2 = defaultObjectMapper.readTree(json2);

            // 使用Jackson的合并功能
            JsonNode merged = defaultObjectMapper.readerForUpdating(node1).readValue(node2);
            return defaultObjectMapper.writeValueAsString(merged);
        } catch (JsonProcessingException e) {
            logger.error("JSON merge failed", e);
            throw new JsonConversionException("Failed to merge JSON", e);
        } catch (IOException e) {
            logger.error("IO error during JSON merge", e);
            throw new JsonConversionException("IO error during JSON merge", e);
        }
    }

    /**
     * 对象转换（深拷贝）
     */
    public static <T> T convertValue(Object fromValue, Class<T> toValueType) {
        try {
            return defaultObjectMapper.convertValue(fromValue, toValueType);
        } catch (IllegalArgumentException e) {
            logger.error("Object conversion failed", e);
            throw new JsonConversionException("Failed to convert object", e);
        }
    }

    /**
     * 对象转换（支持泛型）
     */
    public static <T> T convertValue(Object fromValue, TypeReference<T> toValueType) {
        try {
            return defaultObjectMapper.convertValue(fromValue, toValueType);
        } catch (IllegalArgumentException e) {
            logger.error("Object conversion failed", e);
            throw new JsonConversionException("Failed to convert object", e);
        }
    }

    /**
     * 创建TypeReference（简化泛型类型创建）
     */
    public static <T> TypeReference<T> typeRef(Class<T> clazz) {
        return new TypeReference<T>() {};
    }

    public static <T> TypeReference<List<T>> listTypeRef(Class<T> elementClass) {
        return new TypeReference<List<T>>() {};
    }

    public static <K, V> TypeReference<Map<K, V>> mapTypeRef(Class<K> keyClass, Class<V> valueClass) {
        return new TypeReference<Map<K, V>>() {};
    }

    /**
     * JSON转换异常
     */
    public static class JsonConversionException extends RuntimeException {
        public JsonConversionException(String message) {
            super(message);
        }

        public JsonConversionException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    /**
     * 性能监控方法
     */
    public static class Performance {
        private static final ThreadLocal<Long> START_TIME = new ThreadLocal<>();

        public static void startTiming() {
            START_TIME.set(System.nanoTime());
        }

        public static long getElapsedNanos() {
            Long start = START_TIME.get();
            return start != null ? System.nanoTime() - start : 0;
        }

        public static long getElapsedMillis() {
            return getElapsedNanos() / 1_000_000;
        }

        public static void clearTiming() {
            START_TIME.remove();
        }
    }
}