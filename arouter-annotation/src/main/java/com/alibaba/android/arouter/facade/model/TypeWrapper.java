package com.alibaba.android.arouter.facade.model;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;

/**
 * Used for get type of target object.
 * 方便获取泛型的真实类型,创建一个子类,调用getType即可获取到泛型类型,和gson用法差不多
 * <p>
 * gson这里的{},其实是创建子类的意思,会调用到TypeToken的构造方法,里面就会获取泛型参数
 * new TypeToken<List<T>>() {}.getType()
 *
 * @author Alex <a href="mailto:zhilong.liu@aliyun.com">Contact me.</a>
 * @version 1.0
 * @since 17/10/26 11:56:22
 */
public class TypeWrapper<T> {
    protected final Type type;

    /**
     * 注意这里是protected的,所以要正常使用,必须要创建子类,和gson中的TypeToken一样
     */
    protected TypeWrapper() {
        //获取当前类的泛型父类类型,其实就是TypeWrapper<T>对应的Type类型
        Type superClass = getClass().getGenericSuperclass();

        //父类是一个泛型类,所以类型为ParameterizedType,获取第一个泛型参数的真实类型
        type = ((ParameterizedType) superClass).getActualTypeArguments()[0];
    }

    public Type getType() {
        return type;
    }
}
