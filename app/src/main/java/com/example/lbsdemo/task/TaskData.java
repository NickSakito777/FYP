// TaskData.java
package com.example.lbsdemo.task;

import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Ignore;
import androidx.room.Index;
import androidx.room.PrimaryKey;

import com.example.lbsdemo.user.User;

/**
 * TaskData实体类
 * 用于存储任务信息，包括主线任务、子任务和每日任务
 */
@Entity(tableName = "daily_tasks",
        foreignKeys = @ForeignKey(
                entity = User.class,
                parentColumns = "studentId",
                childColumns = "user_id",
                onDelete = ForeignKey.CASCADE
        ),
        indices = {@Index("user_id")})
public class TaskData {
    @PrimaryKey(autoGenerate = true)
    public int id;

    @ColumnInfo(name = "user_id")
    public String userId;

    @ColumnInfo(name = "title")
    public String title;

    @ColumnInfo(name = "description")
    public String description;

    @ColumnInfo(name = "location")
    public String location; // 建议的学习地点

    @ColumnInfo(name = "start_time")
    public String startTime; // 建议的开始时间

    @ColumnInfo(name = "duration_minutes")
    public int durationMinutes; // 建议的持续时间

    @ColumnInfo(name = "priority")
    public int priority; // 1-5的优先级

    @ColumnInfo(name = "is_completed")
    public boolean isCompleted; // 是否已完成

    @ColumnInfo(name = "creation_date")
    public String creationDate; // 创建日期 yyyy-MM-dd

    @ColumnInfo(name = "creation_timestamp")
    public long creationTimestamp; // 创建时间戳
    
    // 新增字段 - 任务层级关系
    @ColumnInfo(name = "task_type")
    public String taskType; // "main", "sub", "daily"
    
    @ColumnInfo(name = "parent_task_id")
    public Integer parentTaskId; // 父任务ID，可为null
    
    @ColumnInfo(name = "character_id")
    public String characterId; // 关联的虚拟角色ID
    
    @ColumnInfo(name = "storyline_context")
    public String storylineContext; // 任务的故事背景
    
    @ColumnInfo(name = "verification_method")
    public String verificationMethod; // "geofence", "photo", "bluetooth", "time"
    
    // 位置信息（用于地理围栏验证）
    @ColumnInfo(name = "latitude")
    public Double latitude; // 任务地点纬度，可为null
    
    @ColumnInfo(name = "longitude")
    public Double longitude; // 任务地点经度，可为null
    
    @ColumnInfo(name = "radius")
    public Integer radius; // 地理围栏半径(米)，可为null
    
    // 添加新的字段
    @ColumnInfo(name = "status")
    public String status; // 可能的值: "pending", "accepted", "rejected"
    
    // 添加拍照任务位置验证标识字段
    @ColumnInfo(name = "position_id")
    public int positionID = 0; // 0表示无位置要求，1表示有位置要求
    
    // 添加照片验证提示词字段
    @ColumnInfo(name = "photo_verification_prompt")
    public String photoVerificationPrompt; // 照片验证的提示词
    
    // 添加新的字段：特工任务类型
    @ColumnInfo(name = "agent_task_type")
    public String agentTaskType; // 可选值："crucial_action" 或 "support"
    
    /**
     * 默认构造函数 - Room将使用这个构造函数
     */
    public TaskData() {
        this.isCompleted = false;
        this.creationTimestamp = System.currentTimeMillis();
    }
    
    /**
     * 创建日常任务的构造函数 - 使用@Ignore标记为非Room构造函数
     */
    @Ignore
    public TaskData(String userId, String title, String description, String location, 
                   String startTime, int durationMinutes, int priority) {
        this();
        this.userId = userId;
        this.title = title;
        this.description = description;
        this.location = location;
        this.startTime = startTime;
        this.durationMinutes = durationMinutes;
        this.priority = priority;
        this.taskType = "daily"; // 默认为日常任务
        this.creationDate = new java.text.SimpleDateFormat("yyyy-MM-dd")
                              .format(new java.util.Date());
    }

    // 添加缺失的getter方法
    public Integer getId() {
        return id;
    }
    
    public String getTitle() {
        return title;
    }
    
    public String getDescription() {
        return description;
    }
    
    public String getLocation() {
        return location;
    }
    
    public String getCharacterId() {
        return characterId;
    }
    
    public String getStorylineContext() {
        return storylineContext;
    }
    
    public String getVerificationMethod() {
        return verificationMethod;
    }
    
    public int getPositionID() {
        return positionID;
    }
    
    public String getPhotoVerificationPrompt() {
        return photoVerificationPrompt;
    }

    public String getAgentTaskType() {
        return agentTaskType;
    }
}
