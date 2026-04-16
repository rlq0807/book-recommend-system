package com.renlq.bookrecommendsystem.entity;

import lombok.Getter;

import javax.persistence.*;

@Entity
@Table(name="users")
public class User {

    @Id
    @GeneratedValue(strategy=GenerationType.IDENTITY)
    private long id;

    @Getter
    private String username;

    @Getter
    private String password;

    @Getter
    private String role;

    @Getter
    private String email;

    public void setEmail(String email) {
    this.email = email;
}

    public Long getId(){
        return id;
    }

    public void setId(long id){
        this.id=id;
    }

    public void setUsername(String username){
        this.username=username;
    }

    public void setPassword(String password){
        this.password=password;
    }

    public void setRole(String role){
        this.role=role;
    }
}