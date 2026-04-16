package com.renlq.bookrecommendsystem.repository;

import com.renlq.bookrecommendsystem.entity.Book;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface BookRepository extends JpaRepository<Book, Long> {
// 按ID排序（默认）
    List<Book> findAllByOrderByIdAsc();
}