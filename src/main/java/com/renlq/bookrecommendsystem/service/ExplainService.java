package com.renlq.bookrecommendsystem.service;

import com.renlq.bookrecommendsystem.entity.Book;
import com.renlq.bookrecommendsystem.entity.Rating;
import com.renlq.bookrecommendsystem.repository.BookRepository;
import com.renlq.bookrecommendsystem.repository.RatingRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class ExplainService {

    @Autowired
    private BookRepository bookRepository;

    @Autowired
    private RatingRepository ratingRepository;


    /**
     * Hybrid推荐（核心算法）
     */
    public List<Book> hybridRecommend(int userId, double alpha){

        List<Book> userList = userCFRecommend(userId);

        List<Book> itemList = itemCFRecommend(userId);

       Map<Long,Double> scoreMap = new HashMap<>();

        for(int i=0;i<userList.size();i++){

            scoreMap.put(
                    userList.get(i).getId(),
                    alpha*(10-i));
        }

        for(int i=0;i<itemList.size();i++){

            scoreMap.merge(
                    itemList.get(i).getId(),
                    (1-alpha)*(10-i),
                    Double::sum);
        }

        return scoreMap.entrySet()
                .stream()
                .sorted((a,b)->Double.compare(b.getValue(),a.getValue()))
                .limit(10)
                .map(e->bookRepository.findById(e.getKey()).get())
                .collect(Collectors.toList());
    }


    /**
     * UserCF推荐（示例版）
     */
    public List<Book> userCFRecommend(int userId){

        List<Rating> ratings =
                ratingRepository.findAll();

        Map<Long,Double> scoreMap =
                new HashMap<>();

        for(Rating r:ratings){

            if(r.getUserId()!=userId) {

                scoreMap.merge(
                        r.getBookId(),
                        Double.valueOf(r.getScore()),
                        Double::sum);
            }
        }

        return scoreMap.entrySet()
                .stream()
                .sorted((a,b)->Double.compare(b.getValue(),a.getValue()))
                .limit(10)
                .map(e->bookRepository.findById(e.getKey()).get())
                .collect(Collectors.toList());
    }



    /**
     * ItemCF推荐（示例版）
     */
    public List<Book> itemCFRecommend(int userId){

        List<Rating> ratings =
                ratingRepository.findAll();

        Set<Long> userBooks =
                ratings.stream()
                        .filter(r->r.getUserId()==userId)
                        .map(Rating::getBookId)
                        .collect(Collectors.toSet());


        Map<Long,Double>scoreMap =
                new HashMap<>();


        for(Rating r:ratings){

            if(!userBooks.contains(r.getBookId())){

                scoreMap.merge(
                        r.getBookId(),
                        Double.valueOf(r.getScore()),
                        Double::sum);
            }
        }


        return scoreMap.entrySet()
                .stream()
                .sorted((a,b)->Double.compare(b.getValue(),a.getValue()))
                .limit(10)
                .map(e->bookRepository.findById(e.getKey()).get())
                .collect(Collectors.toList());
    }



    /**
     * Hybrid推荐+解释
     */
    public List<Map<String,Object>> recommendWithExplanation( int userId, double alpha){

        List<Book> books =
                hybridRecommend(userId,alpha);

        List<Map<String,Object>> result =
                new ArrayList<>();


        for(Book b:books){

            Map<String,Object> map =
                    new HashMap<>();

            map.put("bookName",
                    b.getName());

            map.put("author",
                    b.getAuthor());

            map.put("reason",
                    buildReason(userId, Math.toIntExact(b.getId())));

            result.add(map);
        }

        return result;
    }



    /**
     * UserCF解释
     */
    public List<Map<String,Object>> recommendWithExplanationUser(int userId){

        List<Book> books =
                userCFRecommend(userId);

        List<Map<String,Object>> result =
                new ArrayList<>();

        for(Book b:books){

            Map<String,Object> map =
                    new HashMap<>();

            map.put("bookName",
                    b.getName());

            map.put("author",
                    b.getAuthor());

            map.put("reason",
                    "与相似用户兴趣相似");

            result.add(map);
        }

        return result;
    }



    /**
     * ItemCF解释
     */
    public List<Map<String,Object>> recommendWithExplanationItem( int userId){

        List<Book> books =
                itemCFRecommend(userId);

        List<Map<String,Object>> result =
                new ArrayList<>();

        for(Book b:books){

            Map<String,Object> map =
                    new HashMap<>();

            map.put("bookName",
                    b.getName());

            map.put("author",
                    b.getAuthor());

            map.put("reason",
                    buildReason(userId, Math.toIntExact(b.getId())));

            result.add(map);
        }

        return result;
    }



    /**
     * 推荐解释核心逻辑
     * 找最相似的一本书
     */
    private String buildReason(
            int userId,
            int bookId){

        List<Rating> ratings =
                ratingRepository.findAll();

        Integer favoriteBookId =
                Math.toIntExact(ratings.stream()
                        .filter(r -> r.getUserId() == userId)
                        .max(
                                Comparator.comparingDouble(
                                        Rating::getScore))
                        .map(Rating::getBookId)
                        .orElse(null));


        if(favoriteBookId==null){

            return "新用户推荐";
        }


        Book favBook =
                bookRepository.findById(
                                Long.valueOf(favoriteBookId))
                        .orElse(null);


        if(favBook==null){

            return "推荐系统计算结果";
        }


        return "因为你喜欢《"
                +favBook.getName()
                +"》，推荐该书";
    }

}