package com.ouroboros.common.app.util;

import java.util.concurrent.atomic.AtomicReference;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.stereotype.Component;

@Component
public class SpringContextUtil implements ApplicationContextAware, DisposableBean {

    private static final AtomicReference<ApplicationContext> applicationContext = new AtomicReference<>();
    private ApplicationContext owner;

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        owner = applicationContext;
        SpringContextUtil.applicationContext.compareAndSet(null, applicationContext);
    }

    @Override
    public void destroy() {
        applicationContext.compareAndSet(owner, null);
    }

    public static ApplicationContext getApplicationContext() {
        return applicationContext.get();
    }

    private static ApplicationContext requireApplicationContext() {
        ApplicationContext current = applicationContext.get();
        if (current == null) {
            throw new IllegalStateException("Spring application context is unavailable");
        }
        return current;
    }

    public static Object getBean(String name) {
        return requireApplicationContext().getBean(name);
    }

    public static <T> T getBean(Class<T> clazz) {
        return requireApplicationContext().getBean(clazz);
    }

    public static <T> T getBean(String name, Class<T> clazz) {
        return requireApplicationContext().getBean(name, clazz);
    }
}
