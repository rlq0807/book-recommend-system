package com.renlq.bookrecommendsystem.entity;

import javax.persistence.*;
import lombok.Data;

import javax.persistence.*;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "ratings")
public class Rating {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long userId;

    private Long bookId;

    private Integer score;

    private LocalDateTime createTime;
    @Transient
    private String bookName;
}