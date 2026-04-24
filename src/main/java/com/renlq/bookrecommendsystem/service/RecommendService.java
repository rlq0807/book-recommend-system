package com.renlq.bookrecommendsystem.service;

import com.renlq.bookrecommendsystem.entity.Book;
import com.renlq.bookrecommendsystem.entity.Rating;
import com.renlq.bookrecommendsystem.entity.RecommendationResult;
import com.renlq.bookrecommendsystem.entity.User;
import com.renlq.bookrecommendsystem.repository.BookRepository;
import com.renlq.bookrecommendsystem.repository.BorrowRecordRepository;
import com.renlq.bookrecommendsystem.repository.RatingRepository;
import com.renlq.bookrecommendsystem.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.File;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
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
    private final UserRepository userRepository;

    private volatile Map<Long, Map<Long, Integer>> userItemMapCache = null;
    private volatile Map<Long, Map<Long, Integer>> itemUserMapCache = null;
    private volatile Map<Long, Double> userAverageCache = null;
    private volatile Map<Long, Double> itemAverageCache = null;
    private volatile Double globalAverageCache = null;
    private volatile long cacheTimestamp = 0;
    private static final long CACHE_EXPIRE_TIME = 60000;

    private final Map<String, Double> userSimilarityCache = new ConcurrentHashMap<>();
    private final Map<String, Double> itemSimilarityCache = new ConcurrentHashMap<>();

    private synchronized Map<Long, Map<Long, Integer>> getUserItemMap() {
        if (userItemMapCache == null || isCacheExpired()) {
            refreshCache();
        }
        return userItemMapCache;
    }

    private synchronized Map<Long, Map<Long, Integer>> getItemUserMap() {
        if (itemUserMapCache == null || isCacheExpired()) {
            refreshCache();
        }
        return itemUserMapCache;
    }

    private synchronized Map<Long, Double> getUserAverageMap() {
        if (userAverageCache == null || isCacheExpired()) {
            refreshCache();
        }
        return userAverageCache;
    }

    private synchronized Map<Long, Double> getItemAverageMap() {
        if (itemAverageCache == null || isCacheExpired()) {
            refreshCache();
        }
        return itemAverageCache;
    }

    private synchronized double getGlobalAverage() {
        if (globalAverageCache == null || isCacheExpired()) {
            refreshCache();
        }
        return globalAverageCache;
    }

    private String getUserSimilarityKey(Long user1, Long user2) {
        if (user1 == null || user2 == null) {
            return null;
        }
        return user1 < user2 ? user1 + "_" + user2 : user2 + "_" + user1;
    }

    private String getItemSimilarityKey(Long item1, Long item2) {
        if (item1 == null || item2 == null) {
            return null;
        }
        return item1 < item2 ? item1 + "_" + item2 : item2 + "_" + item1;
    }

    private boolean isCacheExpired() {
        return System.currentTimeMillis() - cacheTimestamp > CACHE_EXPIRE_TIME;
    }

    public void refreshCache() {
        List<Rating> ratings = ratingRepository.findAll();
        userItemMapCache = buildUserItemMap(ratings);
        itemUserMapCache = buildItemUserMap(ratings);
        userAverageCache = new HashMap<>();
        for (Map.Entry<Long, Map<Long, Integer>> entry : userItemMapCache.entrySet()) {
            userAverageCache.put(entry.getKey(), getUserAverage(entry.getValue()));
        }
        itemAverageCache = new HashMap<>();
        for (Map.Entry<Long, Map<Long, Integer>> entry : itemUserMapCache.entrySet()) {
            itemAverageCache.put(entry.getKey(), getUserAverage(entry.getValue()));
        }
        globalAverageCache = ratings.stream()
                .mapToInt(Rating::getScore)
                .average()
                .orElse(0.0);
        cacheTimestamp = System.currentTimeMillis();
        logger.info("评分数据缓存已刷新，共 {} 条评分记录", ratings.size());
    }

    public void invalidateCache() {
        this.userItemMapCache = null;
        this.itemUserMapCache = null;
        this.userAverageCache = null;
        this.itemAverageCache = null;
        this.globalAverageCache = null;
        this.cacheTimestamp = 0;
        userSimilarityCache.clear();
        itemSimilarityCache.clear();
        logger.info("评分数据缓存已失效");
    }

    public void setAlpha(double alpha){
        this.currentAlpha = alpha;
    }
    public double getAlpha(){
        return currentAlpha;
    }
    private List<Book> getHotBooks() {

        Map<Long, Map<Long, Integer>> userItemMap = getUserItemMap();

        Map<Long, Long> countMap = new HashMap<>();
        for (Map<Long, Integer> itemMap : userItemMap.values()) {
            for (Long bookId : itemMap.keySet()) {
                countMap.merge(bookId, 1L, Long::sum);
            }
        }

        List<Long> hotBookIds = countMap.entrySet().stream()
                .sorted((a, b) -> Long.compare(b.getValue(), a.getValue()))
                .limit(5)
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());

        return bookRepository.findAllById(hotBookIds);
    }

    private List<Book> getHighScoreBooks() {
        Map<Long, Map<Long, Integer>> userItemMap = getUserItemMap();
        Map<Long, Double> avgScoreMap = new HashMap<>();
        Map<Long, Integer> ratingCountMap = new HashMap<>();

        for (Map<Long, Integer> itemMap : userItemMap.values()) {
            for (Map.Entry<Long, Integer> entry : itemMap.entrySet()) {
                Long bookId = entry.getKey();
                Integer score = entry.getValue();
                avgScoreMap.merge(bookId, (double) score, Double::sum);
                ratingCountMap.merge(bookId, 1, Integer::sum);
            }
        }

        for (Map.Entry<Long, Double> entry : avgScoreMap.entrySet()) {
            Long bookId = entry.getKey();
            int count = ratingCountMap.get(bookId);
            double avg = entry.getValue() / count;
            avgScoreMap.put(bookId, avg - Math.log(1 + count) * 0.1);
        }

        List<Long> highScoreBookIds = avgScoreMap.entrySet().stream()
                .sorted((a, b) -> Double.compare(b.getValue(), a.getValue()))
                .limit(10)
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());

        return bookRepository.findAllById(highScoreBookIds);
    }

    private List<Book> mixHotAndHighScoreBooks() {
        List<Book> hotBooks = getHotBooks();
        List<Book> highScoreBooks = getHighScoreBooks();
        List<Book> allBooks = bookRepository.findAll();

        Set<Long> selectedIds = new HashSet<>();
        List<Book> result = new ArrayList<>();

        for (Book book : hotBooks) {
            if (result.size() < 2 && selectedIds.add(book.getId())) {
                result.add(book);
            }
        }

        for (Book book : highScoreBooks) {
            if (result.size() < 4 && selectedIds.add(book.getId())) {
                result.add(book);
            }
        }

        List<Book> remainingBooks = new ArrayList<>();
        for (Book book : allBooks) {
            if (!selectedIds.contains(book.getId())) {
                remainingBooks.add(book);
            }
        }

        Collections.shuffle(remainingBooks);
        for (Book book : remainingBooks) {
            if (result.size() < 5) {
                result.add(book);
            } else {
                break;
            }
        }

        return result;
    }

    private List<String> getUserPreferredCategories(User user) {
        if (user.getPreferredCategories() == null) {
            return Collections.emptyList();
        }
        return Arrays.asList(user.getPreferredCategories().split(","));
    }

    private List<Book> recommendByCategory(User user) {
        List<String> categories = getUserPreferredCategories(user);
        if (categories.isEmpty()) {
            return Collections.emptyList();
        }

        List<Book> books = bookRepository.findByCategoryIn(categories);

        // 排序：综合评分和热门度
        books.sort((a, b) -> {
            double scoreA = (a.getAvgScore() != null ? a.getAvgScore() : 0.0) + Math.log(1 + (a.getHotCount() != null ? a.getHotCount() : 0));
            double scoreB = (b.getAvgScore() != null ? b.getAvgScore() : 0.0) + Math.log(1 + (b.getHotCount() != null ? b.getHotCount() : 0));
            return Double.compare(scoreB, scoreA);
        });

        return books.stream().limit(5).collect(Collectors.toList());
    }

    private List<Book> hybridRecommendInternal(Long userId, boolean useBias) {
        double alpha = configService.getAlpha();

        Map<Long, Double> userMap = normalize(getUserCFScoreMap(userId));
        if (userMap.isEmpty()) {
            return mixHotAndHighScoreBooks();
        }

        Map<Long, Double> itemMap = normalize(getItemCFScoreMap(userId));
        Map<Long, Double> biasMap = useBias ? normalize(getBiasAwareScoreMap(userId)) : new HashMap<>();

        Map<Long, Double> finalScoreMap = new HashMap<>();

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
                double beta = configService.getBeta();
                finalScore = alpha * userScore + (1 - alpha - beta) * itemScore + beta * biasScore;
            } else {
                finalScore = alpha * userScore + (1 - alpha) * itemScore;
            }

            finalScoreMap.put(bookId, finalScore);
        }

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
            return mixHotAndHighScoreBooks();
        }
        logger.debug("alpha={}, useBias={}", alpha, useBias);
        return bookRepository.findAllById(topIds);
    }

    private double calculateSimilarity(Long userId1, Long userId2,
                                       Map<Long, Integer> user1,
                                       Map<Long, Integer> user2) {

        String key = getUserSimilarityKey(userId1, userId2);
        if (key != null && userSimilarityCache.containsKey(key)) {
            return userSimilarityCache.get(key);
        }

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

        double rawSimilarity = numerator / (Math.sqrt(denom1) * Math.sqrt(denom2));

        int commonSize = commonItems.size();
        double confidencePenalty = commonSize / (commonSize + 10.0);

        double similarity = rawSimilarity * confidencePenalty;
        if (key != null) {
            userSimilarityCache.put(key, similarity);
        }
        return similarity;
    }

    private double calculateSimilarity(Map<Long, Integer> user1,
                                       Map<Long, Integer> user2) {
        return calculateSimilarity(null, null, user1, user2);
    }

    private Map<Long, Double> normalize(Map<Long, Double> scoreMap) {

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

    private Map<Long, Map<Long, Integer>> buildUserItemMap(List<Rating> ratings) {
        Map<Long, Map<Long, Integer>> userItemMap = new HashMap<>();
        for (Rating rating : ratings) {
            userItemMap
                    .computeIfAbsent(rating.getUserId(), k -> new HashMap<>())
                    .put(rating.getBookId(), rating.getScore());
        }
        return userItemMap;
    }

    private Map<Long, Map<Long, Integer>> buildItemUserMap(List<Rating> ratings) {
        Map<Long, Map<Long, Integer>> itemUserMap = new HashMap<>();
        for (Rating rating : ratings) {
            itemUserMap
                    .computeIfAbsent(rating.getBookId(), k -> new HashMap<>())
                    .put(rating.getUserId(), rating.getScore());
        }
        return itemUserMap;
    }

    private double calculateGlobalAverage(List<Rating> ratings) {
        return ratings.stream()
                .mapToInt(Rating::getScore)
                .average()
                .orElse(0.0);
    }

    private double calculateUserBias(Long userId,
                                     Map<Long, Map<Long, Integer>> userItemMap,
                                     double globalAvg) {
        Map<Long, Integer> userRatings = userItemMap.get(userId);
        if (userRatings == null || userRatings.isEmpty()) {
            return 0.0;
        }
        double userAvg = getUserAverage(userRatings);
        return userAvg - globalAvg;
    }

    private Map<Long, Double> getBiasAwareScoreMap(Long userId, List<Rating> ratings) {
        Map<Long, Map<Long, Integer>> userItemMap = buildUserItemMap(ratings);
        Map<Long, Map<Long, Integer>> itemUserMap = buildItemUserMap(ratings);

        Map<Long, Double> userAverageMap = new HashMap<>();
        for (Long uid : userItemMap.keySet()) {
            userAverageMap.put(uid, getUserAverage(userItemMap.get(uid)));
        }

        Map<Long, Double> itemAverageMap = new HashMap<>();
        for (Long bid : itemUserMap.keySet()) {
            itemAverageMap.put(bid, getUserAverage(itemUserMap.get(bid)));
        }

        double globalAvg = calculateGlobalAverage(ratings);

        Map<Long, Integer> targetRatings = userItemMap.get(userId);
        if (targetRatings == null || targetRatings.isEmpty()) {
            return new HashMap<>();
        }

        double userBias = userAverageMap.get(userId) - globalAvg;

        Map<Long, Double> scoreMap = new HashMap<>();

        for (Long bookId : itemUserMap.keySet()) {
            if (targetRatings.containsKey(bookId)) continue;

            double bookBias = itemAverageMap.get(bookId) - globalAvg;

            double score = 0.0;
            int count = 0;

            Map<Long, Integer> bookRatings = itemUserMap.get(bookId);
            for (Map.Entry<Long, Integer> entry : bookRatings.entrySet()) {
                Long otherUserId = entry.getKey();
                if (otherUserId.equals(userId)) continue;

                double similarity = calculateSimilarity(
                        userId,
                        otherUserId,
                        targetRatings,
                        userItemMap.get(otherUserId)
                );

                double otherUserAvg = userAverageMap.get(otherUserId);
                double adjustedRating = entry.getValue() - otherUserAvg;
                score += similarity * adjustedRating;
                count++;
            }

            if (count > 0) {
                double predictedScore = globalAvg + userBias + bookBias + score / count;
                scoreMap.put(bookId, predictedScore);
            } else {
                double predictedScore = globalAvg + userBias + bookBias;
                scoreMap.put(bookId, predictedScore);
            }
        }

        return scoreMap;
    }

    private Map<Long, Double> getBiasAwareScoreMap(Long userId) {
        Map<Long, Map<Long, Integer>> userItemMap = getUserItemMap();
        Map<Long, Map<Long, Integer>> itemUserMap = getItemUserMap();
        Map<Long, Double> userAverageMap = getUserAverageMap();
        Map<Long, Double> itemAverageMap = getItemAverageMap();
        double globalAvg = getGlobalAverage();

        Map<Long, Integer> targetRatings = userItemMap.get(userId);
        if (targetRatings == null || targetRatings.isEmpty()) {
            return new HashMap<>();
        }

        double userBias = userAverageMap.get(userId) - globalAvg;

        Map<Long, Double> scoreMap = new HashMap<>();

        for (Long bookId : itemUserMap.keySet()) {
            if (targetRatings.containsKey(bookId)) continue;

            double bookBias = itemAverageMap.get(bookId) - globalAvg;

            double score = 0.0;
            int count = 0;

            Map<Long, Integer> bookRatings = itemUserMap.get(bookId);
            for (Map.Entry<Long, Integer> entry : bookRatings.entrySet()) {
                Long otherUserId = entry.getKey();
                if (otherUserId.equals(userId)) continue;

                double similarity = calculateSimilarity(
                        userId,
                        otherUserId,
                        targetRatings,
                        userItemMap.get(otherUserId)
                );

                double otherUserAvg = userAverageMap.get(otherUserId);
                double adjustedRating = entry.getValue() - otherUserAvg;
                score += similarity * adjustedRating;
                count++;
            }

            if (count > 0) {
                double predictedScore = globalAvg + userBias + bookBias + score / count;
                scoreMap.put(bookId, predictedScore);
            } else {
                double predictedScore = globalAvg + userBias + bookBias;
                scoreMap.put(bookId, predictedScore);
            }
        }

        return scoreMap;
    }

    private double calculateItemSimilarity(Long itemId1, Long itemId2,
                                           Map<Long, Integer> item1,
                                           Map<Long, Integer> item2) {

        String key = getItemSimilarityKey(itemId1, itemId2);
        if (key != null && itemSimilarityCache.containsKey(key)) {
            return itemSimilarityCache.get(key);
        }

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

        if (norm1 == 0 || norm2 == 0) return 0.0;

        double rawSimilarity = dotProduct / (Math.sqrt(norm1) * Math.sqrt(norm2));

        int commonSize = commonUsers.size();
        double confidencePenalty = commonSize / (commonSize + 10.0);

        double similarity = rawSimilarity * confidencePenalty;
        if (key != null) {
            itemSimilarityCache.put(key, similarity);
        }
        return similarity;
    }

    private double calculateItemSimilarity(Map<Long, Integer> item1,
                                           Map<Long, Integer> item2) {
        return calculateItemSimilarity(null, null, item1, item2);
    }


    private Map<Long, Double> getUserCFScoreMap(Long userId, List<Rating> ratings) {

        Map<Long, Map<Long, Integer>> userItemMap = buildUserItemMap(ratings);
        Map<Long, Map<Long, Integer>> itemUserMap = buildItemUserMap(ratings);

        Map<Long, Integer> targetRatings = userItemMap.get(userId);

        if (targetRatings == null || targetRatings.isEmpty()) {
            return new HashMap<>();
        }

        Map<Long, Double> similarityMap = new HashMap<>();

        for (Long otherUserId : userItemMap.keySet()) {
            if (otherUserId.equals(userId)) continue;

            double similarity = calculateSimilarity(
                    userId,
                    otherUserId,
                    targetRatings,
                    userItemMap.get(otherUserId)
            );

            // 保留所有相似度，包括负相似度
            similarityMap.put(otherUserId, similarity);
        }

        List<Long> topUsers = similarityMap.entrySet().stream()
                .sorted((a, b) -> Double.compare(b.getValue(), a.getValue()))
                .limit(configService.getTopN())
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());

        Map<Long, Double> scoreMap = new HashMap<>();

        for (Long similarUser : topUsers) {
            double similarity = similarityMap.get(similarUser);
            Map<Long, Integer> similarRatings = userItemMap.get(similarUser);

            for (Long bookId : similarRatings.keySet()) {

                if (targetRatings.containsKey(bookId)) continue;

                double popularity = Math.log(1 + itemUserMap.get(bookId).size());
                double popularityPenalty = 1.0 / (1.0 + popularity);

                scoreMap.merge(
                        bookId,
                        similarity * similarRatings.get(bookId) * popularityPenalty,
                        Double::sum
                );
            }
        }

        return scoreMap;

    }

    private Map<Long, Double> getUserCFScoreMap(Long userId) {
        Map<Long, Map<Long, Integer>> userItemMap = getUserItemMap();
        Map<Long, Map<Long, Integer>> itemUserMap = getItemUserMap();
        Map<Long, Integer> targetRatings = userItemMap.get(userId);

        if (targetRatings == null || targetRatings.isEmpty()) {
            return new HashMap<>();
        }

        Map<Long, Double> similarityMap = new HashMap<>();

        for (Long otherUserId : userItemMap.keySet()) {
            if (otherUserId.equals(userId)) continue;

            double similarity = calculateSimilarity(
                    userId,
                    otherUserId,
                    targetRatings,
                    userItemMap.get(otherUserId)
            );

            // 保留所有相似度，包括负相似度
            similarityMap.put(otherUserId, similarity);
        }

        List<Long> topUsers = similarityMap.entrySet().stream()
                .sorted((a, b) -> Double.compare(b.getValue(), a.getValue()))
                .limit(configService.getTopN())
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());

        Map<Long, Double> scoreMap = new HashMap<>();

        for (Long similarUser : topUsers) {
            double similarity = similarityMap.get(similarUser);
            Map<Long, Integer> similarRatings = userItemMap.get(similarUser);

            for (Long bookId : similarRatings.keySet()) {

                if (targetRatings.containsKey(bookId)) continue;

                double popularity = Math.log(1 + itemUserMap.get(bookId).size());
                double popularityPenalty = 1.0 / (1.0 + popularity);

                scoreMap.merge(
                        bookId,
                        similarity * similarRatings.get(bookId) * popularityPenalty,
                        Double::sum
                );
            }
        }

        return scoreMap;
    }

    private Map<Long, Double> getItemCFScoreMap(Long userId, List<Rating> ratings) {

        Map<Long, Map<Long, Integer>> userItemMap = buildUserItemMap(ratings);

        Map<Long, Integer> targetRatings = userItemMap.get(userId);

        Map<Long, Map<Long, Integer>> itemUserMap = buildItemUserMap(ratings);

        Map<Long, Double> scoreMap = new HashMap<>();

        for (Long bookId : targetRatings.keySet()) {

            Map<Long, Integer> usersForBook = itemUserMap.get(bookId);

            for (Long otherBookId : itemUserMap.keySet()) {

                if (targetRatings.containsKey(otherBookId)) continue;
                if (bookId.equals(otherBookId)) continue;

                double similarity = calculateItemSimilarity(
                        bookId,
                        otherBookId,
                        itemUserMap.get(bookId),
                        itemUserMap.get(otherBookId)
                );

                // 保留所有相似度，包括负相似度
                // if (similarity <= 0) continue;

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
        Map<Long, Map<Long, Integer>> userItemMap = getUserItemMap();
        Map<Long, Map<Long, Integer>> itemUserMap = getItemUserMap();

        Map<Long, Integer> targetRatings = userItemMap.get(userId);

        Map<Long, Double> scoreMap = new HashMap<>();

        for (Long bookId : targetRatings.keySet()) {

            Map<Long, Integer> usersForBook = itemUserMap.get(bookId);

            for (Long otherBookId : itemUserMap.keySet()) {

                if (targetRatings.containsKey(otherBookId)) continue;
                if (bookId.equals(otherBookId)) continue;

                double similarity = calculateItemSimilarity(
                        bookId,
                        otherBookId,
                        usersForBook,
                        itemUserMap.get(otherBookId)
                );

                // 保留所有相似度，包括负相似度
                // if (similarity <= 0) continue;

                scoreMap.merge(
                        otherBookId,
                        similarity * targetRatings.get(bookId),
                        Double::sum
                );
            }

        }
        return scoreMap;
    }

    private double calculateBookSimilarity(
        Long book1,
        Long book2) {

        Map<Long, Map<Long, Integer>> userItemMap = getUserItemMap();

        Map<Long, Integer> item1 = new HashMap<>();
        Map<Long, Integer> item2 = new HashMap<>();

        for (Map.Entry<Long, Map<Long, Integer>> entry : userItemMap.entrySet()) {
            Long userId = entry.getKey();
            Map<Long, Integer> ratings = entry.getValue();
            if (ratings.containsKey(book1)) {
                item1.put(userId, ratings.get(book1));
            }
            if (ratings.containsKey(book2)) {
                item2.put(userId, ratings.get(book2));
            }
        }

        if (item1.isEmpty() || item2.isEmpty())
            return 0;

        return calculateItemSimilarity(item1, item2);
}

    public List<Book> hybridRecommend(Long userId) {
        return hybridRecommend(userId, false);
    }

    public List<Book> hybridRecommend(Long userId, boolean useBias) {
        User user = userRepository.findById(userId).orElse(null);
        Map<Long, Map<Long, Integer>> userItemMap = getUserItemMap();
        Map<Long, Integer> ratings = userItemMap.get(userId);

        // 冷启动判断
        if (ratings == null || ratings.size() < 3) {
            // 优先：分类推荐
            List<Book> categoryBooks = recommendByCategory(user);
            if (!categoryBooks.isEmpty()) {
                logger.info("用户 {} 使用分类偏好推荐，评分数量：{}", userId, ratings != null ? ratings.size() : 0);
                return categoryBooks;
            }
            // 次级：热门推荐
            logger.info("用户 {} 使用热门推荐，评分数量：{}", userId, ratings != null ? ratings.size() : 0);
            return mixHotAndHighScoreBooks();
        }

        // 原有逻辑
        return hybridRecommendInternal(userId, useBias);
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
                .sorted(Comparator.comparing(Rating::getCreateTime)) // 按时间排序
                .collect(Collectors.toList());

        if (userRatings.size() < 2) {
            return 0.0;
        }

        // 时间切分：前80%作为训练集，后20%作为测试集
        int splitIndex = (int) (userRatings.size() * 0.8);
        if (splitIndex >= userRatings.size() - 1) {
            splitIndex = userRatings.size() - 1; // 确保测试集至少有1条记录
        }
        List<Rating> trainSet = userRatings.subList(0, splitIndex);
        List<Rating> testSet = userRatings.subList(splitIndex, userRatings.size());

        List<Rating> allRatings = new ArrayList<>();
        for (Rating r : ratingRepository.findAll()) {
            if (!r.getUserId().equals(userId)) {
                allRatings.add(r);
            }
        }
        allRatings.addAll(trainSet);

        List<Book> recommendedBooks =
                hybridRecommendWithCustomRatings(userId, allRatings, alpha);

        List<Long> topK = recommendedBooks.stream()
                .limit(k)
                .map(Book::getId)
                .collect(Collectors.toList());

        long hitCount = testSet.stream()
                .filter(r -> topK.contains(r.getBookId()))
                .count();

        return (double) hitCount / k;
    }

    public double precisionAtKWithBias(Long userId, int k, double alpha, double beta) {

        List<Rating> userRatings = ratingRepository.findAll()
                .stream()
                .filter(r -> r.getUserId().equals(userId))
                .sorted(Comparator.comparing(Rating::getCreateTime)) // 按时间排序
                .collect(Collectors.toList());

        if (userRatings.size() < 2) {
            return 0.0;
        }

        // 时间切分：前80%作为训练集，后20%作为测试集
        int splitIndex = (int) (userRatings.size() * 0.8);
        if (splitIndex >= userRatings.size() - 1) {
            splitIndex = userRatings.size() - 1; // 确保测试集至少有1条记录
        }
        List<Rating> trainSet = userRatings.subList(0, splitIndex);
        List<Rating> testSet = userRatings.subList(splitIndex, userRatings.size());

        List<Rating> allRatings = new ArrayList<>();
        for (Rating r : ratingRepository.findAll()) {
            if (!r.getUserId().equals(userId)) {
                allRatings.add(r);
            }
        }
        allRatings.addAll(trainSet);

        List<Book> recommendedBooks =
                hybridRecommendWithCustomRatings(userId, allRatings, alpha, true, beta);

        List<Long> topK = recommendedBooks.stream()
                .limit(k)
                .map(Book::getId)
                .collect(Collectors.toList());

        long hitCount = testSet.stream()
                .filter(r -> topK.contains(r.getBookId()))
                .count();

        return (double) hitCount / k;
    }

    public double recallAtK(Long userId, int k, double alpha) {

        List<Rating> userRatings = ratingRepository.findAll()
                .stream()
                .filter(r -> r.getUserId().equals(userId))
                .sorted(Comparator.comparing(Rating::getCreateTime)) // 按时间排序
                .collect(Collectors.toList());

        if (userRatings.size() < 2) {
            return 0.0;
        }

        // 时间切分：前80%作为训练集，后20%作为测试集
        int splitIndex = (int) (userRatings.size() * 0.8);
        if (splitIndex >= userRatings.size() - 1) {
            splitIndex = userRatings.size() - 1; // 确保测试集至少有1条记录
        }
        List<Rating> trainSet = userRatings.subList(0, splitIndex);
        List<Rating> testSet = userRatings.subList(splitIndex, userRatings.size());

        List<Rating> allRatings = new ArrayList<>();
        for (Rating r : ratingRepository.findAll()) {
            if (!r.getUserId().equals(userId)) {
                allRatings.add(r);
            }
        }
        allRatings.addAll(trainSet);

        List<Book> recommendedBooks =
                hybridRecommendWithCustomRatings(userId, allRatings, alpha);

        List<Long> topK = recommendedBooks.stream()
                .limit(k)
                .map(Book::getId)
                .collect(Collectors.toList());

        long hitCount = testSet.stream()
                .filter(r -> topK.contains(r.getBookId()))
                .count();

        return testSet.isEmpty() ? 0.0 : (double) hitCount / testSet.size();
    }

    public double recallAtK(Long userId, int k, double alpha, double beta) {

        List<Rating> userRatings = ratingRepository.findAll()
                .stream()
                .filter(r -> r.getUserId().equals(userId))
                .sorted(Comparator.comparing(Rating::getCreateTime)) // 按时间排序
                .collect(Collectors.toList());

        if (userRatings.size() < 2) {
            return 0.0;
        }

        // 时间切分：前80%作为训练集，后20%作为测试集
        int splitIndex = (int) (userRatings.size() * 0.8);
        if (splitIndex >= userRatings.size() - 1) {
            splitIndex = userRatings.size() - 1; // 确保测试集至少有1条记录
        }
        List<Rating> trainSet = userRatings.subList(0, splitIndex);
        List<Rating> testSet = userRatings.subList(splitIndex, userRatings.size());

        List<Rating> allRatings = new ArrayList<>();
        for (Rating r : ratingRepository.findAll()) {
            if (!r.getUserId().equals(userId)) {
                allRatings.add(r);
            }
        }
        allRatings.addAll(trainSet);

        List<Book> recommendedBooks =
                hybridRecommendWithCustomRatings(userId, allRatings, alpha, true, beta);

        List<Long> topK = recommendedBooks.stream()
                .limit(k)
                .map(Book::getId)
                .collect(Collectors.toList());

        long hitCount = testSet.stream()
                .filter(r -> topK.contains(r.getBookId()))
                .count();

        return testSet.isEmpty() ? 0.0 : (double) hitCount / testSet.size();
    }

    public double evaluateRecallAllUsers(int k, double alpha) {
        return evaluateRecallAllUsers(k, alpha, 0.0);
    }

    public double evaluateRecallAllUsers(int k, double alpha, double beta) {

        Set<Long> userIds = ratingRepository.findAll()
                .stream()
                .map(Rating::getUserId)
                .collect(Collectors.toSet());

        double total = 0.0;
        int count = 0;

        for (Long userId : userIds) {

            double recall;
            if (beta > 0) {
                recall = recallAtK(userId, k, alpha, beta);
            } else {
                recall = recallAtK(userId, k, alpha);
            }

            total += recall;
            count++;
        }

        return count == 0 ? 0.0 : total / count;
    }

    public double f1Score(double precision, double recall) {
        if (precision + recall == 0) return 0.0;
        return 2 * precision * recall / (precision + recall);
    }

    public double ndcgAtK(Long userId, int k, double alpha) {
        return ndcgAtK(userId, k, alpha, 0.0);
    }

    public double ndcgAtK(Long userId, int k, double alpha, double beta) {
        List<Rating> userRatings = ratingRepository.findAll()
                .stream()
                .filter(r -> r.getUserId().equals(userId))
                .sorted(Comparator.comparing(Rating::getCreateTime)) // 按时间排序
                .collect(Collectors.toList());

        if (userRatings.size() < 2) {
            return 0.0;
        }

        // 时间切分：前80%作为训练集，后20%作为测试集
        int splitIndex = (int) (userRatings.size() * 0.8);
        if (splitIndex >= userRatings.size() - 1) {
            splitIndex = userRatings.size() - 1; // 确保测试集至少有1条记录
        }
        List<Rating> trainSet = userRatings.subList(0, splitIndex);
        List<Rating> testSet = userRatings.subList(splitIndex, userRatings.size());

        List<Rating> allRatings = new ArrayList<>();
        for (Rating r : ratingRepository.findAll()) {
            if (!r.getUserId().equals(userId)) {
                allRatings.add(r);
            }
        }
        allRatings.addAll(trainSet);

        List<Book> recommendedBooks;
        if (beta > 0) {
            recommendedBooks = hybridRecommendWithCustomRatings(userId, allRatings, alpha, true, beta);
        } else {
            recommendedBooks = hybridRecommendWithCustomRatings(userId, allRatings, alpha);
        }

        List<Long> topK = recommendedBooks.stream()
                .limit(k)
                .map(Book::getId)
                .collect(Collectors.toList());

        // 计算DCG
        double dcg = 0.0;
        Map<Long, Integer> testRatingsMap = new HashMap<>();
        for (Rating r : testSet) {
            testRatingsMap.put(r.getBookId(), r.getScore());
        }

        for (int i = 0; i < topK.size(); i++) {
            Long bookId = topK.get(i);
            if (testRatingsMap.containsKey(bookId)) {
                int rel = testRatingsMap.get(bookId);
                dcg += rel / Math.log(i + 2) / Math.log(2); // log2(i+1)
            }
        }

        // 计算IDCG（理想DCG）
        List<Integer> testScores = testSet.stream()
                .map(Rating::getScore)
                .sorted(Comparator.reverseOrder())
                .collect(Collectors.toList());

        double idcg = 0.0;
        for (int i = 0; i < Math.min(k, testScores.size()); i++) {
            idcg += testScores.get(i) / Math.log(i + 2) / Math.log(2);
        }

        return idcg == 0 ? 0.0 : dcg / idcg;
    }

    public double evaluateNdcgAllUsers(int k, double alpha) {
        return evaluateNdcgAllUsers(k, alpha, 0.0);
    }

    public double evaluateNdcgAllUsers(int k, double alpha, double beta) {
        Set<Long> userIds = ratingRepository.findAll()
                .stream()
                .map(Rating::getUserId)
                .collect(Collectors.toSet());

        double total = 0.0;
        int count = 0;

        for (Long userId : userIds) {
            double ndcg = ndcgAtK(userId, k, alpha, beta);
            total += ndcg;
            count++;
        }

        return count == 0 ? 0.0 : total / count;
    }

    public Map<Double, Double> alphaExperimentF1(int k) {

        Map<Double, Double> result = new LinkedHashMap<>();

        for (double alpha = 0; alpha <= 1; alpha += 0.1) {

            double precision = evaluateAllUsers(k, alpha);
            double recall = evaluateRecallAllUsers(k, alpha);
            double f1 = f1Score(precision, recall);

            result.put(
                    Math.round(alpha * 10) / 10.0,
                    f1
            );

            logger.debug("alpha={} f1={}", alpha, f1);
        }

        return result;
    }

    public Map<Integer, Double> topKExperimentF1(double alpha) {
        return topKExperimentF1(alpha, 0.0);
    }

    public Map<Integer, Double> topKExperimentF1(double alpha, double beta) {

        Map<Integer, Double> result = new LinkedHashMap<>();

        int[] ks = {3, 5, 7, 10};

        for (int k : ks) {

            double precision = evaluateAllUsers(k, alpha, beta);
            double recall = evaluateRecallAllUsers(k, alpha, beta);
            double f1 = f1Score(precision, recall);

            result.put(k, f1);

            logger.debug("K={} f1={}", k, f1);
        }

        return result;
    }

    public List<Map<String, Object>> alphaBetaExperimentF1(int k) {

        List<Map<String, Object>> result = new ArrayList<>();

        for (double alpha = 0; alpha <= 1; alpha += 0.1) {
            for (double beta = 0; beta <= 1 - alpha; beta += 0.1) {
                double precision = evaluateAllUsers(k, alpha, beta);
                double recall = evaluateRecallAllUsers(k, alpha, beta);
                double f1 = f1Score(precision, recall);

                Map<String, Object> entry = new HashMap<>();
                entry.put("alpha", Math.round(alpha * 10) / 10.0);
                entry.put("beta", Math.round(beta * 10) / 10.0);
                entry.put("f1", f1);

                result.add(entry);

                logger.debug("alpha={}, beta={}, f1={}", alpha, beta, f1);
            }
        }

        return result;
    }

    public Map<Double, Map<Double, Double>> alphaBetaMatrixF1(int k) {

        Map<Double, Map<Double, Double>> result = new LinkedHashMap<>();

        for (double alpha = 0; alpha <= 1; alpha += 0.1) {

            double a = Math.round(alpha * 10) / 10.0;
            result.put(a, new LinkedHashMap<>());

            for (double beta = 0; beta <= 1 - alpha; beta += 0.1) {

                double b = Math.round(beta * 10) / 10.0;

                double precision = evaluateAllUsers(k, a, b);
                double recall = evaluateRecallAllUsers(k, a, b);
                double f1 = f1Score(precision, recall);

                result.get(a).put(b, f1);

                logger.debug("alpha={}, beta={}, f1={}", a, b, f1);
            }
        }

        return result;
    }

    public Map<String, Double> algorithmComparisonF1(int k) {

        Map<String, Double> result = new LinkedHashMap<>();

        double precisionUserCF = evaluateAllUsers(k, 1.0, 0.0);
        double recallUserCF = evaluateRecallAllUsers(k, 1.0, 0.0);
        result.put("UserCF", f1Score(precisionUserCF, recallUserCF));

        double precisionItemCF = evaluateAllUsers(k, 0.0, 0.0);
        double recallItemCF = evaluateRecallAllUsers(k, 0.0, 0.0);
        result.put("ItemCF", f1Score(precisionItemCF, recallItemCF));

        double precisionUserItemCF = evaluateAllUsers(k, 0.8, 0.0);
        double recallUserItemCF = evaluateRecallAllUsers(k, 0.8, 0.0);
        result.put("UserCF+ItemCF", f1Score(precisionUserItemCF, recallUserItemCF));

        double precisionHybrid = evaluateAllUsers(k, 0.4, 0.4);
        double recallHybrid = evaluateRecallAllUsers(k, 0.4, 0.4);
        result.put("Hybrid (User+Item+Bias)", f1Score(precisionHybrid, recallHybrid));

        return result;
    }

    private List<Book> hybridRecommendWithCustomRatings(Long userId, List<Rating> ratings, double alpha) {
        return hybridRecommendWithCustomRatings(userId, ratings, alpha, false);
    }

    private List<Book> hybridRecommendWithCustomRatings(Long userId, List<Rating> ratings, double alpha, boolean useBias) {
        return hybridRecommendWithCustomRatings(userId, ratings, alpha, useBias, configService.getBeta());
    }

    public Map<Double, Double> alphaExperimentNDCG(int k) {

        Map<Double, Double> result = new LinkedHashMap<>();

        for (double alpha = 0.0; alpha <= 1.0; alpha += 0.1) {
            double ndcg = evaluateNdcgAllUsers(k, alpha);
            result.put(alpha, ndcg);
        }

        return result;
    }

    public Map<Integer, Double> topKExperimentNDCG(double alpha, double beta) {

        Map<Integer, Double> result = new LinkedHashMap<>();

        for (int k = 1; k <= 10; k++) {
            double ndcg = evaluateNdcgAllUsers(k, alpha, beta);
            result.put(k, ndcg);
        }

        return result;
    }

    public Map<Double, Map<Double, Double>> alphaBetaExperimentNDCG(int k) {

        Map<Double, Map<Double, Double>> result = new LinkedHashMap<>();

        for (double alpha = 0.0; alpha <= 1.0; alpha += 0.2) {
            Map<Double, Double> betaMap = new LinkedHashMap<>();
            for (double beta = 0.0; beta <= 1.0 - alpha; beta += 0.2) {
                double ndcg = evaluateNdcgAllUsers(k, alpha, beta);
                betaMap.put(beta, ndcg);
            }
            result.put(alpha, betaMap);
        }

        return result;
    }

    public List<Map<String, Object>> alphaBetaMatrixNDCG(int k) {

        List<Map<String, Object>> result = new ArrayList<>();

        // 使用整数迭代来避免浮点数精度问题
        for (int i = 0; i <= 5; i++) {
            double alpha = i * 0.2;
            double a = Math.round(alpha * 10) / 10.0;
            
            for (int j = 0; j <= 5 - i; j++) {
                double beta = j * 0.2;
                double b = Math.round(beta * 10) / 10.0;
                
                Map<String, Object> item = new HashMap<>();
                item.put("alpha", a);
                item.put("beta", b);
                item.put("ndcg", evaluateNdcgAllUsers(k, a, b));
                result.add(item);
                logger.debug("alpha={}, beta={}, ndcg={}", a, b, evaluateNdcgAllUsers(k, a, b));
            }
        }

        return result;
    }

    public Map<String, Double> algorithmComparisonNDCG(int k) {

        Map<String, Double> result = new LinkedHashMap<>();

        double ndcgUserCF = evaluateNdcgAllUsers(k, 1.0, 0.0);
        result.put("UserCF", ndcgUserCF);

        double ndcgItemCF = evaluateNdcgAllUsers(k, 0.0, 0.0);
        result.put("ItemCF", ndcgItemCF);

        double ndcgUserItemCF = evaluateNdcgAllUsers(k, 0.8, 0.0);
        result.put("UserCF+ItemCF", ndcgUserItemCF);

        double ndcgHybrid = evaluateNdcgAllUsers(k, 0.4, 0.4);
        result.put("Hybrid (User+Item+Bias)", ndcgHybrid);

        return result;
    }

    private List<Book> hybridRecommendWithCustomRatings(Long userId, List<Rating> ratings, double alpha, boolean useBias, double beta) {

        Map<Long, Double> userMap = normalize(getUserCFScoreMap(userId, ratings));
        if (userMap.isEmpty()) {
            return mixHotAndHighScoreBooks();
        }

        Map<Long, Double> itemMap = normalize(getItemCFScoreMap(userId, ratings));
        Map<Long, Double> biasMap = useBias ? normalize(getBiasAwareScoreMap(userId, ratings)) : new HashMap<>();

        Map<Long, Double> finalScoreMap = new HashMap<>();

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
                finalScore = alpha * userScore + (1 - alpha - beta) * itemScore + beta * biasScore;
            } else {
                finalScore = alpha * userScore + (1 - alpha) * itemScore;
            }

            finalScoreMap.put(bookId, finalScore);
        }

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
            return mixHotAndHighScoreBooks();
        }
        return bookRepository.findAllById(topIds);
    }

    public Map<Double, Double> alphaExperiment(int k) {

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
    public Map<Integer, Double> topKExperiment(double alpha) {
        return topKExperiment(alpha, 0.0);
    }

    public Map<Integer, Double> topKExperiment(double alpha, double beta) {

    Map<Integer, Double> result = new LinkedHashMap<>();

    int[] ks = {3, 5, 7, 10};

    for (int k : ks) {

        double precision = evaluateAllUsers(k, alpha, beta);

        result.put(k, precision);

        logger.debug("K={} precision={}", k, precision);
    }

    return result;
    }



    public List<Map<String, Object>> alphaBetaExperiment(int k) {

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

        result.put("UserCF", evaluateAllUsers(k, 1.0, 0.0));

        result.put("ItemCF", evaluateAllUsers(k, 0.0, 0.0));

        result.put("UserCF+ItemCF", evaluateAllUsers(k, 0.8, 0.0));

        result.put("Hybrid (User+Item+Bias)", evaluateAllUsers(k, 0.4, 0.4));

        return result;
    }

    public Map<Double, Double> alphaExperimentRecall(int k) {

        Map<Double, Double> result = new LinkedHashMap<>();

        for (double alpha = 0; alpha <= 1; alpha += 0.1) {

            double recall = evaluateRecallAllUsers(k, alpha);

            result.put(
                    Math.round(alpha * 10) / 10.0,
                    recall
            );

            logger.debug("alpha={} recall={}", alpha, recall);
        }

        return result;
    }

    public Map<Integer, Double> topKExperimentRecall(double alpha) {
        return topKExperimentRecall(alpha, 0.0);
    }

    public Map<Integer, Double> topKExperimentRecall(double alpha, double beta) {

        Map<Integer, Double> result = new LinkedHashMap<>();

        int[] ks = {3, 5, 7, 10};

        for (int k : ks) {

            double recall = evaluateRecallAllUsers(k, alpha, beta);

            result.put(k, recall);

            logger.debug("K={} recall={}", k, recall);
        }

        return result;
    }

    public List<Map<String, Object>> alphaBetaExperimentRecall(int k) {

        List<Map<String, Object>> result = new ArrayList<>();

        for (double alpha = 0; alpha <= 1; alpha += 0.1) {
            for (double beta = 0; beta <= 1 - alpha; beta += 0.1) {
                double recall = evaluateRecallAllUsers(k, alpha, beta);

                Map<String, Object> entry = new HashMap<>();
                entry.put("alpha", Math.round(alpha * 10) / 10.0);
                entry.put("beta", Math.round(beta * 10) / 10.0);
                entry.put("recall", recall);

                result.add(entry);

                logger.debug("alpha={}, beta={}, recall={}", alpha, beta, recall);
            }
        }

        return result;
    }

    public Map<Double, Map<Double, Double>> alphaBetaMatrixRecall(int k) {

        Map<Double, Map<Double, Double>> result = new LinkedHashMap<>();

        for (double alpha = 0; alpha <= 1; alpha += 0.1) {

            double a = Math.round(alpha * 10) / 10.0;
            result.put(a, new LinkedHashMap<>());

            for (double beta = 0; beta <= 1 - alpha; beta += 0.1) {

                double b = Math.round(beta * 10) / 10.0;

                double recall = evaluateRecallAllUsers(k, a, b);

                result.get(a).put(b, recall);

                logger.debug("alpha={}, beta={}, recall={}", a, b, recall);
            }
        }

        return result;
    }

    public Map<String, Double> algorithmComparisonRecall(int k) {

        Map<String, Double> result = new LinkedHashMap<>();

        result.put("UserCF", evaluateRecallAllUsers(k, 1.0, 0.0));

        result.put("ItemCF", evaluateRecallAllUsers(k, 0.0, 0.0));

        result.put("UserCF+ItemCF", evaluateRecallAllUsers(k, 0.8, 0.0));

        result.put("Hybrid (User+Item+Bias)", evaluateRecallAllUsers(k, 0.4, 0.4));

        return result;
    }


public void generateAllReports() throws Exception {

    Map<Double, Double> alphaData =
            alphaExperiment(5);

    Map<Integer, Double> kData =
            topKExperiment(configService.getAlpha());



    List<Map<String, Object>> alphaBetaData =
            alphaBetaExperiment(5);

    String basePath = "D:/Desktop/Final design/book-recommend-system/report/";

    new File(basePath).mkdirs();

    com.renlq.bookrecommendsystem.util.ExperimentUtil.generateChart(
            "融合权重实验（准确率）",
            "α值",
            "准确率",
            alphaData,
            basePath + "alpha_chart.png"
    );

    com.renlq.bookrecommendsystem.util.ExperimentUtil.exportExcel(
            "AlphaExperiment",
            alphaData,
            basePath + "alpha.xlsx"
    );



    com.renlq.bookrecommendsystem.util.ExperimentUtil.generateChart(
            "推荐列表长度实验（准确率）",
            "K值",
            "准确率",
            kData,
            basePath + "topk_chart.png"
    );

    com.renlq.bookrecommendsystem.util.ExperimentUtil.exportExcel(
            "TopKExperiment",
            kData,
            basePath + "topk.xlsx"
    );



    com.renlq.bookrecommendsystem.util.ExperimentUtil.exportAlphaBetaExcel(
            "AlphaBetaExperiment",
            alphaBetaData,
            basePath + "alpha_beta.xlsx"
    );

    Map<Double, Map<Double, Double>> heatmapData = alphaBetaMatrix(5);
    com.renlq.bookrecommendsystem.util.ExperimentUtil.generateHeatMap(
            "α-β双参数融合热力图（准确率）",
            heatmapData,
            basePath + "alpha_beta_heatmap.png"
    );

    Map<String, Double> algorithmData = algorithmComparison(5);
    com.renlq.bookrecommendsystem.util.ExperimentUtil.generateChart(
            "算法对比实验（准确率）",
            "算法",
            "准确率",
            algorithmData,
            basePath + "algorithm_chart.png"
    );

    com.renlq.bookrecommendsystem.util.ExperimentUtil.exportExcel(
            "AlgorithmComparison",
            algorithmData,
            basePath + "algorithm.xlsx"
    );

    Map<Double, Double> alphaDataRecall =
            alphaExperimentRecall(5);

    Map<Integer, Double> kDataRecall =
            topKExperimentRecall(configService.getAlpha());

    List<Map<String, Object>> alphaBetaDataRecall =
            alphaBetaExperimentRecall(5);

    com.renlq.bookrecommendsystem.util.ExperimentUtil.generateChart(
            "融合权重实验（召回率）",
            "α值",
            "召回率",
            alphaDataRecall,
            basePath + "alpha_recall_chart.png"
    );

    com.renlq.bookrecommendsystem.util.ExperimentUtil.exportExcel(
            "AlphaExperimentRecall",
            alphaDataRecall,
            basePath + "alpha_recall.xlsx"
    );

    com.renlq.bookrecommendsystem.util.ExperimentUtil.generateChart(
            "推荐列表长度实验（召回率）",
            "K值",
            "召回率",
            kDataRecall,
            basePath + "topk_recall_chart.png"
    );

    com.renlq.bookrecommendsystem.util.ExperimentUtil.exportExcel(
            "TopKExperimentRecall",
            kDataRecall,
            basePath + "topk_recall.xlsx"
    );

    com.renlq.bookrecommendsystem.util.ExperimentUtil.exportAlphaBetaExcel(
            "AlphaBetaExperimentRecall",
            alphaBetaDataRecall,
            basePath + "alpha_beta_recall.xlsx"
    );

    Map<Double, Map<Double, Double>> heatmapDataRecall = alphaBetaMatrixRecall(5);
    com.renlq.bookrecommendsystem.util.ExperimentUtil.generateHeatMap(
            "α-β双参数融合热力图（召回率）",
            heatmapDataRecall,
            basePath + "alpha_beta_recall_heatmap.png"
    );

    Map<String, Double> algorithmDataRecall = algorithmComparisonRecall(5);
    com.renlq.bookrecommendsystem.util.ExperimentUtil.generateChart(
            "算法对比实验（召回率）",
            "算法",
            "召回率",
            algorithmDataRecall,
            basePath + "algorithm_recall_chart.png"
    );

    com.renlq.bookrecommendsystem.util.ExperimentUtil.exportExcel(
            "AlgorithmComparisonRecall",
            algorithmDataRecall,
            basePath + "algorithm_recall.xlsx"
    );

    Map<Double, Double> alphaDataF1 =
            alphaExperimentF1(5);

    Map<Integer, Double> kDataF1 =
            topKExperimentF1(configService.getAlpha());

    List<Map<String, Object>> alphaBetaDataF1 =
            alphaBetaExperimentF1(5);

    com.renlq.bookrecommendsystem.util.ExperimentUtil.generateChart(
            "融合权重实验（F1值）",
            "α值",
            "F1值",
            alphaDataF1,
            basePath + "alpha_f1_chart.png"
    );

    com.renlq.bookrecommendsystem.util.ExperimentUtil.exportExcel(
            "AlphaExperimentF1",
            alphaDataF1,
            basePath + "alpha_f1.xlsx"
    );

    com.renlq.bookrecommendsystem.util.ExperimentUtil.generateChart(
            "推荐列表长度实验（F1值）",
            "K值",
            "F1值",
            kDataF1,
            basePath + "topk_f1_chart.png"
    );

    com.renlq.bookrecommendsystem.util.ExperimentUtil.exportExcel(
            "TopKExperimentF1",
            kDataF1,
            basePath + "topk_f1.xlsx"
    );

    com.renlq.bookrecommendsystem.util.ExperimentUtil.exportAlphaBetaExcel(
            "AlphaBetaExperimentF1",
            alphaBetaDataF1,
            basePath + "alpha_beta_f1.xlsx"
    );

    Map<Double, Map<Double, Double>> heatmapDataF1 = alphaBetaMatrixF1(5);
    com.renlq.bookrecommendsystem.util.ExperimentUtil.generateHeatMap(
            "α-β双参数融合热力图（F1值）",
            heatmapDataF1,
            basePath + "alpha_beta_f1_heatmap.png"
    );

    Map<String, Double> algorithmDataF1 = algorithmComparisonF1(5);
    com.renlq.bookrecommendsystem.util.ExperimentUtil.generateChart(
            "算法对比实验（F1值）",
            "算法",
            "F1值",
            algorithmDataF1,
            basePath + "algorithm_f1_chart.png"
    );

    com.renlq.bookrecommendsystem.util.ExperimentUtil.exportExcel(
            "AlgorithmComparisonF1",
            algorithmDataF1,
            basePath + "algorithm_f1.xlsx"
    );

    Map<Double, Double> alphaDataNDCG = alphaExperimentNDCG(5);
    Map<Integer, Double> kDataNDCG = topKExperimentNDCG(configService.getAlpha(), configService.getBeta());
    List<Map<String, Object>> alphaBetaDataNDCG = alphaBetaMatrixNDCG(5);

    com.renlq.bookrecommendsystem.util.ExperimentUtil.generateChart(
            "融合权重实验（归一化折损累计增益）",
            "α值",
            "归一化折损累计增益值",
            alphaDataNDCG,
            basePath + "alpha_ndcg_chart.png"
    );

    com.renlq.bookrecommendsystem.util.ExperimentUtil.exportExcel(
            "AlphaExperimentNDCG",
            alphaDataNDCG,
            basePath + "alpha_ndcg.xlsx"
    );

    com.renlq.bookrecommendsystem.util.ExperimentUtil.generateChart(
            "推荐列表长度实验（归一化折损累计增益）",
            "K值",
            "归一化折损累计增益值",
            kDataNDCG,
            basePath + "topk_ndcg_chart.png"
    );

    com.renlq.bookrecommendsystem.util.ExperimentUtil.exportExcel(
            "TopKExperimentNDCG",
            kDataNDCG,
            basePath + "topk_ndcg.xlsx"
    );

    com.renlq.bookrecommendsystem.util.ExperimentUtil.exportAlphaBetaExcel(
            "AlphaBetaExperimentNDCG",
            alphaBetaDataNDCG,
            basePath + "alpha_beta_ndcg.xlsx"
    );

    Map<Double, Map<Double, Double>> heatmapDataNDCG = alphaBetaExperimentNDCG(5);
    com.renlq.bookrecommendsystem.util.ExperimentUtil.generateHeatMap(
            "α-β双参数融合热力图（归一化折损累计增益）",
            heatmapDataNDCG,
            basePath + "alpha_beta_ndcg_heatmap.png"
    );

    Map<String, Double> algorithmDataNDCG = algorithmComparisonNDCG(5);
    com.renlq.bookrecommendsystem.util.ExperimentUtil.generateChart(
            "算法对比实验（归一化折损累计增益）",
            "算法",
            "归一化折损累计增益值",
            algorithmDataNDCG,
            basePath + "algorithm_ndcg_chart.png"
    );

    com.renlq.bookrecommendsystem.util.ExperimentUtil.exportExcel(
            "AlgorithmComparisonNDCG",
            algorithmDataNDCG,
            basePath + "algorithm_ndcg.xlsx"
    );

    logger.info("实验报告生成完成！");
    }
    public List<RecommendationResult> recommendWithExplanation(Long userId) {
        return recommendWithExplanation(userId, false);
    }

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