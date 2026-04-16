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
import com.orsoncharts.Chart3D;
import com.orsoncharts.Chart3DFactory;
import com.orsoncharts.data.xyz.XYZDataset;
import com.orsoncharts.data.xyz.XYZSeries;
import com.orsoncharts.data.xyz.XYZSeriesCollection;
import com.orsoncharts.graphics3d.Dimension3D;
import com.orsoncharts.graphics3d.ExportUtils;
import com.orsoncharts.plot.XYZPlot;
import com.orsoncharts.renderer.xyz.SurfaceRenderer;

import java.awt.*;
import java.awt.Font;
import java.io.File;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

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
                    "Precision",
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

        // 设置字体大小
        Font titleFont = new Font("Microsoft YaHei", Font.BOLD, 24);
        Font labelFont = new Font("Microsoft YaHei", Font.PLAIN, 18);
        Font tickFont = new Font("Microsoft YaHei", Font.PLAIN, 16);
        Font itemLabelFont = new Font("Microsoft YaHei", Font.PLAIN, 14);

        // 设置标题字体
        TextTitle chartTitle = chart.getTitle();
        chartTitle.setFont(titleFont);

        // 设置X轴
        CategoryAxis xAxis = plot.getDomainAxis();
        xAxis.setLabelFont(labelFont);
        xAxis.setTickLabelFont(tickFont);

        // 设置Y轴
        NumberAxis yAxis = (NumberAxis) plot.getRangeAxis();
        yAxis.setLabelFont(labelFont);
        yAxis.setTickLabelFont(tickFont);

        // 设置数据标签字体
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
        header.createCell(1).setCellValue("Precision");

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

        Sheet sheet = 
                workbook.createSheet(sheetName);

        // 收集所有唯一的Alpha和Beta值
        Set<Double> alphaSet = new HashSet<>();
        Set<Double> betaSet = new HashSet<>();
        Map<String, Double> precisionMap = new HashMap<>();

        for (Map<String, Object> entry : data) {
            Double alpha = (Double) entry.get("alpha");
            Double beta = (Double) entry.get("beta");
            Double precision = (Double) entry.get("precision");
            alphaSet.add(alpha);
            betaSet.add(beta);
            precisionMap.put(alpha + "," + beta, precision);
        }

        // 排序
        List<Double> alphaList = new ArrayList<>(alphaSet);
        List<Double> betaList = new ArrayList<>(betaSet);
        alphaList.sort(Collections.reverseOrder()); // Alpha降序，大值在上方
        betaList.sort(Double::compareTo); // Beta升序，小值在左侧

        // 创建表头（第一行是Beta值）
        Row headerRow = sheet.createRow(0);
        headerRow.createCell(0).setCellValue("Alpha\\Beta"); // 第一列标题
        for (int i = 0; i < betaList.size(); i++) {
            headerRow.createCell(i + 1).setCellValue(betaList.get(i));
        }

        // 填充表格内容
        for (int i = 0; i < alphaList.size(); i++) {
            Double alpha = alphaList.get(i);
            Row row = sheet.createRow(i + 1);
            row.createCell(0).setCellValue(alpha); // 第一列是Alpha值

            for (int j = 0; j < betaList.size(); j++) {
                Double beta = betaList.get(j);
                Double precision = precisionMap.get(alpha + "," + beta);
                if (precision != null) {
                    row.createCell(j + 1).setCellValue(precision);
                } else {
                    row.createCell(j + 1).setCellValue("-");
                }
            }
        }

        // 自动调整列宽
        for (int i = 0; i <= betaList.size(); i++) {
            sheet.autoSizeColumn(i);
        }

        FileOutputStream out = 
                new FileOutputStream(filePath);

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

        // 计算实际的Precision值范围
        double minPrecision = Double.MAX_VALUE;
        double maxPrecision = Double.MIN_VALUE;
        for (Double alpha : data.keySet()) {
            for (Double beta : data.get(alpha).keySet()) {
                double precision = data.get(alpha).get(beta);
                if (precision < minPrecision) minPrecision = precision;
                if (precision > maxPrecision) maxPrecision = precision;
            }
        }
        
        // 确保范围合理
        if (minPrecision == maxPrecision) {
            minPrecision = 0;
            maxPrecision = 1;
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

        dataset.addSeries("Precision", new double[][]{x, y, z});

        JFreeChart chart = ChartFactory.createScatterPlot(
                title,
                "Alpha",
                "Beta",
                dataset
        );

        XYPlot plot = chart.getXYPlot();

        XYBlockRenderer renderer = new XYBlockRenderer();

        // 使用实际数据范围创建颜色缩放
        PaintScale scale = new LookupPaintScale(minPrecision, maxPrecision, java.awt.Color.WHITE);
        ((LookupPaintScale) scale).add(minPrecision, java.awt.Color.BLUE);
        ((LookupPaintScale) scale).add((minPrecision + maxPrecision) / 2, java.awt.Color.YELLOW);
        ((LookupPaintScale) scale).add(maxPrecision, java.awt.Color.RED);

        renderer.setPaintScale(scale);
        renderer.setBlockHeight(0.1);
        renderer.setBlockWidth(0.1);

        plot.setRenderer(renderer);

        // 设置轴范围
        NumberAxis xAxis = (NumberAxis) plot.getDomainAxis();
        xAxis.setRange(-0.1, 1.1);
        
        NumberAxis yAxis = (NumberAxis) plot.getRangeAxis();
        yAxis.setRange(-0.1, 1.1);

        ChartUtils.saveChartAsPNG(new File(filePath), chart, 800, 600);
    }

    /**
     * 生成3D曲面图
     */
    public static void generate3DSurfaceChart(
            Map<Double, Map<Double, Double>> data,
            String filePath
    ) throws Exception {

        XYZSeries series = new XYZSeries("Alpha-Beta-Precision");

        for (Double alpha : data.keySet()) {
            for (Double beta : data.get(alpha).keySet()) {

                double precision = data.get(alpha).get(beta);

                series.add(alpha, beta, precision);
            }
        }

        XYZDataset dataset = new XYZSeriesCollection(series);

        Chart3D chart = Chart3DFactory.createSurfaceChart(
                "Alpha-Beta 3D Surface",
                "Alpha",
                "Beta",
                "Precision",
                dataset
        );

        XYZPlot plot = (XYZPlot) chart.getPlot();

        SurfaceRenderer renderer = new SurfaceRenderer();
        plot.setRenderer(renderer);

        chart.setViewPoint(
                new com.orsoncharts.graphics3d.ViewPoint3D(
                        40,   // 旋转角度（左右）
                        30,   // 俯视角
                        10    // 距离
                )
        );

        ExportUtils.writeAsPNG(
                chart,
                800,
                600,
                new java.io.File(filePath)
        );
    }

}