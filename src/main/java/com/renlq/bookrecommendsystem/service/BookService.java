package com.renlq.bookrecommendsystem.service;

import com.renlq.bookrecommendsystem.entity.Book;
import com.renlq.bookrecommendsystem.entity.BorrowRecord;
import com.renlq.bookrecommendsystem.repository.BookRepository;
import com.renlq.bookrecommendsystem.repository.BorrowRecordRepository;
import com.renlq.bookrecommendsystem.repository.RatingRepository;
import org.springframework.stereotype.Repository;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@Repository
public class BookService {
    private final BookRepository bookRepository;
    private final RatingRepository ratingRepository;
    private final BorrowRecordRepository borrowRecordRepository;

    public BookService(BookRepository bookRepository, RatingRepository ratingRepository, BorrowRecordRepository borrowRecordRepository) {
        this.bookRepository = bookRepository;
        this.ratingRepository = ratingRepository;
        this.borrowRecordRepository = borrowRecordRepository;
    }

    public List<Book> getBooksSorted(String sortType) {

        List<Book> books = bookRepository.findAll();

        // ===== 1️⃣ 评分Map =====
        Map<Long, Double> scoreMap = new HashMap<>();
        for (Object[] obj : ratingRepository.findBookAvgScores()) {
            scoreMap.put((Long) obj[0], (Double) obj[1]);
        }

        // ===== 2️⃣ 热门Map =====
        Map<Long, Long> hotMap = new HashMap<>();
        for (Object[] obj : borrowRecordRepository.findHotBooks()) {
            hotMap.put((Long) obj[0], (Long) obj[1]);
        }

        // ===== 3️⃣ 给所有书赋值（核心！！）=====
        for (Book book : books) {
            book.setAvgScore(scoreMap.getOrDefault(book.getId(), 0.0));
            book.setHotCount(hotMap.getOrDefault(book.getId(), 0L));
        }

        // ===== 4️⃣ 排序 =====

        // 默认
        if ("default".equals(sortType)) {
            books.sort((a, b) -> Long.compare(a.getId(), b.getId()));
        }

        // 评分排序
        else if ("rating".equals(sortType)) {
            books.sort((a, b) -> Double.compare(
                    b.getAvgScore(), a.getAvgScore()));
        }

        // 热门排序
        else if ("hot".equals(sortType)) {
            books.sort((a, b) -> Long.compare(
                    b.getHotCount(), a.getHotCount()));
        }

        return books;
    }
}
