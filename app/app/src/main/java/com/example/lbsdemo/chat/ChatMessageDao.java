package com.example.lbsdemo.chat;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Update;

import java.util.List;

/**
 * ChatMessageDao接口
 * 定义对聊天历史表的数据库操作
 */
@Dao
public interface ChatMessageDao {
    
    /**
     * 插入新消息
     * @param message 聊天消息
     * @return 插入的消息ID
     */
    @Insert
    long insert(ChatMessage message);
    
    /**
     * 更新消息
     * @param message 聊天消息
     */
    @Update
    void update(ChatMessage message);
    
    /**
     * 获取指定用户最近的消息记录
     * @param userId 用户ID
     * @param limit 返回的消息数量限制
     * @return 最近的消息列表，按时间戳升序排序
     */
    @Query("SELECT * FROM chat_history WHERE user_id = :userId ORDER BY timestamp ASC LIMIT :limit")
    List<ChatMessage> getRecentMessagesByUserId(String userId, int limit);
    
    /**
     * 获取与特定角色相关的最近消息
     * @param userId 用户ID
     * @param characterId 角色ID
     * @param limit 返回的消息数量限制
     * @return 最近的消息列表，按时间戳升序排序
     */
    @Query("SELECT * FROM chat_history WHERE user_id = :userId AND related_character_id = :characterId ORDER BY timestamp ASC LIMIT :limit")
    List<ChatMessage> getRecentMessagesByCharacterId(String userId, String characterId, int limit);
    
    /**
     * 获取与特定任务相关的消息
     * @param taskId 任务ID
     * @return 与任务相关的消息列表
     */
    @Query("SELECT * FROM chat_history WHERE related_task_id = :taskId ORDER BY timestamp ASC")
    List<ChatMessage> getMessagesByTaskId(int taskId);
    
    /**
     * 获取特定类型的消息
     * @param userId 用户ID
     * @param type 消息类型
     * @return 特定类型的消息列表
     */
    @Query("SELECT * FROM chat_history WHERE user_id = :userId AND message_type = :type ORDER BY timestamp DESC")
    List<ChatMessage> getMessagesByType(String userId, String type);
    
    /**
     * 删除某一时间点之前的消息
     * @param userId 用户ID
     * @param timestamp 时间戳
     */
    @Query("DELETE FROM chat_history WHERE user_id = :userId AND timestamp < :timestamp")
    void deleteMessagesBeforeTimestamp(String userId, long timestamp);
    
    /**
     * 统计用户消息总数
     * @param userId 用户ID
     * @return 消息总数
     */
    @Query("SELECT COUNT(*) FROM chat_history WHERE user_id = :userId")
    int countMessagesForUser(String userId);
} 