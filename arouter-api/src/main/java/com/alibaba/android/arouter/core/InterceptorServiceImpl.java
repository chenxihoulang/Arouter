package com.alibaba.android.arouter.core;

import android.app.Application;
import android.content.Context;

import com.alibaba.android.arouter.exception.HandlerException;
import com.alibaba.android.arouter.facade.Postcard;
import com.alibaba.android.arouter.facade.annotation.Route;
import com.alibaba.android.arouter.facade.callback.InterceptorCallback;
import com.alibaba.android.arouter.facade.service.InterceptorService;
import com.alibaba.android.arouter.facade.template.IInterceptor;
import com.alibaba.android.arouter.thread.CancelableCountDownLatch;
import com.alibaba.android.arouter.utils.MapUtils;

import java.util.Map;
import java.util.concurrent.TimeUnit;

import static com.alibaba.android.arouter.launcher.ARouter.logger;
import static com.alibaba.android.arouter.utils.Consts.TAG;

/**
 * All of interceptors
 * 拦截器服务的实现,是在异步线程中执行的,防止ANR产生
 * 在Arouter初始化的时候,会获取到用户自定义的所有拦截器存储到Warehouse.interceptorsIndex
 *
 * @author zhilong <a href="mailto:zhilong.lzl@alibaba-inc.com">Contact me.</a>
 * @version 1.0
 * @since 2017/2/23 下午2:09
 */
@Route(path = "/arouter/service/interceptor")
public class InterceptorServiceImpl implements InterceptorService {
    /**
     * 拦截器是否初始化完毕,
     * {@link LogisticsCenter#completion(Postcard)}方法反射创建IProvider的时候回调用init方法
     * 并且该类中的init方法会在{@link com.alibaba.android.arouter.launcher.ARouter#init(Application)}方法中调用到
     */
    private static boolean interceptorHasInit;

    /**
     * 拦截器初始化通知等待锁
     */
    private static final Object interceptorInitLock = new Object();

    @Override
    public void doInterceptions(final Postcard postcard, final InterceptorCallback callback) {
        if (MapUtils.isNotEmpty(Warehouse.interceptorsIndex)) {

            //确认拦截器初始化是否初始化完毕
            checkInterceptorsInitStatus();

            if (!interceptorHasInit) {
                callback.onInterrupt(new HandlerException("Interceptors initialization takes too much time."));
                return;
            }

            LogisticsCenter.executor.execute(new Runnable() {
                @Override
                public void run() {
                    CancelableCountDownLatch interceptorCounter = new CancelableCountDownLatch(Warehouse.interceptors.size());
                    try {
                        _execute(0, interceptorCounter, postcard);
                        //等待所有拦截器执行完毕,超时300秒
                        interceptorCounter.await(postcard.getTimeout(), TimeUnit.SECONDS);

                        //超时后还有未执行的拦截器
                        if (interceptorCounter.getCount() > 0) {    // Cancel the navigation this time, if it hasn't return anythings.
                            callback.onInterrupt(new HandlerException("The interceptor processing timed out."));
                        } else if (null != postcard.getTag()) {    // Maybe some exception in the tag.
                            callback.onInterrupt((Throwable) postcard.getTag());
                        } else {
                            callback.onContinue(postcard);
                        }
                    } catch (Exception e) {
                        callback.onInterrupt(e);
                    }
                }
            });
        } else {
            callback.onContinue(postcard);
        }
    }

    /**
     * Excute interceptor
     * 递归执行拦截器
     *
     * @param index    current interceptor index
     * @param counter  interceptor counter
     * @param postcard routeMeta
     */
    private static void _execute(final int index, final CancelableCountDownLatch counter, final Postcard postcard) {
        if (index < Warehouse.interceptors.size()) {
            IInterceptor iInterceptor = Warehouse.interceptors.get(index);
            //执行拦截器方法
            iInterceptor.process(postcard, new InterceptorCallback() {
                @Override
                public void onContinue(Postcard postcard) {
                    // Last interceptor excute over with no exception.
                    //每执行一个,同步计数就减1
                    counter.countDown();
                    _execute(index + 1, counter, postcard);  // When counter is down, it will be execute continue ,but index bigger than interceptors size, then U know.
                }

                @Override
                public void onInterrupt(Throwable exception) {
                    // Last interceptor execute over with fatal exception.

                    postcard.setTag(null == exception ? new HandlerException("No message.") : exception);    // save the exception message for backup.
                    counter.cancel();
                    // Be attention, maybe the thread in callback has been changed,
                    // then the catch block(L207) will be invalid.
                    // The worst is the thread changed to main thread, then the app will be crash, if you throw this exception!
//                    if (!Looper.getMainLooper().equals(Looper.myLooper())) {    // You shouldn't throw the exception if the thread is main thread.
//                        throw new HandlerException(exception.getMessage());
//                    }
                }
            });
        }
    }

    /**
     * 注意,我们的拦截器服务也是IProvider,所以会在{@link LogisticsCenter#completion(Postcard)}中进行反射调用init方法的
     *
     * @param context ctx
     */
    @Override
    public void init(final Context context) {
        LogisticsCenter.executor.execute(new Runnable() {
            @Override
            public void run() {
                if (MapUtils.isNotEmpty(Warehouse.interceptorsIndex)) {
                    for (Map.Entry<Integer, Class<? extends IInterceptor>> entry : Warehouse.interceptorsIndex.entrySet()) {
                        Class<? extends IInterceptor> interceptorClass = entry.getValue();
                        try {
                            //构造出所有的拦截器
                            IInterceptor iInterceptor = interceptorClass.getConstructor().newInstance();
                            //调用拦截器的init方法
                            iInterceptor.init(context);
                            //缓存到全局变量中
                            Warehouse.interceptors.add(iInterceptor);
                        } catch (Exception ex) {
                            throw new HandlerException(TAG + "ARouter init interceptor error! name = [" + interceptorClass.getName() + "], reason = [" + ex.getMessage() + "]");
                        }
                    }

                    //拦截器初始化完毕
                    interceptorHasInit = true;

                    logger.info(TAG, "ARouter interceptors init over.");

                    synchronized (interceptorInitLock) {
                        //通知初始化完毕
                        interceptorInitLock.notifyAll();
                    }
                }
            }
        });
    }

    private static void checkInterceptorsInitStatus() {
        synchronized (interceptorInitLock) {
            while (!interceptorHasInit) {
                try {
                    //等待初始化完毕
                    interceptorInitLock.wait(10 * 1000);
                } catch (InterruptedException e) {
                    throw new HandlerException(TAG + "Interceptor init cost too much time error! reason = [" + e.getMessage() + "]");
                }
            }
        }
    }
}
