package com.renlq.bookrecommendsystem.controller;

import com.renlq.bookrecommendsystem.entity.Book;
import com.renlq.bookrecommendsystem.entity.User;
import com.renlq.bookrecommendsystem.repository.BookRepository;
import com.renlq.bookrecommendsystem.repository.RatingRepository;
import com.renlq.bookrecommendsystem.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpSession;
import java.util.List;

@Controller
@RequestMapping("/book")
@RequiredArgsConstructor
public class BookController {

    private final BookRepository bookRepository;
    private final RatingRepository ratingRepository;

    @GetMapping("/add")
    public String addBook(@RequestParam String name,
                          @RequestParam String author,
                          @RequestParam String category,
                          @RequestParam String description) {

        Book book = new Book();
        book.setName(name);
        book.setAuthor(author);
        book.setCategory(category);
        book.setDescription(description);

        bookRepository.save(book);
        return "添加书籍成功";
    }
    @GetMapping("/{id}")
    public String bookDetail(@PathVariable Long id,
                            Model model,
                            HttpSession session){

        User user = (User) session.getAttribute("user");

        if(user == null){
            return "redirect:/login";
        }

        Book book = bookRepository
                .findById(id)
                .orElse(null);

        // ⭐ 加上评分逻辑
        Double avgScore = ratingRepository.getAverageScore(id);

        if(avgScore == null){
            avgScore = 0.0;
        }

        book.setAvgScore(avgScore);

        model.addAttribute("book",book);

        return "bookDetail";
    }

    @GetMapping("/all")
    public List<Book> getAllBooks() {
        return bookRepository.findAll();
    }
}