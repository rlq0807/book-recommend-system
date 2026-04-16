package com.renlq.bookrecommendsystem.controller;

import com.renlq.bookrecommendsystem.entity.Book;
import com.renlq.bookrecommendsystem.entity.BorrowRecord;
import com.renlq.bookrecommendsystem.entity.Rating;
import com.renlq.bookrecommendsystem.entity.User;
import com.renlq.bookrecommendsystem.repository.BookRepository;
import com.renlq.bookrecommendsystem.repository.BorrowRecordRepository;
import com.renlq.bookrecommendsystem.repository.RatingRepository;
import com.renlq.bookrecommendsystem.service.RatingService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpSession;
import java.util.ArrayList;
import java.util.List;

@Controller
@RequestMapping("/rating")
@RequiredArgsConstructor
public class RatingController {

    private final RatingService ratingService;
    private final BookRepository bookRepository;
    private final RatingRepository ratingRepository;
    private final BorrowRecordRepository borrowRecordRepository;

    @PostMapping("/rate")
    public String rateBook(@RequestParam Long bookId,
                           @RequestParam Integer score,
                           HttpSession session){

        User user = (User) session.getAttribute("user");

        if(user == null){
            return "redirect:/login";
        }

        ratingService.rateBook(
                Long.valueOf((Long) user.getId()),
                bookId,
                score
        );

        return "redirect:/rating?msg=success";
    }
    @GetMapping
    public String ratingPage(HttpSession session, Model model){

    User user = (User) session.getAttribute("user");

    if(user == null){
        return "redirect:/login";
    }

    List<Rating> ratings =
        ratingRepository.findByUserId(user.getId());

List<RatingDTO> ratedList = new ArrayList<>();

for(Rating r : ratings){

    Book book = bookRepository
            .findById(r.getBookId())
            .orElse(null);

    if(book != null){
        ratedList.add(
                new RatingDTO(
                        r.getBookId(),
                        book.getName(),
                        r.getScore()
                )
        );
    }
}

    List<BorrowRecord> returnedList =
        borrowRecordRepository.findByUserIdAndStatus(user.getId(),"RETURNED");

    List<Book> toRateList = new ArrayList<>();



    for(BorrowRecord br : returnedList){

        Rating r = ratingRepository
                .findByUserIdAndBookId(user.getId(), br.getBookId());

        if(r == null){
            Book book = bookRepository.findById(br.getBookId()).orElse(null);
            toRateList.add(book);
        }
    }

    model.addAttribute("ratedList", ratedList);
    model.addAttribute("toRateList", toRateList);

    return "ratings";
}
public static class RatingDTO {

    private Long bookId;
    private String bookName;
    private Integer score;

    public RatingDTO(Long bookId, String bookName, Integer score) {
        this.bookId = bookId;
        this.bookName = bookName;
        this.score = score;
    }

    public Long getBookId() {
        return bookId;
    }

    public String getBookName() {
        return bookName;
    }

    public Integer getScore() {
        return score;
    }

    // getter
}
}