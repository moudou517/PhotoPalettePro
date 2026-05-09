package com.example.photopalettepro;

import android.graphics.Bitmap;
import android.graphics.Color;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

public class ColorExtractor {

    public static List<Integer> getPaletteByMode(Bitmap imgBitmap, String mode) {
        Bitmap small = Bitmap.createScaledBitmap(imgBitmap, 150, 150, true);
        int width = small.getWidth();
        int height = small.getHeight();
        int[] pixels = new int[width * height];
        small.getPixels(pixels, 0, width, 0, 0, width, height);

        switch (mode) {
            case "取反差色":
                return extractContrastMode(pixels);
            case "突出原色":
                return extractVibrantMode(pixels);
            default:
                return extractDefaultMode(pixels);
        }
    }

    /**
     * 精细化反差色逻辑：
     * 1. 寻找聚拢色块。
     * 2. 挖掘隐藏色，但【最多只替换两个】，防止喧宾夺主。
     * 3. 仅在发生替换时进行色彩高级感优化。
     */
    /**
     * 优化后的反差色逻辑：
     * 引入 Uniqueness Score (唯一性得分)，专门捕捉像粉色花朵这种点缀色
     */
    private static List<Integer> extractContrastMode(int[] pixels) {
        // 1. 获取基础色板
        List<Integer> basePalette = extractVibrantMode(pixels);
        int primaryColor = basePalette.get(0);
        List<Integer> others = new ArrayList<>(basePalette.subList(1, basePalette.size()));

        // 2. 检测聚拢
        int[] closestIndices = findThreeClosestIndices(others);
        double groupDist = calculateGroupDistance(others, closestIndices);

        boolean hasReplaced = false;

        // 3. 无论 groupDist 大小，我们都尝试挖掘一下“极度差异色”
        List<Integer> filteredPixels = new ArrayList<>();
        for (int p : pixels) {
            boolean isCovered = false;
            for (int c : basePalette) {
                // 如果像素颜色和已有色板太像，就排除
                if (colorDistance(p, c) < 65) { // 稍微加大半径，强制排除相似色
                    isCovered = true;
                    break;
                }
            }
            // 同时过滤掉太暗或太灰的脏色
            float[] hsv = new float[3];
            Color.colorToHSV(p, hsv);
            if (!isCovered && hsv[2] > 0.15f && hsv[1] > 0.15f) {
                filteredPixels.add(p);
            }
        }

        if (filteredPixels.size() > 50) { // 降低样本量要求，保护小比例花朵
            // 提取 6 个候选簇，增加多样性
            List<Cluster> patches = kMeans(listToArray(filteredPixels), 6, 42);

            // 核心改进：按“唯一性得分”排序
            // 得分 = 鲜艳度 * (到原色板的平均距离)
            Collections.sort(patches, (a, b) -> {
                double scoreA = getVibrance(a.color) * getMinDistToPalette(a.color, basePalette);
                double scoreB = getVibrance(b.color) * getMinDistToPalette(b.color, basePalette);
                return Double.compare(scoreB, scoreA);
            });

            int replaceCount = 0;
            for (Cluster patch : patches) {
                if (replaceCount >= 2) break;

                // 只要这个颜色跟已有颜色够远（色彩反差够大），就替换
                if (getMinDistToPalette(patch.color, basePalette) > 100) {
                    int targetIdx = closestIndices[replaceCount + 1];
                    others.set(targetIdx, patch.color);
                    replaceCount++;
                    hasReplaced = true;
                }
            }
        }

        List<Integer> finalResult = new ArrayList<>();
        finalResult.add(primaryColor);
        finalResult.addAll(others);

        if (hasReplaced) {
            boolean isWarm = checkIsWarmScene(pixels);
            List<Integer> refined = new ArrayList<>();
            for (int c : finalResult) {
                refined.add(refineColor(c, isWarm));
            }
            return refined;
        }

        return finalResult;
    }

    /**
     * 计算一个颜色到现有色板中所有颜色的最短欧式距离
     * 距离越大，代表这个颜色越是“万绿丛中一点红”
     */
    private static double getMinDistToPalette(int color, List<Integer> palette) {
        double minD = Double.MAX_VALUE;
        for (int c : palette) {
            minD = Math.min(minD, colorDistance(color, c));
        }
        return minD;
    }

    /**
     * 突出原色：物理真实取色
     */
    private static List<Integer> extractVibrantMode(int[] pixels) {
        List<Cluster> clusters = kMeans(pixels, 10, 42);
        float maxScore = -1;
        int bestColor = 0;
        int bestIdx = -1;

        for (int i = 0; i < clusters.size(); i++) {
            float score = getVibrance(clusters.get(i).color);
            if (score > maxScore) {
                maxScore = score;
                bestColor = clusters.get(i).color;
                bestIdx = i;
            }
        }

        List<Integer> result = new ArrayList<>();
        result.add(bestColor);
        List<Cluster> copy = new ArrayList<>(clusters);
        copy.remove(bestIdx);

        final int finalBest = bestColor;
        Collections.sort(copy, (a, b) -> Double.compare(colorDistance(b.color, finalBest), colorDistance(a.color, finalBest)));

        for (int i = 0; i < 5 && i < copy.size(); i++) {
            result.add(copy.get(i).color);
        }
        return result;
    }

    /**
     * 默认渲染：聚类+色彩优化
     */
    private static List<Integer> extractDefaultMode(int[] pixels) {
        List<Integer> vP = new ArrayList<>(), bP = new ArrayList<>();
        for (int p : pixels) {
            float[] hsv = new float[3]; Color.colorToHSV(p, hsv);
            if (hsv[1] > 0.2f && hsv[2] > 0.2f) vP.add(p); else bP.add(p);
        }
        boolean isWarm = checkIsWarmScene(pixels);
        List<Cluster> kmV = kMeans(vP.isEmpty() ? pixels : listToArray(vP), 4, 42);
        List<Cluster> kmB = kMeans(bP.isEmpty() ? pixels : listToArray(bP), 2, 42);

        List<Integer> raw = new ArrayList<>();
        for (Cluster c : kmV) raw.add(c.color);
        for (Cluster c : kmB) raw.add(c.color);

        List<Integer> refined = new ArrayList<>();
        for (int c : raw) refined.add(refineColor(c, isWarm));

        Collections.sort(refined, (a, b) -> {
            float[] hA = new float[3], hB = new float[3];
            Color.colorToHSV(a, hA); Color.colorToHSV(b, hB);
            return Float.compare(hB[2], hA[2]);
        });
        return refined;
    }

    /**
     * 高级感调色：针对不同亮度区间进行 S/V 的精细拉伸
     */
    private static int refineColor(int color, boolean isWarmScene) {
        float[] hsv = new float[3];
        Color.colorToHSV(color, hsv);
        float s = hsv[1], v = hsv[2];

        if (v > 0.78f) { // 高明度：降饱和，提亮度，去油腻
            s *= 0.4f; v = Math.min(v * 1.1f, 0.98f);
        } else if (v < 0.35f) { // 低明度：压暗明度，控饱和，深邃感
            v *= 0.75f; s = Math.min(s * 1.1f, 0.4f);
        } else { // 中性色：协调
            s = isWarmScene ? Math.min(s * 1.2f, 0.9f) : s * 0.9f;
            v = Math.max(v, 0.4f);
        }
        hsv[1] = s; hsv[2] = v;
        return Color.HSVToColor(hsv);
    }

    // --- 工具函数 ---

    private static float getVibrance(int color) {
        float[] hsv = new float[3];
        Color.colorToHSV(color, hsv);
        return hsv[1] * hsv[2];
    }

    private static boolean checkIsWarmScene(int[] pixels) {
        int warmCount = 0;
        for (int p : pixels) {
            float[] hsv = new float[3]; Color.colorToHSV(p, hsv);
            float h = hsv[0] / 360f;
            if ((h < 0.12f || h > 0.88f) && hsv[1] > 0.15f) warmCount++;
        }
        return (float) warmCount / pixels.length > 0.3f;
    }

    private static int[] findThreeClosestIndices(List<Integer> colors) {
        int[] best = new int[]{0, 1, 2}; double minD = Double.MAX_VALUE;
        for (int i = 0; i < colors.size(); i++) {
            for (int j = i + 1; j < colors.size(); j++) {
                for (int k = j + 1; k < colors.size(); k++) {
                    double d = colorDistance(colors.get(i), colors.get(j)) + colorDistance(colors.get(j), colors.get(k)) + colorDistance(colors.get(i), colors.get(k));
                    if (d < minD) { minD = d; best = new int[]{i, j, k}; }
                }
            }
        }
        return best;
    }

    private static double calculateGroupDistance(List<Integer> colors, int[] indices) {
        return colorDistance(colors.get(indices[0]), colors.get(indices[1])) + colorDistance(colors.get(indices[1]), colors.get(indices[2])) + colorDistance(colors.get(indices[0]), colors.get(indices[2]));
    }

    private static List<Cluster> kMeans(int[] pixels, int k, long seed) {
        if (pixels.length == 0) return new ArrayList<>();
        Random random = new Random(seed);
        int[][] centroids = new int[k][3];
        int firstIdx = random.nextInt(pixels.length);
        centroids[0] = new int[]{Color.red(pixels[firstIdx]), Color.green(pixels[firstIdx]), Color.blue(pixels[firstIdx])};
        for (int i = 1; i < k; i++) {
            double[] dists = new double[pixels.length]; double sum = 0;
            for (int j = 0; j < pixels.length; j++) {
                double min = Double.MAX_VALUE;
                for (int m = 0; m < i; m++) {
                    double d = Math.pow(Color.red(pixels[j])-centroids[m][0],2)+Math.pow(Color.green(pixels[j])-centroids[m][1],2)+Math.pow(Color.blue(pixels[j])-centroids[m][2],2);
                    min = Math.min(min, d);
                }
                dists[j] = min; sum += min;
            }
            double target = random.nextDouble() * sum; double curr = 0;
            for (int j = 0; j < pixels.length; j++) {
                curr += dists[j];
                if (curr >= target) { centroids[i] = new int[]{Color.red(pixels[j]), Color.green(pixels[j]), Color.blue(pixels[j])}; break; }
            }
        }
        List<List<Integer>> groups = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            groups.clear();
            for (int j = 0; j < k; j++) groups.add(new ArrayList<>());
            for (int p : pixels) {
                int bk = 0; double md = Double.MAX_VALUE;
                for (int j = 0; j < k; j++) {
                    double d = Math.pow(Color.red(p)-centroids[j][0],2)+Math.pow(Color.green(p)-centroids[j][1],2)+Math.pow(Color.blue(p)-centroids[j][2],2);
                    if (d < md) { md = d; bk = j; }
                }
                groups.get(bk).add(p);
            }
            for (int j = 0; j < k; j++) {
                if (groups.get(j).isEmpty()) continue;
                long r=0,g=0,b=0; for (int p : groups.get(j)) { r+=Color.red(p); g+=Color.green(p); b+=Color.blue(p); }
                centroids[j] = new int[]{(int)(r/groups.get(j).size()), (int)(g/groups.get(j).size()), (int)(b/groups.get(j).size())};
            }
        }
        List<Cluster> result = new ArrayList<>();
        for (int i = 0; i < k; i++) result.add(new Cluster(Color.rgb(centroids[i][0], centroids[i][1], centroids[i][2]), groups.get(i).size()));
        return result;
    }

    private static double colorDistance(int c1, int c2) {
        return Math.sqrt(Math.pow(Color.red(c1)-Color.red(c2), 2) + Math.pow(Color.green(c1)-Color.green(c2), 2) + Math.pow(Color.blue(c1)-Color.blue(c2), 2));
    }

    private static int[] listToArray(List<Integer> list) {
        int[] arr = new int[list.size()];
        for (int i = 0; i < list.size(); i++) arr[i] = list.get(i);
        return arr;
    }

    private static class Cluster {
        int color; int count;
        Cluster(int c, int count) { this.color = c; this.count = count; }
    }
}