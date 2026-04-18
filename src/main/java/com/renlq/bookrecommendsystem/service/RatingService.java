package com.renlq.bookrecommendsystem.service;

import com.renlq.bookrecommendsystem.entity.Rating;
import com.renlq.bookrecommendsystem.repository.RatingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class RatingService {

    private final RatingRepository ratingRepository;

    public void rateBook(Long userId, Long bookId, Integer score){

        List<Rating> ratings = 
                ratingRepository.findByUserIdAndBookId(userId, bookId);

        Rating rating;
        if(ratings.isEmpty()){
            rating = new Rating();
            rating.setUserId(userId);
            rating.setBookId(bookId);
        } else {
            // 存在多条记录时，更新第一条并删除其余的
            rating = ratings.get(0);
            for(int i = 1; i < ratings.size(); i++){
                ratingRepository.delete(ratings.get(i));
            }
        }

        rating.setScore(score);
        rating.setCreateTime(LocalDateTime.now());

        ratingRepository.save(rating);
    }

}