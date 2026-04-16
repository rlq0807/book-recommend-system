package com.renlq.bookrecommendsystem.entity;

import lombok.Getter;

@Getter
public class RecommendationResult {

    private final Book book;

    private final String reason;

    public RecommendationResult(Book book,String reason){
        this.book=book;
        this.reason=reason;
    }

}