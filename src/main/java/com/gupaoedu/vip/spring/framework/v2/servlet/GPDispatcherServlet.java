package com.gupaoedu.vip.spring.framework.v2.servlet;

import com.gupaoedu.vip.spring.framework.annotation.*;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.URL;
import java.util.*;

/**
 * @author 孟祥骞
 */
public class GPDispatcherServlet extends HttpServlet {
    private Properties contextConfig = new Properties();
    private List<String> classNames = new ArrayList<String>();
    private Map<String, Object> ioc = new HashMap<String, Object>();
    private Map<String, Method> handlerMapping = new HashMap<String, Method>();

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        doPost(req, resp);
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        try {
            doDispatcher(req,resp);
        } catch (Exception e) {
            e.printStackTrace();
            resp.getWriter().write("500 Exception,Detail : " + Arrays.toString(e.getStackTrace()));
        }



    }

    private void doDispatcher(HttpServletRequest req, HttpServletResponse resp) throws Exception {
        String uri = req.getRequestURI();
        System.out.println("uri：" + uri);
        String contextPath = req.getContextPath();
        System.out.println("contextPath：" + contextPath);
        uri = uri.replaceAll(contextPath, "").replaceAll("/+", "/");

        if (!handlerMapping.containsKey(uri)) {
            resp.getWriter().write("404 not found!");
            return;
        }
        //请求的参数
        Map<String,String[]> params = req.getParameterMap();

        Method method = handlerMapping.get(uri);
        //形参列表
        Class<?>[] parameterTypes = method.getParameterTypes();
        Object[] paramsValues = new Object[parameterTypes.length];

        for (int i = 0; i < parameterTypes.length; i++) {
            Class<?> parameterType = parameterTypes[i];
            if (parameterType == HttpServletRequest.class) {
                paramsValues[i] = req;
            } else if (parameterType == HttpServletResponse.class) {
                paramsValues[i] = resp;
            } else if (parameterType == String.class||parameterType == Integer.class) {

                Annotation[][] parameterAnnotations = method.getParameterAnnotations();
//                for (int j = 0; j < parameterAnnotations.length; j++) {
                    for (Annotation a : parameterAnnotations[i]) {
                        if (a instanceof GPRequestParam) {
                            String paramName =  ((GPRequestParam) a).value();
                            if (!"".equals(paramName.trim())) {
                                String value = Arrays.toString(params.get(paramName))
                                        .replaceAll("\\[|\\]","")
                                        .replaceAll("\\s+",",");
                                paramsValues[i] = value;
                            }
                        }
                    }
//                }
            }

        }
        String beanName = toLowerFirstCase(method.getDeclaringClass().getSimpleName());
        method.invoke(ioc.get(beanName), paramsValues);
    }

    @Override
    public void init(ServletConfig config) throws ServletException {
        //加载配置文件
        doLoadingConfig(config.getInitParameter("contextConfigLocation"));

        //扫描相关类
        doScanner(contextConfig.getProperty("scanPackage"));

        //初始化IOC容器，将扫描的相关类实例化并保存到IOC容器
        doInstance();

        //完成依赖注入
        doAutowired();

        //初始化HandlerMapping
        doHandlerMapping();
    }

    private void doHandlerMapping() {
        if (ioc.isEmpty()) {
            return;
        }

        Class<?> clazz;
        for (Map.Entry<String, Object> entry : ioc.entrySet()) {
            clazz = entry.getValue().getClass();

            if (!clazz.isAnnotationPresent(GPController.class)) {
                continue;
            }
            String baseUrl = "";
            if (clazz.isAnnotationPresent(GPRequestMapping.class)) {
                baseUrl = clazz.getAnnotation(GPRequestMapping.class).value();
            }
            for (Method method : clazz.getMethods()) {
                if (!method.isAnnotationPresent(GPRequestMapping.class)) {
                    continue;
                }
                //提取每个方法上面配置的url
                GPRequestMapping methodAnnotation = method.getAnnotation(GPRequestMapping.class);
                String url = (baseUrl + "/" + methodAnnotation.value()).replaceAll("/+", "/");
                handlerMapping.put(url, method);
                System.out.println("Mapped : " + url + "," + method);
            }
        }
    }

    private void doAutowired() {
        if (ioc.isEmpty()) {
            return;
        }
        String fieldName;
        Class<?> clazz;
        for (Map.Entry<String, Object> entry : ioc.entrySet()) {
            clazz = entry.getValue().getClass();
            for (Field field : clazz.getDeclaredFields()) {
                if (field.isAnnotationPresent(GPAutowired.class)) {
                    fieldName = field.getAnnotation(GPAutowired.class).value();
                    if ("".equals(fieldName)) {
                        fieldName = field.getName();
                    }
                    field.setAccessible(true);
                    try {

                        field.set(entry.getValue(), ioc.get(fieldName));
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            }

        }
    }

    private void doInstance() {
        if (classNames.isEmpty()) {
            return;
        }

        try {
            for (String className : classNames) {
                Class<?> clazz = Class.forName(className);
                String beanName;
                if (clazz.isAnnotationPresent(GPController.class)) {
                    beanName = toLowerFirstCase(clazz.getSimpleName());
                    Object instance = clazz.newInstance();
                    ioc.put(beanName, instance);
                } else if (clazz.isAnnotationPresent(GPService.class)) {
                    //在多个包下出现相同的类名，只能寄几（自己）起一个全局唯一的名字
                    //自定义命名
                    beanName = clazz.getAnnotation(GPService.class).value();
                    if ("".equals(beanName.trim())) {
                        beanName = toLowerFirstCase(clazz.getSimpleName());
                    }

                    Object instance = clazz.newInstance();
                    ioc.put(beanName, instance);

                    //如果是接口
                    //判断有多少个实现类，如果只有一个，默认就选择这个实现类
                    //如果有多个，只能抛异常
                    for (Class<?> i : clazz.getInterfaces()) {
                        if (ioc.containsKey(i.getSimpleName())) {
                            throw new Exception("The " + i.getName() + " is exists!");
                        }
                        ioc.put(i.getSimpleName(), instance);
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

    }

    private String toLowerFirstCase(String beanName) {
        char[] chars = beanName.toCharArray();
        chars[0] += 32;
        return String.valueOf(chars);
    }

    private void doScanner(String scanPackage) {
        String replace = scanPackage.replaceAll("\\.", "/");
        URL url = this.getClass().getClassLoader().getResource("/" + replace);
        File scanFile = new File(url.getFile());
        File[] files = scanFile.listFiles();
        for (File file : files) {
            if (file.isDirectory()) {
                doScanner(scanPackage + "." + file.getName());
            } else {
                if (!file.getName().endsWith(".class")) {
                    continue;
                }
                String className = scanPackage + "." + file.getName().replace(".class", "");
                classNames.add(className);
            }
        }

    }

    private void doLoadingConfig(String contextConfigLocation) {
        InputStream inputStream = this.getClass().getClassLoader().getResourceAsStream(contextConfigLocation);
        try {
            contextConfig.load(inputStream);
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            if (inputStream != null) {
                try {
                    inputStream.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }
}
