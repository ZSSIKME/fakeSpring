package com.spz;

import com.spz.annotatoons.SPZController;
import com.spz.annotatoons.SPZRequestMapping;
import com.spz.annotatoons.SPZService;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

public class SPZServlet extends HttpServlet {

    private Map<String, Object> mapping = new HashMap<>();


    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        this.doPost(req, resp);
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        try {
            this.doDispatch(req, resp);
        } catch (InvocationTargetException e) {
            e.printStackTrace();
        } catch (IllegalAccessException e) {
            e.printStackTrace();
        }
    }

    private void doDispatch(HttpServletRequest req, HttpServletResponse resp) throws IOException, InvocationTargetException, IllegalAccessException {
        String url = req.getRequestURI();
        String contextPath = req.getContextPath();
        url = url.replace(contextPath, "").replaceAll("/+", "/");
        if (!this.mapping.containsKey(url)) {
            resp.getWriter().write("404 Not Found!");
            return;
        }
        Method method = (Method) this.mapping.get(url);
        Map<String, String[]> params = req.getParameterMap();
        method.invoke(this.mapping.get(method.getDeclaringClass().getName()), new Object[]{
                req, resp, params.get("name")[0]
        });

    }


    public static void main(String[] args) throws ClassNotFoundException {
        Class<?> string = Class.forName("com.spz.annotatoons.SPZController");
        String name = string.getName();
        System.out.println(name);
    }

    @Override
    public void init(ServletConfig config) throws ServletException {
        try {
            InputStream is = null;
            Properties configContext = new Properties();
            is = this.getClass().getClassLoader().getResourceAsStream(config.getInitParameter("contextConfigLocation"));
            configContext.load(is);
            String scanPackage = configContext.getProperty("scanPackage");
            doScaner(scanPackage);
            for (String className : mapping.keySet()) {
                if(!className.contains(".")){continue;}
                Class<?> clazz = Class.forName(className);
                if(clazz.isAnnotationPresent(SPZController.class)){
                    mapping.put(className,clazz.newInstance());
                    String baseUrl = "";
                    if(clazz.isAnnotationPresent(SPZRequestMapping.class)){
                        SPZRequestMapping requestMapping = clazz.getAnnotation(SPZRequestMapping.class);
                        baseUrl = requestMapping.value();
                    }
                    Method[] methods = clazz.getMethods();
                    for (Method method : methods) {
                        if(!method.isAnnotationPresent(SPZRequestMapping.class)){
                            continue;
                        }
                        SPZRequestMapping requestMapping = method.getAnnotation(SPZRequestMapping.class);
                        String url = (baseUrl+"/"+requestMapping.value()).replaceAll("/+","/");
                        mapping.put(url,method);
                        System.out.println("Mapped"+url+","+method);
                    }
                } else if (clazz.isAnnotationPresent(SPZService.class)) {
                    SPZService service = clazz.getAnnotation(SPZService.class);
                    String beanName = service.value();
                    if("".equals(beanName)){
                        beanName = clazz.getName();
                    }
                    Object instance = clazz.newInstance();
                    mapping.put(beanName,instance);
                    for (Class<?> i : clazz.getInterfaces()) {
                        mapping.put(i.getName(),instance);
                    }
                }else {
                    continue;
                }
            }


            for (Object object : mapping.values()) {
                if(object == null) continue;
                Class<?> clazz = object.getClass();
            }




        } catch (IOException | ClassNotFoundException e) {
            e.printStackTrace();
        } catch (IllegalAccessException e) {
            e.printStackTrace();
        } catch (InstantiationException e) {
            e.printStackTrace();
        }
    }

    private void doScaner(String scanPackage) {
        URL url = this.getClass().getClassLoader().getResource("/" + scanPackage.replaceAll("\\.", "/"));
        File classDir = new File(url.getFile());
        for (File file : classDir.listFiles()) {

            if(file.isDirectory()){
                doScaner(scanPackage+"."+file.getName());
            }else {
                if(!file.getName().endsWith(".class")){
                    continue;
                }
                String clazzName = scanPackage + "." + file.getName().replace(".class", "");
                mapping.put(clazzName,null);
            }
        }
    }
}
