package com.renlq.bookrecommendsystem.controller;

import com.renlq.bookrecommendsystem.entity.Book;
import com.renlq.bookrecommendsystem.entity.BorrowRecord;
import com.renlq.bookrecommendsystem.entity.User;
import com.renlq.bookrecommendsystem.repository.BookRepository;
import com.renlq.bookrecommendsystem.repository.BorrowRecordRepository;
import com.renlq.bookrecommendsystem.repository.UserRepository;
import com.renlq.bookrecommendsystem.service.BookService;
import com.renlq.bookrecommendsystem.service.BorrowService;
import com.renlq.bookrecommendsystem.service.ConfigService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpSession;
import java.util.List;

@Controller
@RequestMapping("/admin")
@RequiredArgsConstructor
public class AdminController {
    private final ConfigService configService;
    private final BookRepository bookRepository;
    private final BorrowRecordRepository borrowRecordRepository;
    private final UserRepository userRepository;
    private final BorrowService borrowService;

    // 主页
    @GetMapping
    public String adminPage(HttpSession session, Model model){

        User user = (User) session.getAttribute("user");

        if(user == null || !"administer".equals(user.getRole())){
            return "redirect:/login";
        }

        model.addAttribute("username", user.getUsername());

        return "admin";
    }

    // ===== 图书列表 =====
@GetMapping("/books")
public String manageBooks(Model model){
    model.addAttribute("books", bookRepository.findAll());
    return "admin_books";
}

// ===== 新增图书 =====
@PostMapping("/book/add")
public String addBook(Book book){
    bookRepository.save(book);
    return "redirect:/admin/books";
}

// ===== 删除图书 =====
@PostMapping("/book/delete")
public String deleteBook(@RequestParam Long id){
    bookRepository.deleteById(id);
    return "redirect:/admin/books";
}

// ===== 进入编辑页面 =====
@GetMapping("/book/edit/{id}")
public String editBookPage(@PathVariable Long id, Model model){

    Book book = bookRepository.findById(id).orElse(null);

    model.addAttribute("book", book);

    return "admin_edit_book";
}

// ===== 提交修改 =====
@PostMapping("/book/update")
public String updateBook(Book book){

    bookRepository.save(book); // save会自动更新

    return "redirect:/admin/books";
}

    // ===== 逾期未归还 =====
    @GetMapping("/overdue")
    public String overdueBooks(Model model){

    List<BorrowRecord> list =
            borrowService.getOverdueBooks();

    // 获取所有用户和图书的映射
    List<User> allUsers = userRepository.findAll();
    List<Book> allBooks = bookRepository.findAll();

    // 转换为Map格式，方便前端查找
    java.util.Map<Long, User> userMap = new java.util.HashMap<>();
    for (User user : allUsers) {
        userMap.put(user.getId(), user);
    }

    java.util.Map<Long, Book> bookMap = new java.util.HashMap<>();
    for (Book book : allBooks) {
        bookMap.put(book.getId(), book);
    }

    model.addAttribute("records", list);
    model.addAttribute("users", allUsers);
    model.addAttribute("books", allBooks);
    model.addAttribute("userMap", userMap);
    model.addAttribute("bookMap", bookMap);

    return "admin_overdue";
}

    // ===== 设置管理员 =====
    @PostMapping("/setAdmin")
    public String setAdmin(@RequestParam Long userId){

        User user = userRepository.findById(userId).orElse(null);

        if(user != null){
            user.setRole("administer");
            userRepository.save(user);
        }

        return "redirect:/admin";
    }
     @PostMapping("/updateAlpha")
    public String updateAlpha(@RequestParam double alpha){

        configService.setAlpha(alpha);

        return "redirect:/admin?msg=alpha_updated";
    }
}