package com.alibaba.android.arouter.facade.template;

import com.alibaba.android.arouter.facade.model.RouteMeta;

import java.util.Map;

/**
 * Group element.
 * 路由分组填充,填充属于同一分组下的所有路由数据(包括页面路由和Provider路由)
 * 1:N  1个IRouteGroup:N个RouteMeta
 *
 * @author Alex <a href="mailto:zhilong.liu@aliyun.com">Contact me.</a>
 * @version 1.0
 * @since 16/8/23 16:37
 */
public interface IRouteGroup {
    /**
     * Fill the atlas with routes in group.
     */
    void loadInto(Map<String, RouteMeta> atlas);
}
