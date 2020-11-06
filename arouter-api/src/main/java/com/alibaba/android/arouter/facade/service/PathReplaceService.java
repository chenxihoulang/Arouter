package com.alibaba.android.arouter.facade.service;

import android.net.Uri;

import com.alibaba.android.arouter.facade.template.IProvider;

/**
 * Preprocess your path
 * 自定义路由地址转换的服务
 *
 * @author Alex <a href="mailto:zhilong.liu@aliyun.com">Contact me.</a>
 * @version 1.0
 * @since 2016/12/9 16:48
 */
public interface PathReplaceService extends IProvider {

    /**
     * For normal path.
     * 普通字符串路由地址转换
     *
     * @param path raw path
     */
    String forString(String path);

    /**
     * For uri type.
     * Uri路由地址转换
     *
     * @param uri raw uri
     */
    Uri forUri(Uri uri);
}
