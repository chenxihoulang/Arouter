package com.alibaba.android.arouter.core;

import android.content.Context;

import com.alibaba.android.arouter.base.UniqueKeyTreeMap;
import com.alibaba.android.arouter.facade.model.RouteMeta;
import com.alibaba.android.arouter.facade.template.IInterceptor;
import com.alibaba.android.arouter.facade.template.IProvider;
import com.alibaba.android.arouter.facade.template.IRouteGroup;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Storage of route meta and other data.
 * 存储路由元数据的仓库
 *
 * @author zhilong <a href="mailto:zhilong.lzl@alibaba-inc.com">Contact me.</a>
 * @version 1.0
 * @since 2017/2/23 下午1:39
 */
class Warehouse {
    // Cache route and metas
    /**
     * 存储所有分组对应的路由信息,也就是自动生成的下面的类里面的信息
     * public class ARouter$$Root$$presentation_principal implements IRouteRoot
     */
    static Map<String, Class<? extends IRouteGroup>> groupsIndex = new HashMap<>();
    /**
     * 路由地址:路由元数据
     */
    static Map<String, RouteMeta> routes = new HashMap<>();

    // Cache provider
    /**
     * IProvider的子类:IProvider
     */
    static Map<Class, IProvider> providers = new HashMap<>();

    /**
     * 存储根据IProvider路由地址映射的Provider路由元数据信息,也就是自动生成的下面的类里面的信息
     * public class ARouter$$Providers$$presentation_principal implements IProviderGroup
     */
    static Map<String, RouteMeta> providersIndex = new HashMap<>();

    // Cache interceptor
    /**
     * 存储根据优先级映射的拦截器信息,优先级不能重复,也就是自动生成的下面的类里面的信息
     * ARouter$$Interceptors$$presentation_principal implements IInterceptorGroup
     */
    static Map<Integer, Class<? extends IInterceptor>> interceptorsIndex = new UniqueKeyTreeMap<>("More than one interceptors use same priority [%s]");

    /**
     * 存储所有的拦截器,就是上面interceptorsIndex里面的所有的拦截器
     * 参考  {@link InterceptorServiceImpl#init(Context)}
     */
    static List<IInterceptor> interceptors = new ArrayList<>();

    static void clear() {
        routes.clear();
        groupsIndex.clear();
        providers.clear();
        providersIndex.clear();
        interceptors.clear();
        interceptorsIndex.clear();
    }
}
