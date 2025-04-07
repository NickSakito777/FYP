package com.example.lbsdemo.chat;

/**
 * 简化版ChatMessage类
 * 只包含聊天界面显示所需的基本信息
 */
public class SimpleChatMessage {
    private String userId;
    private String role;  // "user" 或 "assistant"
    private String content;
    private long timestamp;
    
    public SimpleChatMessage(String userId, String role, String content) {
        this.userId = userId;
        this.role = role;
        this.content = content;
        this.timestamp = System.currentTimeMillis();
    }
    
    public String getUserId() {
        return userId;
    }
    
    public String getRole() {
        return role;
    }
    
    public String getContent() {
        return content;
    }
    
    public long getTimestamp() {
        return timestamp;
    }
} 