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
            response.sendRedirect("/login");
            return false;
        }

        return true;
    }
}
