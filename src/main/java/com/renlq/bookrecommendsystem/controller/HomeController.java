package com.renlq.bookrecommendsystem.controller;

import com.renlq.bookrecommendsystem.entity.BorrowRecord;
import com.renlq.bookrecommendsystem.entity.User;
import com.renlq.bookrecommendsystem.service.BorrowService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import javax.servlet.http.HttpSession;
import java.util.List;

@Controller
@RequiredArgsConstructor
public class HomeController {

    private final BorrowService borrowService;

    @GetMapping("/home")
    public String home(
            HttpSession session,
            Model model
    ){

        User user=(User)session.getAttribute("user");

        if(user==null){
            return "redirect:/login";
        }

        // 获取3天内即将过期的书籍
        List<BorrowRecord> soonExpireBooks = borrowService.getSoonExpireBooks(user.getId());

        model.addAttribute("username",user.getUsername());
        model.addAttribute("soonExpireBooks", soonExpireBooks);

        return "home";
    }

}