package com.renlq.bookrecommendsystem.repository;

import com.renlq.bookrecommendsystem.entity.Rating;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface RatingRepository extends JpaRepository<Rating, Long> {

    List<Rating> findByUserId(Long userId);
     List<Rating> findByUserIdAndBookId(Long userId, Long bookId);
     @Query("SELECT AVG(r.score) FROM Rating r WHERE r.bookId = :bookId")
    Double getAverageScore(@Param("bookId") Long bookId);
     @Query("SELECT r.bookId, AVG(r.score) as avgScore " +
       "FROM Rating r GROUP BY r.bookId ORDER BY avgScore DESC")
    List<Object[]> findBookAvgScores();
     @Query("SELECT r, b.name FROM Rating r JOIN Book b ON r.bookId = b.id WHERE r.userId = :userId")
    List<Object[]> findRatingsWithBookName(@Param("userId") Long userId);
}