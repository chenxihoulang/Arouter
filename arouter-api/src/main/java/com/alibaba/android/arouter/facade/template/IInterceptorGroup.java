package com.alibaba.android.arouter.facade.template;

import java.util.Map;

/**
 * Template of interceptor group.
 * 拦截器接口分组
 * @author Alex <a href="mailto:zhilong.liu@aliyun.com">Contact me.</a>
 * @version 1.0
 * @since 16/8/29 09:51
 */
public interface IInterceptorGroup {
    /**
     * Load interceptor to input
     *
     * @param interceptor input  优先级:拦截器实现类
     */
    void loadInto(Map<Integer, Class<? extends IInterceptor>> interceptor);
}
