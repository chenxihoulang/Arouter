package com.alibaba.android.arouter.facade.template;

import com.alibaba.android.arouter.facade.model.RouteMeta;

import java.util.Map;

/**
 * Template of provider group.
 * Provider路由分组填充,填充属于同一分组下的所有Provider路由数据
 *  1:N  1个IProviderGroup:N个RouteMeta
 * @author Alex <a href="mailto:zhilong.liu@aliyun.com">Contact me.</a>
 * @version 1.0
 * @since 16/08/30 12:42
 */
public interface IProviderGroup {
    /**
     * Load providers map to input
     *
     * @param providers input
     */
    void loadInto(Map<String, RouteMeta> providers);
}