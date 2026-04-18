package com.renlq.bookrecommendsystem.config;

import com.renlq.bookrecommendsystem.interceptor.AdminInterceptor;
import com.renlq.bookrecommendsystem.interceptor.LoginInterceptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Autowired
    private LoginInterceptor loginInterceptor;

    @Autowired
    private AdminInterceptor adminInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {

        // 登录拦截（普通用户也要登录）
        registry.addInterceptor(loginInterceptor)
                .addPathPatterns("/borrow/**", "/user/**", "/admin/**", "/recommend", "/rating", "/book/**", "/home", "/analysis")
                .excludePathPatterns("/login", "/register", "/css/**", "/js/**");

        // 管理员拦截（只限制 admin）
        registry.addInterceptor(adminInterceptor)
                .addPathPatterns("/admin/**");
    }
}
