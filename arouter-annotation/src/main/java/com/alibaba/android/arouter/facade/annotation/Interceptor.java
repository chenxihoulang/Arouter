package com.alibaba.android.arouter.facade.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Mark a interceptor to interception the route.
 * BE ATTENTION : This annotation can be mark the implements of #{IInterceptor} ONLY!!!
 * <p>
 * 拦截器注解,只能注解到IInterceptor的实现类上
 *
 * @author Alex <a href="mailto:zhilong.liu@aliyun.com">Contact me.</a>
 * @version 1.0
 * @since 16/8/23 14:03
 */
@Target({ElementType.TYPE})
@Retention(RetentionPolicy.CLASS)
public @interface Interceptor {
    /**
     * The priority of interceptor, ARouter will be excute them follow the priority.
     * 拦截器的优先级
     */
    int priority();

    /**
     * The name of interceptor, may be used to generate javadoc.
     */
    String name() default "Default";
}
