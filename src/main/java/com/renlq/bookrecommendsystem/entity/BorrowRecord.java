package com.renlq.bookrecommendsystem.entity;

import lombok.Getter;
import lombok.Setter;

import javax.persistence.*;
import java.time.LocalDateTime;

@Getter
@Entity
@Table(name = "borrow_record")
public class BorrowRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Setter
    private Long userId;

    @Setter
    private Long bookId;

    @Setter
    private LocalDateTime borrowTime;

    @Setter
    private LocalDateTime dueTime;

    @Setter
    private LocalDateTime returnTime;

    @Setter
    private String status; // BORROWED / RETURNED

    // ===== Getter =====

    // ===== Setter =====

}