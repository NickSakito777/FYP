package com.example.lbsdemo.chat;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.Ignore;
import androidx.room.PrimaryKey;

/**
 * Character实体类
 * 用于存储虚拟角色信息
 */
@Entity(tableName = "characters")
public class Character {
    @PrimaryKey
    @NonNull
    public String id;
    
    @ColumnInfo(name = "name")
    public String name;
    
    @ColumnInfo(name = "background")
    public String background;
    
    @ColumnInfo(name = "personality")
    public String personality;
    
    @ColumnInfo(name = "avatar_resource")
    public String avatarResource;
    
    @ColumnInfo(name = "task_themes")
    public String taskThemes; // JSON数组存储
    
    /**
     * 默认构造函数 - Room将使用这个构造函数
     */
    public Character() {
        this.id = "";
    }
    
    /**
     * 完整参数构造函数 - 使用@Ignore标记为非Room构造函数
     */
    @Ignore
    public Character(@NonNull String id, String name, String background, 
                    String personality, String avatarResource, String taskThemes) {
        this.id = id;
        this.name = name;
        this.background = background;
        this.personality = personality;
        this.avatarResource = avatarResource;
        this.taskThemes = taskThemes;
    }
    
    /**
     * 创建角色示例 - 使用@Ignore标记为非Room方法
     * @return 默认角色
     */
    @Ignore
    public static Character createDefaultCharacter() {
        return new Character(
            "default_assistant",
            "学习助手",
            "我是一个校园学习助手，专注于帮助学生规划学习活动和任务。",
            "友好、细心、有条理",
            "default_avatar",
            "[\"学习规划\", \"时间管理\", \"自我提升\"]"
        );
    }
    
    /**
     * 创建特工角色Zero - 使用@Ignore标记为非Room方法
     * @return 特工角色Zero
     */
    @Ignore
    public static Character createAgentZero() {
        return new Character(
            "agent_zero",
            "特工Zero",
            "我是一名秘密特工，负责招募和指导校园特工完成各种任务，共同阻止灰域组织在校园内散布虚假信息。",
            "神秘、冷静、专业、偶尔幽默",
            "agent_avatar",
            "[\"情报收集\", \"密码破解\", \"秘密任务\", \"校园安全\"]"
        );
    }
    
    /**
     * 获取角色名称
     */
    public String getName() {
        return name;
    }
    
    /**
     * 获取角色背景
     */
    public String getBackground() {
        return background;
    }
    
    /**
     * 获取角色性格
     */
    public String getPersonality() {
        return personality;
    }
    
    /**
     * 获取角色主题
     */
    public String getTaskThemes() {
        return taskThemes;
    }
} 