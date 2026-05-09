package com.example.photopalettepro.helper;

import java.util.HashMap;
import java.util.Map;

/**
 * EXIF 信息管理类
 * 职责：管理和维护照片的 EXIF 元数据
 */
public class ExifInfoManager {

    private final Map<String, String> exifInfo;

    public ExifInfoManager() {
        this.exifInfo = new HashMap<>();
    }

    /**
     * 初始化 EXIF 信息
     */
    public void initialize(Map<String, String> initialInfo) {
        if (initialInfo != null) {
            this.exifInfo.putAll(initialInfo);
        }
    }

    /**
     * 获取设备信息
     */
    public String getDevice() {
        return getValue("device", "Unknown Device");
    }

    /**
     * 设置设备信息
     */
    public void setDevice(String device) {
        setValue("device", device);
    }

    /**
     * 获取镜头信息
     */
    public String getLens() {
        return getValue("lens", "Unknown Lens");
    }

    /**
     * 设置镜头信息
     */
    public void setLens(String lens) {
        setValue("lens", lens);
    }

    /**
     * 获取快门信息
     */
    public String getShutter() {
        return getValue("s", "1/100s");
    }

    /**
     * 设置快门信息
     */
    public void setShutter(String shutter) {
        setValue("s", shutter);
    }

    /**
     * 获取光圈信息
     */
    public String getAperture() {
        return getValue("f", "f/2.8");
    }

    /**
     * 设置光圈信息
     */
    public void setAperture(String aperture) {
        setValue("f", aperture);
    }

    /**
     * 获取 ISO 信息
     */
    public String getIso() {
        return getValue("iso", "ISO 100");
    }

    /**
     * 设置 ISO 信息
     */
    public void setIso(String iso) {
        setValue("iso", iso);
    }

    /**
     * 获取水印签名
     */
    public String getSign() {
        return getValue("sign", "");
    }

    /**
     * 设置水印签名
     */
    public void setSign(String sign) {
        setValue("sign", sign);
    }

    /**
     * 获取水印显示状态
     */
    public boolean isWatermarkEnabled() {
        String value = getValue("show_watermark", "false");
        return "true".equalsIgnoreCase(value) || "1".equals(value);
    }

    /**
     * 设置水印显示状态
     */
    public void setWatermarkEnabled(boolean enabled) {
        setValue("show_watermark", String.valueOf(enabled));
    }

    /**
     * 获取所有 EXIF 信息
     */
    public Map<String, String> getAll() {
        return new HashMap<>(exifInfo);
    }

    /**
     * 更新所有信息
     */
    public void updateAll(Map<String, String> newInfo) {
        if (newInfo != null) {
            exifInfo.clear();
            exifInfo.putAll(newInfo);
        }
    }

    /**
     * 清空所有信息
     */
    public void clear() {
        exifInfo.clear();
    }

    /**
     * 获取单个值
     */
    private String getValue(String key, String defaultValue) {
        String value = exifInfo.get(key);
        return (value == null || value.isEmpty()) ? defaultValue : value;
    }

    /**
     * 设置单个值
     */
    private void setValue(String key, String value) {
        if (value != null) {
            exifInfo.put(key, value);
        }
    }

    /**
     * 检查是否为空
     */
    public boolean isEmpty() {
        return exifInfo.isEmpty();
    }
}

