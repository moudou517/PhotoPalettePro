package com.example.photopalettepro;

import android.content.Context;
import android.net.Uri;
import androidx.exifinterface.media.ExifInterface;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

public class ExifUtil {

    // ============================================================
    // 第一步：品牌专属哈希表 (在这里添加你搜集到的映射关系)
    // ============================================================
    private static final Map<String, String> HUAWEI_MAP = new HashMap<>();
    private static final Map<String, String> XIAOMI_MAP = new HashMap<>();
    private static final Map<String, String> OPPO_MAP = new HashMap<>();
    private static final Map<String, String> VIVO_MAP = new HashMap<>();
    private static final Map<String, String> SAMSUNG_MAP = new HashMap<>();
    private static final Map<String, String> APPLE_MAP = new HashMap<>();

    static {
        // —————— 【华为 HUAWEI / 荣耀 HONOR】 待添加 ——————
        // —————— 【华为 HUAWEI / 荣耀 HONOR】 待添加标注 ——————
        // Mate 40 系列 (2020-2021)
        HUAWEI_MAP.put("TAS-AN00", "HUAWEI Mate 40");
        HUAWEI_MAP.put("TAS-AL00", "HUAWEI Mate 40 4G");
        HUAWEI_MAP.put("OCE-AN50", "HUAWEI Mate 40");
        HUAWEI_MAP.put("OCE-AN10", "HUAWEI Mate 40 Pro");
        HUAWEI_MAP.put("OCE-AL00", "HUAWEI Mate 40 Pro 4G");
        HUAWEI_MAP.put("NOH-AN00", "HUAWEI Mate 40 Pro+");
        HUAWEI_MAP.put("NOH-AL00", "HUAWEI Mate 40 Pro+ 4G");
        HUAWEI_MAP.put("ANA-AN00", "HUAWEI Mate 40E");
        HUAWEI_MAP.put("ANA-AL00", "HUAWEI Mate 40E 4G");

        // Mate 50 系列 (2022)
        HUAWEI_MAP.put("CET-AL00", "HUAWEI Mate 50");
        HUAWEI_MAP.put("CET-AN00", "HUAWEI Mate 50 5G");
        HUAWEI_MAP.put("DCO-AL00", "HUAWEI Mate 50 Pro");
        HUAWEI_MAP.put("DCO-AN00", "HUAWEI Mate 50 Pro 5G");
        HUAWEI_MAP.put("LIO-AN00", "HUAWEI Mate 50 RS Porsche");

        // Mate 60 系列 (2023)
        HUAWEI_MAP.put("BRA-AL00", "HUAWEI Mate 60");
        HUAWEI_MAP.put("ALN-AL00", "HUAWEI Mate 60 Pro");
        HUAWEI_MAP.put("ALN-AL80", "HUAWEI Mate 60 Pro");
        HUAWEI_MAP.put("ALN-AL10", "HUAWEI Mate 60 Pro+");
        // 注：部分型号代号重叠，RS非凡大师通常会有特殊后缀或定制Make字段

        // Mate 70 系列 (2024 最新)
        HUAWEI_MAP.put("CLS-AL00", "HUAWEI Mate 70");
        HUAWEI_MAP.put("CLS-AL30", "HUAWEI Mate 70");
        HUAWEI_MAP.put("PLR-AL00", "HUAWEI Mate 70 Pro");
        HUAWEI_MAP.put("PLR-AL30", "HUAWEI Mate 70 Pro");
        HUAWEI_MAP.put("PLR-AL50", "HUAWEI Mate 70 Pro 优享版");
        HUAWEI_MAP.put("PLA-AL10", "HUAWEI Mate 70 Pro+");
        HUAWEI_MAP.put("PLU-AL10", "HUAWEI Mate 70 RS 非凡大师");

        // P50 / P60 系列
        HUAWEI_MAP.put("ABR-AL00", "HUAWEI P50");
        HUAWEI_MAP.put("ABR-AL80", "HUAWEI P50");
        HUAWEI_MAP.put("ABR-AL60", "HUAWEI P50E");
        HUAWEI_MAP.put("JAD-AL00", "HUAWEI P50 Pro");
        HUAWEI_MAP.put("JAD-AL80", "HUAWEI P50 Pro");
        HUAWEI_MAP.put("BAL-AL00", "HUAWEI P50 Pocket");
        HUAWEI_MAP.put("LNA-AL00", "HUAWEI P60");
        HUAWEI_MAP.put("MNA-AL00", "HUAWEI P60 Pro");

        // Pura 70 系列 (2024)
        HUAWEI_MAP.put("ADY-AL00", "HUAWEI Pura 70");
        HUAWEI_MAP.put("ADY-AL10", "HUAWEI Pura 70 北斗卫星版");
        HUAWEI_MAP.put("HBN-AL00", "HUAWEI Pura 70 Pro");
        HUAWEI_MAP.put("HBN-AL10", "HUAWEI Pura 70 Pro+");
        HUAWEI_MAP.put("HBN-AL80", "HUAWEI Pura 70 Pro+");
        HUAWEI_MAP.put("HBP-AL00", "HUAWEI Pura 70 Ultra");

        // nova 系列
        HUAWEI_MAP.put("NAM-AL00", "HUAWEI nova 9");
        HUAWEI_MAP.put("RTE-AL00", "HUAWEI nova 9 Pro");
        HUAWEI_MAP.put("NCO-AL00", "HUAWEI nova 10");
        HUAWEI_MAP.put("GLA-AL00", "HUAWEI nova 10 Pro");
        HUAWEI_MAP.put("FOA-AL00", "HUAWEI nova 11");
        HUAWEI_MAP.put("GOA-AL80", "HUAWEI nova 11 Pro");
        HUAWEI_MAP.put("BLK-AL00", "HUAWEI nova 12");
        HUAWEI_MAP.put("ADA-AL00", "HUAWEI nova 12 Pro");
        HUAWEI_MAP.put("BLK-AL80", "HUAWEI nova 13");
        HUAWEI_MAP.put("MIS-AL00", "HUAWEI nova 13 Pro");

        // 折叠屏系列
        HUAWEI_MAP.put("TET-AN00", "HUAWEI Mate X2 5G");
        HUAWEI_MAP.put("TET-AL00", "HUAWEI Mate X2 4G");
        HUAWEI_MAP.put("PAL-AL00", "HUAWEI Mate Xs 2");
        HUAWEI_MAP.put("ALT-AL00", "HUAWEI Mate X3");
        HUAWEI_MAP.put("ALT-AL10", "HUAWEI Mate X5");
        HUAWEI_MAP.put("ICL-AL10", "HUAWEI Mate X6");
        HUAWEI_MAP.put("GRL-AL10", "HUAWEI Mate XT 非凡大师");
        HUAWEI_MAP.put("LEM-AL00", "HUAWEI Pocket 2");
        // <在这里粘贴更多华为/荣耀型号>
        // —————— 【荣耀 HONOR】 映射补充 ——————
        // Magic 系列 (旗舰)
        HUAWEI_MAP.put("ELZ-AN00", "Honor Magic3");
        HUAWEI_MAP.put("ELZ-AN10", "Honor Magic3 Pro");
        HUAWEI_MAP.put("ELZ-AN20", "Honor Magic3 Pro+");
        HUAWEI_MAP.put("ELZ-AN30", "Honor Magic3 至臻版");

        HUAWEI_MAP.put("TNA-AN00", "Honor Magic4 Pro");
        HUAWEI_MAP.put("TNA-AN10", "Honor Magic4 Pro+");

        HUAWEI_MAP.put("PGT-AN00", "Honor Magic5");
        HUAWEI_MAP.put("PGT-AN10", "Honor Magic5");
        HUAWEI_MAP.put("PGT-AN20", "Honor Magic5 Pro");
        HUAWEI_MAP.put("PGT-AN30", "Honor Magic5 Pro");
        HUAWEI_MAP.put("PGT-AN40", "Honor Magic5 至臻版");

        HUAWEI_MAP.put("BVL-AN00", "Honor Magic6"); // 注：与V40部分代号重叠，优先保障Magic
        HUAWEI_MAP.put("BVL-AN10", "Honor Magic6");
        HUAWEI_MAP.put("BVL-AN16", "Honor Magic6 Pro");
        HUAWEI_MAP.put("BVL-AN20", "Honor Magic6 至臻版/RSR");

        HUAWEI_MAP.put("PTP-AN00", "Honor Magic7");
        HUAWEI_MAP.put("PTP-AN60", "Honor Magic7");
        HUAWEI_MAP.put("PTP-AN10", "Honor Magic7 Pro");
        HUAWEI_MAP.put("PTP-AN70", "Honor Magic7 Pro");
        HUAWEI_MAP.put("PTP-AN20", "Honor Magic7 RSR保时捷");

        // 数字系列
        HUAWEI_MAP.put("RNA-AN00", "Honor 70 Pro");
        HUAWEI_MAP.put("GIA-AN00", "Honor 80 Pro");
        HUAWEI_MAP.put("JAD-AN00", "Honor 90 Pro");
        HUAWEI_MAP.put("ELP-AN00", "Honor 200 Pro");

        // V系列 / X系列
        HUAWEI_MAP.put("YOK-AN00", "Honor V50 Pro");
        HUAWEI_MAP.put("SDY-AN00", "Honor X40");
        HUAWEI_MAP.put("SDY-AN10", "Honor X40 GT");
        HUAWEI_MAP.put("FRI-AN00", "Honor X50 Pro");

        // 折叠屏系列
        HUAWEI_MAP.put("FRI-NX9", "Honor Magic Vs");
        HUAWEI_MAP.put("VER-AN00", "Honor Magic Vs2");
        HUAWEI_MAP.put("FLC-AN00", "Honor Magic Vs3");
        HUAWEI_MAP.put("FCP-AN10", "Honor Magic V3");
        HUAWEI_MAP.put("FCP-AN20", "Honor Magic V3");
        HUAWEI_MAP.put("LRA-AN00", "Honor Magic V Flip");

        // 其他
        HUAWEI_MAP.put("NAM-AN00", "Honor Play 50");

        // —————— 【小米 XIAOMI / 红米 REDMI】 待添加 ——————
        XIAOMI_MAP.put("24107PN9DC", "Xiaomi 15");
        XIAOMI_MAP.put("23127PN0CC", "Xiaomi 14");
        XIAOMI_MAP.put("23116PN5BC", "Xiaomi 14 Pro");
        // —————— 【小米 XIAOMI / 红米 REDMI】 映射补充 ——————
        // 小米 11 系列
        XIAOMI_MAP.put("M2011K2G", "Xiaomi 11");
        XIAOMI_MAP.put("VENUS", "Xiaomi 11");
        XIAOMI_MAP.put("M2102K1AC", "Xiaomi 11 Pro");
        XIAOMI_MAP.put("STAR", "Xiaomi 11 Pro");
        XIAOMI_MAP.put("M2102K1C", "Xiaomi 11 Ultra");
        XIAOMI_MAP.put("MARS", "Xiaomi 11 Ultra");

        // 小米 12 / 12S 系列
        XIAOMI_MAP.put("2112123AC", "Xiaomi 12");
        XIAOMI_MAP.put("CUPID", "Xiaomi 12");
        XIAOMI_MAP.put("2112122AC", "Xiaomi 12 Pro");
        XIAOMI_MAP.put("ZEUS", "Xiaomi 12 Pro");
        XIAOMI_MAP.put("2112124AC", "Xiaomi 12X");
        XIAOMI_MAP.put("PSYCHE", "Xiaomi 12X");
        XIAOMI_MAP.put("2206122C", "Xiaomi 12S Pro");
        XIAOMI_MAP.put("UNICORN", "Xiaomi 12S Pro");
        XIAOMI_MAP.put("2203121C", "Xiaomi 12S Ultra");
        XIAOMI_MAP.put("THOR", "Xiaomi 12S Ultra");

        // 小米 13 系列
        XIAOMI_MAP.put("2211133C", "Xiaomi 13");
        XIAOMI_MAP.put("FUXI", "Xiaomi 13");
        XIAOMI_MAP.put("2210132C", "Xiaomi 13 Pro");
        XIAOMI_MAP.put("NUWA", "Xiaomi 13 Pro");
        XIAOMI_MAP.put("23041PN5BC", "Xiaomi 13 Ultra");
        XIAOMI_MAP.put("ISHTAR", "Xiaomi 13 Ultra");

        // 小米 14 系列
        XIAOMI_MAP.put("23127PN0CC", "Xiaomi 14");
        XIAOMI_MAP.put("HOULI", "Xiaomi 14");
        XIAOMI_MAP.put("23116PN5BC", "Xiaomi 14 Pro");
        XIAOMI_MAP.put("LUNAR", "Xiaomi 14 Pro");
        XIAOMI_MAP.put("24031PN0DC", "Xiaomi 14 Ultra");
        XIAOMI_MAP.put("AURORA", "Xiaomi 14 Ultra");

        // 小米 15 系列 (2024-2025 最新)
        XIAOMI_MAP.put("24129PN74C", "Xiaomi 15");
        XIAOMI_MAP.put("DADA", "Xiaomi 15");
        XIAOMI_MAP.put("24101PNB7C", "Xiaomi 15 Pro");
        XIAOMI_MAP.put("HAOTIAN", "Xiaomi 15 Pro");
        XIAOMI_MAP.put("25010PN30C", "Xiaomi 15 Ultra");
        XIAOMI_MAP.put("XUANYUAN", "Xiaomi 15 Ultra");
        XIAOMI_MAP.put("25042PN24C", "Xiaomi 15S Pro");
        XIAOMI_MAP.put("DIJUN", "Xiaomi 15S Pro");
        XIAOMI_MAP.put("25069PTEBG", "Xiaomi 15T");
        XIAOMI_MAP.put("KLIMT", "Xiaomi 15T");
        XIAOMI_MAP.put("2506BPN68G", "Xiaomi 15T Pro");
        XIAOMI_MAP.put("TURNER", "Xiaomi 15T Pro");

        // 红米 Redmi K 系列
        XIAOMI_MAP.put("22041211AC", "Redmi K50");
        XIAOMI_MAP.put("RUBENS", "Redmi K50");
        XIAOMI_MAP.put("2202122AC", "Redmi K50 Pro");
        XIAOMI_MAP.put("MATISSE", "Redmi K50 Pro");
        XIAOMI_MAP.put("22122RK93C", "Redmi K60");
        XIAOMI_MAP.put("SOCRATES", "Redmi K60");
        XIAOMI_MAP.put("22101RK66C", "Redmi K60 Pro");
        XIAOMI_MAP.put("MURPHY", "Redmi K60 Pro");
        XIAOMI_MAP.put("23127RK46C", "Redmi K70");
        XIAOMI_MAP.put("LIGHT", "Redmi K70");
        XIAOMI_MAP.put("23117RK66C", "Redmi K70 Pro");
        XIAOMI_MAP.put("FLASH", "Redmi K70 Pro");

        // 折叠屏 MIX Fold
        XIAOMI_MAP.put("22061218C", "Xiaomi MIX Fold 2");
        XIAOMI_MAP.put("ZIZHAN", "Xiaomi MIX Fold 2");
        XIAOMI_MAP.put("23081PN1DC", "Xiaomi MIX Fold 3");
        XIAOMI_MAP.put("BETELGEUSE", "Xiaomi MIX Fold 3");

        // —————— 【OPPO / 一加 / REALME】 待添加 ——————
        // —————— 【OPPO / 一加 ONEPLUS / 真我 REALME】 映射补充 ——————
        // --- OPPO Find 系列 ---
        OPPO_MAP.put("CPH2173", "OPPO Find X3");
        OPPO_MAP.put("OCTOPUS", "OPPO Find X3");
        OPPO_MAP.put("CPH2171", "OPPO Find X3 Pro");
        OPPO_MAP.put("OCTOPUSPRO", "OPPO Find X3 Pro");
        OPPO_MAP.put("CPH2145", "OPPO Find X3 Lite");
        OPPO_MAP.put("CPH2207", "OPPO Find X3 Neo");
        OPPO_MAP.put("CPH2307", "OPPO Find X5");
        OPPO_MAP.put("LUWU", "OPPO Find X5");
        OPPO_MAP.put("CPH2305", "OPPO Find X5 Pro");
        OPPO_MAP.put("BAIZE", "OPPO Find X5 Pro");
        OPPO_MAP.put("CPH2371", "OPPO Find X5 Lite");
        OPPO_MAP.put("CPH2441", "OPPO Find X6");
        OPPO_MAP.put("MONA", "OPPO Find X6");
        OPPO_MAP.put("CPH2439", "OPPO Find X6 Pro");
        OPPO_MAP.put("CPH2551", "OPPO Find X7");
        OPPO_MAP.put("DORA", "OPPO Find X7");
        OPPO_MAP.put("CPH2553", "OPPO Find X7 Ultra");
        OPPO_MAP.put("CPH2651", "OPPO Find X8");
        OPPO_MAP.put("YALA", "OPPO Find X8");
        OPPO_MAP.put("CPH2659", "OPPO Find X8 Pro");
        OPPO_MAP.put("KONKA", "OPPO Find X8 Pro");
        OPPO_MAP.put("CPH2797", "OPPO Find X9");
        OPPO_MAP.put("CPH2801", "OPPO Find X9 Ultra");

        // --- OPPO Reno / K / 折叠屏 ---
        OPPO_MAP.put("PEQM00", "OPPO Reno6");
        OPPO_MAP.put("PEPM00", "OPPO Reno6 Pro");
        OPPO_MAP.put("PEGT00", "OPPO Reno6 Pro+");
        OPPO_MAP.put("PFJM10", "OPPO Reno7");
        OPPO_MAP.put("PFKM10", "OPPO Reno7 Pro");
        OPPO_MAP.put("PGBM10", "OPPO Reno8");
        OPPO_MAP.put("PGAM10", "OPPO Reno8 Pro");
        OPPO_MAP.put("PHM110", "OPPO Reno9");
        OPPO_MAP.put("PHK110", "OPPO Reno9 Pro");
        OPPO_MAP.put("PJH110", "OPPO Reno10");
        OPPO_MAP.put("PJJ110", "OPPO Reno11"); // 代号重叠，优先匹配新一代
        OPPO_MAP.put("PJK110", "OPPO Reno11 Pro");
        OPPO_MAP.put("PLW110", "OPPO Reno12");
        OPPO_MAP.put("PLK110", "OPPO Reno12 Pro");
        OPPO_MAP.put("PEUM00", "OPPO Find N");
        OPPO_MAP.put("PGU110", "OPPO Find N2");
        OPPO_MAP.put("PHU110", "OPPO Find N3");
        OPPO_MAP.put("PLU110", "OPPO Find N4");

        // --- 一加 OnePlus 系列 ---
        OPPO_MAP.put("LE2110", "OnePlus 9");
        OPPO_MAP.put("LE2120", "OnePlus 9 Pro");
        OPPO_MAP.put("LE2100", "OnePlus 9R");
        OPPO_MAP.put("NE2210", "OnePlus 10 Pro");
        OPPO_MAP.put("PHB110", "OnePlus 11");
        OPPO_MAP.put("PLB110", "OnePlus 12");
        OPPO_MAP.put("PMB110", "OnePlus 13");
        OPPO_MAP.put("PGM110", "OnePlus Ace");
        OPPO_MAP.put("PGP110", "OnePlus Ace Pro");
        OPPO_MAP.put("PHV110", "OnePlus Ace 2V");
        OPPO_MAP.put("PLV110", "OnePlus Ace 3V");

        // --- 真我 realme 系列 ---
        OPPO_MAP.put("RMX2202", "realme GT");
        OPPO_MAP.put("RMX3031", "realme GT Neo");
        OPPO_MAP.put("RMX3370", "realme GT Neo2");
        OPPO_MAP.put("RMX3560", "realme GT Neo3");
        OPPO_MAP.put("RMX3700", "realme GT Neo5");
        OPPO_MAP.put("RMX3820", "realme GT5");
        OPPO_MAP.put("RMX3920", "realme GT6");
        OPPO_MAP.put("RMX5010", "realme GT7 Pro");
        OPPO_MAP.put("RMX3610", "realme 10");
        OPPO_MAP.put("RMX3687", "realme 10 Pro+");
        OPPO_MAP.put("RMX3770", "realme 11 Pro");
        OPPO_MAP.put("RMX3740", "realme 11 Pro+");
        OPPO_MAP.put("RMX3161", "realme Q3");
        OPPO_MAP.put("RMX3475", "realme Q5 Pro");

        // —————— 【VIVO / IQOO】 待添加 ——————
        // —————— 【VIVO / IQOO】 映射补充 ——————
        // --- vivo X 影像旗舰系列 ---
        VIVO_MAP.put("V2133A", "vivo X70");
        VIVO_MAP.put("V2134A", "vivo X70 Pro");
        VIVO_MAP.put("V2135A", "vivo X70 Pro+");
        VIVO_MAP.put("V2183A", "vivo X80");
        VIVO_MAP.put("V2185A", "vivo X80 Pro");
        VIVO_MAP.put("V2241A", "vivo X90");
        VIVO_MAP.put("V2242A", "vivo X90 Pro");
        VIVO_MAP.put("V2243A", "vivo X90 Pro+");
        VIVO_MAP.put("V2309A", "vivo X100");
        VIVO_MAP.put("V2310A", "vivo X100 Pro");
        VIVO_MAP.put("V2352A", "vivo X100 Ultra");
        VIVO_MAP.put("THANOS", "vivo X100 Ultra"); // 灭霸
        VIVO_MAP.put("V2429A", "vivo X200");
        VIVO_MAP.put("V2430A", "vivo X200 Pro");
        VIVO_MAP.put("V2431A", "vivo X200 Ultra");
        VIVO_MAP.put("V2515A", "vivo X300");
        VIVO_MAP.put("V2516A", "vivo X300s");

        // --- vivo S / Y 系列 ---
        VIVO_MAP.put("V2121A", "vivo S10");
        VIVO_MAP.put("V2162A", "vivo S12");
        VIVO_MAP.put("V2203A", "vivo S15");
        VIVO_MAP.put("V2244A", "vivo S16");
        VIVO_MAP.put("V2283A", "vivo S17");
        VIVO_MAP.put("V2323A", "vivo S18");
        VIVO_MAP.put("V2364A", "vivo S19");
        VIVO_MAP.put("V2485A", "vivo S30");
        VIVO_MAP.put("V2541A", "vivo S50");
        VIVO_MAP.put("V2120A", "vivo Y55s");
        VIVO_MAP.put("V2219A", "vivo Y77");
        VIVO_MAP.put("V2317A", "vivo Y100");

        // --- vivo 折叠屏系列 ---
        VIVO_MAP.put("V2178A", "vivo X Fold");
        VIVO_MAP.put("V2179A", "vivo X Fold+");
        VIVO_MAP.put("V2266A", "vivo X Fold2");
        VIVO_MAP.put("V2337A", "vivo X Fold3");
        VIVO_MAP.put("V2338A", "vivo X Fold3 Pro");
        VIVO_MAP.put("V2255A", "vivo X Flip");
        VIVO_MAP.put("V2445A", "vivo X Flip2");

        // --- iQOO 数字旗舰系列 ---
        VIVO_MAP.put("V2141A", "iQOO 8");
        VIVO_MAP.put("V2142A", "iQOO 8 Pro");
        VIVO_MAP.put("V2171A", "iQOO 9");
        VIVO_MAP.put("V2172A", "iQOO 9 Pro");
        VIVO_MAP.put("V2218A", "iQOO 10");
        VIVO_MAP.put("V2254A", "iQOO 11");
        VIVO_MAP.put("V2308A", "iQOO 12");
        VIVO_MAP.put("V2365A", "iQOO 13");
        VIVO_MAP.put("V2366A", "iQOO 13 Pro");
        VIVO_MAP.put("V2435A", "iQOO 15");
        VIVO_MAP.put("V2436A", "iQOO 15 Pro");
        VIVO_MAP.put("V2451A", "iQOO 15 Ultra");

        // --- iQOO Neo / Z / U 系列 ---
        VIVO_MAP.put("V2114A", "iQOO Neo5");
        VIVO_MAP.put("V2196A", "iQOO Neo6");
        VIVO_MAP.put("V2231A", "iQOO Neo7");
        VIVO_MAP.put("V2261A", "iQOO Neo8");
        VIVO_MAP.put("V2329A", "iQOO Neo9");
        VIVO_MAP.put("V2407A", "iQOO Neo10");
        VIVO_MAP.put("V2148A", "iQOO Z5");
        VIVO_MAP.put("V2220A", "iQOO Z6");
        VIVO_MAP.put("V2270A", "iQOO Z7");
        VIVO_MAP.put("V2344A", "iQOO Z8");
        VIVO_MAP.put("V2417A", "iQOO Z9");
        VIVO_MAP.put("V2106A", "iQOO U3");
        VIVO_MAP.put("V2205A", "iQOO U5");

        // —————— 【三星 SAMSUNG】 待添加 ——————
        // —————— 【三星 SAMSUNG】 映射补充 ——————
        // --- Galaxy S 系列 (旗舰) ---
        SAMSUNG_MAP.put("SM-G9910", "Samsung Galaxy S21");
        SAMSUNG_MAP.put("SM-G9960", "Samsung Galaxy S21+");
        SAMSUNG_MAP.put("SM-G9980", "Samsung Galaxy S21 Ultra");
        SAMSUNG_MAP.put("SM-S9010", "Samsung Galaxy S22");
        SAMSUNG_MAP.put("SM-S9060", "Samsung Galaxy S22+");
        SAMSUNG_MAP.put("SM-S9080", "Samsung Galaxy S22 Ultra");
        SAMSUNG_MAP.put("SM-S9110", "Samsung Galaxy S23");
        SAMSUNG_MAP.put("SM-S9160", "Samsung Galaxy S23+");
        SAMSUNG_MAP.put("SM-S9180", "Samsung Galaxy S23 Ultra");
        SAMSUNG_MAP.put("SM-S9210", "Samsung Galaxy S24");
        SAMSUNG_MAP.put("SM-S9260", "Samsung Galaxy S24+");
        SAMSUNG_MAP.put("SM-S9280", "Samsung Galaxy S24 Ultra");
        SAMSUNG_MAP.put("SM-S9310", "Samsung Galaxy S25");
        SAMSUNG_MAP.put("SM-S9360", "Samsung Galaxy S25+");
        SAMSUNG_MAP.put("SM-S9380", "Samsung Galaxy S25 Ultra");
        SAMSUNG_MAP.put("SM-S9370", "Samsung Galaxy S25 Edge");
        SAMSUNG_MAP.put("SM-S9420", "Samsung Galaxy S26 Pro");
        SAMSUNG_MAP.put("SM-S9470", "Samsung Galaxy S26 Edge");
        SAMSUNG_MAP.put("SM-S9480", "Samsung Galaxy S26 Ultra");

        // --- Galaxy Z 系列 (折叠屏) ---
        SAMSUNG_MAP.put("SM-F9260", "Samsung Galaxy Z Fold3");
        SAMSUNG_MAP.put("SM-F9360", "Samsung Galaxy Z Fold4");
        SAMSUNG_MAP.put("SM-F9460", "Samsung Galaxy Z Fold5");
        SAMSUNG_MAP.put("SM-F9560", "Samsung Galaxy Z Fold6");
        SAMSUNG_MAP.put("SM-F7110", "Samsung Galaxy Z Flip3");
        SAMSUNG_MAP.put("SM-F7210", "Samsung Galaxy Z Flip4");
        SAMSUNG_MAP.put("SM-F7310", "Samsung Galaxy Z Flip5");
        SAMSUNG_MAP.put("SM-F7410", "Samsung Galaxy Z Flip6");

        // --- 心系天下 W 系列 (高端定制) ---
        SAMSUNG_MAP.put("SM-W2022", "Samsung W22");
        SAMSUNG_MAP.put("SM-W2023", "Samsung W23");
        SAMSUNG_MAP.put("SM-W9024", "Samsung W24");
        SAMSUNG_MAP.put("SM-W9025", "Samsung W25");
        SAMSUNG_MAP.put("SM-W9026", "Samsung W26");

        // --- Galaxy A 系列 (中端) ---
        SAMSUNG_MAP.put("SM-A5260", "Samsung Galaxy A52 5G");
        SAMSUNG_MAP.put("SM-A5360", "Samsung Galaxy A53 5G");
        SAMSUNG_MAP.put("SM-A5460", "Samsung Galaxy A54 5G");
        SAMSUNG_MAP.put("SM-A5560", "Samsung Galaxy A55 5G");
        SAMSUNG_MAP.put("SM-A5660", "Samsung Galaxy A56 5G");
        SAMSUNG_MAP.put("SM-A3560", "Samsung Galaxy A35 5G");

        // --- 研发代号兜底 (可选) ---
        SAMSUNG_MAP.put("EUREKAULTRA", "Samsung Galaxy S24 Ultra");
        SAMSUNG_MAP.put("PARADIGMULTRA", "Samsung Galaxy S25 Ultra");

        // —————— 【苹果 IPHONE】 待添加 ——————
        // —————— 【苹果 APPLE IPHONE】 最终精准映射 ——————
        // iPhone 17 系列 / Air (2025 预测)
        APPLE_MAP.put("iPhone18,1", "iPhone 17");
        APPLE_MAP.put("A3521", "iPhone 17");
        APPLE_MAP.put("iPhone18,2", "iPhone 17 Plus");
        APPLE_MAP.put("A3523", "iPhone 17 Plus");
        APPLE_MAP.put("iPhone18,3", "iPhone 17 Pro");
        APPLE_MAP.put("A3525", "iPhone 17 Pro");
        APPLE_MAP.put("iPhone18,4", "iPhone 17 Pro Max");
        APPLE_MAP.put("A3527", "iPhone 17 Pro Max");
        APPLE_MAP.put("iPhone18,5", "iPhone Air");
        APPLE_MAP.put("A3529", "iPhone Air");

        // iPhone 16 系列 (2024)
        APPLE_MAP.put("iPhone17,1", "iPhone 16");
        APPLE_MAP.put("A3288", "iPhone 16");
        APPLE_MAP.put("iPhone17,2", "iPhone 16 Plus");
        APPLE_MAP.put("A3290", "iPhone 16 Plus");
        APPLE_MAP.put("iPhone17,3", "iPhone 16 Pro");
        APPLE_MAP.put("A3292", "iPhone 16 Pro");
        APPLE_MAP.put("iPhone17,4", "iPhone 16 Pro Max");
        APPLE_MAP.put("A3294", "iPhone 16 Pro Max");
        APPLE_MAP.put("iPhone17,5", "iPhone 16e");
        APPLE_MAP.put("A3302", "iPhone 16e");

        // iPhone 15 系列 (2023)
        APPLE_MAP.put("iPhone16,1", "iPhone 15");
        APPLE_MAP.put("A2994", "iPhone 15");
        APPLE_MAP.put("iPhone16,2", "iPhone 15 Plus");
        APPLE_MAP.put("A2996", "iPhone 15 Plus");
        APPLE_MAP.put("iPhone16,3", "iPhone 15 Pro");
        APPLE_MAP.put("A2998", "iPhone 15 Pro");
        APPLE_MAP.put("iPhone16,4", "iPhone 15 Pro Max");
        APPLE_MAP.put("A3000", "iPhone 15 Pro Max");

        // iPhone 14 系列 (2022)
        APPLE_MAP.put("iPhone15,2", "iPhone 14");
        APPLE_MAP.put("A2884", "iPhone 14");
        APPLE_MAP.put("iPhone15,4", "iPhone 14 Plus");
        APPLE_MAP.put("A2888", "iPhone 14 Plus");
        APPLE_MAP.put("iPhone15,3", "iPhone 14 Pro");
        APPLE_MAP.put("A2890", "iPhone 14 Pro");
        APPLE_MAP.put("iPhone15,5", "iPhone 14 Pro Max");
        APPLE_MAP.put("A2894", "iPhone 14 Pro Max");

        // iPhone 13 / SE 3 系列 (2021-2022)
        APPLE_MAP.put("iPhone14,5", "iPhone 13");
        APPLE_MAP.put("A2634", "iPhone 13");
        APPLE_MAP.put("iPhone14,4", "iPhone 13 mini");
        APPLE_MAP.put("A2629", "iPhone 13 mini");
        APPLE_MAP.put("iPhone14,2", "iPhone 13 Pro");
        APPLE_MAP.put("A2636", "iPhone 13 Pro");
        APPLE_MAP.put("iPhone14,3", "iPhone 13 Pro Max");
        APPLE_MAP.put("A2644", "iPhone 13 Pro Max");
        APPLE_MAP.put("iPhone14,6", "iPhone SE (3rd Gen)");
        APPLE_MAP.put("A2785", "iPhone SE (3rd Gen)");
    }

    // ============================================================
    // 第二步：二级映射查询逻辑
    // ============================================================
    private static String mapModelName(String make, String model) {
        if (model == null || model.trim().isEmpty()) return model;

        String makeLower = (make != null) ? make.toLowerCase() : "";
        String modelKey = model.toUpperCase(); // 映射表通常用大写作为 Key

        // 1. 苹果
        if (makeLower.contains("apple") || makeLower.contains("iphone")) {
            return APPLE_MAP.getOrDefault(modelKey, model);
        }
        // 2. 华为 & 荣耀 (通常公用华为映射逻辑)
        if (makeLower.contains("huawei") || makeLower.contains("honor")) {
            return HUAWEI_MAP.getOrDefault(modelKey, model);
        }
        // 3. 小米 & 红米
        if (makeLower.contains("xiaomi") || makeLower.contains("redmi")) {
            return XIAOMI_MAP.getOrDefault(modelKey, model);
        }
        // 4. OPPO / 一加 / 真我
        if (makeLower.contains("oppo") || makeLower.contains("oneplus") || makeLower.contains("realme")) {
            return OPPO_MAP.getOrDefault(modelKey, model);
        }
        // 5. vivo / iQOO
        if (makeLower.contains("vivo") || makeLower.contains("iqoo")) {
            return VIVO_MAP.getOrDefault(modelKey, model);
        }
        // 6. 三星
        if (makeLower.contains("samsung")) {
            return SAMSUNG_MAP.getOrDefault(modelKey, model);
        }

        return model; // 如果都没匹配到，返回原始型号
    }

    public static Map<String, String> getPhotoInfo(Context context, Uri uri) {
        Map<String, String> info = new HashMap<>();
        try (InputStream is = context.getContentResolver().openInputStream(uri)) {
            ExifInterface exif = new ExifInterface(is);

            // 1. 抓取原始 EXIF 字段
            String rawMake = exif.getAttribute(ExifInterface.TAG_MAKE);
            String rawModel = exif.getAttribute(ExifInterface.TAG_MODEL);
            String rawLens = exif.getAttribute(ExifInterface.TAG_LENS_MODEL);

            // 2. 判定设备类型
            boolean isMobileDevice = isMobile(rawMake, rawModel);

            // 3. 处理机身名称 (Device) —— 接入映射表逻辑
            String finalDevice;
            if (isMobileDevice) {
                // 【核心修改】：如果是手机，先尝试查找对应表
                if (rawModel != null && !rawModel.trim().isEmpty()) {
                    // mapModelName 会优先返回正式名称，查不到则返回原 rawModel
                    finalDevice = mapModelName(rawMake, rawModel);
                } else {
                    finalDevice = "Smartphone";
                }
            } else {
                // 相机逻辑：多级抓取策略
                if (rawModel != null && !rawModel.trim().isEmpty()) {
                    // 情况 A: 有型号名，检查是否需要补齐品牌名
                    if (rawMake != null && !rawModel.toLowerCase().contains(rawMake.toLowerCase())) {
                        String brand = rawMake.split(" ")[0];
                        if (!brand.isEmpty()) {
                            brand = brand.substring(0, 1).toUpperCase() + brand.substring(1).toLowerCase();
                            finalDevice = brand + " " + rawModel;
                        } else {
                            finalDevice = rawModel;
                        }
                    } else {
                        finalDevice = rawModel;
                    }
                } else if (rawMake != null && !rawMake.trim().isEmpty()) {
                    // 情况 B: 型号丢失但有制造商信息
                    finalDevice = rawMake;
                } else if (rawLens != null && !rawLens.trim().isEmpty()) {
                    // 情况 C: 型号和制造商全丢，通过镜头反推品牌
                    String lensLower = rawLens.toLowerCase();
                    if (lensLower.contains("fe ") || lensLower.contains(" e ") || lensLower.contains("sel")) {
                        finalDevice = "Sony Camera";
                    } else if (lensLower.contains("rf") || lensLower.contains("ef") || lensLower.contains("canon")) {
                        finalDevice = "Canon Camera";
                    } else if (lensLower.contains("nikkor") || lensLower.contains("nikon")) {
                        finalDevice = "Nikon Camera";
                    } else if (lensLower.contains("fujinon") || lensLower.contains("xf")) {
                        finalDevice = "Fujifilm Camera";
                    } else if (lensLower.contains("lumix")) {
                        finalDevice = "Panasonic Camera";
                    } else {
                        finalDevice = "Digital Camera";
                    }
                } else {
                    finalDevice = "Unknown Device";
                }
            }
            info.put("device", finalDevice);

            // 4. 处理镜头信息 (Lens)
            if (isMobileDevice) {
                // 手机：强制显示等效焦段
                int focal35mm = exif.getAttributeInt(ExifInterface.TAG_FOCAL_LENGTH_IN_35MM_FILM, 0);
                if (focal35mm > 0) {
                    info.put("lens", "LENS " + focal35mm + "MM");
                } else {
                    double f = exif.getAttributeDouble(ExifInterface.TAG_FOCAL_LENGTH, 0);
                    info.put("lens", f > 0 ? "LENS " + (int)Math.round(f) + "MM" : "MOBILE LENS");
                }
            } else {
                // 相机：显示原始镜头型号
                info.put("lens", (rawLens != null && !rawLens.trim().isEmpty()) ? rawLens : "Unknown Lens");
            }

            // 5. 处理快门速度 (s)
            double exposureTime = exif.getAttributeDouble(ExifInterface.TAG_EXPOSURE_TIME, 0);
            String shutter;
            if (exposureTime >= 1.0) {
                shutter = exposureTime + "s";
            } else if (exposureTime > 0) {
                shutter = "1/" + (int) Math.round(1.0 / exposureTime) + "s";
            } else {
                shutter = "1/100s";
            }
            info.put("s", shutter);

            // 6. 处理光圈值 (f)
            double fNumber = exif.getAttributeDouble(ExifInterface.TAG_F_NUMBER, 0);
            String aperture;
            if (fNumber > 0) {
                double roundedF = Math.round(fNumber * 10.0) / 10.0;
                aperture = "f/" + roundedF;
            } else {
                aperture = "f/2.8";
            }
            info.put("f", aperture);

            // 7. 处理 ISO
            String isoValue = exif.getAttribute(ExifInterface.TAG_ISO_SPEED_RATINGS);
            info.put("iso", (isoValue != null) ? "ISO " + isoValue : "ISO 100");

            // 8. 拼接汇总字段
            info.put("param", shutter + "  " + aperture + "  " + info.get("iso"));

        } catch (Exception e) {
            e.printStackTrace();
        }
        return info;
    }

    private static String inferCameraBrand(String lens) {
        String l = lens.toLowerCase();
        if (l.contains("fe ") || l.contains(" e ") || l.contains("sel")) return "Sony Camera";
        if (l.contains("rf") || l.contains("ef") || l.contains("canon")) return "Canon Camera";
        if (l.contains("nikkor") || l.contains("nikon")) return "Nikon Camera";
        return "Digital Camera";
    }

    private static boolean isMobile(String make, String model) {
        if (make == null && model == null) return false;
        String bio = ((make != null ? make : "") + " " + (model != null ? model : "")).toLowerCase();
        if (bio.contains("sony")) return bio.contains("xq-") || bio.contains("so-") || bio.contains("xperia");
        if (bio.contains("leica")) return bio.contains("leitz");
        return bio.contains("apple") || bio.contains("iphone") || bio.contains("xiaomi") ||
                bio.contains("huawei") || bio.contains("samsung") || bio.contains("oppo") ||
                bio.contains("vivo") || bio.contains("honor") || bio.contains("realme") || bio.contains("oneplus");
    }
}