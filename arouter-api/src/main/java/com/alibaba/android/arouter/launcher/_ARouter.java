package com.alibaba.android.arouter.launcher;

import android.app.Activity;
import android.app.Application;
import android.app.Fragment;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.support.v4.app.ActivityCompat;
import android.util.Log;
import android.widget.Toast;

import com.alibaba.android.arouter.core.InstrumentationHook;
import com.alibaba.android.arouter.core.LogisticsCenter;
import com.alibaba.android.arouter.exception.HandlerException;
import com.alibaba.android.arouter.exception.InitException;
import com.alibaba.android.arouter.exception.NoRouteFoundException;
import com.alibaba.android.arouter.facade.Postcard;
import com.alibaba.android.arouter.facade.callback.InterceptorCallback;
import com.alibaba.android.arouter.facade.callback.NavigationCallback;
import com.alibaba.android.arouter.facade.model.RouteMeta;
import com.alibaba.android.arouter.facade.service.*;
import com.alibaba.android.arouter.facade.template.ILogger;
import com.alibaba.android.arouter.facade.template.IRouteGroup;
import com.alibaba.android.arouter.thread.DefaultPoolExecutor;
import com.alibaba.android.arouter.utils.Consts;
import com.alibaba.android.arouter.utils.DefaultLogger;
import com.alibaba.android.arouter.utils.TextUtils;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * ARouter core (Facade patten)
 *
 * @author Alex <a href="mailto:zhilong.liu@aliyun.com">Contact me.</a>
 * @version 1.0
 * @since 16/8/16 14:39
 */
final class _ARouter {
    static ILogger logger = new DefaultLogger(Consts.TAG);
    private volatile static boolean monitorMode = false;
    private volatile static boolean debuggable = false;
    private volatile static boolean autoInject = false;
    private volatile static _ARouter instance = null;
    private volatile static boolean hasInit = false;
    private volatile static ThreadPoolExecutor executor = DefaultPoolExecutor.getInstance();
    private static Handler mHandler;
    private static Context mContext;

    private static InterceptorService interceptorService;

    private _ARouter() {
    }

    /**
     * 初始化
     *
     * @param application
     * @return
     */
    protected static synchronized boolean init(Application application) {
        mContext = application;
        //初始化逻辑中心,将编译时注解解析生成的路由数据存储到 Warehouse类里面的变量中
        LogisticsCenter.init(mContext, executor);
        logger.info(Consts.TAG, "ARouter init success!");
        hasInit = true;

        //主线程,用于页面跳转使用
        mHandler = new Handler(Looper.getMainLooper());

        return true;
    }

    /**
     * Destroy arouter, it can be used only in debug mode.
     * 清除Arouter,其实就是清除所有生成的配置缓存信息
     */
    static synchronized void destroy() {
        if (debuggable()) {
            hasInit = false;
            LogisticsCenter.suspend();
            logger.info(Consts.TAG, "ARouter destroy success!");
        } else {
            logger.error(Consts.TAG, "Destroy can be used in debug mode only!");
        }
    }

    protected static _ARouter getInstance() {
        if (!hasInit) {
            throw new InitException("ARouterCore::Init::Invoke init(context) first!");
        } else {
            if (instance == null) {
                synchronized (_ARouter.class) {
                    if (instance == null) {
                        instance = new _ARouter();
                    }
                }
            }
            return instance;
        }
    }

    static synchronized void openDebug() {
        debuggable = true;
        logger.info(Consts.TAG, "ARouter openDebug");
    }

    static synchronized void openLog() {
        logger.showLog(true);
        logger.info(Consts.TAG, "ARouter openLog");
    }

    @Deprecated
    static synchronized void enableAutoInject() {
        autoInject = true;
    }

    @Deprecated
    static boolean canAutoInject() {
        return autoInject;
    }

    @Deprecated
    static void attachBaseContext() {
        Log.i(Consts.TAG, "ARouter start attachBaseContext");
        try {
            Class<?> mMainThreadClass = Class.forName("android.app.ActivityThread");

            // Get current main thread.
            Method getMainThread = mMainThreadClass.getDeclaredMethod("currentActivityThread");
            getMainThread.setAccessible(true);
            Object currentActivityThread = getMainThread.invoke(null);

            // The field contain instrumentation.
            Field mInstrumentationField = mMainThreadClass.getDeclaredField("mInstrumentation");
            mInstrumentationField.setAccessible(true);

            // Hook current instrumentation
            mInstrumentationField.set(currentActivityThread, new InstrumentationHook());
            Log.i(Consts.TAG, "ARouter hook instrumentation success!");
        } catch (Exception ex) {
            Log.e(Consts.TAG, "ARouter hook instrumentation failed! [" + ex.getMessage() + "]");
        }
    }

    static synchronized void printStackTrace() {
        logger.showStackTrace(true);
        logger.info(Consts.TAG, "ARouter printStackTrace");
    }

    static synchronized void setExecutor(ThreadPoolExecutor tpe) {
        executor = tpe;
    }

    static synchronized void monitorMode() {
        monitorMode = true;
        logger.info(Consts.TAG, "ARouter monitorMode on");
    }

    static boolean isMonitorMode() {
        return monitorMode;
    }

    static boolean debuggable() {
        return debuggable;
    }

    static void setLogger(ILogger userLogger) {
        if (null != userLogger) {
            logger = userLogger;
        }
    }

    /**
     * 注入普通参数或者IProvider服务
     *
     * @param thiz
     */
    static void inject(Object thiz) {
        //对应AutowiredServiceImpl类型
        AutowiredService autowiredService = ((AutowiredService) ARouter.getInstance().build("/arouter/service/autowired").navigation());
        if (null != autowiredService) {
            autowiredService.autowire(thiz);
        }
    }

    /**
     * Build postcard by path and default group
     * 构建路由参数数据
     */
    protected Postcard build(String path) {
        if (TextUtils.isEmpty(path)) {
            throw new HandlerException(Consts.TAG + "Parameter is invalid!");
        } else {
            //自定义路由地址转换的服务
            PathReplaceService pService = ARouter.getInstance().navigation(PathReplaceService.class);
            if (null != pService) {
                path = pService.forString(path);
            }
            return build(path, extractGroup(path), true);
        }
    }

    /**
     * Build postcard by uri
     * Uri格式的路由参数构造
     */
    protected Postcard build(Uri uri) {
        if (null == uri || TextUtils.isEmpty(uri.toString())) {
            throw new HandlerException(Consts.TAG + "Parameter invalid!");
        } else {
            PathReplaceService pService = ARouter.getInstance().navigation(PathReplaceService.class);
            if (null != pService) {
                uri = pService.forUri(uri);
            }
            return new Postcard(uri.getPath(), extractGroup(uri.getPath()), uri, null);
        }
    }

    /**
     * Build postcard by path and group
     * 构建路由参数数据
     *
     * @param path         路由地址
     * @param group        路由地址分组
     * @param afterReplace 路由地址是否通过自定义的PathReplaceService给转换过
     */
    protected Postcard build(String path, String group, Boolean afterReplace) {
        if (TextUtils.isEmpty(path) || TextUtils.isEmpty(group)) {
            throw new HandlerException(Consts.TAG + "Parameter is invalid!");
        } else {
            if (!afterReplace) {
                PathReplaceService pService = ARouter.getInstance().navigation(PathReplaceService.class);
                if (null != pService) {
                    path = pService.forString(path);
                }
            }
            return new Postcard(path, group);
        }
    }

    /**
     * Extract the default group from path.
     * 提取路由地址中的分组信息,也就是路由地址前面两个"/"之间的内容,当成group信息
     */
    private String extractGroup(String path) {
        if (TextUtils.isEmpty(path) || !path.startsWith("/")) {
            throw new HandlerException(Consts.TAG + "Extract the default group failed, the path must be start with '/' and contain more than 2 '/'!");
        }

        try {
            String defaultGroup = path.substring(1, path.indexOf("/", 1));
            if (TextUtils.isEmpty(defaultGroup)) {
                throw new HandlerException(Consts.TAG + "Extract the default group failed! There's nothing between 2 '/'!");
            } else {
                return defaultGroup;
            }
        } catch (Exception e) {
            logger.warning(Consts.TAG, "Failed to extract default group! " + e.getMessage());
            return null;
        }
    }

    static void afterInit() {
        // Trigger interceptor init, use byName.

        //这里面只是将InterceptorService的实现获取出来,也就是获取到InterceptorServiceImpl
        interceptorService = (InterceptorService) ARouter.getInstance().build("/arouter/service/interceptor").navigation();
    }

    protected <T> T navigation(Class<? extends T> service) {
        try {
            Postcard postcard = LogisticsCenter.buildProvider(service.getName());

            // Compatible 1.0.5 compiler sdk.
            // Earlier versions did not use the fully qualified name to get the service
            if (null == postcard) {
                // No service, or this service in old version.
                postcard = LogisticsCenter.buildProvider(service.getSimpleName());
            }

            if (null == postcard) {
                return null;
            }

            // Set application to postcard.
            postcard.setContext(mContext);

            LogisticsCenter.completion(postcard);
            return (T) postcard.getProvider();
        } catch (NoRouteFoundException ex) {
            logger.warning(Consts.TAG, ex.getMessage());
            return null;
        }
    }

    /**
     * Use router navigation.
     * 导航到指定路由地址
     *
     * @param context     Activity or null.
     * @param postcard    Route metas 路由参数数据
     * @param requestCode RequestCode
     * @param callback    cb
     */
    protected Object navigation(final Context context, final Postcard postcard, final int requestCode,
                                final NavigationCallback callback) {
        //用于确定是否需要进行导航,也是一个IProvider,所以我们能自定义
        PretreatmentService pretreatmentService = ARouter.getInstance().navigation(PretreatmentService.class);
        if (null != pretreatmentService && !pretreatmentService.onPretreatment(context, postcard)) {
            // Pretreatment failed, navigation canceled.
            //注意:这里之间返回的null,并没有触发回调,坑
            return null;
        }

        // Set context to postcard.
        //如果没有设置context,则使用初始化传入的ApplicationContext
        postcard.setContext(null == context ? mContext : context);

        try {
            //由逻辑中心将路由相关数据填充完整,比如解析Uri路径参数,构造IProvider类型实例等
            LogisticsCenter.completion(postcard);
        } catch (NoRouteFoundException ex) {
            logger.warning(Consts.TAG, ex.getMessage());

            if (debuggable()) {
                // Show friendly tips for user.
                //这是我们经常看到的提示,没有找到指定路由
                runInMainThread(new Runnable() {
                    @Override
                    public void run() {
                        Toast.makeText(mContext, "There's no route matched!\n" +
                                " Path = [" + postcard.getPath() + "]\n" +
                                " Group = [" + postcard.getGroup() + "]", Toast.LENGTH_LONG).show();
                    }
                });
            }

            if (null != callback) {
                callback.onLost(postcard);
            } else {
                // No callback for this invoke, then we use the global degrade service.
                //如果没有设置回调,再确认是否自定义了路由缺失通知服务
                DegradeService degradeService = ARouter.getInstance().navigation(DegradeService.class);
                if (null != degradeService) {
                    degradeService.onLost(context, postcard);
                }
            }

            return null;
        }

        if (null != callback) {
            //找到路由目的地实例了
            callback.onFound(postcard);
        }

        //是否开启绿色通道,绿色通道会跳过所有的拦截器,IProvider和Fragment会开启
        if (!postcard.isGreenChannel()) {   // It must be run in async thread, maybe interceptor cost too mush time made ANR.
            //执行拦截器逻辑,我们的普通页面跳转会执行到这里,
            // interceptorService在afterInit()方法中初始化,实现是InterceptorServiceImpl
            interceptorService.doInterceptions(postcard, new InterceptorCallback() {
                /**
                 * Continue process
                 *
                 * @param postcard route meta
                 */
                @Override
                public void onContinue(Postcard postcard) {
                    //拦截器放行了,继续执行跳转
                    _navigation(postcard, requestCode, callback);
                }

                /**
                 * Interrupt process, pipeline will be destory when this method called.
                 *
                 * @param exception Reson of interrupt.
                 */
                @Override
                public void onInterrupt(Throwable exception) {
                    if (null != callback) {
                        callback.onInterrupt(postcard);
                    }

                    logger.info(Consts.TAG, "Navigation failed, termination by interceptor : " + exception.getMessage());
                }
            });
        } else {
            //开启了绿色通道,直接跳转
            return _navigation(postcard, requestCode, callback);
        }

        return null;
    }

    /**
     * 真正的执行路由逻辑
     *
     * @param postcard    路由参数
     * @param requestCode
     * @param callback
     * @return
     */
    private Object _navigation(final Postcard postcard, final int requestCode, final NavigationCallback callback) {
        final Context currentContext = postcard.getContext();

        switch (postcard.getType()) {
            case ACTIVITY:
                // Build intent
                final Intent intent = new Intent(currentContext, postcard.getDestination());
                intent.putExtras(postcard.getExtras());

                // Set flags.
                int flags = postcard.getFlags();
                if (0 != flags) {
                    intent.setFlags(flags);
                }

                // Non activity, need FLAG_ACTIVITY_NEW_TASK
                if (!(currentContext instanceof Activity)) {
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                }

                // Set Actions
                String action = postcard.getAction();
                if (!TextUtils.isEmpty(action)) {
                    intent.setAction(action);
                }

                // Navigation in main looper.
                runInMainThread(new Runnable() {
                    @Override
                    public void run() {
                        startActivity(requestCode, currentContext, intent, postcard, callback);
                    }
                });

                break;
            case PROVIDER:
                //如果是IProvider,直接返回,用户进行强制转换即可
                return postcard.getProvider();
            case BOARDCAST:
            case CONTENT_PROVIDER:
            case FRAGMENT://如果是Fragment,构造Fragment参数,并直接返回,用户进行强制转换即可
                Class<?> fragmentMeta = postcard.getDestination();
                try {
                    Object instance = fragmentMeta.getConstructor().newInstance();
                    if (instance instanceof Fragment) {
                        ((Fragment) instance).setArguments(postcard.getExtras());
                    } else if (instance instanceof android.support.v4.app.Fragment) {
                        ((android.support.v4.app.Fragment) instance).setArguments(postcard.getExtras());
                    }

                    return instance;
                } catch (Exception ex) {
                    logger.error(Consts.TAG, "Fetch fragment instance error, " + TextUtils.formatStackTrace(ex.getStackTrace()));
                }
            case METHOD:
            case SERVICE:
            default:
                return null;
        }

        return null;
    }

    /**
     * Be sure execute in main thread.
     *
     * @param runnable code
     */
    private void runInMainThread(Runnable runnable) {
        if (Looper.getMainLooper().getThread() != Thread.currentThread()) {
            mHandler.post(runnable);
        } else {
            runnable.run();
        }
    }

    /**
     * Start activity
     * Activity页面跳转
     *
     * @see ActivityCompat
     */
    private void startActivity(int requestCode, Context currentContext, Intent intent,
                               Postcard postcard, NavigationCallback callback) {
        if (requestCode >= 0) {  // Need start for result
            if (currentContext instanceof Activity) {
                ActivityCompat.startActivityForResult((Activity) currentContext, intent, requestCode, postcard.getOptionsBundle());
            } else {
                logger.warning(Consts.TAG, "Must use [navigation(activity, ...)] to support [startActivityForResult]");
            }
        } else {
            ActivityCompat.startActivity(currentContext, intent, postcard.getOptionsBundle());
        }

        if ((-1 != postcard.getEnterAnim()
                && -1 != postcard.getExitAnim())
                && currentContext instanceof Activity) {    // Old version.
            ((Activity) currentContext).overridePendingTransition(postcard.getEnterAnim(), postcard.getExitAnim());
        }

        if (null != callback) { // Navigation over.
            //回调已经跳转过去了
            callback.onArrival(postcard);
        }
    }

    /**
     * 动态添加路由
     *
     * @param group 路由分组,里面包含改分组下的所有路由信息
     * @return 返回是否添加成功, 主要是一定要有分组
     */
    boolean addRouteGroup(IRouteGroup group) {
        if (null == group) {
            return false;
        }

        String groupName = null;

        try {
            // Extract route meta.
            //路由地址:路由元数据
            Map<String, RouteMeta> dynamicRoute = new HashMap<>();
            //获取到动态配置的路由信息
            group.loadInto(dynamicRoute);

            // Check route meta.
            //注意:判断所有的路由是否属于同一分组
            for (Map.Entry<String, RouteMeta> route : dynamicRoute.entrySet()) {
                String path = route.getKey();
                String groupByExtract = extractGroup(path);
                RouteMeta meta = route.getValue();

                if (null == groupName) {
                    groupName = groupByExtract;
                }

                if (null == groupName || !groupName.equals(groupByExtract) || !groupName.equals(meta.getGroup())) {
                    // Group name not consistent
                    return false;
                }
            }

            //动态添加路由到全局变量中
            LogisticsCenter.addRouteGroupDynamic(groupName, group);

            logger.info(Consts.TAG, "Add route group [" + groupName + "] finish, " + dynamicRoute.size() + " new route meta.");

            return true;
        } catch (Exception exception) {
            logger.error(Consts.TAG, "Add route group dynamic exception! " + exception.getMessage());
        }

        return false;
    }
}
