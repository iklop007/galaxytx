package com.galaxytx.core.annotation;

import java.lang.annotation.*;

/**
 * TCC服务注解
 * 用于标记TCC模式的服务类
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface TCCService {

    /**
     * 资源ID，用于唯一标识TCC资源
     */
    String resourceId();

    /**
     * Confirm方法名，默认为"confirm"
     */
    String confirmMethod() default "confirm";

    /**
     * Cancel方法名，默认为"cancel"
     */
    String cancelMethod() default "cancel";

    /**
     * 服务描述
     */
    String description() default "";

    /**
     * 超时时间（毫秒），0表示不超时
     */
    long timeout() default 0;

    /**
     * 最大重试次数
     */
    int maxRetries() default 3;

    /**
     * 是否启用
     */
    boolean enabled() default true;
}
