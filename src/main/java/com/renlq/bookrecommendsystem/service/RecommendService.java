package com.renlq.bookrecommendsystem.service;

import com.renlq.bookrecommendsystem.entity.Book;
import com.renlq.bookrecommendsystem.entity.Rating;
import com.renlq.bookrecommendsystem.entity.RecommendationResult;
import com.renlq.bookrecommendsystem.repository.BookRepository;
import com.renlq.bookrecommendsystem.repository.BorrowRecordRepository;
import com.renlq.bookrecommendsystem.repository.RatingRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.File;
import java.util.*;
import java.util.stream.Collectors;


@Service
@RequiredArgsConstructor
public class RecommendService {
    private static final Logger logger = LoggerFactory.getLogger(RecommendService.class);
    private double currentAlpha = 0.6; // 默认值
    private final ConfigService configService;
    private final BookRepository bookRepository;
    private final RatingRepository ratingRepository;
    private final BorrowRecordRepository borrowRecordRepository;
    
    public void setAlpha(double alpha){
        this.currentAlpha = alpha;
    }
    public double getAlpha(){
        return currentAlpha;
    }
    private List<Book> getHotBooks() {

        List<Rating> ratings = ratingRepository.findAll();

        Map<Long, Long> countMap = ratings.stream()
                .collect(Collectors.groupingBy(
                        Rating::getBookId,
                        Collectors.counting()
                ));

        List<Long> hotBookIds = countMap.entrySet().stream()
                .sorted((a, b) -> Long.compare(b.getValue(), a.getValue()))
                .limit(5)
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());

        return bookRepository.findAllById(hotBookIds);
    }

    // 余弦相似度
    private double calculateSimilarity(Map<Long, Integer> user1,
                                       Map<Long, Integer> user2) {

        Set<Long> commonItems = new HashSet<>(user1.keySet());
        commonItems.retainAll(user2.keySet());

        if (commonItems.isEmpty()) return 0.0;

        double avg1 = getUserAverage(user1);
        double avg2 = getUserAverage(user2);

        double numerator = 0.0;
        double denom1 = 0.0;
        double denom2 = 0.0;

        for (Long item : commonItems) {
            double diff1 = user1.get(item) - avg1;
            double diff2 = user2.get(item) - avg2;

            numerator += diff1 * diff2;
            denom1 += diff1 * diff1;
            denom2 += diff2 * diff2;
        }

        if (denom1 == 0 || denom2 == 0) return 0.0;

        return numerator / (Math.sqrt(denom1) * Math.sqrt(denom2));
    }

    private Map<Long, Double> normalize(Map<Long, Double> scoreMap) {    //Min-Max归一化

        if (scoreMap.isEmpty()) return scoreMap;

        double min = Collections.min(scoreMap.values());
        double max = Collections.max(scoreMap.values());

        Map<Long, Double> normalized = new HashMap<>();

        for (Map.Entry<Long, Double> entry : scoreMap.entrySet()) {

            double value = entry.getValue();

            double norm;

            if (max - min == 0) {
                norm = 0.0;
            } else {
                norm = (value - min) / (max - min);
            }

            normalized.put(entry.getKey(), norm);
        }

        return normalized;
    }

    private double getUserAverage(Map<Long, Integer> ratings) {
        return ratings.values().stream()
                .mapToInt(Integer::intValue)
                .average()
                .orElse(0.0);
    }

    // 构建用户-物品评分映射
    private Map<Long, Map<Long, Integer>> buildUserItemMap(List<Rating> ratings) {
        Map<Long, Map<Long, Integer>> userItemMap = new HashMap<>();
        for (Rating rating : ratings) {
            userItemMap
                    .computeIfAbsent(rating.getUserId(), k -> new HashMap<>())
                    .put(rating.getBookId(), rating.getScore());
        }
        return userItemMap;
    }

    // 构建物品-用户评分映射
    private Map<Long, Map<Long, Integer>> buildItemUserMap(List<Rating> ratings) {
        Map<Long, Map<Long, Integer>> itemUserMap = new HashMap<>();
        for (Rating rating : ratings) {
            itemUserMap
                    .computeIfAbsent(rating.getBookId(), k -> new HashMap<>())
                    .put(rating.getUserId(), rating.getScore());
        }
        return itemUserMap;
    }

    // 计算全局平均评分
    private double calculateGlobalAverage(List<Rating> ratings) {
        return ratings.stream()
                .mapToInt(Rating::getScore)
                .average()
                .orElse(0.0);
    }

    // 计算用户评分偏置（复用预构建的userItemMap和globalAvg）
    private double calculateUserBias(Long userId,
                                     Map<Long, Map<Long, Integer>> userItemMap,
                                     double globalAvg) {
        Map<Long, Integer> userRatings = userItemMap.get(userId);
        if (userRatings == null || userRatings.isEmpty()) {
            return 0.0; // 没有评分记录，偏置为0
        }
        double userAvg = getUserAverage(userRatings);
        return userAvg - globalAvg;
    }

    // 基于评分偏置的推荐算法
    private Map<Long, Double> getBiasAwareScoreMap(Long userId, List<Rating> ratings) {
        // 预先构建映射，避免重复构建
        Map<Long, Map<Long, Integer>> userItemMap = buildUserItemMap(ratings);
        Map<Long, Map<Long, Integer>> itemUserMap = buildItemUserMap(ratings);

        // 预先计算所有用户的平均评分，避免重复计算
        Map<Long, Double> userAverageMap = new HashMap<>();
        for (Long uid : userItemMap.keySet()) {
            userAverageMap.put(uid, getUserAverage(userItemMap.get(uid)));
        }

        Map<Long, Integer> targetRatings = userItemMap.get(userId);
        if (targetRatings == null || targetRatings.isEmpty()) {
            return new HashMap<>();
        }

        Map<Long, Double> scoreMap = new HashMap<>();

        // 对每个物品计算基于偏置的推荐分数
        for (Long bookId : itemUserMap.keySet()) {
            if (targetRatings.containsKey(bookId)) continue; // 跳过已评分的物品

            double score = 0.0;
            int count = 0;

            // 找到对该物品评分的用户
            Map<Long, Integer> bookRatings = itemUserMap.get(bookId);
            for (Map.Entry<Long, Integer> entry : bookRatings.entrySet()) {
                Long otherUserId = entry.getKey();
                if (otherUserId.equals(userId)) continue;

                // 计算与其他用户的相似度
                double similarity = calculateSimilarity(
                        targetRatings,
                        userItemMap.get(otherUserId)
                );

                if (similarity > 0) {
                    // 直接从map中获取其他用户的平均评分，避免重复计算
                    double otherUserAvg = userAverageMap.get(otherUserId);
                    // 调整评分：减去其他用户的平均评分
                    double adjustedRating = entry.getValue() - otherUserAvg;
                    // 加权求和
                    score += similarity * adjustedRating;
                    count++;
                }
            }

            if (count > 0) {
                // 直接从map中获取当前用户的平均评分，避免重复计算
                double userAvg = userAverageMap.get(userId);
                // 最终预测评分：用户平均评分 + 加权调整分数
                double predictedScore = userAvg + score / count;
                scoreMap.put(bookId, predictedScore);
            }
        }

        return scoreMap;
    }

    private Map<Long, Double> getBiasAwareScoreMap(Long userId) {
        return getBiasAwareScoreMap(userId, ratingRepository.findAll());
    }

    private double calculateItemSimilarity(Map<Long, Integer> item1,
                                           Map<Long, Integer> item2) {

        Set<Long> commonUsers = new HashSet<>(item1.keySet());
        commonUsers.retainAll(item2.keySet());

        if (commonUsers.isEmpty()) return 0.0;

        double dotProduct = 0.0;
        double norm1 = 0.0;
        double norm2 = 0.0;

        for (Long user : commonUsers) {
            dotProduct += item1.get(user) * item2.get(user);
        }

        for (int score : item1.values()) {
            norm1 += score * score;
        }

        for (int score : item2.values()) {
            norm2 += score * score;
        }

        return dotProduct / (Math.sqrt(norm1) * Math.sqrt(norm2));
    }


    private Map<Long, Double> getUserCFScoreMap(Long userId, List<Rating> ratings) {

        Map<Long, Map<Long, Integer>> userItemMap = new HashMap<>();

        for (Rating rating : ratings) {
            userItemMap
                    .computeIfAbsent(rating.getUserId(), k -> new HashMap<>())
                    .put(rating.getBookId(), rating.getScore());
        }

        Map<Long, Integer> targetRatings = userItemMap.get(userId);

        if (targetRatings == null || targetRatings.isEmpty()) {
            // 用户没有评分，直接返回空map
            return new HashMap<>();
        }
        // 1️⃣ 计算所有用户相似度
        Map<Long, Double> similarityMap = new HashMap<>();

        for (Long otherUserId : userItemMap.keySet()) {
            if (otherUserId.equals(userId)) continue;

            double similarity = calculateSimilarity(
                    targetRatings,
                    userItemMap.get(otherUserId)
            );

            if (similarity > 0) {
                similarityMap.put(otherUserId, similarity);
            }
        }

        // 2️⃣ 选前3个最相似用户
        List<Long> topUsers = similarityMap.entrySet().stream()
                .sorted((a, b) -> Double.compare(b.getValue(), a.getValue()))
                .limit(3)
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());

        Map<Long, Double> scoreMap = new HashMap<>();

        // 3️⃣ 预测评分
        for (Long similarUser : topUsers) {
            double similarity = similarityMap.get(similarUser);
            Map<Long, Integer> similarRatings = userItemMap.get(similarUser);

            for (Long bookId : similarRatings.keySet()) {

                if (targetRatings.containsKey(bookId)) continue;

                scoreMap.merge(
                        bookId,
                        similarity * similarRatings.get(bookId),
                        Double::sum
                );
            }
        }

        return scoreMap;

    }

    private Map<Long, Double> getUserCFScoreMap(Long userId) {
        return getUserCFScoreMap(userId, ratingRepository.findAll());
    }

    private Map<Long, Double> getItemCFScoreMap(Long userId, List<Rating> ratings) {

        // 构建 用户 -> (书 -> 评分)
        Map<Long, Map<Long, Integer>> userItemMap = new HashMap<>();

        for (Rating rating : ratings) {
            userItemMap
                    .computeIfAbsent(rating.getUserId(), k -> new HashMap<>())
                    .put(rating.getBookId(), rating.getScore());
        }


        Map<Long, Integer> targetRatings = userItemMap.get(userId);

        // 构建 书 -> (用户 -> 评分)
        Map<Long, Map<Long, Integer>> itemUserMap = new HashMap<>();

        for (Rating rating : ratings) {
            itemUserMap
                    .computeIfAbsent(rating.getBookId(), k -> new HashMap<>())
                    .put(rating.getUserId(), rating.getScore());
        }

        Map<Long, Double> scoreMap = new HashMap<>();

        for (Long bookId : targetRatings.keySet()) {

            Map<Long, Integer> usersForBook = itemUserMap.get(bookId);

            for (Long otherBookId : itemUserMap.keySet()) {

                if (targetRatings.containsKey(otherBookId)) continue;
                if (bookId.equals(otherBookId)) continue;

                double similarity = calculateItemSimilarity(
                        itemUserMap.get(bookId),
                        itemUserMap.get(otherBookId)
                );

                if (similarity <= 0) continue;

                scoreMap.merge(
                        otherBookId,
                        similarity * targetRatings.get(bookId),
                        Double::sum
                );
            }

        }
        return scoreMap;
    }

    private Map<Long, Double> getItemCFScoreMap(Long userId) {
        return getItemCFScoreMap(userId, ratingRepository.findAll());
    }

    private double calculateBookSimilarity(
        Long book1,
        Long book2) {

    List<Rating> ratings =
            ratingRepository.findAll();

    Map<Long,Integer> item1 =
            new HashMap<>();

    Map<Long,Integer> item2 =
            new HashMap<>();


    for (Rating r : ratings) {

        if (r.getBookId().equals(book1)) {

            item1.put(
                    r.getUserId(),
                    r.getScore()
            );
        }

        if (r.getBookId().equals(book2)) {

            item2.put(
                    r.getUserId(),
                    r.getScore()
            );
        }
    }

    if (item1.isEmpty() || item2.isEmpty())
        return 0;

    return calculateItemSimilarity(
            item1,
            item2
    );
}

    public List<Book> hybridRecommend(Long userId) {
        return hybridRecommend(userId, false);
    }

    // 支持评分偏置的混合推荐算法
    public List<Book> hybridRecommend(Long userId, boolean useBias) {

        double alpha = configService.getAlpha();

        Map<Long, Double> userMap = normalize(getUserCFScoreMap(userId));
        if (userMap.isEmpty()) {
            // UserCF 没有数据，直接用热门推荐
            return getHotBooks();
        }
        
        Map<Long, Double> itemMap = normalize(getItemCFScoreMap(userId));
        Map<Long, Double> biasMap = useBias ? normalize(getBiasAwareScoreMap(userId)) : new HashMap<>();

        Map<Long, Double> finalScoreMap = new HashMap<>();

        // 合并所有候选书
        Set<Long> allBookIds = new HashSet<>();
        allBookIds.addAll(userMap.keySet());
        allBookIds.addAll(itemMap.keySet());
        if (useBias) {
            allBookIds.addAll(biasMap.keySet());
        }

        for (Long bookId : allBookIds) {

            double userScore = userMap.getOrDefault(bookId, 0.0);
            double itemScore = itemMap.getOrDefault(bookId, 0.0);
            double biasScore = useBias ? biasMap.getOrDefault(bookId, 0.0) : 0.0;

            double finalScore;
            if (useBias) {
                // 三算法融合：UserCF + ItemCF + 偏置算法
                double beta = configService.getBeta();
                finalScore = alpha * userScore + (1 - alpha - beta) * itemScore + beta * biasScore;
            } else {
                // 原有的两算法融合
                finalScore = alpha * userScore + (1 - alpha) * itemScore;
            }

            finalScoreMap.put(bookId, finalScore);
        }

        // 排序取前5
        List<Long> topIds = finalScoreMap.entrySet().stream()
                .sorted((a, b) -> Double.compare(b.getValue(), a.getValue()))
                .limit(5)
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());
        
        logger.debug("UserCF: {}", userMap);
        logger.debug("ItemCF: {}", itemMap);
        if (useBias) {
            logger.debug("BiasAware: {}", biasMap);
        }

        if (topIds.isEmpty()) {
            logger.info("用户已评分所有书籍，无候选推荐。");
            return getHotBooks();
        }
        logger.debug("alpha={}, useBias={}", alpha, useBias);
        return bookRepository.findAllById(topIds);
    }



    public double evaluateAllUsers(int k, double alpha) {
        return evaluateAllUsers(k, alpha, 0.0);
    }

    public double evaluateAllUsers(int k, double alpha, double beta) {

        Set<Long> userIds = ratingRepository.findAll()
                .stream()
                .map(Rating::getUserId)
                .collect(Collectors.toSet());

        double total = 0.0;
        int count = 0;

        for (Long userId : userIds) {
            double precision;
            if (beta > 0) {
                precision = precisionAtKWithBias(userId, k, alpha, beta);
            } else {
                precision = precisionAtK(userId, k, alpha);
            }
            total += precision;
            count++;
        }

        if (count == 0) return 0.0;

        return total / count;
    }

    public double evaluateAllUsersWithBias(int k, double alpha, double beta) {

        Set<Long> userIds = ratingRepository.findAll()
                .stream()
                .map(Rating::getUserId)
                .collect(Collectors.toSet());

        double total = 0.0;
        int count = 0;

        for (Long userId : userIds) {

            double precision = precisionAtKWithBias(userId, k, alpha, beta);

            total += precision;
            count++;
        }

        if (count == 0) return 0.0;

        return total / count;
    }

    public double precisionAtK(Long userId, int k, double alpha) {

        List<Rating> userRatings = ratingRepository.findAll()
                .stream()
                .filter(r -> r.getUserId().equals(userId))
                .collect(Collectors.toList());

        // 如果评分太少无法测试
        if (userRatings.size() < 2) {
            return 0.0;
        }

        // 1️⃣ 随机隐藏一条评分
        Rating testRating = userRatings.get(0); // 简化处理，直接取第一条
        Long hiddenBookId = testRating.getBookId();

        // 2️⃣ 构造训练数据（移除隐藏评分）
        List<Rating> trainingRatings = new ArrayList<>(ratingRepository.findAll());
        trainingRatings.removeIf(r ->
                r.getUserId().equals(userId)
                        && r.getBookId().equals(hiddenBookId)
        );

        // 3️⃣ 用训练数据计算推荐
        List<Book> recommendedBooks =
                hybridRecommendWithCustomRatings(userId, trainingRatings, alpha);

        // 4️⃣ 取前K个
        List<Long> topK = recommendedBooks.stream()
                .limit(k)
                .map(Book::getId)
                .collect(Collectors.toList());

        // 5️⃣ 判断是否命中
        if (topK.contains(hiddenBookId)) {
            return 1.0 / k;
        } else {
            return 0.0;
        }
    }

    public double precisionAtKWithBias(Long userId, int k, double alpha, double beta) {

        List<Rating> userRatings = ratingRepository.findAll()
                .stream()
                .filter(r -> r.getUserId().equals(userId))
                .collect(Collectors.toList());

        // 如果评分太少无法测试
        if (userRatings.size() < 2) {
            return 0.0;
        }

        // 1️⃣ 随机隐藏一条评分
        Rating testRating = userRatings.get(0); // 简化处理，直接取第一条
        Long hiddenBookId = testRating.getBookId();

        // 2️⃣ 构造训练数据（移除隐藏评分）
        List<Rating> trainingRatings = new ArrayList<>(ratingRepository.findAll());
        trainingRatings.removeIf(r ->
                r.getUserId().equals(userId)
                        && r.getBookId().equals(hiddenBookId)
        );

        // 3️⃣ 用训练数据计算推荐（带偏置）
        List<Book> recommendedBooks =
                hybridRecommendWithCustomRatings(userId, trainingRatings, alpha, true, beta);

        // 4️⃣ 取前K个
        List<Long> topK = recommendedBooks.stream()
                .limit(k)
                .map(Book::getId)
                .collect(Collectors.toList());

        // 5️⃣ 判断是否命中
        if (topK.contains(hiddenBookId)) {
            return 1.0 / k;
        } else {
            return 0.0;
        }
    }

    private List<Book> hybridRecommendWithCustomRatings(Long userId, List<Rating> ratings, double alpha) {
        return hybridRecommendWithCustomRatings(userId, ratings, alpha, false);
    }

    // 支持评分偏置的混合推荐算法（自定义评分数据）
    private List<Book> hybridRecommendWithCustomRatings(Long userId, List<Rating> ratings, double alpha, boolean useBias) {
        return hybridRecommendWithCustomRatings(userId, ratings, alpha, useBias, configService.getBeta());
    }

    // 支持评分偏置的混合推荐算法（自定义评分数据和beta值）
    private List<Book> hybridRecommendWithCustomRatings(Long userId, List<Rating> ratings, double alpha, boolean useBias, double beta) {

        Map<Long, Double> userMap = normalize(getUserCFScoreMap(userId, ratings));
        if (userMap.isEmpty()) {
            // UserCF 没有数据，直接用热门推荐
            return getHotBooks();
        }
        
        Map<Long, Double> itemMap = normalize(getItemCFScoreMap(userId, ratings));
        Map<Long, Double> biasMap = useBias ? normalize(getBiasAwareScoreMap(userId, ratings)) : new HashMap<>();

        Map<Long, Double> finalScoreMap = new HashMap<>();

        // 合并所有候选书
        Set<Long> allBookIds = new HashSet<>();
        allBookIds.addAll(userMap.keySet());
        allBookIds.addAll(itemMap.keySet());
        if (useBias) {
            allBookIds.addAll(biasMap.keySet());
        }

        for (Long bookId : allBookIds) {

            double userScore = userMap.getOrDefault(bookId, 0.0);
            double itemScore = itemMap.getOrDefault(bookId, 0.0);
            double biasScore = useBias ? biasMap.getOrDefault(bookId, 0.0) : 0.0;

            double finalScore;
            if (useBias) {
                // 三算法融合：UserCF + ItemCF + 偏置算法
                finalScore = alpha * userScore + (1 - alpha - beta) * itemScore + beta * biasScore;
            } else {
                // 原有的两算法融合
                finalScore = alpha * userScore + (1 - alpha) * itemScore;
            }

            finalScoreMap.put(bookId, finalScore);
        }

        // 排序取前5
        List<Long> topIds = finalScoreMap.entrySet().stream()
                .sorted((a, b) -> Double.compare(b.getValue(), a.getValue()))
                .limit(5)
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());
        
        logger.debug("UserCF: {}", userMap);
        logger.debug("ItemCF: {}", itemMap);
        if (useBias) {
            logger.debug("BiasAware: {}", biasMap);
        }

        if (topIds.isEmpty()) {
            logger.info("用户已评分所有书籍，无候选推荐。");
            return getHotBooks();
        }
        return bookRepository.findAllById(topIds);
    }

    public Map<Double, Double> alphaExperiment(int k) {    //alpha值实验

    Map<Double, Double> result = new LinkedHashMap<>();

    for (double alpha = 0; alpha <= 1; alpha += 0.1) {

        double precision = evaluateAllUsers(k, alpha);

        result.put(
                Math.round(alpha * 10) / 10.0,
                precision
        );

        logger.debug("alpha={} precision={}", alpha, precision);
    }

    return result;
    }
    public Map<Integer, Double> topKExperiment(double alpha) {     //推荐列表长度对系统性能的影响
        return topKExperiment(alpha, 0.0);
    }

    public Map<Integer, Double> topKExperiment(double alpha, double beta) {     //推荐列表长度对系统性能的影响

    Map<Integer, Double> result = new LinkedHashMap<>();

    int[] ks = {3, 5, 7, 10};

    for (int k : ks) {

        double precision = evaluateAllUsers(k, alpha, beta);

        result.put(k, precision);

        logger.debug("K={} precision={}", k, precision);
    }

    return result;
    }



    public List<Map<String, Object>> alphaBetaExperiment(int k) {    //alpha和beta融合实验

        List<Map<String, Object>> result = new ArrayList<>();

        for (double alpha = 0; alpha <= 1; alpha += 0.1) {
            for (double beta = 0; beta <= 1 - alpha; beta += 0.1) {
                double precision = evaluateAllUsers(k, alpha, beta);
                
                Map<String, Object> entry = new HashMap<>();
                entry.put("alpha", Math.round(alpha * 10) / 10.0);
                entry.put("beta", Math.round(beta * 10) / 10.0);
                entry.put("precision", precision);
                
                result.add(entry);
                
                logger.debug("alpha={}, beta={}, precision={}", alpha, beta, precision);
            }
        }

        return result;
    }

    public Map<Double, Map<Double, Double>> alphaBetaMatrix(int k) { 

        Map<Double, Map<Double, Double>> result = new LinkedHashMap<>(); 

        for (double alpha = 0; alpha <= 1; alpha += 0.1) { 

            double a = Math.round(alpha * 10) / 10.0; 
            result.put(a, new LinkedHashMap<>()); 

            for (double beta = 0; beta <= 1 - alpha; beta += 0.1) { 

                double b = Math.round(beta * 10) / 10.0; 

                double precision = evaluateAllUsers(k, a, b); 

                result.get(a).put(b, precision); 

                logger.debug("alpha={}, beta={}, precision={}", a, b, precision); 
            } 
        } 

        return result; 
    }

    public Map<String, Double> algorithmComparison(int k) { 

        Map<String, Double> result = new LinkedHashMap<>(); 

        // 纯 UserCF 
        result.put("UserCF", evaluateAllUsers(k, 1.0, 0.0)); 

        // 纯 ItemCF 
        result.put("ItemCF", evaluateAllUsers(k, 0.0, 0.0)); 

        // UserCF + ItemCF 
        result.put("UserCF+ItemCF", evaluateAllUsers(k, 0.5, 0.0)); 

        // 三算法融合（你的核心算法） 
        result.put("Hybrid (User+Item+Bias)", evaluateAllUsers(k, 0.2, 0.2)); 

        return result; 
    }




public void generateAllReports() throws Exception {

    // α实验
    Map<Double, Double> alphaData =
            alphaExperiment(5);

    // TopK实验
    Map<Integer, Double> kData =
            topKExperiment(configService.getAlpha());



    // Alpha和Beta融合实验
    List<Map<String, Object>> alphaBetaData =
            alphaBetaExperiment(5);

    String basePath = "D:/Desktop/Final design/book-recommend-system/report/";

    new File(basePath).mkdirs();

    // ===== α实验 =====

    com.renlq.bookrecommendsystem.util.ExperimentUtil.generateChart(
            "Alpha Experiment",
            "Alpha",
            "Precision@5",
            alphaData,
            basePath + "alpha_chart.png"
    );

    com.renlq.bookrecommendsystem.util.ExperimentUtil.exportExcel(
            "AlphaExperiment",
            alphaData,
            basePath + "alpha.xlsx"
    );



    // ===== TopK实验 =====

    com.renlq.bookrecommendsystem.util.ExperimentUtil.generateChart(
            "TopK Experiment",
            "K",
            "Precision@K",
            kData,
            basePath + "topk_chart.png"
    );

    com.renlq.bookrecommendsystem.util.ExperimentUtil.exportExcel(
            "TopKExperiment",
            kData,
            basePath + "topk.xlsx"
    );



    // ===== Alpha和Beta融合实验 =====
    com.renlq.bookrecommendsystem.util.ExperimentUtil.exportAlphaBetaExcel(
            "AlphaBetaExperiment",
            alphaBetaData,
            basePath + "alpha_beta.xlsx"
    );

    // 生成热力图
    Map<Double, Map<Double, Double>> heatmapData = alphaBetaMatrix(5);
    com.renlq.bookrecommendsystem.util.ExperimentUtil.generateHeatMap(
            "Alpha-Beta Heatmap",
            heatmapData,
            basePath + "alpha_beta_heatmap.png"
    );

    // 算法对比实验
    Map<String, Double> algorithmData = algorithmComparison(5);
    com.renlq.bookrecommendsystem.util.ExperimentUtil.generateChart(
            "Algorithm Comparison",
            "Algorithm",
            "Precision@5",
            algorithmData,
            basePath + "algorithm_chart.png"
    );

    com.renlq.bookrecommendsystem.util.ExperimentUtil.exportExcel(
            "AlgorithmComparison",
            algorithmData,
            basePath + "algorithm.xlsx"
    );

    logger.info("实验报告生成完成！");
    }
    public List<RecommendationResult> recommendWithExplanation(Long userId) {
        return recommendWithExplanation(userId, false);
    }

    // 支持评分偏置的推荐算法（带解释）
    public List<RecommendationResult> recommendWithExplanation(Long userId, boolean useBias) {

    double alpha = configService.getAlpha();

    List<Book> recommendedBooks =
        hybridRecommend(userId, useBias);;

    List<Rating> userRatings =
            ratingRepository.findAll()
                    .stream()
                    .filter(r -> r.getUserId().equals(userId))
                    .collect(Collectors.toList());

    List<RecommendationResult> results =
            new ArrayList<>();


    for (Book recBook : recommendedBooks) {

        double maxSimilarity = -1;

        Book bestMatchBook = null;


        for (Rating rating : userRatings) {

            Book ratedBook =
                    bookRepository.findById(
                            rating.getBookId()
                    ).orElse(null);

            if (ratedBook == null) continue;


            double similarity =
                    calculateBookSimilarity(
                            recBook.getId(),
                            ratedBook.getId()
                    );

            if (similarity > maxSimilarity) {

                maxSimilarity = similarity;

                bestMatchBook = ratedBook;
            }
        }


        String reason;

        if (bestMatchBook != null) {

            reason = "因为你喜欢《"
                    + bestMatchBook.getName()
                    + "》 (相似度="
                    + String.format("%.2f", maxSimilarity)
                    + ")";
        }
        else {

            reason = "基于热门推荐";
        }


        results.add(
                new RecommendationResult(
                        recBook,
                        reason
                )
        );

    }
    for (RecommendationResult r : results) {

    Long bookId = r.getBook().getId();

    Double avg = ratingRepository.getAverageScore(bookId);
    Long hot = borrowRecordRepository.countByBookId(bookId);

    r.getBook().setAvgScore(avg != null ? avg : 0.0);
    r.getBook().setHotCount(hot != null ? hot : 0);
    }
    return results;
    }
}