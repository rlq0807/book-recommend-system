package com.renlq.bookrecommendsystem.repository;

import com.renlq.bookrecommendsystem.entity.Book;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface BookRepository extends JpaRepository<Book, Long> {
// 按ID排序（默认）
    List<Book> findAllByOrderByIdAsc();
    
    // 根据分类列表查询书籍
    @Query("SELECT b FROM Book b WHERE b.category IN :categories")
    List<Book> findByCategoryIn(@org.springframework.data.repository.query.Param("categories") List<String> categories);
    
    // 获取所有唯一的分类
    @Query("SELECT DISTINCT b.category FROM Book b WHERE b.category IS NOT NULL AND b.category != ''")
    List<String> findAllDistinctCategories();
    
    // 获取出现次数最多的前8个分类
    @Query("SELECT b.category, COUNT(b.id) as countBooks FROM Book b WHERE b.category IS NOT NULL AND b.category != '' GROUP BY b.category ORDER BY countBooks DESC")
    List<Object[]> findTop8CategoriesByCount();
}