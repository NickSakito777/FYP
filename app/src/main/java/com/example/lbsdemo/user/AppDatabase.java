// AppDatabase.java
package com.example.lbsdemo.user;

import android.content.Context;

import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.room.migration.Migration;
import androidx.sqlite.db.SupportSQLiteDatabase;

import com.example.lbsdemo.chat.Character;
import com.example.lbsdemo.chat.CharacterDao;
import com.example.lbsdemo.chat.ChatMessage;
import com.example.lbsdemo.chat.ChatMessageDao;
import com.example.lbsdemo.task.TaskDao;
import com.example.lbsdemo.task.TaskData;
import com.example.lbsdemo.task.TaskVerificationDao;
import com.example.lbsdemo.task.TaskVerificationData;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 应用数据库类
 * 定义所有实体和DAO接口
 */
@Database(entities = {
        User.class,
        QuestionnaireData.class,
        LocationHistoryData.class,
        TaskData.class,
        ChatMessage.class,
        Character.class,
        TaskVerificationData.class
    },
    version = 22,
    exportSchema = false)
//        BuildingCenterPositionData.class},
//        version = 8) // 版本号需递增 在每次数据库版本更新后
public abstract class AppDatabase extends RoomDatabase {
    private static volatile AppDatabase INSTANCE;
    
    // 数据库写入执行器，用于在后台线程执行数据库操作
    public static final ExecutorService databaseWriteExecutor = 
            Executors.newFixedThreadPool(4);

    // 必须声明所有DAO接口
    public abstract UserDao userDao();
    public abstract QuestionnaireDao questionnaireDao(); // 新增DAO声明 [^2]
    public abstract LocationHistoryDao locationHistoryDao();
    public abstract TaskDao taskDao();
    public abstract ChatMessageDao chatMessageDao();
    public abstract CharacterDao characterDao();
    public abstract TaskVerificationDao taskVerificationDao();

    public static AppDatabase getInstance(Context context) {
        if (INSTANCE == null) {
            synchronized (AppDatabase.class) {
                if (INSTANCE == null) {
                    INSTANCE = Room.databaseBuilder(
                                    context.getApplicationContext(),
                                    AppDatabase.class,
                                    "app_db"
                            )
                            .addMigrations(
                                MIGRATION_10_11,
                                MIGRATION_11_12,
                                MIGRATION_12_13,
                                MIGRATION_13_14,
                                MIGRATION_14_15,
                                MIGRATION_15_16,
                                MIGRATION_16_17,
                                MIGRATION_17_18,
                                MIGRATION_18_19,
                                MIGRATION_21_22
                            )
                            .fallbackToDestructiveMigration()
                            .build();
                }
            }
        }
        return INSTANCE;
    }

    // 版本迁移：从版本10到11
    private static final Migration MIGRATION_10_11 = new Migration(10, 11) {
        @Override
        public void migrate(SupportSQLiteDatabase database) {
            // 创建chat_history表
            database.execSQL(
                    "CREATE TABLE IF NOT EXISTS chat_history (" +
                            "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                            "user_id TEXT, " +
                            "role TEXT, " +
                            "content TEXT, " +
                            "timestamp INTEGER, " +
                            "related_task_id INTEGER, " +
                            "message_type TEXT)"
            );
            
            // 创建characters表
            database.execSQL(
                    "CREATE TABLE IF NOT EXISTS characters (" +
                            "id TEXT PRIMARY KEY NOT NULL, " +
                            "name TEXT, " +
                            "background TEXT, " +
                            "personality TEXT, " +
                            "avatar_resource TEXT, " +
                            "task_themes TEXT)"
            );
            
            // 更新TaskData表，添加新字段
            database.execSQL("ALTER TABLE daily_tasks ADD COLUMN task_type TEXT");
            database.execSQL("ALTER TABLE daily_tasks ADD COLUMN parent_task_id INTEGER");
            database.execSQL("ALTER TABLE daily_tasks ADD COLUMN character_id TEXT");
            database.execSQL("ALTER TABLE daily_tasks ADD COLUMN storyline_context TEXT");
            database.execSQL("ALTER TABLE daily_tasks ADD COLUMN verification_method TEXT");
            database.execSQL("ALTER TABLE daily_tasks ADD COLUMN latitude REAL");
            database.execSQL("ALTER TABLE daily_tasks ADD COLUMN longitude REAL");
            database.execSQL("ALTER TABLE daily_tasks ADD COLUMN radius INTEGER");
        }
    };
    
    // 版本迁移：从版本11到12，修复timestamp和id列的非空约束
    private static final Migration MIGRATION_11_12 = new Migration(11, 12) {
        @Override
        public void migrate(SupportSQLiteDatabase database) {
            // 进行版本11到12的数据库迁移 - 添加chat_history表
            database.execSQL("CREATE TABLE IF NOT EXISTS `chat_history` (" +
                    "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "`user_id` TEXT, " +
                    "`role` TEXT, " +
                    "`content` TEXT, " +
                    "`timestamp` INTEGER NOT NULL DEFAULT 0)");
        }
    };
    
    // 添加从版本12到13的迁移
    private static final Migration MIGRATION_12_13 = new Migration(12, 13) {
        @Override
        public void migrate(SupportSQLiteDatabase database) {
            // 向chat_history表添加related_task_id字段和message_type字段
            database.execSQL("ALTER TABLE `chat_history` ADD COLUMN `related_task_id` INTEGER");
            database.execSQL("ALTER TABLE `chat_history` ADD COLUMN `message_type` TEXT");
        }
    };
    
    // 添加从版本13到14的迁移
    private static final Migration MIGRATION_13_14 = new Migration(13, 14) {
        @Override
        public void migrate(SupportSQLiteDatabase database) {
            // 创建characters表
            database.execSQL("CREATE TABLE IF NOT EXISTS `characters` (" +
                    "`id` TEXT PRIMARY KEY NOT NULL, " +
                    "`name` TEXT, " +
                    "`background` TEXT, " +
                    "`personality` TEXT, " +
                    "`avatar_resource` TEXT, " +
                    "`task_themes` TEXT)");
        }
    };
    
    // 添加从版本14到15的迁移
    private static final Migration MIGRATION_14_15 = new Migration(14, 15) {
        @Override
        public void migrate(SupportSQLiteDatabase database) {
            // 向daily_tasks表添加status字段
            database.execSQL("ALTER TABLE `daily_tasks` ADD COLUMN `status` TEXT");
        }
    };

    // 添加从版本15到16的迁移
    private static final Migration MIGRATION_15_16 = new Migration(15, 16) {
        @Override
        public void migrate(SupportSQLiteDatabase database) {
            // 创建任务验证表
            database.execSQL("CREATE TABLE IF NOT EXISTS `task_verification` (" +
                    "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "`task_id` INTEGER NOT NULL, " +
                    "`user_id` TEXT, " +
                    "`verification_type` TEXT, " +
                    "`verification_data` TEXT, " +
                    "`llm_description` TEXT, " +
                    "`photo_verification_prompt` TEXT, " +
                    "`verification_result` INTEGER NOT NULL DEFAULT 0, " +
                    "`confidence` INTEGER NOT NULL DEFAULT 0, " +
                    "`feedback` TEXT, " +
                    "`verification_status` TEXT, " +
                    "`timestamp` INTEGER NOT NULL DEFAULT 0, " +
                    "FOREIGN KEY(`task_id`) REFERENCES `daily_tasks`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE)");
            
            // 创建索引
            database.execSQL("CREATE INDEX IF NOT EXISTS `index_task_verification_task_id` ON `task_verification` (`task_id`)");
        }
    };
    
    // 添加从版本16到17的迁移
    private static final Migration MIGRATION_16_17 = new Migration(16, 17) {
        @Override
        public void migrate(SupportSQLiteDatabase database) {
            // 这是已有的Migration，可以留空或添加实际代码
        }
    };
    
    // 添加从版本17到18的迁移
    private static final Migration MIGRATION_17_18 = new Migration(17, 18) {
        @Override
        public void migrate(SupportSQLiteDatabase database) {
            // 向daily_tasks表添加position_id字段
            database.execSQL("ALTER TABLE `daily_tasks` ADD COLUMN `position_id` INTEGER NOT NULL DEFAULT 0");
        }
    };
    
    // 添加从版本18到19的迁移
    private static final Migration MIGRATION_18_19 = new Migration(18, 19) {
        @Override
        public void migrate(SupportSQLiteDatabase database) {
            // 向daily_tasks表添加photo_verification_prompt字段
            database.execSQL("ALTER TABLE `daily_tasks` ADD COLUMN `photo_verification_prompt` TEXT");
        }
    };

    // 添加从版本21到22的迁移
    private static final Migration MIGRATION_21_22 = new Migration(21, 22) {
        @Override
        public void migrate(SupportSQLiteDatabase database) {
            // 向location_history表添加is_inside字段
            database.execSQL("ALTER TABLE `location_history` ADD COLUMN `is_inside` INTEGER DEFAULT 0");
        }
    };
}
