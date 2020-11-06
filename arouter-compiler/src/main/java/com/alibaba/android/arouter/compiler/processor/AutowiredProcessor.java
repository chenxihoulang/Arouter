package com.alibaba.android.arouter.compiler.processor;

import com.alibaba.android.arouter.compiler.utils.Consts;
import com.alibaba.android.arouter.facade.annotation.Autowired;
import com.alibaba.android.arouter.facade.enums.TypeKind;
import com.google.auto.service.AutoService;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.CodeBlock;
import com.squareup.javapoet.FieldSpec;
import com.squareup.javapoet.JavaFile;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.ParameterSpec;
import com.squareup.javapoet.TypeName;
import com.squareup.javapoet.TypeSpec;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.StringUtils;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.Processor;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.annotation.processing.SupportedOptions;
import javax.annotation.processing.SupportedSourceVersion;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.Element;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.TypeMirror;

import static com.alibaba.android.arouter.compiler.utils.Consts.*;
import static javax.lang.model.element.Modifier.PUBLIC;

/**
 * Processor used to create autowired helper
 * Autowired注解处理器
 *
 * @author zhilong <a href="mailto:zhilong.lzl@alibaba-inc.com">Contact me.</a>
 * @version 1.0
 * @since 2017/2/20 下午5:56
 */
@AutoService(Processor.class)
@SupportedAnnotationTypes({ANNOTATION_TYPE_AUTOWIRED})
public class AutowiredProcessor extends BaseProcessor {
    /**
     * @Autowired注解的字段所属的类:和所有字段元素
     */
    private Map<TypeElement, List<Element>> parentAndChild = new HashMap<>();   // Contain field need autowired and his super class.
    private static final ClassName ARouterClass = ClassName.get("com.alibaba.android.arouter.launcher", "ARouter");
    private static final ClassName AndroidLog = ClassName.get("android.util", "Log");

    @Override
    public synchronized void init(ProcessingEnvironment processingEnvironment) {
        super.init(processingEnvironment);

        logger.info(">>> AutowiredProcessor init. <<<");
    }

    @Override
    public boolean process(Set<? extends TypeElement> set, RoundEnvironment roundEnvironment) {
        if (CollectionUtils.isNotEmpty(set)) {
            try {
                logger.info(">>> Found autowired field, start... <<<");
                //找到所有@Autowired注解的元素,并根据所属类型进行分组
                categories(roundEnvironment.getElementsAnnotatedWith(Autowired.class));

                generateHelper();

            } catch (Exception e) {
                logger.error(e);
            }
            return true;
        }

        return false;
    }

    /**
     * 在字段所属类的包名下创建辅助文件
     * <p>
     * public class Test1$$ARouter$$Autowired implements ISyringe {
     * private SerializationService serializationService;
     *
     * @Override public void inject(Object target) {
     * serializationService = ARouter.getInstance().navigation(SerializationService.class);
     * Test1 substitute = (Test1)target;
     * substitute.param1 = substitute.getIntent().getStringExtra("param1");
     * substitute.param2 = substitute.getIntent().getStringExtra("name");
     * if (null == substitute.param2) {
     * Log.e("ARouter::", "The field 'param2' is null, in class '" + Test1.class.getName() + "!");
     * }
     * substitute.mProvider1 = ARouter.getInstance().navigation(PageRouteProvider.class);
     * if (substitute.mProvider1 == null) {
     * throw new RuntimeException("The field 'mProvider1' is null, in class '" + Test1.class.getName() + "!");
     * }
     * substitute.mProvider2 = (PageRouteProvider)ARouter.getInstance().build("MyPageRouteProvider").navigation();
     * substitute.res = (com.cmcc.hbb.android.phone.common_data.responseentity.BaseResponse) substitute.getIntent().getSerializableExtra("res");
     * if (null != serializationService) {
     * substitute.mParam = serializationService.parseObject(substitute.getIntent().getStringExtra("mParam"), new com.alibaba.android.arouter.facade.model.TypeWrapper<CreateSchoolParam>(){}.getType());
     * } else {
     * Log.e("ARouter::", "You want automatic inject the field 'mParam' in class 'Test1' , then you should implement 'SerializationService' to support object auto inject!");
     * }
     * }
     * }
     */
    private void generateHelper() throws IOException, IllegalAccessException {
        //模板接口类型
        TypeElement type_ISyringe = elementUtils.getTypeElement(ISYRINGE);
        //用于json转换的接口类型
        TypeElement type_JsonService = elementUtils.getTypeElement(JSON_SERVICE);

        TypeMirror iProvider = elementUtils.getTypeElement(Consts.IPROVIDER).asType();
        TypeMirror activityTm = elementUtils.getTypeElement(Consts.ACTIVITY).asType();
        TypeMirror fragmentTm = elementUtils.getTypeElement(Consts.FRAGMENT).asType();
        TypeMirror fragmentTmV4 = elementUtils.getTypeElement(Consts.FRAGMENT_V4).asType();

        // Build input param name.
        //构造一个Object target参数
        ParameterSpec objectParamSpec = ParameterSpec.builder(TypeName.OBJECT, "target").build();

        if (MapUtils.isNotEmpty(parentAndChild)) {
            for (Map.Entry<TypeElement, List<Element>> entry : parentAndChild.entrySet()) {
                // Build method : 'inject'
                /*
                     @Override public void inject(Object target)
                 */
                MethodSpec.Builder injectMethodBuilder = MethodSpec.methodBuilder(METHOD_INJECT)
                        .addAnnotation(Override.class)
                        .addModifiers(PUBLIC)
                        .addParameter(objectParamSpec);

                //字段所属类
                TypeElement parent = entry.getKey();
                //该类中所有的被@Autowired注解的字段
                List<Element> childs = entry.getValue();

                //类全路径名
                String qualifiedName = parent.getQualifiedName().toString();
                //包名
                String packageName = qualifiedName.substring(0, qualifiedName.lastIndexOf("."));
                //生成的类名:Test1$$ARouter$$Autowired
                String fileName = parent.getSimpleName() + NAME_OF_AUTOWIRED;

                logger.info(">>> Start process " + childs.size() + " field in " + parent.getSimpleName() + " ... <<<");

                //使用javapoet创建类
                TypeSpec.Builder helper = TypeSpec.classBuilder(fileName)
                        .addJavadoc(WARNING_TIPS)
                        .addSuperinterface(ClassName.get(type_ISyringe))
                        .addModifiers(PUBLIC);

                //private SerializationService serializationService;
                FieldSpec jsonServiceField = FieldSpec.builder(TypeName.get(type_JsonService.asType()),
                        "serializationService", Modifier.PRIVATE).build();
                helper.addField(jsonServiceField);

                //serializationService = ARouter.getInstance().navigation(SerializationService.class);
                injectMethodBuilder.addStatement("serializationService = $T.getInstance().navigation($T.class)",
                        ARouterClass, ClassName.get(type_JsonService));
                //Test1 substitute = (Test1)target;
                injectMethodBuilder.addStatement("$T substitute = ($T)target", ClassName.get(parent), ClassName.get(parent));

                // Generate method body, start inject.
                //解析所有的字段,开始获取值,给字段注入
                for (Element element : childs) {
                    Autowired fieldConfig = element.getAnnotation(Autowired.class);
                    //字段名称
                    String fieldName = element.getSimpleName().toString();

                    //字段是否是IProvider的子类型
                    if (types.isSubtype(element.asType(), iProvider)) {  // It's provider
                        //@Autowired是否设置了name值
                        if ("".equals(fieldConfig.name())) {    // User has not set service path, then use byType.

                            // Getter
                            //substitute.mProvider1 = ARouter.getInstance().navigation(PageRouteProvider.class);
                            injectMethodBuilder.addStatement(
                                    "substitute." + fieldName + " = $T.getInstance().navigation($T.class)",
                                    ARouterClass,
                                    ClassName.get(element.asType())
                            );
                        } else {    // use byName
                            // Getter
                            //substitute.mProvider2 = (PageRouteProvider)ARouter.getInstance().build("MyPageRouteProvider").navigation();
                            injectMethodBuilder.addStatement(
                                    "substitute." + fieldName + " = ($T)$T.getInstance().build($S).navigation()",
                                    ClassName.get(element.asType()),
                                    ARouterClass,
                                    fieldConfig.name()
                            );
                        }

                        // Validator
                        /*
                        如果是必填字段,添加一个判断语句,为null的时候抛出异常
                        if (substitute.mProvider1 == null) {
                            throw new RuntimeException("The field 'mProvider1' is null, in class '" + Test1.class.getName() + "!");
                        }
                         */
                        if (fieldConfig.required()) {
                            injectMethodBuilder.beginControlFlow("if (substitute." + fieldName + " == null)");
                            injectMethodBuilder.addStatement(
                                    "throw new RuntimeException(\"The field '" + fieldName + "' is null, in class '\" + $T.class.getName() + \"!\")", ClassName.get(parent));
                            injectMethodBuilder.endControlFlow();
                        }
                    } else {    //非IProvider子类型的字段, It's normal intent value
                        //这个是字段的默认值,如果有的话
                        String originalValue = "substitute." + fieldName;

                        //substitute.res = (com.cmcc.hbb.android.phone.common_data.responseentity.BaseResponse) substitute.getIntent().getSerializableExtra("res");
                        String statement = "substitute." + fieldName + " = " + buildCastCode(element) + "substitute.";

                        //判断是否是Activity或者Fragment里面的字段,对应使用getIntent()或getArguments()来获取参数值
                        boolean isActivity = false;
                        if (types.isSubtype(parent.asType(), activityTm)) {  // Activity, then use getIntent()
                            isActivity = true;
                            statement += "getIntent().";
                        } else if (types.isSubtype(parent.asType(), fragmentTm)
                                || types.isSubtype(parent.asType(), fragmentTmV4)) {   // Fragment, then use getArguments()
                            statement += "getArguments().";
                        } else {
                            throw new IllegalAccessException("The field [" + fieldName + "] need autowired from intent, its parent must be activity or fragment!");
                        }

                        //构造获取参数数据的语句
                        statement = buildStatement(originalValue, statement, typeUtils.typeExchange(element), isActivity, isKtClass(parent));

                         /*
                            如果是对象类型,需要进行json数据转换
                            if (null != serializationService) {
                              substitute.mParam = serializationService.parseObject(substitute.getIntent().getStringExtra("mParam"), new com.alibaba.android.arouter.facade.model.TypeWrapper<CreateSchoolParam>(){}.getType());
                            } else {
                              Log.e("ARouter::", "You want automatic inject the field 'mParam' in class 'Test1' , then you should implement 'SerializationService' to support object auto inject!");
                            }
                         */
                        if (statement.startsWith("serializationService.")) {   // Not mortals
                            injectMethodBuilder.beginControlFlow("if (null != serializationService)");
                            injectMethodBuilder.addStatement(
                                    "substitute." + fieldName + " = " + statement,
                                    (StringUtils.isEmpty(fieldConfig.name()) ? fieldName : fieldConfig.name()),
                                    ClassName.get(element.asType())
                            );
                            injectMethodBuilder.nextControlFlow("else");
                            injectMethodBuilder.addStatement(
                                    "$T.e(\"" + Consts.TAG + "\", \"You want automatic inject the field '" + fieldName + "' in class '$T' , then you should implement 'SerializationService' to support object auto inject!\")", AndroidLog, ClassName.get(parent));
                            injectMethodBuilder.endControlFlow();
                        } else {
                            injectMethodBuilder.addStatement(statement, StringUtils.isEmpty(fieldConfig.name()) ? fieldName : fieldConfig.name());
                        }

                        // Validator
                        //非基本类型的必填字段,添加非空判断
                        if (fieldConfig.required() && !element.asType().getKind().isPrimitive()) {  // Primitive wont be check.
                            injectMethodBuilder.beginControlFlow("if (null == substitute." + fieldName + ")");
                            injectMethodBuilder.addStatement(
                                    "$T.e(\"" + Consts.TAG + "\", \"The field '" + fieldName + "' is null, in class '\" + $T.class.getName() + \"!\")", AndroidLog, ClassName.get(parent));
                            injectMethodBuilder.endControlFlow();
                        }
                    }
                }

                helper.addMethod(injectMethodBuilder.build());

                // Generate autowire helper
                JavaFile.builder(packageName, helper.build()).build().writeTo(mFiler);

                logger.info(">>> " + parent.getSimpleName() + " has been processed, " + fileName + " has been generated. <<<");
            }

            logger.info(">>> Autowired processor stop. <<<");
        }
    }

    /**
     * 是否是Kotlin类型
     *
     * @param element
     * @return
     */
    private boolean isKtClass(Element element) {
        for (AnnotationMirror annotationMirror : elementUtils.getAllAnnotationMirrors(element)) {
            if (annotationMirror.getAnnotationType().toString().contains("kotlin")) {
                return true;
            }
        }

        return false;
    }

    /**
     * 序列化类型转换
     *
     * @param element
     * @return
     */
    private String buildCastCode(Element element) {
        //如果是Serializable的类型,需要进行转换,比如:(com.cmcc.hbb.android.phone.common_data.responseentity.BaseResponse)
        if (typeUtils.typeExchange(element) == TypeKind.SERIALIZABLE.ordinal()) {
            return CodeBlock.builder().add("($T) ", ClassName.get(element.asType())).build().toString();
        }
        return "";
    }

    /**
     * Build param inject statement
     * <p>
     * <p>
     * 构造获取参数数据的语句
     *
     * @param originalValue 字段默认值
     * @param statement     获取参数的公共语句
     */
    private String buildStatement(String originalValue, String statement, int type, boolean isActivity, boolean isKt) {
        switch (TypeKind.values()[type]) {
            case BOOLEAN:
                statement += "getBoolean" + (isActivity ? "Extra" : "") + "($S, " + originalValue + ")";
                break;
            case BYTE:
                statement += "getByte" + (isActivity ? "Extra" : "") + "($S, " + originalValue + ")";
                break;
            case SHORT:
                statement += "getShort" + (isActivity ? "Extra" : "") + "($S, " + originalValue + ")";
                break;
            case INT:
                statement += "getInt" + (isActivity ? "Extra" : "") + "($S, " + originalValue + ")";
                break;
            case LONG:
                statement += "getLong" + (isActivity ? "Extra" : "") + "($S, " + originalValue + ")";
                break;
            case CHAR:
                statement += "getChar" + (isActivity ? "Extra" : "") + "($S, " + originalValue + ")";
                break;
            case FLOAT:
                statement += "getFloat" + (isActivity ? "Extra" : "") + "($S, " + originalValue + ")";
                break;
            case DOUBLE:
                statement += "getDouble" + (isActivity ? "Extra" : "") + "($S, " + originalValue + ")";
                break;
            case STRING:
                statement += (isActivity ? ("getExtras() == null ? " + originalValue + " : substitute.getIntent().getExtras().getString($S") : ("getString($S")) + ", " + originalValue + ")";
                break;
            case SERIALIZABLE:
                statement += (isActivity ? ("getSerializableExtra($S)") : ("getSerializable($S)"));
                break;
            case PARCELABLE:
                statement += (isActivity ? ("getParcelableExtra($S)") : ("getParcelable($S)"));
                break;
            case OBJECT:
                /*
                如果是对象类型,需要进行json数据转换
                substitute.mParam = serializationService.parseObject(substitute.getIntent().getStringExtra("mParam"), new com.alibaba.android.arouter.facade.model.TypeWrapper<CreateSchoolParam>(){}.getType());
                 */
                statement = "serializationService.parseObject(substitute." + (isActivity ? "getIntent()." : "getArguments().") + (isActivity ? "getStringExtra($S)" : "getString($S)") + ", new " + TYPE_WRAPPER + "<$T>(){}.getType())";
                break;
        }

        return statement;
    }

    /**
     * Categories field, find his papa.
     * 找到所有@Autowired注解的元素,并根据所属类型进行分组
     *
     * @param elements Field need autowired
     */
    private void categories(Set<? extends Element> elements) throws IllegalAccessException {
        if (CollectionUtils.isNotEmpty(elements)) {
            for (Element element : elements) {
                //字段所属类型
                TypeElement enclosingElement = (TypeElement) element.getEnclosingElement();

                //字段不能是private的
                if (element.getModifiers().contains(Modifier.PRIVATE)) {
                    throw new IllegalAccessException("The inject fields CAN NOT BE 'private'!!! please check field ["
                            + element.getSimpleName() + "] in class [" + enclosingElement.getQualifiedName() + "]");
                }

                //根据所属类将其中的字段分组
                if (parentAndChild.containsKey(enclosingElement)) { // Has categries
                    parentAndChild.get(enclosingElement).add(element);
                } else {
                    List<Element> childs = new ArrayList<>();
                    childs.add(element);
                    parentAndChild.put(enclosingElement, childs);
                }
            }

            logger.info("categories finished.");
        }
    }
}
