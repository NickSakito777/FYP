package com.example.lbsdemo.chat;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.Ignore;
import androidx.room.PrimaryKey;

import java.util.Date;

/**
 * ChatMessage实体类
 * 用于存储用户与系统/虚拟角色的对话历史
 */
@Entity(tableName = "chat_history")
public class ChatMessage {
    @PrimaryKey(autoGenerate = true)
    @NonNull
    public int id;
    
    @ColumnInfo(name = "user_id")
    public String userId;
    
    @ColumnInfo(name = "role") // "system", "user", "assistant"
    public String role;
    
    @ColumnInfo(name = "content")
    public String content;
    
    @ColumnInfo(name = "timestamp")
    @NonNull
    public long timestamp;
    
    @ColumnInfo(name = "related_task_id")
    public Integer relatedTaskId; // 可为null
    
    @ColumnInfo(name = "message_type")
    public String messageType; // "text", "task_card", "notification"
    
    @ColumnInfo(name = "related_character_id")
    public String relatedCharacterId; // 关联的角色ID，可为null
    
    @ColumnInfo(name = "image_uri")
    public String imageUri; // 图片URI，用于图片消息
    
    @ColumnInfo(name = "sender_name")
    public String senderName; // 发送者名称，例如"特工Zero"
    
    /**
     * 默认构造函数 - Room将使用这个构造函数
     */
    public ChatMessage() {
        this.timestamp = System.currentTimeMillis(); // 确保timestamp始终有值
    }
    
    /**
     * 完整参数构造函数 - 使用@Ignore标记为非Room构造函数
     */
    @Ignore
    public ChatMessage(String userId, String role, String content, long timestamp, Integer relatedTaskId, String messageType) {
        this.userId = userId;
        this.role = role;
        this.content = content;
        this.timestamp = timestamp;
        this.relatedTaskId = relatedTaskId;
        this.messageType = messageType;
    }
    
    /**
     * 构造函数 - 带消息类型和关联任务 - 使用@Ignore标记为非Room构造函数
     */
    @Ignore
    public ChatMessage(String userId, String role, String content, String messageType, Integer relatedTaskId) {
        this.userId = userId;
        this.role = role;
        this.content = content;
        this.messageType = messageType;
        this.relatedTaskId = relatedTaskId;
        this.timestamp = System.currentTimeMillis();
    }
    
    /**
     * 简单消息构造函数 - 使用@Ignore标记为非Room构造函数
     */
    @Ignore
    public ChatMessage(String userId, String role, String content) {
        this(userId, role, content, "text", null);
    }
    
    /**
     * 简单消息构造函数（带用户标志） - 使用@Ignore标记为非Room构造函数
     */
    @Ignore
    public ChatMessage(String role, String content, boolean isUser, Date timestamp) {
        this.userId = "current_user"; // 假设当前用户
        this.role = role;
        this.content = content;
        this.messageType = "text";
        this.timestamp = timestamp.getTime();
    }
    
    /**
     * 获取消息角色
     * @return 消息角色（system/user/assistant）
     */
    public String getRole() {
        return role;
    }
    
    /**
     * 获取消息内容
     * @return 消息内容
     */
    public String getContent() {
        return content;
    }
    
    /**
     * 获取消息时间戳
     * @return 时间戳
     */
    public long getTimestamp() {
        return timestamp;
    }
    
    /**
     * 获取关联任务ID
     * @return 任务ID
     */
    public Integer getRelatedTaskId() {
        return relatedTaskId;
    }
    
    /**
     * 获取消息类型
     * @return 消息类型
     */
    public String getMessageType() {
        return messageType;
    }
    
    /**
     * 获取用户ID
     * @return 用户ID
     */
    public String getUserId() {
        return userId;
    }
    
    /**
     * 获取图片URI
     * @return 图片URI
     */
    public String getImageUri() {
        return imageUri;
    }
    
    /**
     * 设置图片URI
     * @param imageUri 图片URI
     */
    public void setImageUri(String imageUri) {
        this.imageUri = imageUri;
        if (this.messageType == null || this.messageType.equals("text")) {
            this.messageType = "image";
        }
    }
} 