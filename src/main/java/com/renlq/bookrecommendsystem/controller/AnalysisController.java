package com.renlq.bookrecommendsystem.controller;

import com.renlq.bookrecommendsystem.repository.BookRepository;
import com.renlq.bookrecommendsystem.repository.RatingRepository;
import com.renlq.bookrecommendsystem.repository.UserRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/analysis")
public class AnalysisController {

    private final UserRepository userRepository;
    private final BookRepository bookRepository;
    private final RatingRepository ratingRepository;

    public AnalysisController(UserRepository userRepository,
                              BookRepository bookRepository,
                              RatingRepository ratingRepository) {
        this.userRepository = userRepository;
        this.bookRepository = bookRepository;
        this.ratingRepository = ratingRepository;
    }

    @GetMapping("/summary")
    public Map<String, Object> summary() {

        Map<String, Object> result = new HashMap<>();

        result.put("用户数量", userRepository.count());
        result.put("图书数量", bookRepository.count());
        result.put("评分数量", ratingRepository.count());

        return result;
    }
}