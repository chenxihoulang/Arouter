package com.alibaba.android.arouter.core;

import android.content.Context;
import android.net.Uri;

import com.alibaba.android.arouter.exception.HandlerException;
import com.alibaba.android.arouter.exception.NoRouteFoundException;
import com.alibaba.android.arouter.facade.Postcard;
import com.alibaba.android.arouter.facade.enums.TypeKind;
import com.alibaba.android.arouter.facade.model.RouteMeta;
import com.alibaba.android.arouter.facade.template.IInterceptorGroup;
import com.alibaba.android.arouter.facade.template.IProvider;
import com.alibaba.android.arouter.facade.template.IProviderGroup;
import com.alibaba.android.arouter.facade.template.IRouteGroup;
import com.alibaba.android.arouter.facade.template.IRouteRoot;
import com.alibaba.android.arouter.launcher.ARouter;
import com.alibaba.android.arouter.utils.ClassUtils;
import com.alibaba.android.arouter.utils.Consts;
import com.alibaba.android.arouter.utils.MapUtils;
import com.alibaba.android.arouter.utils.PackageUtils;
import com.alibaba.android.arouter.utils.TextUtils;

import java.lang.reflect.InvocationTargetException;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ThreadPoolExecutor;

import static com.alibaba.android.arouter.launcher.ARouter.logger;
import static com.alibaba.android.arouter.utils.Consts.AROUTER_SP_CACHE_KEY;
import static com.alibaba.android.arouter.utils.Consts.AROUTER_SP_KEY_MAP;
import static com.alibaba.android.arouter.utils.Consts.DOT;
import static com.alibaba.android.arouter.utils.Consts.ROUTE_ROOT_PAKCAGE;
import static com.alibaba.android.arouter.utils.Consts.SDK_NAME;
import static com.alibaba.android.arouter.utils.Consts.SEPARATOR;
import static com.alibaba.android.arouter.utils.Consts.SUFFIX_INTERCEPTORS;
import static com.alibaba.android.arouter.utils.Consts.SUFFIX_PROVIDERS;
import static com.alibaba.android.arouter.utils.Consts.SUFFIX_ROOT;
import static com.alibaba.android.arouter.utils.Consts.TAG;

/**
 * LogisticsCenter contains all of the map.
 * <p>
 * 逻辑中心,包含所有的路由元数据map信息
 * <p>
 * 1. Creates instance when it is first used.
 * 2. Handler Multi-Module relationship map(*)
 * 3. Complex logic to solve duplicate group definition
 *
 * @author Alex <a href="mailto:zhilong.liu@aliyun.com">Contact me.</a>
 * @version 1.0
 * @since 16/8/23 15:02
 */
public class LogisticsCenter {
    private static Context mContext;
    static ThreadPoolExecutor executor;
    private static boolean registerByPlugin;

    /**
     * arouter-auto-register plugin will generate code inside this method
     * call this method to register all Routers, Interceptors and Providers
     */
    private static void loadRouterMap() {
        registerByPlugin = false;
        // auto generate register code by gradle plugin: arouter-auto-register
        // looks like below:
        // registerRouteRoot(new ARouter..Root..modulejava());
        // registerRouteRoot(new ARouter..Root..modulekotlin());
    }

    /**
     * register by class name
     * Sacrificing a bit of efficiency to solve
     * the problem that the main dex file size is too large
     */
    private static void register(String className) {
        if (!TextUtils.isEmpty(className)) {
            try {
                Class<?> clazz = Class.forName(className);
                Object obj = clazz.getConstructor().newInstance();
                if (obj instanceof IRouteRoot) {
                    registerRouteRoot((IRouteRoot) obj);
                } else if (obj instanceof IProviderGroup) {
                    registerProvider((IProviderGroup) obj);
                } else if (obj instanceof IInterceptorGroup) {
                    registerInterceptor((IInterceptorGroup) obj);
                } else {
                    logger.info(TAG, "register failed, class name: " + className
                            + " should implements one of IRouteRoot/IProviderGroup/IInterceptorGroup.");
                }
            } catch (Exception e) {
                logger.error(TAG, "register class error:" + className);
            }
        }
    }

    /**
     * method for arouter-auto-register plugin to register Routers
     *
     * @param routeRoot IRouteRoot implementation class in the package: com.alibaba.android.arouter.core.routers
     */
    private static void registerRouteRoot(IRouteRoot routeRoot) {
        markRegisteredByPlugin();
        if (routeRoot != null) {
            routeRoot.loadInto(Warehouse.groupsIndex);
        }
    }

    /**
     * method for arouter-auto-register plugin to register Interceptors
     *
     * @param interceptorGroup IInterceptorGroup implementation class in the package: com.alibaba.android.arouter.core.routers
     */
    private static void registerInterceptor(IInterceptorGroup interceptorGroup) {
        markRegisteredByPlugin();
        if (interceptorGroup != null) {
            interceptorGroup.loadInto(Warehouse.interceptorsIndex);
        }
    }

    /**
     * method for arouter-auto-register plugin to register Providers
     *
     * @param providerGroup IProviderGroup implementation class in the package: com.alibaba.android.arouter.core.routers
     */
    private static void registerProvider(IProviderGroup providerGroup) {
        markRegisteredByPlugin();
        if (providerGroup != null) {
            providerGroup.loadInto(Warehouse.providersIndex);
        }
    }

    /**
     * mark already registered by arouter-auto-register plugin
     */
    private static void markRegisteredByPlugin() {
        if (!registerByPlugin) {
            registerByPlugin = true;
        }
    }

    /**
     * LogisticsCenter init, load all metas in memory. Demand initialization
     * 初始化,加载所有路由元数据到内存
     */
    public synchronized static void init(Context context, ThreadPoolExecutor tpe) throws HandlerException {
        mContext = context;
        executor = tpe;

        try {
            long startInit = System.currentTimeMillis();
            //load by plugin first
            loadRouterMap();
            if (registerByPlugin) {
                logger.info(TAG, "Load router map by arouter-auto-register plugin.");
            } else {
                //存储编译时注解生成的所有类路径
                Set<String> routerMap;

                // It will rebuild router map every times when debuggable.
                if (ARouter.debuggable() || PackageUtils.isNewVersion(context)) {
                    logger.info(TAG, "Run with debug mode or new install, rebuild router map.");
                    // These class was generated by arouter-compiler.
                    //通过指定包名，扫描包下面包含的所有的ClassName
                    routerMap = ClassUtils.getFileNameByPackageName(mContext, ROUTE_ROOT_PAKCAGE);
                    if (!routerMap.isEmpty()) {
                        context.getSharedPreferences(AROUTER_SP_CACHE_KEY, Context.MODE_PRIVATE).edit().putStringSet(AROUTER_SP_KEY_MAP, routerMap).apply();
                    }

                    PackageUtils.updateVersion(context);    // Save new version name when router map update finishes.
                } else {
                    logger.info(TAG, "Load router map from cache.");
                    routerMap = new HashSet<>(context.getSharedPreferences(AROUTER_SP_CACHE_KEY, Context.MODE_PRIVATE).getStringSet(AROUTER_SP_KEY_MAP, new HashSet<String>()));
                }

                logger.info(TAG, "Find router map finished, map size = " + routerMap.size() + ", cost " + (System.currentTimeMillis() - startInit) + " ms.");
                startInit = System.currentTimeMillis();

                for (String className : routerMap) {
                    if (className.startsWith(ROUTE_ROOT_PAKCAGE + DOT + SDK_NAME + SEPARATOR + SUFFIX_ROOT)) {
                        // This one of root elements, load root.

                        //将这个类:public class ARouter$$Root$$presentation_principal implements IRouteRoot
                        //里面的信息存储到静态变量中
                        ((IRouteRoot) (Class.forName(className).getConstructor().newInstance())).loadInto(Warehouse.groupsIndex);
                    } else if (className.startsWith(ROUTE_ROOT_PAKCAGE + DOT + SDK_NAME + SEPARATOR + SUFFIX_INTERCEPTORS)) {
                        // Load interceptorMeta
                        //将这个类:public class ARouter$$Interceptors$$presentation_principal implements IInterceptorGroup
                        //里面的信息存储到静态变量中
                        ((IInterceptorGroup) (Class.forName(className).getConstructor().newInstance())).loadInto(Warehouse.interceptorsIndex);
                    } else if (className.startsWith(ROUTE_ROOT_PAKCAGE + DOT + SDK_NAME + SEPARATOR + SUFFIX_PROVIDERS)) {
                        // Load providerIndex

                        //将这个类:public class ARouter$$Providers$$presentation_principal implements IProviderGroup
                        //里面的信息存储到静态变量中
                        ((IProviderGroup) (Class.forName(className).getConstructor().newInstance())).loadInto(Warehouse.providersIndex);
                    }
                }
            }

            logger.info(TAG, "Load root element finished, cost " + (System.currentTimeMillis() - startInit) + " ms.");

            if (Warehouse.groupsIndex.size() == 0) {
                logger.error(TAG, "No mapping files were found, check your configuration please!");
            }

            if (ARouter.debuggable()) {
                logger.debug(TAG, String.format(Locale.getDefault(), "LogisticsCenter has already been loaded, GroupIndex[%d], InterceptorIndex[%d], ProviderIndex[%d]", Warehouse.groupsIndex.size(), Warehouse.interceptorsIndex.size(), Warehouse.providersIndex.size()));
            }
        } catch (Exception e) {
            throw new HandlerException(TAG + "ARouter init logistics center exception! [" + e.getMessage() + "]");
        }
    }

    /**
     * Build postcard by serviceName
     *
     * @param serviceName interfaceName
     * @return postcard
     */
    public static Postcard buildProvider(String serviceName) {
        RouteMeta meta = Warehouse.providersIndex.get(serviceName);

        if (null == meta) {
            return null;
        } else {
            return new Postcard(meta.getPath(), meta.getGroup());
        }
    }

    /**
     * Completion the postcard by route metas
     * 将路由相关数据填充完整
     *
     * @param postcard Incomplete postcard, should complete by this method.
     */
    public synchronized static void completion(Postcard postcard) {
        if (null == postcard) {
            throw new NoRouteFoundException(TAG + "No postcard!");
        }

        //是否有缓存过的路由元数据
        RouteMeta routeMeta = Warehouse.routes.get(postcard.getPath());
        if (null == routeMeta) {
            // Maybe its does't exist, or didn't load.
            //路由参数对应的分组是否存在
            if (!Warehouse.groupsIndex.containsKey(postcard.getGroup())) {
                throw new NoRouteFoundException(TAG + "There is no route match the path [" + postcard.getPath() + "], in group [" + postcard.getGroup() + "]");
            } else {
                // Load route and cache it into memory, then delete from metas.
                try {
                    if (ARouter.debuggable()) {
                        logger.debug(TAG, String.format(Locale.getDefault(), "The group [%s] starts loading, trigger by [%s]", postcard.getGroup(), postcard.getPath()));
                    }

                    //将指定分组下的路由数据缓存到内存中
                    addRouteGroupDynamic(postcard.getGroup(), null);

                    if (ARouter.debuggable()) {
                        logger.debug(TAG, String.format(Locale.getDefault(), "The group [%s] has already been loaded, trigger by [%s]", postcard.getGroup(), postcard.getPath()));
                    }
                } catch (Exception e) {
                    throw new HandlerException(TAG + "Fatal exception when loading group meta. [" + e.getMessage() + "]");
                }

                //注意:递归一下,这样Warehouse.routes就有路由数据了,走下面的else
                completion(postcard);   // Reload
            }
        } else {
            //路由目的地所属的类
            postcard.setDestination(routeMeta.getDestination());
            //路由类型,比如:RouteType.ACTIVITY
            postcard.setType(routeMeta.getType());
            //优先级
            postcard.setPriority(routeMeta.getPriority());
            //通常是Integer.MIN_VALUE
            postcard.setExtra(routeMeta.getExtra());

            //解析Uri格式的跳转请求路径,比如:Uri testUriMix = Uri.parse("arouter://m.aliyun.com/test/activity2")
            Uri rawUri = postcard.getUri();
            if (null != rawUri) {   // Try to set params into bundle.
                //获取参数信息
                Map<String, String> resultMap = TextUtils.splitQueryParameters(rawUri);
                //参数名称:参数类型,Activity和Fragment有可能会有值,其他没有
                Map<String, Integer> paramsType = routeMeta.getParamsType();

                if (MapUtils.isNotEmpty(paramsType)) {
                    // Set value by its type, just for params which annotation by @Param
                    for (Map.Entry<String, Integer> params : paramsType.entrySet()) {
                        //将路径中的参数数据设置到请求元数据中
                        setValue(postcard,
                                params.getValue(),//参数类型
                                params.getKey(),//参数名称
                                resultMap.get(params.getKey())//参数值
                        );
                    }

                    // Save params name which need auto inject.
                    //将所有参数的名称也存储起来,方便字段注入使用
                    postcard.getExtras().putStringArray(ARouter.AUTO_INJECT, paramsType.keySet().toArray(new String[]{}));
                }

                // Save raw uri
                //将请求的原始uri存储起来
                postcard.withString(ARouter.RAW_URI, rawUri.toString());
            }

            //确定路由类型
            switch (routeMeta.getType()) {
                //如果是IProvider的路由,使用反射将IProvider的子类构造出来
                case PROVIDER:  // if the route is provider, should find its instance
                    // Its provider, so it must implement IProvider

                    //路由目的Class转换为 Class<? extends IProvider>
                    Class<? extends IProvider> providerMeta = (Class<? extends IProvider>) routeMeta.getDestination();
                    IProvider instance = Warehouse.providers.get(providerMeta);
                    if (null == instance) { // There's no instance of this provider
                        IProvider provider;
                        try {
                            //调用无参数构造方法,构造实例
                            provider = providerMeta.getConstructor().newInstance();

                            //注意:这里调用了IProvider的init方法,传递的是ApplicationContext
                            provider.init(mContext);

                            Warehouse.providers.put(providerMeta, provider);
                            instance = provider;
                        } catch (Exception e) {
                            throw new HandlerException("Init provider failed! " + e.getMessage());
                        }
                    }
                    postcard.setProvider(instance);
                    //IProvider开启绿色通道
                    postcard.greenChannel();    // Provider should skip all of interceptors
                    break;
                case FRAGMENT:
                    //Fragment默认开启绿色通道,无需拦截器拦截
                    postcard.greenChannel();    // Fragment needn't interceptors
                default:
                    break;
            }
        }
    }

    /**
     * Set value by known type
     * 给请求路由元数据设置参数
     *
     * @param postcard postcard 请求路由元数据
     * @param typeDef  type 参数类型
     * @param key      key 参数名称
     * @param value    value 参数的值
     */
    private static void setValue(Postcard postcard, Integer typeDef, String key, String value) {
        if (TextUtils.isEmpty(key) || TextUtils.isEmpty(value)) {
            return;
        }

        try {
            if (null != typeDef) {
                if (typeDef == TypeKind.BOOLEAN.ordinal()) {
                    postcard.withBoolean(key, Boolean.parseBoolean(value));
                } else if (typeDef == TypeKind.BYTE.ordinal()) {
                    postcard.withByte(key, Byte.parseByte(value));
                } else if (typeDef == TypeKind.SHORT.ordinal()) {
                    postcard.withShort(key, Short.parseShort(value));
                } else if (typeDef == TypeKind.INT.ordinal()) {
                    postcard.withInt(key, Integer.parseInt(value));
                } else if (typeDef == TypeKind.LONG.ordinal()) {
                    postcard.withLong(key, Long.parseLong(value));
                } else if (typeDef == TypeKind.FLOAT.ordinal()) {
                    postcard.withFloat(key, Float.parseFloat(value));
                } else if (typeDef == TypeKind.DOUBLE.ordinal()) {
                    postcard.withDouble(key, Double.parseDouble(value));
                } else if (typeDef == TypeKind.STRING.ordinal()) {
                    postcard.withString(key, value);
                } else if (typeDef == TypeKind.PARCELABLE.ordinal()) {
                    // TODO : How to description parcelable value with string?
                } else if (typeDef == TypeKind.OBJECT.ordinal()) {
                    //Object类型的参数,直接传递的字符串,后续再使用SerializationService解析
                    postcard.withString(key, value);
                } else {    // Compatible compiler sdk 1.0.3, in that version, the string type = 18
                    postcard.withString(key, value);
                }
            } else {
                postcard.withString(key, value);
            }
        } catch (Throwable ex) {
            logger.warning(Consts.TAG, "LogisticsCenter setValue failed! " + ex.getMessage());
        }
    }

    /**
     * Suspend business, clear cache.
     * 清除Arouter,其实就是清除所有生成的配置缓存信息
     */
    public static void suspend() {
        Warehouse.clear();
    }

    /**
     * 将初始化时填充的路由分组数据Warehouse.groupsIndex填充到Warehouse.routes中,并删除Warehouse.groupsIndex
     * 也就是将某个groupName下的所有路由数据换一个缓存方式,以路由地址:路由元数据的形式进行缓存
     * <p>
     * public class ARouter$$Group$$test implements IRouteGroup {
     *
     * @param groupName
     * @param group
     * @throws NoSuchMethodException
     * @throws IllegalAccessException
     * @throws InvocationTargetException
     * @throws InstantiationException
     * @Override public void loadInto(Map<String, RouteMeta> atlas) {
     * atlas.put("/test/test1", RouteMeta.build(RouteType.ACTIVITY, Test1.class, "/test/test1", "test", new java.util.HashMap<String, Integer>(){{put("res", 9); put("mParam", 11); put("name", 8); put("param1", 8); }}, -1, -2147483648));
     * }
     * }
     * <p>
     * public class ARouter$$Root$$presentation_principal implements IRouteRoot {
     * @Override public void loadInto(Map<String, Class<? extends IRouteGroup>> routes) {
     * routes.put("common", ARouter$$Group$$common.class);
     * routes.put("integral_task_principal", ARouter$$Group$$integral_task_principal.class);
     * routes.put("main", ARouter$$Group$$main.class);
     * routes.put("parkwork", ARouter$$Group$$parkwork.class);
     * routes.put("route", ARouter$$Group$$route.class);
     * routes.put("teaching", ARouter$$Group$$teaching.class);
     * routes.put("test", ARouter$$Group$$test.class);
     * routes.put("userinfo", ARouter$$Group$$userinfo.class);
     * routes.put("work", ARouter$$Group$$work.class);
     * routes.put("xxx", ARouter$$Group$$xxx.class);
     * }
     * }
     * <p>
     * 将ARouter$$Group$$test.atlas中数据填充到Warehouse.routes中
     * 将ARouter$$Root$$presentation_principal.routes中的routes.put("test", ARouter$$Group$$test.class);删除掉
     */
    public synchronized static void addRouteGroupDynamic(String groupName, IRouteGroup group) throws NoSuchMethodException, IllegalAccessException, InvocationTargetException, InstantiationException {
        if (Warehouse.groupsIndex.containsKey(groupName)) {
            // If this group is included, but it has not been loaded
            // load this group first, because dynamic route has high priority.

            //构造出自动生成的IRouteGroup的子类,并调用loadInto方法,将方法体中的路由数据存储到Warehouse.routes
            Warehouse.groupsIndex.get(groupName).getConstructor().newInstance().loadInto(Warehouse.routes);
            //移除掉Root中的对应分组信息
            Warehouse.groupsIndex.remove(groupName);
        }

        // cover old group.
        if (null != group) {
            group.loadInto(Warehouse.routes);
        }
    }
}
