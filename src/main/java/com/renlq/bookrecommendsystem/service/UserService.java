package com.renlq.bookrecommendsystem.service;

import com.renlq.bookrecommendsystem.entity.User;
import com.renlq.bookrecommendsystem.repository.UserRepository;
import org.springframework.stereotype.Service;

@Service
public class UserService {

    private final UserRepository userRepository;

    public UserService(UserRepository userRepository){
        this.userRepository=userRepository;
    }

    public User login(String username,String password){

        User user =
                userRepository.findByUsername(username);

        if(user!=null &&
                user.getPassword().equals(password)){

            return user;
        }

        return null;
    }


    public void register(User user){

        user.setRole("user");

        userRepository.save(user);

    }
}