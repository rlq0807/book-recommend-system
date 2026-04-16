package com.renlq.bookrecommendsystem.repository;

import com.renlq.bookrecommendsystem.entity.BorrowRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface BorrowRecordRepository
        extends JpaRepository<BorrowRecord, Long> {
        Optional<BorrowRecord> findByUserIdAndBookIdAndStatus(long userId,
                                                        long bookId,
                                                        String status);
    List<BorrowRecord> findByUserId(long userId);

    List<BorrowRecord> findByBookIdAndStatus(Long bookId,String status);

    BorrowRecord findByUserIdAndBookIdAndStatus(long attr0, Long attr1, String status);
    @Query("SELECT b.bookId, COUNT(b.id) as cnt " +
       "FROM BorrowRecord b GROUP BY b.bookId ORDER BY cnt DESC")
    List<Object[]> findHotBooks();
    // 已归还的书
    List<BorrowRecord> findByUserIdAndStatus(Long userId, String status);
    @Query("SELECT b FROM BorrowRecord b WHERE b.status='BORROWED' AND b.dueTime < CURRENT_TIMESTAMP")
    List<BorrowRecord> findOverdueBooks();
    @Query("SELECT b FROM BorrowRecord b " +
       "WHERE b.status = 'BORROWED' " +
       "AND b.dueTime < :now")
    List<BorrowRecord> findOverdueBooks(@Param("now") LocalDateTime now);
    // ⭐ 统计借阅次数（新增这个）
    Long countByBookId(Long bookId);

    // 查询3天内即将过期的书籍
    @Query("SELECT b FROM BorrowRecord b " +
           "WHERE b.status = 'BORROWED' " +
           "AND b.userId = :userId " +
           "AND b.dueTime BETWEEN :now AND :threeDaysLater")
    List<BorrowRecord> findSoonExpireBooks(@Param("userId") Long userId, 
                                         @Param("now") LocalDateTime now, 
                                         @Param("threeDaysLater") LocalDateTime threeDaysLater);

    // 查询用户当前借阅数量
    @Query("SELECT COUNT(b) FROM BorrowRecord b WHERE b.userId = :userId AND b.status = 'BORROWED'")
    Long countCurrentBorrowByUserId(@Param("userId") Long userId);

    // 查询用户三十天内的逾期次数
    @Query("SELECT COUNT(b) FROM BorrowRecord b WHERE b.userId = :userId AND b.status = 'RETURNED' AND b.returnTime > b.dueTime AND b.returnTime >= :thirtyDaysAgo")
    Long countOverdueTimesByUserId(@Param("userId") Long userId, @Param("thirtyDaysAgo") LocalDateTime thirtyDaysAgo);

    // 查询用户是否有超过三次逾期且在禁止借阅期内
    @Query("SELECT CASE WHEN COUNT(b) >= 3 THEN MAX(b.dueTime) ELSE NULL END FROM BorrowRecord b WHERE b.userId = :userId AND ((b.status = 'RETURNED' AND b.returnTime > b.dueTime AND b.returnTime >= :thirtyDaysAgo) OR (b.status = 'BORROWED' AND b.dueTime < CURRENT_TIMESTAMP AND b.dueTime >= :thirtyDaysAgo))")
    LocalDateTime findLastOverdueDueTimeByUserId(@Param("userId") Long userId, @Param("thirtyDaysAgo") LocalDateTime thirtyDaysAgo);

}