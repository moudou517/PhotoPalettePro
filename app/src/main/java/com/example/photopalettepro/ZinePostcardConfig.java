package com.example.photopalettepro;

/**
 * Zine 明信片元数据配置
 *
 * 遵循 photo-to-zine-postcard skill 第 11 节：只允许 title / 可选 subtitle /
 * LOCATION / DATE / 小序号；未填写时保持留空，不编造内容。
 */
public class ZinePostcardConfig {

    /** 标题 */
    public String title = "";
    /** 可选短副标题 */
    public String subtitle = "";
    /** 拍摄地点（LOCATION） */
    public String location = "";
    /** 拍摄日期（DATE） */
    public String date = "";
    /** 小序号，默认 01 */
    public String index = "01";

    public ZinePostcardConfig() {
    }

    public ZinePostcardConfig(String title, String subtitle, String location, String date, String index) {
        this.title = title == null ? "" : title;
        this.subtitle = subtitle == null ? "" : subtitle;
        this.location = location == null ? "" : location;
        this.date = date == null ? "" : date;
        this.index = (index == null || index.isEmpty()) ? "01" : index;
    }
}
