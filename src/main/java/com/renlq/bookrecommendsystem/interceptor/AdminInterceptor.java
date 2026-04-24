package com.renlq.bookrecommendsystem.interceptor;

import com.renlq.bookrecommendsystem.entity.User;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

@Component
public class AdminInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(HttpServletRequest request,
                             HttpServletResponse response,
                             Object handler) throws Exception {

        HttpSession session = request.getSession();
        User user = (User) session.getAttribute("user");

        if (user == null || !"administer".equals(user.getRole())) {
            response.setContentType("text/html;charset=UTF-8");
            if (user == null) {
                response.getWriter().write("<!DOCTYPE html><html><head><meta charset=\"UTF-8\"><title>提示</title></head><body><script type=\"text/javascript\">alert('请先登录！');window.location.href='/login';</script></body></html>");
            } else {
                response.getWriter().write("<!DOCTYPE html><html><head><meta charset=\"UTF-8\"><title>提示</title></head><body><script type=\"text/javascript\">alert('您没有管理员权限！');window.location.href='/home';</script></body></html>");
            }
            response.getWriter().flush();
            return false;
        }

        return true;
    }
}
