package com.alibaba.android.arouter.facade.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation for field, which need autowired.
 * 自动给字段注入值
 *
 * @author zhilong <a href="mailto:zhilong.lzl@alibaba-inc.com">Contact me.</a>
 * @version 1.0
 * @since 2017/2/20 下午4:26
 */
@Target({ElementType.FIELD})
@Retention(RetentionPolicy.CLASS)
public @interface Autowired {

    // Mark param's name or service name.

    /**
     * 参数or服务的名称
     */
    String name() default "";

    // If required, app will be crash when value is null.
    // Primitive type wont be check!

    /**
     * 是否是必须的,如果为true,当值为空时,app会崩溃
     */
    boolean required() default false;

    // Description of the field
    String desc() default "";
}
