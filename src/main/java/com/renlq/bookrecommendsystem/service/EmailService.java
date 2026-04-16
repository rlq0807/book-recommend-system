package com.renlq.bookrecommendsystem.service;

import lombok.RequiredArgsConstructor;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class EmailService {

    private final JavaMailSender mailSender;
    
    public void sendBatchOverdueReminder(String to, String username, List<String> bookTitles, List<String> dueDates) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom("2323758014@qq.com");
        message.setTo(to);
        message.setSubject("【图书借阅提醒】您有" + bookTitles.size() + "本书籍已逾期");
        
        StringBuilder emailContent = new StringBuilder();
        emailContent.append("尊敬的").append(username).append("：\n\n");
        emailContent.append("您有以下书籍已逾期，请尽快归还：\n\n");
        
        for (int i = 0; i < bookTitles.size(); i++) {
            emailContent.append("- 《").append(bookTitles.get(i)).append("》 逾期日期：").append(dueDates.get(i)).append("\n");
        }
        
        emailContent.append("\n感谢您的配合！\n");
        emailContent.append("图书推荐管理系统");
        
        message.setText(emailContent.toString());
        mailSender.send(message);
    }
}
