package com.renlq.bookrecommendsystem.controller;

import com.renlq.bookrecommendsystem.entity.RecommendationResult;
import com.renlq.bookrecommendsystem.entity.User;
import com.renlq.bookrecommendsystem.service.RecommendService;
import javax.persistence.*;
import javax.servlet.http.HttpSession;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

@Controller
@RequestMapping("/recommend")
@RequiredArgsConstructor
public class RecommendController {

    private final RecommendService recommendService;

   @GetMapping
public String recommendPage(HttpSession session,
                            Model model,
                            @RequestParam(required = false, defaultValue = "true") boolean useBias,
                            @RequestParam(required = false) String msg) {

    User user = (User) session.getAttribute("user");

    if (user == null) {
        return "redirect:/login";
    }

    List<RecommendationResult> recommendList =
            recommendService.recommendWithExplanation(user.getId(), useBias);

    model.addAttribute("recommendList", recommendList);
    model.addAttribute("username", user.getUsername());
    model.addAttribute("msg", msg);

    return "recommend";
}
}
