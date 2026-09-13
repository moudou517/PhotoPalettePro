package com.example.photopalettepro.data;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "export_history")
public class ExportHistory {
    @PrimaryKey(autoGenerate = true)
    public long id;

    // 原始照片 Uri (content://... 或 file://...)
    public String originalUri;

    // 导出时的参数（JSON 字符串）
    public String optionsJson;

    // 水印签名
    public String sign;

    // 输出文件路径或 content Uri (字符串形式)
    public String outputPath;

    // 时间戳 millis
    public long timestamp;

    // 新增：手动修改后的 EXIF 字段
    public String device;
    public String lens;
    public String shutter;
    public String aperture;
    public String iso;

    // 新增：Zine 明信片信息（JSON：title / subtitle / location / date / index）
    // 用于再次导入同一张照片时恢复上次填写的明信片内容
    public String zineJson;

    public ExportHistory(String originalUri, String optionsJson, String sign, String outputPath, long timestamp,
                         String device, String lens, String shutter, String aperture, String iso,
                         String zineJson) {
        this.originalUri = originalUri;
        this.optionsJson = optionsJson;
        this.sign = sign;
        this.outputPath = outputPath;
        this.timestamp = timestamp;
        this.device = device;
        this.lens = lens;
        this.shutter = shutter;
        this.aperture = aperture;
        this.iso = iso;
        this.zineJson = zineJson;
    }
}