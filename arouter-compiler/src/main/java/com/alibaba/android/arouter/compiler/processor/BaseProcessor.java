package com.alibaba.android.arouter.compiler.processor;

import com.alibaba.android.arouter.compiler.utils.Logger;
import com.alibaba.android.arouter.compiler.utils.TypeUtils;

import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.StringUtils;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.Filer;
import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.SourceVersion;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static com.alibaba.android.arouter.compiler.utils.Consts.*;
import static com.alibaba.android.arouter.compiler.utils.Consts.NO_MODULE_NAME_TIPS;

/**
 * Base Processor
 * 注解处理器基类
 *
 * @author zhilong [Contact me.](mailto:zhilong.lzl@alibaba-inc.com)
 * @version 1.0
 * @since 2019-03-01 12:31
 */
public abstract class BaseProcessor extends AbstractProcessor {
    Filer mFiler;
    /**
     * 日志工具类
     */
    Logger logger;
    Types types;
    Elements elementUtils;
    /**
     * 类型转换工具类
     */
    TypeUtils typeUtils;
    // Module name, maybe its 'app' or others
    String moduleName = null;
    // If need generate router doc
    boolean generateDoc;

    @Override
    public synchronized void init(ProcessingEnvironment processingEnv) {
        super.init(processingEnv);

        mFiler = processingEnv.getFiler();
        types = processingEnv.getTypeUtils();
        elementUtils = processingEnv.getElementUtils();
        typeUtils = new TypeUtils(types, elementUtils);
        logger = new Logger(processingEnv.getMessager());

        // Attempt to get user configuration [moduleName]

        /**
         * 获取编译器选择参数
         *
         * kapt {
         *     arguments {
         *         arg("AROUTER_MODULE_NAME", project.getName())
         *         arg("AROUTER_GENERATE_DOC": "enable")
         *     }
         * }
         */
        Map<String, String> options = processingEnv.getOptions();
        if (MapUtils.isNotEmpty(options)) {
            //模块名称
            moduleName = options.get(KEY_MODULE_NAME);
            //是否生产doc
            generateDoc = VALUE_ENABLE.equals(options.get(KEY_GENERATE_DOC_NAME));
        }

        //将模块名称中非数字字母下划线去掉
        if (StringUtils.isNotEmpty(moduleName)) {
            moduleName = moduleName.replaceAll("[^0-9a-zA-Z_]+", "");

            logger.info("The user has configuration the module name, it was [" + moduleName + "]");
        } else {
            logger.error(NO_MODULE_NAME_TIPS);
            throw new RuntimeException("ARouter::Compiler >>> No module name, for more information, look at gradle log.");
        }
    }

    @Override
    public SourceVersion getSupportedSourceVersion() {
        return SourceVersion.latestSupported();
    }

    /**
     * 返回注解支持的编译选项参数
     */
    @Override
    public Set<String> getSupportedOptions() {
        return new HashSet<String>() {{
            this.add(KEY_MODULE_NAME);
            this.add(KEY_GENERATE_DOC_NAME);
        }};
    }
}
