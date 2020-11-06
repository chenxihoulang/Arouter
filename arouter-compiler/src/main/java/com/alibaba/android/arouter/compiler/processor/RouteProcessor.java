package com.alibaba.android.arouter.compiler.processor;

import com.alibaba.android.arouter.compiler.entity.RouteDoc;
import com.alibaba.android.arouter.compiler.utils.Consts;
import com.alibaba.android.arouter.facade.annotation.Autowired;
import com.alibaba.android.arouter.facade.annotation.Route;
import com.alibaba.android.arouter.facade.enums.RouteType;
import com.alibaba.android.arouter.facade.enums.TypeKind;
import com.alibaba.android.arouter.facade.model.RouteMeta;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.serializer.SerializerFeature;
import com.google.auto.service.AutoService;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.JavaFile;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.ParameterSpec;
import com.squareup.javapoet.ParameterizedTypeName;
import com.squareup.javapoet.TypeSpec;
import com.squareup.javapoet.WildcardTypeName;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.StringUtils;

import java.io.IOException;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.Processor;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeMirror;
import javax.tools.StandardLocation;

import static com.alibaba.android.arouter.compiler.utils.Consts.ACTIVITY;
import static com.alibaba.android.arouter.compiler.utils.Consts.ANNOTATION_TYPE_AUTOWIRED;
import static com.alibaba.android.arouter.compiler.utils.Consts.ANNOTATION_TYPE_ROUTE;
import static com.alibaba.android.arouter.compiler.utils.Consts.FRAGMENT;
import static com.alibaba.android.arouter.compiler.utils.Consts.IPROVIDER_GROUP;
import static com.alibaba.android.arouter.compiler.utils.Consts.IROUTE_GROUP;
import static com.alibaba.android.arouter.compiler.utils.Consts.ITROUTE_ROOT;
import static com.alibaba.android.arouter.compiler.utils.Consts.METHOD_LOAD_INTO;
import static com.alibaba.android.arouter.compiler.utils.Consts.NAME_OF_GROUP;
import static com.alibaba.android.arouter.compiler.utils.Consts.NAME_OF_PROVIDER;
import static com.alibaba.android.arouter.compiler.utils.Consts.NAME_OF_ROOT;
import static com.alibaba.android.arouter.compiler.utils.Consts.PACKAGE_OF_GENERATE_DOCS;
import static com.alibaba.android.arouter.compiler.utils.Consts.PACKAGE_OF_GENERATE_FILE;
import static com.alibaba.android.arouter.compiler.utils.Consts.SEPARATOR;
import static com.alibaba.android.arouter.compiler.utils.Consts.SERVICE;
import static com.alibaba.android.arouter.compiler.utils.Consts.WARNING_TIPS;
import static javax.lang.model.element.Modifier.PUBLIC;

/**
 * A processor used for find route.
 * 路由相关的注解处理器,要处理的注解有@Route(Activity和IProvider子类)和@Autowired两个
 * 对@Autowired注解的处理只是获取到注解的字段名称和类型相关信息,并存储到路由元数据中
 *
 * @author Alex <a href="mailto:zhilong.liu@aliyun.com">Contact me.</a>
 * @version 1.0
 * @since 16/8/15 下午10:08
 */
@AutoService(Processor.class)
@SupportedAnnotationTypes({ANNOTATION_TYPE_ROUTE, ANNOTATION_TYPE_AUTOWIRED})
public class RouteProcessor extends BaseProcessor {
    /**
     * 存储某个group下的所有路由信息,路由group:同一组下面的所有路由元数据
     */
    private Map<String, Set<RouteMeta>> groupMap = new HashMap<>(); // ModuleName and routeMeta.
    /**
     * 分组名称:根据组名生成的文件名称
     */
    private Map<String, String> rootMap = new TreeMap<>();  // Map of root metas, used for generate class file in order.

    /**
     * IProvider对应的类型
     */
    private TypeMirror iProvider = null;
    private Writer docWriter;       // Writer used for write doc

    @Override
    public synchronized void init(ProcessingEnvironment processingEnv) {
        super.init(processingEnv);

        //生成doc文档
        if (generateDoc) {
            try {
                docWriter = mFiler.createResource(
                        StandardLocation.SOURCE_OUTPUT,
                        PACKAGE_OF_GENERATE_DOCS,
                        "arouter-map-of-" + moduleName + ".json"
                ).openWriter();
            } catch (IOException e) {
                logger.error("Create doc writer failed, because " + e.getMessage());
            }
        }

        iProvider = elementUtils.getTypeElement(Consts.IPROVIDER).asType();

        logger.info(">>> RouteProcessor init. <<<");
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
            //获取所有被Route注解的元素
            Set<? extends Element> routeElements = roundEnv.getElementsAnnotatedWith(Route.class);
            try {
                logger.info(">>> Found routes, start... <<<");

                //解析被Route注解的元素信息
                this.parseRoutes(routeElements);

            } catch (Exception e) {
                logger.error(e);
            }
            return true;
        }

        return false;
    }

    /*
      解析被Route注解的元素信息,大概会创建下面3个类及对应的方法

      public class ARouter$$Group$$data implements IRouteGroup {
        @Override
        public void loadInto(Map<String, RouteMeta> atlas) {
          atlas.put("/data/dbopt", RouteMeta.build(RouteType.PROVIDER, DataDBOpt.class, "/data/dbopt", "data", null, -1, -2147483648));
        }
      }

      public class ARouter$$Root$$data implements IRouteRoot {
        @Override
        public void loadInto(Map<String, Class<? extends IRouteGroup>> routes) {
          routes.put("data", ARouter$$Group$$data.class);
        }
      }

      public class ARouter$$Providers$$data implements IProviderGroup {
        @Override
        public void loadInto(Map<String, RouteMeta> providers) {
          providers.put("com.cmcc.hbb.android.phone.principal.serviceprovider.provider.DBOperatorProvider", RouteMeta.build(RouteType.PROVIDER, DataDBOpt.class, "/data/dbopt", "data", null, -1, -2147483648));
        }
      }

      @param routeElements
      @throws IOException
     */
    private void parseRoutes(Set<? extends Element> routeElements) throws IOException {
        if (CollectionUtils.isNotEmpty(routeElements)) {
            // prepare the type an so on.

            logger.info(">>> Found routes, size is " + routeElements.size() + " <<<");

            rootMap.clear();

            //获取4种对应的类型信息
            TypeMirror type_Activity = elementUtils.getTypeElement(ACTIVITY).asType();
            TypeMirror type_Service = elementUtils.getTypeElement(SERVICE).asType();
            TypeMirror fragmentTm = elementUtils.getTypeElement(FRAGMENT).asType();
            TypeMirror fragmentTmV4 = elementUtils.getTypeElement(Consts.FRAGMENT_V4).asType();

            // Interface of ARouter
            //生成的类需要实现的基类or接口
            TypeElement type_IRouteGroup = elementUtils.getTypeElement(IROUTE_GROUP);
            TypeElement type_IProviderGroup = elementUtils.getTypeElement(IPROVIDER_GROUP);

            //包含路由基本信息类
            ClassName routeMetaCn = ClassName.get(RouteMeta.class);
            //路由类型,基本对应上面4中类型
            ClassName routeTypeCn = ClassName.get(RouteType.class);

            /*
               Build input type, format as :

               ```Map<String, Class<? extends IRouteGroup>>```
             */
            //使用javapoet创建一个泛型类型,在IRouteRoot方法参数会用到
            ParameterizedTypeName inputMapTypeOfRoot = ParameterizedTypeName.get(
                    ClassName.get(Map.class),//原始类型
                    ClassName.get(String.class),//第一个类型参数
                    //第二个泛型类型
                    ParameterizedTypeName.get(
                            ClassName.get(Class.class),
                            //通配符类型
                            WildcardTypeName.subtypeOf(ClassName.get(type_IRouteGroup))
                    )
            );

            /*

              ```Map<String, RouteMeta>```
             */
            //在IRouteGroup和IProviderGroup方法参数会用到
            ParameterizedTypeName inputMapTypeOfGroup = ParameterizedTypeName.get(
                    ClassName.get(Map.class),
                    ClassName.get(String.class),
                    ClassName.get(RouteMeta.class)
            );

            /*
             Build input param name.
              构造方法参数对象,将参数名称和参数类型对应起来
             */
            ParameterSpec rootParamSpec = ParameterSpec.builder(inputMapTypeOfRoot, "routes").build();
            ParameterSpec groupParamSpec = ParameterSpec.builder(inputMapTypeOfGroup, "atlas").build();
            ParameterSpec providerParamSpec = ParameterSpec.builder(inputMapTypeOfGroup, "providers").build();  // Ps. its param type same as groupParamSpec!

            /**
             Build method : 'loadInto'
             创建loadInto方法,参数名称是routes
             */
            MethodSpec.Builder loadIntoMethodOfRootBuilder = MethodSpec.methodBuilder(METHOD_LOAD_INTO)
                    .addAnnotation(Override.class)
                    .addModifiers(PUBLIC)
                    .addParameter(rootParamSpec);

            //  Follow a sequence, find out metas of group first, generate java file, then statistics them as root.
            //解析对应的类型,生成java文件
            for (Element element : routeElements) {
                //获取元素类型信息
                TypeMirror tm = element.asType();
                //获取Route注解信息
                Route route = element.getAnnotation(Route.class);
                RouteMeta routeMeta;

                // Activity or Fragment
                //判断被注解的类型是否是Activity或Fragment的子类型
                if (types.isSubtype(tm, type_Activity)
                        || types.isSubtype(tm, fragmentTm)
                        || types.isSubtype(tm, fragmentTmV4)) {
                    // Get all fields annotation by @Autowired
                    //参数名称:参数类型
                    Map<String, Integer> paramsType = new HashMap<>();
                    //参数名称:对应的Autowired注解信息
                    Map<String, Autowired> injectConfig = new HashMap<>();
                    //解析类及父类中被Autowired注解的所有字段
                    injectParamCollector(element, paramsType, injectConfig);

                    //页面路由
                    if (types.isSubtype(tm, type_Activity)) {
                        // Activity
                        logger.info(">>> Found activity route: " + tm.toString() + " <<<");
                        routeMeta = new RouteMeta(route, element, RouteType.ACTIVITY, paramsType);
                    } else {
                        // Fragment
                        logger.info(">>> Found fragment route: " + tm.toString() + " <<<");
                        routeMeta = new RouteMeta(route, element, RouteType.parse(FRAGMENT), paramsType);
                    }

                    // 设置自动注入值的参数相关信息
                    routeMeta.setInjectConfig(injectConfig);
                } else if (types.isSubtype(tm, iProvider)) {         // IProvider
                    logger.info(">>> Found provider route: " + tm.toString() + " <<<");

                    //被注解的类型是IProvider的子类型
                    routeMeta = new RouteMeta(route, element, RouteType.PROVIDER, null);
                } else if (types.isSubtype(tm, type_Service)) {           // Service
                    logger.info(">>> Found service route: " + tm.toString() + " <<<");
                    routeMeta = new RouteMeta(route, element, RouteType.parse(SERVICE), null);
                } else {
                    throw new RuntimeException("The @Route is marked on unsupported class, look at [" + tm.toString() + "].");
                }

                //分解解析出的路由元数据信息,进行分组,填充好groupMap
                categories(routeMeta);
            }

            /*
            创建loadInto方法,参数名称是providers
             */
            MethodSpec.Builder loadIntoMethodOfProviderBuilder = MethodSpec.methodBuilder(METHOD_LOAD_INTO)
                    .addAnnotation(Override.class)
                    .addModifiers(PUBLIC)
                    .addParameter(providerParamSpec);

            //根据分组创建描述文档
            Map<String, List<RouteDoc>> docSource = new HashMap<>();

            // Start generate java source, structure is divided into upper and lower levels, used for demand initialization.
            for (Map.Entry<String, Set<RouteMeta>> entry : groupMap.entrySet()) {
                //组名
                String groupName = entry.getKey();

                /*
               创建loadInto方法,参数名称是 atlas
                 */
                MethodSpec.Builder loadIntoMethodOfGroupBuilder = MethodSpec.methodBuilder(METHOD_LOAD_INTO)
                        .addAnnotation(Override.class)
                        .addModifiers(PUBLIC)
                        .addParameter(groupParamSpec);

                List<RouteDoc> routeDocList = new ArrayList<>();

                // Build group method body
                Set<RouteMeta> groupData = entry.getValue();
                for (RouteMeta routeMeta : groupData) {
                    RouteDoc routeDoc = extractDocInfo(routeMeta);

                    //@Router注解的类名
                    ClassName className = ClassName.get((TypeElement) routeMeta.getRawType());

                    //处理注解的IProvider
                    switch (routeMeta.getType()) {
                        case PROVIDER:  // Need cache provider's super class
                            //获取注解类实现的所有接口
                            List<? extends TypeMirror> interfaces = ((TypeElement) routeMeta.getRawType()).getInterfaces();

                            for (TypeMirror tm : interfaces) {
                                routeDoc.addPrototype(tm.toString());

                                //就是实现IProvider接口
                                if (types.isSameType(tm, iProvider)) {   //直接实现IProvider, Its implements iProvider interface himself.
                                    // This interface extend the IProvider, so it can be used for mark provider
                                    loadIntoMethodOfProviderBuilder.addStatement(
                                            "providers.put($S, $T.build($T." + routeMeta.getType() + ", $T.class, $S, $S, null, " + routeMeta.getPriority() + ", " + routeMeta.getExtra() + "))",
                                            (routeMeta.getRawType()).toString(),
                                            routeMetaCn,
                                            routeTypeCn,
                                            className,
                                            routeMeta.getPath(),
                                            routeMeta.getGroup());
                                } else if (types.isSubtype(tm, iProvider)) {//间接实现IProvider
                                    /*
                                    providers.put("com.cmcc.hbb.android.phone.principal.serviceprovider.provider.DBOperatorProvider", RouteMeta.build(RouteType.PROVIDER, DataDBOpt.class, "/data/dbopt", "data", null, -1, -2147483648));
                                     */
                                    // This interface extend the IProvider, so it can be used for mark provider
                                    loadIntoMethodOfProviderBuilder.addStatement(
                                            "providers.put($S, $T.build($T." + routeMeta.getType() + ", $T.class, $S, $S, null, " + routeMeta.getPriority() + ", " + routeMeta.getExtra() + "))",
                                            tm.toString(),    //中间接口的全路径名称, So stupid, will duplicate only save class name.
                                            routeMetaCn,
                                            routeTypeCn,
                                            className,
                                            routeMeta.getPath(),
                                            routeMeta.getGroup());
                                }
                            }
                            break;
                        default:
                            break;
                    }

                    /*
                    存储需自动注入的参数信息
                    每一项的个数为  put(参数名称,参数类型);

                    最终使用如下代码添加到生成的代码中:
                     new HashMap<String,Integer>(){{
                        put("param1", 1);
                        put("param2", 2);
                    }};

                    atlas.put("/main/test1", RouteMeta.build(RouteType.ACTIVITY, Test1.class, "/main/test1", "main",
                     new java.util.HashMap<String, Integer>(){{put("name", 8); put("param1", 8); }}, -1, -2147483648));
                     */
                    // Make map body for paramsType
                    StringBuilder mapBodyBuilder = new StringBuilder();
                    //参数名称:参数类型
                    Map<String, Integer> paramsType = routeMeta.getParamsType();
                    //参数名称:对应的Autowired注解信息
                    Map<String, Autowired> injectConfigs = routeMeta.getInjectConfig();

                    if (MapUtils.isNotEmpty(paramsType)) {
                        List<RouteDoc.Param> paramList = new ArrayList<>();

                        for (Map.Entry<String, Integer> types : paramsType.entrySet()) {
                            mapBodyBuilder.append("put(\"").append(types.getKey()).append("\", ").append(types.getValue()).append("); ");

                            RouteDoc.Param param = new RouteDoc.Param();
                            Autowired injectConfig = injectConfigs.get(types.getKey());
                            param.setKey(types.getKey());
                            param.setType(TypeKind.values()[types.getValue()].name().toLowerCase());
                            param.setDescription(injectConfig.desc());
                            param.setRequired(injectConfig.required());

                            paramList.add(param);
                        }

                        routeDoc.setParams(paramList);
                    }

                    //所有的参数字符串
                    String mapBody = mapBodyBuilder.toString();


                    /*
                    添加方法体,里面会包含页面路由和IProvider的路由信息
                      @Override
  public void loadInto(Map<String, RouteMeta> atlas) {
    atlas.put("/userinfo/change_password", RouteMeta.build(RouteType.ACTIVITY, ChangePasswordActivity.class, "/userinfo/change_password", "userinfo", null, -1, -2147483648));
    atlas.put("/userinfo/setting", RouteMeta.build(RouteType.ACTIVITY, PrincipalInfoUpdateActivity.class, "/userinfo/setting", "userinfo", null, -1, -2147483648));
  }

                     */
                    loadIntoMethodOfGroupBuilder.addStatement(
                            "atlas.put($S, $T.build($T." + routeMeta.getType() + ", $T.class, $S, $S, "
                                    + (StringUtils.isEmpty(mapBody) ? null : ("new java.util.HashMap<String, Integer>(){{" + mapBodyBuilder.toString() + "}}")) + ", " + routeMeta.getPriority() + ", " + routeMeta.getExtra() + "))",
                            routeMeta.getPath(),
                            routeMetaCn,
                            routeTypeCn,
                            className,
                            routeMeta.getPath().toLowerCase(),
                            routeMeta.getGroup().toLowerCase());

                    routeDoc.setClassName(className.toString());
                    routeDocList.add(routeDoc);
                }

                /*
                根据分组名称创建类,分组:所有的路由
                public class ARouter$$Group$$userinfo implements IRouteGroup

                并将上面创建的loadInto方法填充进类中
                 */
                // Generate groups
                String groupFileName = NAME_OF_GROUP + groupName;
                JavaFile.builder(PACKAGE_OF_GENERATE_FILE,
                        TypeSpec.classBuilder(groupFileName)
                                .addJavadoc(WARNING_TIPS)
                                .addSuperinterface(ClassName.get(type_IRouteGroup))
                                .addModifiers(PUBLIC)
                                .addMethod(loadIntoMethodOfGroupBuilder.build())
                                .build()
                ).build().writeTo(mFiler);

                logger.info(">>> Generated group: " + groupName + "<<<");

                //将分组名称和文件名称对应存储起来,方便下面使用
                rootMap.put(groupName, groupFileName);
                docSource.put(groupName, routeDocList);
            }

            if (MapUtils.isNotEmpty(rootMap)) {
                /*
                将页面路由分组后的信息,全部放入IRouteRoot的loadInto方法中
                分组名:IRouteGroup
                 */

                // Generate root meta by group name, it must be generated before root, then I can find out the class of group.
                for (Map.Entry<String, String> entry : rootMap.entrySet()) {
                    loadIntoMethodOfRootBuilder.addStatement("routes.put($S, $T.class)",
                            entry.getKey(), ClassName.get(PACKAGE_OF_GENERATE_FILE, entry.getValue()));
                }
            }

            // Output route doc
            if (generateDoc) {
                docWriter.append(JSON.toJSONString(docSource, SerializerFeature.PrettyFormat));
                docWriter.flush();
                docWriter.close();
            }

            /*
            创建IProviderGroup的实现类
            public class ARouter$$Providers$$data implements IProviderGroup {
  @Override
  public void loadInto(Map<String, RouteMeta> providers) {
    providers.put("com.cmcc.hbb.android.phone.principal.serviceprovider.provider.DBOperatorProvider", RouteMeta.build(RouteType.PROVIDER, DataDBOpt.class, "/data/dbopt", "data", null, -1, -2147483648));
  }
}

   最后面的名称就是选项参数配置的模块名称
             */
            // Write provider into disk
            String providerMapFileName = NAME_OF_PROVIDER + SEPARATOR + moduleName;
            JavaFile.builder(PACKAGE_OF_GENERATE_FILE,
                    TypeSpec.classBuilder(providerMapFileName)
                            .addJavadoc(WARNING_TIPS)
                            .addSuperinterface(ClassName.get(type_IProviderGroup))
                            .addModifiers(PUBLIC)
                            .addMethod(loadIntoMethodOfProviderBuilder.build())
                            .build()
            ).build().writeTo(mFiler);

            logger.info(">>> Generated provider map, name is " + providerMapFileName + " <<<");

            // Write root meta into disk.
            /*
            创建一个IRouteRoot类文件
            public class ARouter$$Root$$data implements IRouteRoot
            最后面的名称就是选项参数配置的模块名称
             */
            String rootFileName = NAME_OF_ROOT + SEPARATOR + moduleName;
            JavaFile.builder(PACKAGE_OF_GENERATE_FILE,
                    TypeSpec.classBuilder(rootFileName)
                            .addJavadoc(WARNING_TIPS)
                            .addSuperinterface(ClassName.get(elementUtils.getTypeElement(ITROUTE_ROOT)))
                            .addModifiers(PUBLIC)
                            .addMethod(loadIntoMethodOfRootBuilder.build())
                            .build()
            ).build().writeTo(mFiler);

            logger.info(">>> Generated root, name is " + rootFileName + " <<<");
        }
    }

    /**
     * Recursive inject config collector.
     *
     * @param element current element.
     */
    private void injectParamCollector(Element element, Map<String, Integer> paramsType,
                                      Map<String, Autowired> injectConfig) {
        //获取Route注解的类所包含的所有元素
        for (Element field : element.getEnclosedElements()) {
            //判断是否是被Autowired注解的字段,并且该字段不是IProvider的子类
            if (field.getKind().isField()
                    && field.getAnnotation(Autowired.class) != null
                    && !types.isSubtype(field.asType(), iProvider)) {
                // It must be field, then it has annotation, but it not be provider.

                Autowired paramConfig = field.getAnnotation(Autowired.class);
                //获取注解参数名称,如果为空,直接使用字段名称
                String injectName = StringUtils.isEmpty(paramConfig.name()) ? field.getSimpleName().toString() : paramConfig.name();

                //将参数名称和参数的类型对应起来
                paramsType.put(injectName, typeUtils.typeExchange(field));

                //将参数名称和Autowired注解缓存起来,因为Autowired里面有参数名,是否必填和描述等信息
                injectConfig.put(injectName, paramConfig);
            }
        }

        // if has parent?
        //将父类中的对应Autowired注解的信息也解析出来,一起缓存
        //注意:父类截止到全路径名称以android开头的为止
        TypeMirror parent = ((TypeElement) element).getSuperclass();
        if (parent instanceof DeclaredType) {
            Element parentElement = ((DeclaredType) parent).asElement();
            if (parentElement instanceof TypeElement
                    && !((TypeElement) parentElement).getQualifiedName().toString().startsWith("android")) {
                injectParamCollector(parentElement, paramsType, injectConfig);
            }
        }
    }

    /**
     * Extra doc info from route meta
     *
     * @param routeMeta meta
     * @return doc
     */
    private RouteDoc extractDocInfo(RouteMeta routeMeta) {
        RouteDoc routeDoc = new RouteDoc();
        routeDoc.setGroup(routeMeta.getGroup());
        routeDoc.setPath(routeMeta.getPath());
        routeDoc.setDescription(routeMeta.getName());
        routeDoc.setType(routeMeta.getType().name().toLowerCase());
        routeDoc.setMark(routeMeta.getExtra());

        return routeDoc;
    }

    /**
     * Sort metas in group.
     * 分解解析出的路由元数据信息,进行分组
     *
     * @param routeMete metas.
     */
    private void categories(RouteMeta routeMete) {
        //验证路由地址是否合法
        if (routeVerify(routeMete)) {
            logger.info(">>> Start categories, group = " + routeMete.getGroup() + ", path = " + routeMete.getPath() + " <<<");

            Set<RouteMeta> routeMetas = groupMap.get(routeMete.getGroup());
            if (CollectionUtils.isEmpty(routeMetas)) {
                Set<RouteMeta> routeMetaSet = new TreeSet<>(new Comparator<RouteMeta>() {
                    @Override
                    public int compare(RouteMeta r1, RouteMeta r2) {
                        //根据路由地址进行排序
                        try {
                            return r1.getPath().compareTo(r2.getPath());
                        } catch (NullPointerException npe) {
                            logger.error(npe.getMessage());
                            return 0;
                        }
                    }
                });
                routeMetaSet.add(routeMete);
                groupMap.put(routeMete.getGroup(), routeMetaSet);
            } else {
                routeMetas.add(routeMete);
            }
        } else {
            logger.warning(">>> Route meta verify error, group is " + routeMete.getGroup() + " <<<");
        }
    }

    /**
     * Verify the route meta
     * 验证路由元数据,必须也"/"开头,要么在注解上配置group值,要么包含两个"/",这两个"/"之间的内容为group
     *
     * @param meta raw meta
     */
    private boolean routeVerify(RouteMeta meta) {
        //路由地址
        String path = meta.getPath();

        //路由地址必须以"/"开头
        if (StringUtils.isEmpty(path) || !path.startsWith("/")) {   // The path must be start with '/' and not empty!
            return false;
        }

        //如果@Route路由的group注解配置为空,则取前面两个"/"之间的内容作为路由group
        if (StringUtils.isEmpty(meta.getGroup())) { // Use default group(the first word in path)
            try {
                String defaultGroup = path.substring(1, path.indexOf("/", 1));
                if (StringUtils.isEmpty(defaultGroup)) {
                    return false;
                }

                meta.setGroup(defaultGroup);
                return true;
            } catch (Exception e) {
                logger.error("Failed to extract default group! " + e.getMessage());
                return false;
            }
        }

        return true;
    }
}
