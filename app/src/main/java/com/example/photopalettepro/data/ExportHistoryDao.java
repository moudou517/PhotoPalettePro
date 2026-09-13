package com.example.photopalettepro.data;

import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import java.util.List;

@Dao
public interface ExportHistoryDao {

    // 插入一条新的导出配置
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    long insertHistory(ExportHistory history);

    // 获取单张图片上一次的最近导出配置规格
    @Query("SELECT * FROM export_history WHERE originalUri = :uri ORDER BY timestamp DESC LIMIT 1")
    ExportHistory getLastConfigByUri(String uri);

    // 提取所有历史导出的列表（可用于后续你做历史界面）
    @Query("SELECT * FROM export_history ORDER BY timestamp DESC")
    List<ExportHistory> getAllHistory();

    // 删除单条历史记录
    @Delete
    void deleteHistory(ExportHistory history);

    // 清空整个历史数据库
    @Query("DELETE FROM export_history")
    void clearAllHistory();
}