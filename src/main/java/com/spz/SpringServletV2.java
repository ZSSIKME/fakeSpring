package com.spz;

import com.spz.annotatoons.*;

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
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URL;
import java.util.*;

public class SpringServletV2 extends HttpServlet {
    //保存application.properties配置文件中的内容
    private Properties contextConfig = new Properties();
    //保存扫描的所有类名
    private List<String>classNames = new ArrayList<String>();
    //IOC容器
    private Map<String,Object> ioc = new HashMap<String,Object>();
    //保存url和方法对应的关系
    private Map<String, Method> handlerMapping = new HashMap<>();

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) {
        try {
            doPost(req, resp);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp){
        try {
            doDispatch(req,resp);
        } catch (InvocationTargetException e) {
            e.printStackTrace();
        } catch (IllegalAccessException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void doDispatch(HttpServletRequest req, HttpServletResponse resp) throws InvocationTargetException, IllegalAccessException, IOException {
        //获取url
        String url = req.getRequestURI();
        String contextPath = req.getContextPath();
        url = url.replaceAll(contextPath, "").replaceAll("/+", "");
        //判断handlerMapping中是否有对应的url
        if(!handlerMapping.containsKey(url)){
            resp.getWriter().write("404 not found");
            return;
        }
        //对数据参数进行动态设置
        Method method = this.handlerMapping.get(url);
        //执行method方法
        Map<String,String[]> params = req.getParameterMap();
        Class<?>[] parameterTypes = method.getParameterTypes();
        Object[] paramValues = new Object[parameterTypes.length];
        //根据参数动态设置
        for (int i = 0; i < parameterTypes.length; i++) {
            Class<?> parameterType = parameterTypes[i];

            if(parameterType == HttpServletRequest.class){
                paramValues[i] = req;
            }else if(parameterType == HttpServletResponse.class){
                paramValues[i] = resp;
            } else if (parameterType == String.class) {
                //提取方法中加注解的参数
                Annotation[][] pa = method.getParameterAnnotations();
                for (int j = 0; j < pa.length; j++) {
                    for (Annotation a : pa[i]) {
                        if(a instanceof SPZRequestParam){
                            String value = ((SPZRequestParam) a).value().replaceAll("\\[|\\]","").replaceAll("\\s",",");
                            paramValues[i] = value;
                        }
                    }
                }

            }
        }

        //获取beanName
        String beanName = toLowerFirstCase(method.getDeclaringClass().getSimpleName());
        method.invoke(ioc.get(beanName),paramValues);
    }

    @Override
    public void init(ServletConfig config) throws ServletException {
        //加载配置文件
        doLoadConfig(config.getInitParameter("contextConfigLocation"));
        //扫描相关的类
        doScanner(contextConfig.getProperty("scanPackage"));
        //初始化扫描到的类并且将他们放入ioc容器中
        doInstance();
        //完成依赖注入
        doAutowired();
        //初始化handlerMapping
        initHandlerMapping();
        System.out.println("GPSpring framework is init");
    }

    private void initHandlerMapping() {

        if(ioc.isEmpty())return;
        for (Map.Entry<String, Object> entry : ioc.entrySet()) {
            Class<? extends Map.Entry> clazz = entry.getClass();
            if(!clazz.isAnnotationPresent(SPZController.class)){
                continue;
            }
            //获取类上的baseurl
            String baseUrl = "";
            if(clazz.isAnnotationPresent(SPZRequestMapping.class)){
                SPZRequestMapping requestMapping = clazz.getAnnotation(SPZRequestMapping.class);
                baseUrl = requestMapping.value();
            }
            //获取方法上的url
            for (Method method : clazz.getMethods()) {
                if(!method.isAnnotationPresent(SPZRequestMapping.class)){
                    continue;
                }
                SPZRequestMapping mapping = method.getAnnotation(SPZRequestMapping.class);
                String url = ("/"+baseUrl+mapping.value()).replaceAll("/+","/");
                handlerMapping.put(url,method);
                System.out.println("Mapped:"+url+","+method);
            }
        }

    }

    private void doAutowired() {
        for (Map.Entry<String, Object> entry : ioc.entrySet()) {
            Field[] fields = entry.getValue().getClass().getDeclaredFields();
            for (Field field : fields) {
                if(!field.isAnnotationPresent(SPZAutowired.class)){continue;}
                SPZAutowired autowired = field.getAnnotation(SPZAutowired.class);
                String beanName = autowired.value().trim();
                if("".equals(beanName)){
                    beanName = field.getType().getName();
                }
                //如果是public以外的类型只要加了AutoWired都要进行赋值
                field.setAccessible(true);
                try {
                    field.set(entry.getValue(),ioc.get(beanName));
                } catch (IllegalAccessException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    private void doInstance() {
        //如果ioc容器为空直接返回
        if(ioc.isEmpty()){
            return;
        }
        try{
            for (String className : classNames) {
                Class<?> clazz = Class.forName(className);
                //加了注解才能实例化
                if(clazz.isAnnotationPresent(SPZController.class)){
                    Object instance = clazz.newInstance();
                    //放进ioc容器中
                    SPZController controller = clazz.getAnnotation(SPZController.class);
                    String beanName = controller.value();
                    if("".equals(beanName.trim())){
                        beanName = toLowerFirstCase(clazz.getName());
                    }
                    ioc.put(beanName,instance);
                }else if(clazz.isAnnotationPresent(SPZService.class)){
                    Object instance = clazz.newInstance();
                    SPZService service = clazz.getAnnotation(SPZService.class);
                    String beanName = service.value();
                    if("".equals(beanName.trim())){
                        beanName = toLowerFirstCase(clazz.getName());
                    }
                    ioc.put(beanName,instance);
                }else {
                    continue;
                }
            }
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        } catch (InstantiationException e) {
            e.printStackTrace();
        } catch (IllegalAccessException e) {
            throw new RuntimeException(e);
        }

    }

    //将类的首字母小写
    private String toLowerFirstCase(String className) {
        char[] chars = className.toCharArray();

        //首字母小写将第一个字符加32
        chars[0] +=32;
        return String.valueOf(chars);
    }

    //扫描类
    private void doScanner(String scanPackage) {
        //转换为文件路径
        URL url = this.getClass().getClassLoader().getResource("/" + scanPackage.replaceAll("\\.", "/"));
        File classPath = new File(url.getFile());
        File[] files = classPath.listFiles();
        for (File file : files) {
            if(file.isDirectory()){
                doScanner(scanPackage+"."+file.getName());
            }else {
                if(!file.getName().endsWith(".class")){continue;}
                String className = file.getName().replaceAll(".class", "");
                classNames.add(className);
            }
        }
    }

    //加载配置文件
    private void doLoadConfig(String contextConfigLocation) {
        /**
         * 直接通过类路径找到spring主配置文件所在的路径
         * 并且将其读取出来放到properties对象中
         * 相当于将scanpackage = com.pupaoedu.demo保存到了内存中
         */
        InputStream fis = this.getClass().getClassLoader().getResourceAsStream(contextConfigLocation);
        try{
            contextConfig.load(fis);
        }catch (Exception e){
            e.printStackTrace();
        }finally {
            try {
                fis.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }


    }
}
