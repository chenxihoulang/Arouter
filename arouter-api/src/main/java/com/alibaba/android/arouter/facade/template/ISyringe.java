package com.alibaba.android.arouter.facade.template;

/**
 * Template of syringe
 * Autowired标识的字段,最终生成的类实现的接口
 *
 * @author zhilong <a href="mailto:zhilong.lzl@alibaba-inc.com">Contact me.</a>
 * @version 1.0
 * @since 2017/2/20 下午4:41
 */
public interface ISyringe {
    void inject(Object target);
}
