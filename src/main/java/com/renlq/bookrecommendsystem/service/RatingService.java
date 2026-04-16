package com.renlq.bookrecommendsystem.service;

import com.renlq.bookrecommendsystem.entity.Rating;
import com.renlq.bookrecommendsystem.repository.RatingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class RatingService {

    private final RatingRepository ratingRepository;

    public void rateBook(Long userId, Long bookId, Integer score){

        Rating rating =
                ratingRepository.findByUserIdAndBookId(userId, bookId);

        if(rating == null){

            rating = new Rating();

            rating.setUserId(userId);
            rating.setBookId(bookId);
        }

        rating.setScore(score);
        rating.setCreateTime(LocalDateTime.now());

        ratingRepository.save(rating);
    }

}