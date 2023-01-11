package com.spz;

import com.spz.annotatoons.SPZController;
import com.spz.annotatoons.SPZService;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
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
//        initHandlerMapping();
        System.out.println("GPSpring framework is init");
    }

    private void doAutowired() {
        for (Map.Entry<String, Object> entry : ioc.entrySet()) {
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
