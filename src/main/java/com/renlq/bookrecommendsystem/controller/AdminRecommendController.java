package com.renlq.bookrecommendsystem.controller;

import com.renlq.bookrecommendsystem.service.BookService;
import com.renlq.bookrecommendsystem.service.ConfigService;
import com.renlq.bookrecommendsystem.service.RecommendService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;
import java.util.Map;

@Controller
@RequestMapping("/admin/recommend")
@RequiredArgsConstructor
public class AdminRecommendController {

    private final RecommendService recommendService;
    private final ConfigService configService;

    @PostMapping("/updateAlpha")
    public String updateAlpha(@RequestParam double alpha, Model model){
        try {
            configService.setAlpha(alpha);
            // 直接返回recommendPage，避免重定向导致的重复计算
            return recommendPage(model);
        } catch (IllegalArgumentException e) {
            model.addAttribute("error", e.getMessage());
            return recommendPage(model);
        }
    }

    @PostMapping("/updateBeta")
    public String updateBeta(@RequestParam double beta, Model model){
        try {
            configService.setBeta(beta);
            // 直接返回recommendPage，避免重定向导致的重复计算
            return recommendPage(model);
        } catch (IllegalArgumentException e) {
            model.addAttribute("error", e.getMessage());
            return recommendPage(model);
        }
    }

    @PostMapping("/updateTopN")
    public String updateTopN(@RequestParam int topN, Model model){
        try {
            configService.setTopN(topN);
            // 直接返回recommendPage，避免重定向导致的重复计算
            return recommendPage(model);
        } catch (IllegalArgumentException e) {
            model.addAttribute("error", e.getMessage());
            return recommendPage(model);
        }
    }

    @GetMapping
    public String recommendPage(Model model) {
        try {
            recommendService.generateAllReports();
            model.addAttribute("success", "实验报告生成成功！");
        } catch (Exception e) {
            model.addAttribute("error", "生成实验报告失败：" + e.getMessage());
        }

        Map<Double, Double> alphaData = recommendService.alphaExperiment(5);
        Map<Integer, Double> kData = recommendService.topKExperiment(configService.getAlpha());
        List<Map<String, Object>> alphaBetaData = recommendService.alphaBetaExperiment(5);

        Map<Double, Double> alphaDataRecall = recommendService.alphaExperimentRecall(5);
        Map<Integer, Double> kDataRecall = recommendService.topKExperimentRecall(configService.getAlpha());
        List<Map<String, Object>> alphaBetaDataRecall = recommendService.alphaBetaExperimentRecall(5);

        Map<Double, Double> alphaDataF1 = recommendService.alphaExperimentF1(5);
        Map<Integer, Double> kDataF1 = recommendService.topKExperimentF1(configService.getAlpha());
        List<Map<String, Object>> alphaBetaDataF1 = recommendService.alphaBetaExperimentF1(5);

        Map<Double, Double> alphaDataNDCG = recommendService.alphaExperimentNDCG(5);
        Map<Integer, Double> kDataNDCG = recommendService.topKExperimentNDCG(configService.getAlpha(), configService.getBeta());
        List<Map<String, Object>> alphaBetaDataNDCG = recommendService.alphaBetaMatrixNDCG(5);

        model.addAttribute("alphaData", alphaData);
        model.addAttribute("kData", kData);
        model.addAttribute("alphaBetaData", alphaBetaData);
        model.addAttribute("alphaDataRecall", alphaDataRecall);
        model.addAttribute("kDataRecall", kDataRecall);
        model.addAttribute("alphaBetaDataRecall", alphaBetaDataRecall);
        model.addAttribute("alphaDataF1", alphaDataF1);
        model.addAttribute("kDataF1", kDataF1);
        model.addAttribute("alphaBetaDataF1", alphaBetaDataF1);
        model.addAttribute("alphaDataNDCG", alphaDataNDCG);
        model.addAttribute("kDataNDCG", kDataNDCG);
        model.addAttribute("alphaBetaDataNDCG", alphaBetaDataNDCG);
        model.addAttribute("alpha", configService.getAlpha());
        model.addAttribute("beta", configService.getBeta());
        model.addAttribute("topN", configService.getTopN());
        return "admin_recommend";
    }
    
    @PostMapping("/generateReports")
    public String generateReports(Model model) {
        try {
            // 生成所有实验报告并保存到本地
            recommendService.generateAllReports();
            model.addAttribute("success", "实验报告生成成功！");
        } catch (Exception e) {
            model.addAttribute("error", "生成实验报告失败：" + e.getMessage());
        }
        return recommendPage(model);
    }
}