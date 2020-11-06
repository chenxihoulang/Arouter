package com.alibaba.android.arouter.compiler.processor;

import com.alibaba.android.arouter.compiler.utils.Consts;
import com.alibaba.android.arouter.facade.annotation.Interceptor;
import com.google.auto.service.AutoService;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.JavaFile;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.ParameterSpec;
import com.squareup.javapoet.ParameterizedTypeName;
import com.squareup.javapoet.TypeSpec;
import com.squareup.javapoet.WildcardTypeName;

import org.apache.commons.collections4.CollectionUtils;

import java.io.IOException;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.Processor;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.annotation.processing.SupportedOptions;
import javax.annotation.processing.SupportedSourceVersion;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.TypeMirror;

import static com.alibaba.android.arouter.compiler.utils.Consts.*;
import static javax.lang.model.element.Modifier.PUBLIC;

/**
 * Process the annotation of #{@link Interceptor}
 * 拦截器注解处理器
 *
 * @author Alex <a href="mailto:zhilong.liu@aliyun.com">Contact me.</a>
 * @version 1.0
 * @since 16/8/23 14:11
 */
@AutoService(Processor.class)
@SupportedAnnotationTypes(ANNOTATION_TYPE_INTECEPTOR)
public class InterceptorProcessor extends BaseProcessor {
    /**
     * Interceptor注解的元素的优先级:对应的拦截器元素
     */
    private Map<Integer, Element> interceptors = new TreeMap<>();
    private TypeMirror iInterceptor = null;

    @Override
    public synchronized void init(ProcessingEnvironment processingEnv) {
        super.init(processingEnv);

        iInterceptor = elementUtils.getTypeElement(Consts.IINTERCEPTOR).asType();

        logger.info(">>> InterceptorProcessor init. <<<");
    }

    /**
     * {@inheritDoc}
     *
     * @param annotations
     * @param roundEnv
     */
    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        if (CollectionUtils.isNotEmpty(annotations)) {
            Set<? extends Element> elements = roundEnv.getElementsAnnotatedWith(Interceptor.class);
            try {
                parseInterceptors(elements);
            } catch (Exception e) {
                logger.error(e);
            }
            return true;
        }

        return false;
    }

    /**
     * Parse tollgate.
     * <p>
     * 生成拦截器代码
     * <p>
     * public class ARouter$$Interceptors$$presentation_principal implements IInterceptorGroup {
     *
     * @param elements elements of tollgate.
     * @Override public void loadInto(Map<Integer, Class<? extends IInterceptor>> interceptors) {
     * interceptors.put(1, TestInterceptor.class);
     * }
     * }
     */
    private void parseInterceptors(Set<? extends Element> elements) throws IOException {
        if (CollectionUtils.isNotEmpty(elements)) {
            logger.info(">>> Found interceptors, size is " + elements.size() + " <<<");

            // Verify and cache, sort incidentally.
            for (Element element : elements) {
                if (verify(element)) {  // Check the interceptor meta
                    logger.info("A interceptor verify over, its " + element.asType());
                    Interceptor interceptor = element.getAnnotation(Interceptor.class);

                    /**
                     注意:所有的拦截器的优先级不能相同
                     */
                    Element lastInterceptor = interceptors.get(interceptor.priority());
                    if (null != lastInterceptor) { // Added, throw exceptions
                        throw new IllegalArgumentException(
                                String.format(Locale.getDefault(), "More than one interceptors use same priority [%d], They are [%s] and [%s].",
                                        interceptor.priority(),
                                        lastInterceptor.getSimpleName(),
                                        element.getSimpleName())
                        );
                    }

                    interceptors.put(interceptor.priority(), element);
                } else {
                    logger.error("A interceptor verify failed, its " + element.asType());
                }
            }

            // Interface of ARouter.
            //拦截器接口
            TypeElement type_ITollgate = elementUtils.getTypeElement(IINTERCEPTOR);
            //拦截器分组接口 IInterceptorGroup
            TypeElement type_ITollgateGroup = elementUtils.getTypeElement(IINTERCEPTOR_GROUP);

            /**
             *  Build input type, format as :
             *
             *  ```Map<Integer, Class<? extends IInterceptor>>```
             *
             *  构造泛型参数
             */
            ParameterizedTypeName inputMapTypeOfTollgate = ParameterizedTypeName.get(
                    ClassName.get(Map.class),//泛型类
                    ClassName.get(Integer.class),//第一个泛型参数
                    //第二个泛型参数
                    ParameterizedTypeName.get(
                            ClassName.get(Class.class),
                            //通配符类型
                            WildcardTypeName.subtypeOf(ClassName.get(type_ITollgate))
                    )
            );

            // Build input param name.
            //构造interceptors参数
            ParameterSpec tollgateParamSpec = ParameterSpec.builder(inputMapTypeOfTollgate, "interceptors").build();

            // Build method : 'loadInto'
            //构造loadInto方法
            MethodSpec.Builder loadIntoMethodOfTollgateBuilder = MethodSpec.methodBuilder(METHOD_LOAD_INTO)
                    .addAnnotation(Override.class)
                    .addModifiers(PUBLIC)
                    .addParameter(tollgateParamSpec);

            // Generate
            if (null != interceptors && interceptors.size() > 0) {
                // Build method body
                for (Map.Entry<Integer, Element> entry : interceptors.entrySet()) {

                    /*
                    根据优先级,将具体的拦截器放入缓存中
                    interceptors.put(1, TestInterceptor.class);
                     */
                    loadIntoMethodOfTollgateBuilder.addStatement("interceptors.put(" + entry.getKey() + ", $T.class)", ClassName.get((TypeElement) entry.getValue()));
                }
            }

            // Write to disk(Write file even interceptors is empty.)
            /*
            创建类文件
            public class ARouter$$Interceptors$$presentation_principal implements IInterceptorGroup
             */
            JavaFile.builder(PACKAGE_OF_GENERATE_FILE,
                    TypeSpec.classBuilder(NAME_OF_INTERCEPTOR + SEPARATOR + moduleName)
                            .addModifiers(PUBLIC)
                            .addJavadoc(WARNING_TIPS)
                            .addMethod(loadIntoMethodOfTollgateBuilder.build())
                            .addSuperinterface(ClassName.get(type_ITollgateGroup))
                            .build()
            ).build().writeTo(mFiler);

            logger.info(">>> Interceptor group write over. <<<");
        }
    }

    /**
     * Verify inteceptor meta
     * 验证Interceptor注解的类是否实现了IInterceptor接口
     *
     * @param element Interceptor taw type
     * @return verify result
     */
    private boolean verify(Element element) {
        Interceptor interceptor = element.getAnnotation(Interceptor.class);
        // It must be implement the interface IInterceptor and marked with annotation Interceptor.
        return null != interceptor && ((TypeElement) element).getInterfaces().contains(iInterceptor);
    }
}
