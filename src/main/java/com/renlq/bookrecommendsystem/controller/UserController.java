package com.renlq.bookrecommendsystem.controller;

import com.renlq.bookrecommendsystem.entity.User;
import com.renlq.bookrecommendsystem.repository.UserRepository;
import com.renlq.bookrecommendsystem.repository.BookRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import java.util.ArrayList;
import java.util.List;

import javax.servlet.http.HttpSession;

@Controller
@RequestMapping("/user")
@RequiredArgsConstructor
public class UserController {

    private final UserRepository userRepository;
    private final BookRepository bookRepository;


    // 用户中心页面
    @GetMapping
    public String userCenter(HttpSession session, Model model){

        User user = (User) session.getAttribute("user");

        if(user == null){
            return "redirect:/login";
        }

        model.addAttribute("user", user);
        // 获取所有唯一的分类（用于搜索）
        model.addAttribute("allCategories", bookRepository.findAllDistinctCategories());
        // 获取出现次数最多的前8个分类（默认显示）
        List<Object[]> topCategoriesWithCount = bookRepository.findTop8CategoriesByCount();
        List<String> topCategories = new ArrayList<>();
        // 只取前8个分类
        int limit = Math.min(8, topCategoriesWithCount.size());
        for (int i = 0; i < limit; i++) {
            topCategories.add((String) topCategoriesWithCount.get(i)[0]);
        }
        model.addAttribute("categories", topCategories);

        return "user";
    }

    @PostMapping("/changeEmail")
    public String changeEmail(String email, HttpSession session){

        User user = (User) session.getAttribute("user");
        if(user == null){
            return "redirect:/login";
        }

        // 简单校验（不能为空）
        if(email == null || email.trim().isEmpty()){
            return "redirect:/user?msg=emailEmpty";
        }
        if(userRepository.findByEmail(email) != null){
            return "redirect:/user?msg=emailExist";
        }
        // 更新邮箱
        user.setEmail(email);
        userRepository.save(user);

        // 更新 session
        session.setAttribute("user", user);

        return "redirect:/user?msg=emailSuccess";
    }
    // 修改密码
    @PostMapping("/changePassword")
    public String changePassword(String oldPassword,
                                 String newPassword,
                                 String confirmPassword,
                                 HttpSession session){

        User user = (User) session.getAttribute("user");

        if(user == null){
            return "redirect:/login";
        }

        // 校验旧密码
        if(!user.getPassword().equals(oldPassword)){
            return "redirect:/user?msg=oldError";
        }

        // 校验新密码一致
        if(!newPassword.equals(confirmPassword)){
            return "redirect:/user?msg=notMatch";
        }

        // 更新密码
        user.setPassword(newPassword);
        userRepository.save(user);

        // 更新 session
        session.setAttribute("user", user);

        return "redirect:/user?msg=success";
    }

    // 修改偏好分类
    @PostMapping("/changeCategories")
    public String changeCategories(@org.springframework.web.bind.annotation.RequestParam(required = false) String preferredCategories, HttpSession session) {

        User user = (User) session.getAttribute("user");

        if (user == null) {
            return "redirect:/login";
        }

        // 更新偏好分类
        user.setPreferredCategories(preferredCategories);
        userRepository.save(user);

        // 更新 session
        session.setAttribute("user", user);

        return "redirect:/user?msg=categoriesSuccess";
    }
}