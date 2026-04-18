package com.renlq.bookrecommendsystem.util;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.jfree.chart.*;
import org.jfree.chart.labels.StandardCategoryItemLabelGenerator;
import org.jfree.chart.plot.CategoryPlot;
import org.jfree.chart.renderer.category.LineAndShapeRenderer;
import org.jfree.chart.axis.CategoryAxis;
import org.jfree.chart.axis.NumberAxis;
import org.jfree.chart.title.TextTitle;
import org.jfree.data.category.DefaultCategoryDataset;
import org.jfree.data.xy.DefaultXYZDataset;
import org.jfree.chart.plot.XYPlot;
import org.jfree.chart.renderer.xy.XYBlockRenderer;
import org.jfree.chart.renderer.PaintScale;
import org.jfree.chart.renderer.LookupPaintScale;


import java.awt.*;
import java.awt.Font;
import java.io.File;
import java.io.FileOutputStream;
import java.util.*;
import java.util.List;

public class ExperimentUtil {

    /**
     * 生成折线图（带数据标签）
     */
    public static void generateChart(
            String title,
            String xLabel,
            String yLabel,
            Map<?, Double> data,
            String outputFile
    ) throws Exception {

        DefaultCategoryDataset dataset =
                new DefaultCategoryDataset();

        for (Map.Entry<?, Double> entry : data.entrySet()) {

            dataset.addValue(
                    entry.getValue(),
                    "Metric",
                    entry.getKey().toString()
            );
        }

        JFreeChart chart = ChartFactory.createLineChart(
                title,
                xLabel,
                yLabel,
                dataset
        );

        CategoryPlot plot =
                chart.getCategoryPlot();

        LineAndShapeRenderer renderer =
                (LineAndShapeRenderer) plot.getRenderer();

        renderer.setDefaultShapesVisible(true);

        renderer.setDefaultItemLabelsVisible(true);

        renderer.setDefaultItemLabelGenerator(
                new StandardCategoryItemLabelGenerator()
        );

        Font titleFont = new Font("Microsoft YaHei", Font.BOLD, 24);
        Font labelFont = new Font("Microsoft YaHei", Font.PLAIN, 18);
        Font tickFont = new Font("Microsoft YaHei", Font.PLAIN, 16);
        Font itemLabelFont = new Font("Microsoft YaHei", Font.PLAIN, 14);

        TextTitle chartTitle = chart.getTitle();
        chartTitle.setFont(titleFont);

        CategoryAxis xAxis = plot.getDomainAxis();
        xAxis.setLabelFont(labelFont);
        xAxis.setTickLabelFont(tickFont);

        NumberAxis yAxis = (NumberAxis) plot.getRangeAxis();
        yAxis.setLabelFont(labelFont);
        yAxis.setTickLabelFont(tickFont);

        renderer.setDefaultItemLabelFont(itemLabelFont);

        ChartUtils.saveChartAsPNG(
                new File(outputFile),
                chart,
                900,
                600
        );
    }


    /**
     * 导出Excel表格
     */
    public static void exportExcel(
            String sheetName,
            Map<?, Double> data,
            String filePath
    ) throws Exception {

        Workbook workbook = new XSSFWorkbook();

        Sheet sheet =
                workbook.createSheet(sheetName);

        Row header = sheet.createRow(0);

        header.createCell(0).setCellValue("Parameter");
        header.createCell(1).setCellValue("Value");

        int rowIndex = 1;

        for (Map.Entry<?, Double> entry : data.entrySet()) {

            Row row =
                    sheet.createRow(rowIndex++);

            row.createCell(0)
                    .setCellValue(entry.getKey().toString());

            row.createCell(1)
                    .setCellValue(entry.getValue());
        }

        FileOutputStream out =
                new FileOutputStream(filePath);

        workbook.write(out);

        workbook.close();

        out.close();
    }

    /**
     * 导出Alpha和Beta融合实验的Excel表格（标准网状图格式）
     */
    public static void exportAlphaBetaExcel(
            String sheetName,
            List<Map<String, Object>> data,
            String filePath
    ) throws Exception {

        Workbook workbook = new XSSFWorkbook();

        Sheet sheet = workbook.createSheet(sheetName);

        Set<Double> alphaSet = new LinkedHashSet<>();
        Set<Double> betaSet = new LinkedHashSet<>();
        Map<String, Double> valueMap = new HashMap<>();

        for (Map<String, Object> entry : data) {
            Double alpha = (Double) entry.get("alpha");
            Double beta = (Double) entry.get("beta");
            Double value = null;

            if (entry.containsKey("f1") && entry.get("f1") != null) {
                value = ((Number) entry.get("f1")).doubleValue();
            } else if (entry.containsKey("precision") && entry.get("precision") != null) {
                value = ((Number) entry.get("precision")).doubleValue();
            } else if (entry.containsKey("recall") && entry.get("recall") != null) {
                value = ((Number) entry.get("recall")).doubleValue();
            } else if (entry.containsKey("ndcg") && entry.get("ndcg") != null) {
                value = ((Number) entry.get("ndcg")).doubleValue();
            }

            if (value != null) {
                double roundedAlpha = Math.round(alpha * 10) / 10.0;
                double roundedBeta = Math.round(beta * 10) / 10.0;
                alphaSet.add(roundedAlpha);
                betaSet.add(roundedBeta);
                valueMap.put(roundedAlpha + "," + roundedBeta, value);
            }
        }

        List<Double> alphaList = new ArrayList<>(alphaSet);
        List<Double> betaList = new ArrayList<>(betaSet);
        Collections.sort(alphaList, Collections.reverseOrder());
        Collections.sort(betaList);

        Row headerRow = sheet.createRow(0);
        Cell firstCell = headerRow.createCell(0);
        firstCell.setCellValue("Alpha\\Beta");

        for (int i = 0; i < betaList.size(); i++) {
            Cell cell = headerRow.createCell(i + 1);
            cell.setCellValue(betaList.get(i));
        }

        for (int i = 0; i < alphaList.size(); i++) {
            Double alpha = alphaList.get(i);
            Row row = sheet.createRow(i + 1);
            row.createCell(0).setCellValue(alpha);

            for (int j = 0; j < betaList.size(); j++) {
                Double beta = betaList.get(j);
                Double value = valueMap.get(alpha + "," + beta);
                Cell cell = row.createCell(j + 1);
                if (value != null) {
                    cell.setCellValue(value);
                } else {
                    cell.setCellValue("-");
                }
            }
        }

        for (int i = 0; i <= betaList.size(); i++) {
            sheet.autoSizeColumn(i);
        }

        FileOutputStream out = new FileOutputStream(filePath);
        workbook.write(out);
        workbook.close();
        out.close();
    }

    /**
     * 生成热力图
     */
    public static void generateHeatMap(
            String title,
            Map<Double, Map<Double, Double>> data,
            String filePath
    ) throws Exception {

        double minValue = Double.MAX_VALUE;
        double maxValue = Double.MIN_VALUE;
        for (Double alpha : data.keySet()) {
            for (Double beta : data.get(alpha).keySet()) {
                double value = data.get(alpha).get(beta);
                if (value < minValue) minValue = value;
                if (value > maxValue) maxValue = value;
            }
        }

        if (minValue == maxValue) {
            minValue = 0;
            maxValue = 1;
        }

        DefaultXYZDataset dataset = new DefaultXYZDataset();

        List<Double> xList = new ArrayList<>();
        List<Double> yList = new ArrayList<>();
        List<Double> zList = new ArrayList<>();

        for (Double alpha : data.keySet()) {
            for (Double beta : data.get(alpha).keySet()) {
                xList.add(alpha);
                yList.add(beta);
                zList.add(data.get(alpha).get(beta));
            }
        }

        double[] x = xList.stream().mapToDouble(Double::doubleValue).toArray();
        double[] y = yList.stream().mapToDouble(Double::doubleValue).toArray();
        double[] z = zList.stream().mapToDouble(Double::doubleValue).toArray();

        dataset.addSeries("Metric", new double[][]{x, y, z});

        JFreeChart chart = ChartFactory.createScatterPlot(
                title,
                "Alpha",
                "Beta",
                dataset
        );

        XYPlot plot = chart.getXYPlot();

        XYBlockRenderer renderer = new XYBlockRenderer();

        PaintScale scale = new LookupPaintScale(minValue, maxValue, java.awt.Color.WHITE);
        ((LookupPaintScale) scale).add(minValue, java.awt.Color.BLUE);
        ((LookupPaintScale) scale).add((minValue + maxValue) / 2, java.awt.Color.YELLOW);
        ((LookupPaintScale) scale).add(maxValue, java.awt.Color.RED);

        renderer.setPaintScale(scale);
        renderer.setBlockHeight(0.1);
        renderer.setBlockWidth(0.1);

        plot.setRenderer(renderer);

        NumberAxis xAxis = (NumberAxis) plot.getDomainAxis();
        xAxis.setRange(-0.1, 1.1);

        NumberAxis yAxis = (NumberAxis) plot.getRangeAxis();
        yAxis.setRange(-0.1, 1.1);

        ChartUtils.saveChartAsPNG(new File(filePath), chart, 800, 600);
    }
}
