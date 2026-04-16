package com.renlq.bookrecommendsystem.scheduled;

import com.renlq.bookrecommendsystem.service.BorrowService;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class OverdueReminderTask {

    private final BorrowService borrowService;

    // 每天上午9点执行检查
    @Scheduled(cron = "0 0 9 * * ?")
    public void checkOverdueAndSendEmail() {
        borrowService.checkOverdueAndSendEmail();
    }
}
