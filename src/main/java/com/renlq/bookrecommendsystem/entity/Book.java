package com.renlq.bookrecommendsystem.entity;

import javax.persistence.*;
import lombok.Data;

import javax.persistence.*;

@Data
@Entity
@Table(name = "books")
public class Book {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;

    private String author;

    private String category;

    private String description;
    @Transient
    private Double avgScore;

    @Transient
    private Long hotCount;
}