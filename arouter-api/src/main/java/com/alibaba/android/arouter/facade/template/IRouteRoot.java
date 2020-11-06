package com.alibaba.android.arouter.facade.template;

import java.util.Map;

/**
 * Root element.
 * 将所有的分组聚合起来,分组下面有对应于当前分组下面的所有路由数据
 * 1:N  1个IRouteRoot:N个IRouteGroup
 * <p>
 * 总体来说是   1:N:N   1个IRouteRoot:N个IRouteGroup:N个RouteMeta
 *
 * @author Alex <a href="mailto:zhilong.liu@aliyun.com">Contact me.</a>
 * @version 1.0
 * @since 16/8/23 16:36
 */
public interface IRouteRoot {

    /**
     * Load routes to input
     *
     * @param routes input
     */
    void loadInto(Map<String, Class<? extends IRouteGroup>> routes);
}
