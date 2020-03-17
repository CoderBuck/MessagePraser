package com.github.coderbuck.messageparser.compiler;

import com.annimon.stream.Stream;
import com.github.coderbuck.messageparser.AbstractParser;
import com.github.coderbuck.messageparser.EmMsg;
import com.github.coderbuck.messageparser.GsonUtils;
import com.github.coderbuck.messageparser.annotation.Body;
import com.github.coderbuck.messageparser.annotation.Message;
import com.github.coderbuck.messageparser.bean.MessageBean;
import com.google.auto.service.AutoService;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.FieldSpec;
import com.squareup.javapoet.JavaFile;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.ParameterizedTypeName;
import com.squareup.javapoet.TypeSpec;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.Filer;
import javax.annotation.processing.Messager;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.Processor;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.util.Elements;
import javax.tools.Diagnostic;


@AutoService(Processor.class)
public class MessageParserProcessor extends AbstractProcessor {

    private Messager messager;
    private Elements elementUtils;


    private HashMap<EmMsg, Class> mMap = new HashMap();
    private List<ItemBean> mItems = new ArrayList<>();

    @Override
    public Set<String> getSupportedAnnotationTypes() {
        LinkedHashSet<String> set = new LinkedHashSet<>();
        set.add(Message.class.getCanonicalName());
        return set;
    }

    @Override
    public SourceVersion getSupportedSourceVersion() {
        return SourceVersion.latestSupported();
    }

    @Override
    public synchronized void init(ProcessingEnvironment processingEnv) {
        super.init(processingEnv);
        messager = processingEnv.getMessager();
        elementUtils = processingEnv.getElementUtils();
    }

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        mMap.clear();
        mItems.clear();
        Set<? extends Element> messageElements = roundEnv.getElementsAnnotatedWith(Message.class);
        log("elements size = " + messageElements.size());

        messageElements.forEach(element -> {

            TypeElement typeElement = (TypeElement) element;
            String clazzSimpleName = typeElement.getSimpleName().toString();
            String clazzFullName = typeElement.getQualifiedName().toString();
            String pkgName = elementUtils.getPackageOf(typeElement).toString();

            EmMsg[] emMsgs = element.getAnnotation(Message.class).value();
            for (EmMsg emMsg : emMsgs) {
                Class clazz = null;
                try {
                    clazz = emMsg.getClass().getField(emMsg.name()).getAnnotation(Body.class).value();
                    String bodyClassFullName = clazz.getCanonicalName();
                    String pg = clazz.getPackage().toString();
                    String msgName = emMsg.name();

                    ItemBean itemBean = new ItemBean();
                    itemBean.pkgName = pkgName;
                    itemBean.fullClassName = clazzFullName;
                    itemBean.simpleClassName = clazzSimpleName;
                    itemBean.msgName = msgName;
                    itemBean.bodyClassFullName = bodyClassFullName;
                    itemBean.bodyPkgName = clazz.getPackage().getName();
                    itemBean.bodySimpleName = clazz.getSimpleName();
                    mItems.add(itemBean);
                    mMap.put(emMsg, clazz);
                } catch (NoSuchFieldException e) {
                    error(element, e.getMessage());
                }
            }
        });

        Stream.of(mItems)
                .groupBy(itemBean -> itemBean.fullClassName)
                .forEach(entry -> {
                    List<ItemBean> items = entry.getValue();
                    String clazzFullName = entry.getKey();
                    String clazzSimpleName = items.get(0).simpleClassName;
                    String pkgName = items.get(0).pkgName;

                    makeInterceptor(items, clazzSimpleName);
                    makeHandler(items, clazzSimpleName);
                    makeParser(items, clazzSimpleName);



                });

        return false;
    }

    private void makeHandler(List<ItemBean> items, String clazzSimpleName) {
        TypeSpec.Builder handlerTypeSpec = TypeSpec.classBuilder(clazzSimpleName + "Handler")
                .addModifiers(Modifier.PUBLIC, Modifier.ABSTRACT)
                .addMethod(MethodSpec.methodBuilder("register")
                        .addModifiers(Modifier.PUBLIC)
                        .returns(void.class)
                        .addStatement(clazzSimpleName + "Parser" + ".register(this)")
                        .build()
                )
                .addMethod(MethodSpec.methodBuilder("unregister")
                        .addModifiers(Modifier.PUBLIC)
                        .returns(void.class)
                        .addStatement(clazzSimpleName + "Parser" + ".unregister(this)")
                        .build()
                );

        for (ItemBean item : items) {
            MethodSpec.Builder methodBuilder = MethodSpec.methodBuilder(item.msgName)
                    .addModifiers(Modifier.PUBLIC, Modifier.ABSTRACT)
                    .returns(void.class)
                    .addParameter(ClassName.bestGuess(item.bodyClassFullName), "body");
            handlerTypeSpec.addMethod(methodBuilder.build());
        }

        TypeSpec.Builder simpleTypeSpec = TypeSpec.classBuilder("Simple")
                .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
                .superclass(ClassName.get("kd.message.parser", clazzSimpleName + "Handler"));

        for (ItemBean item : items) {
            simpleTypeSpec.addMethod(
                    MethodSpec.methodBuilder(item.msgName)
                            .addModifiers(Modifier.PUBLIC)
                            .returns(void.class)
                            .addParameter(ClassName.bestGuess(item.bodyClassFullName), "body")
                            .build()
            );
        }

        handlerTypeSpec.addType(simpleTypeSpec.build());


        JavaFile javaFile = JavaFile.builder("kd.message.parser", handlerTypeSpec.build()).
                build();

        Filer filer = processingEnv.getFiler();
        try {
            javaFile.writeTo(filer);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void makeParser(List<ItemBean> items, String clazzSimpleName) {
        ClassName UI_THREAD_CLASS_NAME = ClassName.bestGuess("android.os.Handler");
        ClassName LOOPER_CLASS_NAME = ClassName.bestGuess("android.os.Looper");
        ClassName INTERCEPTOR_CLASS_NAME = ClassName.get("kd.message.parser", clazzSimpleName + "Interceptor");
        ClassName HANDLER_CLASS_NAME = ClassName.get("kd.message.parser", clazzSimpleName + "Handler");
        ClassName LIST_CLASS_NAME = ClassName.get(List.class);
        ParameterizedTypeName par = ParameterizedTypeName.get(LIST_CLASS_NAME, HANDLER_CLASS_NAME);

        FieldSpec uiThreadFieldSpec = FieldSpec.builder(UI_THREAD_CLASS_NAME, "uiThread")
                .addModifiers(Modifier.PRIVATE,Modifier.STATIC)
                .initializer("new $T($T.getMainLooper())", UI_THREAD_CLASS_NAME,LOOPER_CLASS_NAME)
                .build();

        FieldSpec interceptorfieldSpec = FieldSpec.builder(INTERCEPTOR_CLASS_NAME, "interceptor")
                .addModifiers(Modifier.PRIVATE,Modifier.STATIC)
                .build();

        FieldSpec handlerFieldSpec = FieldSpec.builder(par, "handlers")
                .addModifiers(Modifier.PRIVATE,Modifier.STATIC)
                .initializer("new $T<>()", CopyOnWriteArrayList.class)
                .build();

        MethodSpec.Builder initMethodSpec = MethodSpec.methodBuilder("init")
                .addAnnotation(Override.class)
                .addModifiers(Modifier.PROTECTED)
                .returns(void.class);

        for (ItemBean item : items) {
            initMethodSpec.addStatement("addMsg($S, this::$L)", item.msgName, item.msgName);
        }

        MethodSpec registerHandlerMethod = MethodSpec.methodBuilder("register")
                .addModifiers(Modifier.PUBLIC,Modifier.STATIC)
                .returns(void.class)
                .addParameter(HANDLER_CLASS_NAME, "handler")
                .addStatement("handlers.add(handler)")
                .build();

        MethodSpec unregisterHandlerMethod = MethodSpec.methodBuilder("unregister")
                .addModifiers(Modifier.PUBLIC,Modifier.STATIC)
                .returns(void.class)
                .addParameter(HANDLER_CLASS_NAME, "handler")
                .addStatement("handlers.remove(handler)")
                .build();

        MethodSpec registerInterceptorMethod = MethodSpec.methodBuilder("register")
                .addModifiers(Modifier.PUBLIC,Modifier.STATIC)
                .returns(void.class)
                .addParameter(INTERCEPTOR_CLASS_NAME, "in")
                .addStatement("interceptor = in")
                .build();

        MethodSpec unregisterInterceptorMethod = MethodSpec.methodBuilder("unregister")
                .addModifiers(Modifier.PUBLIC,Modifier.STATIC)
                .returns(void.class)
                .addParameter(INTERCEPTOR_CLASS_NAME, "in")
                .addStatement("interceptor = null")
                .build();

        TypeSpec.Builder typeSpec = TypeSpec.classBuilder(clazzSimpleName + "Parser")
                .addModifiers(Modifier.PUBLIC, Modifier.FINAL)
                .superclass(AbstractParser.class)
                .addField(uiThreadFieldSpec)
                .addField(interceptorfieldSpec)
                .addField(handlerFieldSpec)
                .addMethod(initMethodSpec.build())
                .addMethod(registerInterceptorMethod)
                .addMethod(unregisterInterceptorMethod)
                .addMethod(registerHandlerMethod)
                .addMethod(unregisterHandlerMethod);


        for (ItemBean item : items) {
            ClassName bodyClassName = ClassName.get(item.bodyPkgName, item.bodySimpleName);
            MethodSpec.Builder method = MethodSpec.methodBuilder(item.msgName)
                    .addModifiers(Modifier.PUBLIC)
                    .returns(void.class)
                    .addParameter(MessageBean.class, "messageBean")
                    .addStatement("$T body = $T.fromJson(messageBean.getBody(), $T.class)", bodyClassName, GsonUtils.class, bodyClassName)
                    .addCode("uiThread.post(() -> {\n")
                    .addCode("  if (interceptor != null && interceptor.$L(body)) return;\n",item.msgName)
                    .addCode("  for ($T handler : handlers){\n", HANDLER_CLASS_NAME)
                    .addCode("    handler.$L(body);\n", item.msgName)
                    .addCode("  }")
                    .addCode("});\n")
                    ;

            typeSpec.addMethod(method.build());
        }


        JavaFile javaFile2 = JavaFile.builder("kd.message.parser", typeSpec.build()).
                build();

        Filer filer2 = processingEnv.getFiler();
        try {
            javaFile2.writeTo(filer2);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void makeInterceptor(List<ItemBean> items, String clazzSimpleName) {
        TypeSpec.Builder interceptorTypeSpec = TypeSpec.classBuilder(clazzSimpleName + "Interceptor")
                .addModifiers(Modifier.PUBLIC, Modifier.ABSTRACT)
                .addMethod(MethodSpec.methodBuilder("register")
                        .addModifiers(Modifier.PUBLIC)
                        .returns(void.class)
                        .addStatement(clazzSimpleName + "Parser" + ".register(this)")
                        .build()
                )
                .addMethod(MethodSpec.methodBuilder("unregister")
                        .addModifiers(Modifier.PUBLIC)
                        .returns(void.class)
                        .addStatement(clazzSimpleName + "Parser" + ".unregister(this)")
                        .build()
                );

        for (ItemBean item : items) {
            MethodSpec.Builder methodBuilder = MethodSpec.methodBuilder(item.msgName)
                    .addModifiers(Modifier.PUBLIC, Modifier.ABSTRACT)
                    .returns(boolean.class)
                    .addParameter(ClassName.bestGuess(item.bodyClassFullName), "body");
            interceptorTypeSpec.addMethod(methodBuilder.build());
        }

        TypeSpec.Builder simpleTypeSpec = TypeSpec.classBuilder("Simple")
                .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
                .superclass(ClassName.get("kd.message.parser", clazzSimpleName + "Interceptor"));

        for (ItemBean item : items) {
            simpleTypeSpec.addMethod(
                    MethodSpec.methodBuilder(item.msgName)
                            .addModifiers(Modifier.PUBLIC)
                            .returns(boolean.class)
                            .addParameter(ClassName.bestGuess(item.bodyClassFullName), "body")
                            .addStatement("return false")
                            .build()
            );
        }

        interceptorTypeSpec.addType(simpleTypeSpec.build());


        JavaFile javaFile = JavaFile.builder("kd.message.parser", interceptorTypeSpec.build()).
                build();

        Filer filer = processingEnv.getFiler();
        try {
            javaFile.writeTo(filer);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void log(String msg) {
        messager.printMessage(Diagnostic.Kind.NOTE, msg);
    }

    private void error(Element element, String message, Object... args) {
        if (args.length > 0) {
            message = String.format(message, args);
        }
        processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR, message, element);
    }
}
