package com.renlq.bookrecommendsystem.service;

import com.renlq.bookrecommendsystem.entity.BorrowRecord;
import com.renlq.bookrecommendsystem.entity.Book;
import com.renlq.bookrecommendsystem.entity.User;
import com.renlq.bookrecommendsystem.repository.BorrowRecordRepository;
import com.renlq.bookrecommendsystem.repository.UserRepository;
import com.renlq.bookrecommendsystem.repository.BookRepository;
import com.renlq.bookrecommendsystem.service.EmailService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class BorrowService {

    private final BorrowRecordRepository borrowRecordRepository;
    private final UserRepository userRepository;
    private final BookRepository bookRepository;
    private final EmailService emailService;

    // ===== 借书 =====
    public String borrowBook(Long userId, Long bookId){

        // 检查借阅权限
        String permissionMsg = checkBorrowPermission(userId);
        if (permissionMsg != null) {
            return permissionMsg;
        }

        // ❗ 防止重复借
        boolean exists = borrowRecordRepository
                .findByBookIdAndStatus(bookId, "BORROWED")
                .isEmpty();

        if(!exists){
            return "该书已被借出";
        }

        BorrowRecord record = new BorrowRecord();

        record.setUserId(userId);
        record.setBookId(bookId);
        record.setBorrowTime(LocalDateTime.now());
        record.setDueTime(LocalDateTime.now().plusDays(30));
        record.setStatus("BORROWED");

        borrowRecordRepository.save(record);

        Book book = bookRepository.findById(bookId).orElse(null);
        if (book != null) {
            book.setHotCount(book.getHotCount() == null ? 1 : book.getHotCount() + 1);
            bookRepository.save(book);
        }

        return "借阅成功";
    }

    // ===== 还书 =====
    public void returnBook(Long recordId){

        BorrowRecord record =
                borrowRecordRepository.findById(recordId).orElse(null);

        if(record == null) return;

        record.setStatus("RETURNED");
        record.setReturnTime(LocalDateTime.now());

        borrowRecordRepository.save(record);
    }

    // ===== 用户借阅记录 =====
    public List<BorrowRecord> getUserBorrowRecords(Long userId){
        return borrowRecordRepository.findByUserId(userId);
    }

    // ===== 是否可借 =====
    public boolean isAvailable(Long bookId){
        return borrowRecordRepository
                .findByBookIdAndStatus(bookId,"BORROWED")
                .isEmpty();
    }

    // ===== 续借 =====
    public void renewBook(Long recordId){

        BorrowRecord record =
                borrowRecordRepository.findById(recordId).orElse(null);

        if(record != null && "BORROWED".equals(record.getStatus())){

            record.setDueTime(record.getDueTime().plusDays(7));

            borrowRecordRepository.save(record);
        }
    }

    // ===== 获取逾期 =====
    public List<BorrowRecord> getOverdueBooks(){
    return borrowRecordRepository.findOverdueBooks(LocalDateTime.now());
    }

    // ===== 获取3天内即将过期的书籍 =====
    public List<BorrowRecord> getSoonExpireBooks(Long userId){
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime threeDaysLater = now.plusDays(3);
        return borrowRecordRepository.findSoonExpireBooks(userId, now, threeDaysLater);
    }

    // ===== 检查用户当前借阅数量 =====
    public long getCurrentBorrowCount(Long userId) {
        Long count = borrowRecordRepository.countCurrentBorrowByUserId(userId);
        return count != null ? count : 0;
    }

    // ===== 检查用户是否被禁止借阅 =====
    public String checkBorrowPermission(Long userId) {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime thirtyDaysAgo = now.minusDays(30);
        
        // 检查三十天内逾期次数（优先级高于借阅数量限制）
        LocalDateTime lastOverdueDueTime = borrowRecordRepository.findLastOverdueDueTimeByUserId(userId, thirtyDaysAgo);
        if (lastOverdueDueTime != null) {
            // 计算禁止借阅期结束时间（最后一次逾期书籍的到期日期后30天）
            LocalDateTime banEndTime = lastOverdueDueTime.plusDays(30);
            if (now.isBefore(banEndTime)) {
                return "您在三十天内逾期超过三次，已被禁止借阅，禁止期至：" + banEndTime.format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"));
            }
        }
        
        // 检查借阅数量限制（普通用户最多5本）
        long currentBorrowCount = getCurrentBorrowCount(userId);
        if (currentBorrowCount >= 5) {
            return "您当前已借阅5本书，达到借阅上限，无法继续借阅";
        }
        
        return null; // 无限制
    }

    // ===== 检查逾期书籍并发送邮件提醒 =====
    public void checkOverdueAndSendEmail() {
        LocalDateTime now = LocalDateTime.now();
        List<BorrowRecord> overdueBooks = borrowRecordRepository.findOverdueBooks(now);
        
        // 按用户分组
        Map<Long, List<BorrowRecord>> userOverdueMap = new HashMap<>();
        for (BorrowRecord record : overdueBooks) {
            userOverdueMap.computeIfAbsent(record.getUserId(), k -> new ArrayList<>()).add(record);
        }
        
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
        
        // 为每个用户发送一封包含所有逾期书籍的邮件
        for (Map.Entry<Long, List<BorrowRecord>> entry : userOverdueMap.entrySet()) {
            Long userId = entry.getKey();
            List<BorrowRecord> userOverdueBooks = entry.getValue();
            
            User user = userRepository.findById(userId).orElse(null);
            if (user != null && user.getEmail() != null && !user.getEmail().isEmpty()) {
                // 准备书籍标题和逾期日期列表
                List<String> bookTitles = new ArrayList<>();
                List<String> dueDates = new ArrayList<>();
                
                for (BorrowRecord record : userOverdueBooks) {
                    Book book = bookRepository.findById(record.getBookId()).orElse(null);
                    String bookTitle = book != null ? book.getName() : "书籍ID: " + record.getBookId();
                    String dueDate = record.getDueTime().format(formatter);
                    bookTitles.add(bookTitle);
                    dueDates.add(dueDate);
                }
                
                // 发送批量邮件
                emailService.sendBatchOverdueReminder(user.getEmail(), user.getUsername(), bookTitles, dueDates);
            }
        }
    }
}