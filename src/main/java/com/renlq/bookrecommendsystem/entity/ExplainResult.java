package com.renlq.bookrecommendsystem.entity;

public class ExplainResult {

    private Book book;

    private String explanation;

    private double score;

    public ExplainResult(Book book,String explanation,double score){

        this.book=book;

        this.explanation=explanation;

        this.score=score;

    }

    public Book getBook() {
        return book;
    }

    public String getExplanation() {
        return explanation;
    }

    public double getScore() {
        return score;
    }

}