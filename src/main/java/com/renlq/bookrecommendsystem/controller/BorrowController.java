package com.renlq.bookrecommendsystem.controller;

import com.renlq.bookrecommendsystem.entity.Book;
import com.renlq.bookrecommendsystem.entity.BorrowRecord;
import com.renlq.bookrecommendsystem.entity.User;
import com.renlq.bookrecommendsystem.repository.BookRepository;
import com.renlq.bookrecommendsystem.service.BookService;
import com.renlq.bookrecommendsystem.service.BorrowService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import javax.servlet.http.HttpSession;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Controller
@RequestMapping("/borrow")
@RequiredArgsConstructor
public class BorrowController {

    private final BorrowService borrowService;
    private final BookRepository bookRepository;
    private final BookService bookService;

    // 图书列表页面
    @GetMapping
    public String borrowPage(@RequestParam(required = false, defaultValue = "default") String sort,
                            Model model){

        List<Book> books = bookService.getBooksSorted(sort);

        model.addAttribute("books", books);
        model.addAttribute("sort", sort);

        return "borrow";
    }
    // 借阅记录页面
    @GetMapping("/records")
    public String borrowRecords(HttpSession session, Model model){

        User user = (User) session.getAttribute("user");

        if(user == null){
            return "redirect:/login";
        }

        List<BorrowRecord> records =
                borrowService.getUserBorrowRecords(user.getId());

        List<Map<String,Object>> recordList = new ArrayList<>();

        for(BorrowRecord r : records){

            Book book = bookRepository.findById(r.getBookId()).orElse(null);

            Map<String,Object> map = new HashMap<>();
            map.put("record",r);
            map.put("book",book);

            // 判断是否已过期或即将过期
            if("BORROWED".equals(r.getStatus())) {
                LocalDateTime now = LocalDateTime.now();
                LocalDateTime dueTime = r.getDueTime();
                
                if (now.isAfter(dueTime)) {
                    map.put("expireStatus", "overdue"); // 已过期
                } else if (now.plusDays(3).isAfter(dueTime)) {
                    map.put("expireStatus", "soon"); // 即将过期（3天内）
                } else {
                    map.put("expireStatus", "normal"); // 正常
                }
            } else {
                map.put("expireStatus", "normal"); // 已归还，视为正常
            }

            recordList.add(map);
        }

        // 按记录id降序排序
        recordList.sort((a, b) -> {
            BorrowRecord recordA = (BorrowRecord) a.get("record");
            BorrowRecord recordB = (BorrowRecord) b.get("record");
            return Long.compare(recordB.getId(), recordA.getId());
        });

        model.addAttribute("records",recordList);

        return "borrowRecords";
    }
    // 借书
    @PostMapping("/borrowBook")
    public String borrowBook(@RequestParam Long bookId,
                            @RequestParam(required = false, defaultValue = "borrow") String from,
                            HttpSession session,
                            RedirectAttributes ra) {

        User user = (User) session.getAttribute("user");

        if (user == null) {
            return "redirect:/login";
        }

        if (!borrowService.isAvailable(bookId)) {
            if ("bookDetail".equals(from)) {
                return "redirect:/book/" + bookId + "?msg=exist";
            }
            return "redirect:/" + from + "?msg=exist";
        }

        String result = borrowService.borrowBook(user.getId(), bookId);

        if ("借阅成功".equals(result)) {
            if ("bookDetail".equals(from)) {
                return "redirect:/book/" + bookId + "?msg=success";
            }
            return "redirect:/" + from + "?msg=success";
        } else {
            try {
                String encodedErrorMsg = java.net.URLEncoder.encode(result, "UTF-8");
                if ("bookDetail".equals(from)) {
                    return "redirect:/book/" + bookId + "?msg=error&errorMsg=" + encodedErrorMsg;
                }
                return "redirect:/" + from + "?msg=error&errorMsg=" + encodedErrorMsg;
            } catch (java.io.UnsupportedEncodingException e) {
                if ("bookDetail".equals(from)) {
                    return "redirect:/book/" + bookId + "?msg=error&errorMsg=系统错误";
                }
                return "redirect:/" + from + "?msg=error&errorMsg=系统错误";
            }
        }
    }

    // 还书
    @PostMapping("/return")
    public String returnBook(@RequestParam Integer recordId){

        borrowService.returnBook(Long.valueOf(recordId));

        return "redirect:/borrow/records";
    }
    @PostMapping("/renew")
    public String renewBook(@RequestParam Integer recordId){
        borrowService.renewBook(Long.valueOf(recordId));
        return "redirect:/borrow/records";
    }
}