package com.renlq.bookrecommendsystem.controller;

import com.renlq.bookrecommendsystem.entity.User;
import com.renlq.bookrecommendsystem.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpSession;

@Controller
@RequiredArgsConstructor
public class LoginController {

    private final UserRepository userRepository;

    // ===== 登录页面 =====
    @GetMapping("/login")
    public String loginPage(){
        return "login";
    }

    // ===== 登录验证 =====
    @PostMapping("/login")
    public String login(@RequestParam String username,
                        @RequestParam String password,
                        HttpSession session,
                        Model model){

        User user = userRepository.findByUsername(username);

        if(user == null){
            model.addAttribute("msg","用户名不存在");
            return "login";
        }

        if(!user.getPassword().equals(password)){
            model.addAttribute("msg","密码错误");
            return "login";
        }

        // 登录成功
        session.setAttribute("user", user);

         // ⭐ 根据角色跳转
        if("administer".equals(user.getRole())){
            return "redirect:/admin";
        }else{
            return "redirect:/home";
    }
    }

    // ===== 注册页面 =====
    @GetMapping("/register")
    public String registerPage(){
        return "register";
    }

    // ===== 注册处理 =====
    @PostMapping("/register")
    public String register(@RequestParam String username,
                           @RequestParam String password,
                           @RequestParam String confirmPassword,
                           @RequestParam String email,
                           Model model){

        if(!password.equals(confirmPassword)){
            model.addAttribute("msg","两次密码不一致");
            return "register";
        }

        if(userRepository.findByUsername(username) != null){
            model.addAttribute("msg","用户名已存在");
            return "register";
        }

        if(userRepository.findByEmail(email) != null){
            model.addAttribute("msg","邮箱已被使用");
            return "register";
        }

        User user = new User();
        user.setUsername(username);
        user.setPassword(password);
        user.setEmail(email);

        userRepository.save(user);

        model.addAttribute("msg","注册成功，请登录");
        return "login";
    }

    // ===== 忘记密码页面 =====
    @GetMapping("/forgot")
    public String forgotPage(){
        return "forgot";
    }

    // ===== 忘记密码处理 =====
    @PostMapping("/forgot")
    public String forgotPassword(@RequestParam String username,
                                 @RequestParam String email,
                                 @RequestParam String newPassword,
                                 @RequestParam String confirmPassword,
                                 Model model){

        User user = userRepository.findByUsername(username);

        if(user == null){
            model.addAttribute("msg","用户名不存在");
            return "forgot";
        }

        if(!user.getEmail().equals(email)){
            model.addAttribute("msg","邮箱不匹配");
            return "forgot";
        }

        if(!newPassword.equals(confirmPassword)){
            model.addAttribute("msg","两次密码不一致");
            return "forgot";
        }

        user.setPassword(newPassword);
        userRepository.save(user);

        model.addAttribute("msg","密码修改成功，请登录");
        return "login";
    }

    // ===== 登出 =====
    @GetMapping("/logout")
    public String logout(HttpSession session){
        session.invalidate();
        return "redirect:/login";
    }
}