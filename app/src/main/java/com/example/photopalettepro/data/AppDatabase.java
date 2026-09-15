package com.example.photopalettepro.data;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.room.migration.Migration;
import androidx.sqlite.db.SupportSQLiteDatabase;

@Database(entities = {ExportHistory.class}, version = 4, exportSchema = false)
public abstract class AppDatabase extends RoomDatabase {

    private static volatile AppDatabase INSTANCE;

    /**
     * v2 → v3：新增 zineJson 列（Zine 明信片信息）。
     * 用正式迁移而不是破坏性迁移，保证老用户已有的导出历史不丢。
     */
    private static final Migration MIGRATION_2_3 = new Migration(2, 3) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase db) {
            db.execSQL("ALTER TABLE export_history ADD COLUMN zineJson TEXT");
        }
    };

    /**
     * v3 → v4：新增 filmJson 列（胶片边框配置）。
     *
     * <p>同样走正式迁移：老用户的导出历史里没有胶片信息是正常的，
     * 但海报 / 明信片那几列必须原样留着。
     */
    private static final Migration MIGRATION_3_4 = new Migration(3, 4) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase db) {
            db.execSQL("ALTER TABLE export_history ADD COLUMN filmJson TEXT");
        }
    };

    public abstract ExportHistoryDao exportHistoryDao();

    public static AppDatabase getDatabase(final Context context) {
        if (INSTANCE == null) {
            synchronized (AppDatabase.class) {
                if (INSTANCE == null) {
                    INSTANCE = Room.databaseBuilder(context.getApplicationContext(),
                                    AppDatabase.class, "photo_palette_pro_db")
                            .addMigrations(MIGRATION_2_3, MIGRATION_3_4)
                            // 兜底：以后若漏写迁移，宁可重建也不要闪退
                            .fallbackToDestructiveMigration()
                            .build();
                }
            }
        }
        return INSTANCE;
    }
}