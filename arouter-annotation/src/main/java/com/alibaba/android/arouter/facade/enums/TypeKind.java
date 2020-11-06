package com.alibaba.android.arouter.facade.enums;

/**
 * Kind of field type.
 * 字段类型
 *
 * @author Alex <a href="mailto:zhilong.liu@aliyun.com">Contact me.</a>
 * @version 1.0
 * @since 2017-03-16 19:13:38
 */
public enum TypeKind {
    // Base type
    //基本类型
    BOOLEAN,
    BYTE,
    SHORT,
    INT,
    LONG,
    CHAR,
    FLOAT,
    DOUBLE,

    // Other type
    //其他类型
    STRING,
    SERIALIZABLE,
    PARCELABLE,
    OBJECT;
}
