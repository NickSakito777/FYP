package com.example.lbsdemo.task;

import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;
import androidx.room.PrimaryKey;

/**
 * 任务验证数据实体类
 * 用于存储任务验证的相关信息，如照片URI、验证时间等
 */
@Entity(tableName = "task_verification",
        foreignKeys = @ForeignKey(
                entity = TaskData.class,
                parentColumns = "id",
                childColumns = "task_id",
                onDelete = ForeignKey.CASCADE
        ),
        indices = {@Index("task_id")})
public class TaskVerificationData {
    @PrimaryKey(autoGenerate = true)
    public int id;

    @ColumnInfo(name = "task_id")
    public int taskId;
    
    @ColumnInfo(name = "user_id")
    public String userId;

    @ColumnInfo(name = "verification_type")
    public String verificationType; // "photo", "geofence", "time", "time+geofence"
    
    @ColumnInfo(name = "verification_data")
    public String verificationData; // 对于照片，存储URI；对于位置，存储坐标等
    
    @ColumnInfo(name = "llm_description")
    public String llmDescription; // LLM对照片的描述或其他验证数据的分析
    
    @ColumnInfo(name = "photo_verification_prompt")
    public String photoVerificationPrompt; // 照片验证的提示词
    
    @ColumnInfo(name = "verification_result")
    public boolean verificationResult; // 修改为boolean类型
    
    @ColumnInfo(name = "confidence")
    public int confidence; // 验证的置信度(0-100)
    
    @ColumnInfo(name = "feedback")
    public String feedback; // 提供给用户的反馈信息
    
    @ColumnInfo(name = "verification_status")
    public String verificationStatus; // "pending", "verified", "failed"
    
    @ColumnInfo(name = "timestamp")
    public long timestamp; // 验证时间戳
    
    /**
     * 默认构造函数
     */
    public TaskVerificationData() {
        this.timestamp = System.currentTimeMillis();
        this.verificationResult = false;
        this.confidence = 0;
    }
    
    /**
     * 创建照片验证记录
     */
    public static TaskVerificationData createPhotoVerification(int taskId, String userId, String photoUri, String llmDescription) {
        TaskVerificationData data = new TaskVerificationData();
        data.taskId = taskId;
        data.userId = userId;
        data.verificationType = "photo";
        data.verificationData = photoUri;
        data.llmDescription = llmDescription;
        data.verificationStatus = "pending";
        return data;
    }
    
    /**
     * 创建照片验证记录（包含提示词）
     */
    public static TaskVerificationData createPhotoVerificationWithPrompt(int taskId, String userId, String photoUri, String prompt) {
        TaskVerificationData data = new TaskVerificationData();
        data.taskId = taskId;
        data.userId = userId;
        data.verificationType = "photo";
        data.verificationData = photoUri;
        data.photoVerificationPrompt = prompt;
        data.verificationStatus = "pending";
        return data;
    }
    
    /**
     * 创建地理围栏验证记录
     */
    public static TaskVerificationData createGeofenceVerification(int taskId, String userId, double latitude, double longitude) {
        TaskVerificationData data = new TaskVerificationData();
        data.taskId = taskId;
        data.userId = userId;
        data.verificationType = "geofence";
        data.verificationData = latitude + "," + longitude;
        return data;
    }
    
    /**
     * 创建时长验证记录
     */
    public static TaskVerificationData createTimeVerification(int taskId, String userId, int durationMinutes) {
        TaskVerificationData data = new TaskVerificationData();
        data.taskId = taskId;
        data.userId = userId;
        data.verificationType = "time";
        data.verificationData = String.valueOf(durationMinutes);
        data.verificationStatus = "pending";
        return data;
    }
    
    /**
     * 创建组合验证记录（时长+地理围栏）
     */
    public static TaskVerificationData createTimeGeofenceVerification(int taskId, String userId, 
                                                                       double latitude, double longitude, 
                                                                       int durationMinutes) {
        TaskVerificationData data = new TaskVerificationData();
        data.taskId = taskId;
        data.userId = userId;
        data.verificationType = "time+geofence";
        // 结合位置和时长信息
        data.verificationData = latitude + "," + longitude + "," + durationMinutes;
        data.verificationStatus = "pending";
        return data;
    }
} 