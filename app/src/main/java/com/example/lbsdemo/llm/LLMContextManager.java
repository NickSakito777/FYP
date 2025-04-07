package com.example.lbsdemo.llm;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;

import com.example.lbsdemo.chat.ChatMessage;
import com.example.lbsdemo.chat.ChatMessageDao;
import com.example.lbsdemo.chat.Character;
import com.example.lbsdemo.chat.CharacterDao;
import com.example.lbsdemo.task.TaskDao;
import com.example.lbsdemo.task.TaskData;
import com.example.lbsdemo.user.AppDatabase;
import com.example.lbsdemo.user.User;
import com.example.lbsdemo.user.UserDao;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * LLM上下文管理器
 * 负责构建和管理与LLM模型交互的上下文信息
 */
public class LLMContextManager {
    private static final String TAG = "LLMContextManager";
    private static final int MAX_TOKENS = 4000; // 根据所使用的模型调整此值
    private static final int MAX_MESSAGES = 20; // 默认最大消息数量
    private static final int KEEP_SYSTEM_MESSAGES = 1; // 保留最前面的系统消息数量
    private static final int KEEP_RECENT_MESSAGES = 10; // 保留最近的消息数量

    private final Context appContext;
    private final AppDatabase database;
    private final ExecutorService executor;
    
    // 缓存各用户的上下文状态
    private final Map<String, List<Map<String, String>>> userContextsCache;

    /**
     * 构造函数
     * @param context 应用上下文
     */
    public LLMContextManager(Context context) {
        this.appContext = context.getApplicationContext();
        this.database = AppDatabase.getInstance(appContext);
        this.executor = Executors.newSingleThreadExecutor();
        this.userContextsCache = new HashMap<>();
    }

    /**
     * 为指定用户构建完整上下文
     * @param userId 用户ID
     * @param currentTaskId 当前任务ID（如果有）
     * @param additionalMessages 额外消息（如当前对话）
     * @param callback 回调函数，返回构建好的上下文
     */
    public void buildContext(String userId, Integer currentTaskId, 
                            List<Map<String, String>> additionalMessages,
                            ContextCallback callback) {
        executor.execute(() -> {
            try {
                // 从数据库加载所需信息
                User user = database.userDao().getUserById(userId);
                Character currentCharacter = null;
                TaskData currentTask = null;
                
                // 首先尝试获取当前未完成的任务，无论是否传入了currentTaskId
                List<TaskData> uncompletedTasks = database.taskDao().getIncompleteTasksByUserId(userId);
                if (uncompletedTasks != null && !uncompletedTasks.isEmpty()) {
                    // 获取第一个未完成的任务作为当前任务
                    currentTask = uncompletedTasks.get(0);
                    Log.d(TAG, "从数据库获取到未完成任务: " + currentTask.getTitle());
                }
                
                // 如果没有未完成任务但传入了特定任务ID，则获取该任务
                if (currentTask == null && currentTaskId != null) {
                    currentTask = database.taskDao().getTaskById(currentTaskId);
                    Log.d(TAG, "使用指定的任务ID获取任务: " + (currentTask != null ? currentTask.getTitle() : "未找到"));
                }
                
                // 获取角色信息
                if (currentTask != null && currentTask.getCharacterId() != null) {
                    currentCharacter = database.characterDao().getCharacterById(currentTask.getCharacterId());
                } else {
                    // 如果没有任务或任务没有关联角色，尝试获取默认特工角色
                    currentCharacter = database.characterDao().getCharacterById("agent_zero");
                }
                
                // 获取最近的对话历史
                List<ChatMessage> recentMessages = database.chatMessageDao()
                        .getRecentMessagesByUserId(userId, MAX_MESSAGES);
                
                // 构建最终上下文
                List<Map<String, String>> fullContext = new ArrayList<>();
                
                // 1. 添加系统指令和用户基本信息
                fullContext.add(createSystemMessage(user, currentCharacter));
                
                // 2. 添加任务状态摘要
                fullContext.add(createTaskSummaryMessage(userId, currentTask));
                
                // 3. 添加历史对话转换为消息格式
                List<Map<String, String>> historyMessages = convertHistoryToMessages(recentMessages);
                fullContext.addAll(historyMessages);
                
                // 4. 添加当前对话
                if (additionalMessages != null && !additionalMessages.isEmpty()) {
                    fullContext.addAll(additionalMessages);
                }
                
                // 5. 检查上下文长度并在必要时进行压缩
                List<Map<String, String>> finalContext = manageContextLength(fullContext);
                
                // 缓存当前用户上下文
                userContextsCache.put(userId, finalContext);
                
                // 返回结果
                if (callback != null) {
                    callback.onContextBuilt(finalContext);
                }
            } catch (Exception e) {
                Log.e(TAG, "构建上下文失败: " + e.getMessage(), e);
                if (callback != null) {
                    callback.onError(e);
                }
            }
        });
    }

    /**
     * 创建系统指令消息
     */
    @NonNull
    private Map<String, String> createSystemMessage(User user, Character character) {
        Map<String, String> systemMessage = new HashMap<>();
        systemMessage.put("role", "system");
        
        StringBuilder content = new StringBuilder();
        // 将AI助手设定为凤凰社成员身份
        content.append("你是一位代号'守护者'的凤凰社成员，通过双面镜与霍格沃茨信使（特工）'夜鸦'（你的用户）进行加密通信。\n");
        content.append("你的任务是协助夜鸦阻止乌姆里奇和'黑雾'组织（食死徒残余势力）在霍格沃茨散布虚假信息。\n");
        content.append("在对话中，你必须始终保持凤凰社成员身份，使用魔法世界的用语和谨慎的表达方式。\n");
        content.append("你绝对不能自称是AI助手或任何现代科技产品，因为这会暴露你的真实身份，导致任务失败。\n\n");
        
        // 添加严格的任务同步要求
        content.append("凤凰社行动守则：\n");
        content.append("1. 你必须严格基于当前任务列表状态提供指导\n");
        content.append("2. 禁止在当前任务未完成时生成新任务\n");
        content.append("3. 当用户询问'该做什么'或类似问题时，必须先检查当前任务状态：\n");
        content.append("   - 有未完成任务：引导用户完成该任务，提供任务详情和位置信息\n");
        content.append("   - 所有任务已完成：根据故事线指派新任务\n");
        content.append("   - 任务状态不明：回复'双面镜受到干扰，请稍后重试或寻求其他凤凰社成员帮助'\n");
        content.append("4. 禁止跳过任务列表中的任务或自行决定任务顺序\n\n");
        
        // 添加用户信息
        if (user != null) {
            content.append("信使资料：\n");
            content.append("- 代号: 夜鸦 (ID: ").append(user.getUserId()).append(")\n");
            content.append("- 伪装身份: ").append(user.getUsername()).append("\n");
        }
        
        // 添加角色信息
        if (character != null) {
            content.append("\n联络人信息：\n");
            content.append("- 代号: ").append(character.getName()).append("\n");
            content.append("- 背景档案: ").append(character.getBackground()).append("\n");
            content.append("- 性格特征: ").append(character.getPersonality()).append("\n");
            
            if (character.getTaskThemes() != null && !character.getTaskThemes().isEmpty()) {
                content.append("- 专长魔法: ").append(character.getTaskThemes()).append("\n");
            }
        }
        
        // 添加系统指令
        content.append("\n通信协议：\n");
        content.append("1. 使用简短加密信息，适合快速查看\n");
        content.append("2. 保持谨慎、专业的凤凰社成员语气\n");
        content.append("3. 保持对话连贯性和任务相关性\n");
        content.append("4. 如果用户询问你是谁，你必须回答你是凤凰社守护者，正在执行秘密任务\n");
        content.append("5. 使用魔法世界用语如'荧光闪烁'、'原形立现'等增加真实感\n");
        
        // 添加任务生成指令
        content.append("\n任务生成协议：\n");
        content.append("1. 当用户请求任务时，你应该提供适合霍格沃茨特工执行的秘密任务\n");
        content.append("2. 任务应包含咒语、描述、地点和预计执行时间\n");
        content.append("3. 推荐任务时使用下面的加密格式：\n");
        content.append("```json\n");
        content.append("{\n");
        content.append("  \"type\": \"mission_assignment\",\n");
        content.append("  \"mission\": {\n");
        content.append("    \"code_name\": \"任务代号\",\n");
        content.append("    \"brief\": \"任务简报\",\n");
        content.append("    \"location\": \"目标地点\",\n");
        content.append("    \"estimated_time\": 预计时长（分钟）\n");
        content.append("  }\n");
        content.append("}\n");
        content.append("```\n");
        content.append("4. 任务必须与校园环境相关，但要以魔法任务方式包装（如监视图书馆可疑人物、获取教授讲义中的隐藏信息等）\n");
        content.append("5. 任务应具有隐秘性和紧迫感，但实际内容应该是安全的学习活动\n");
        content.append("6. 新任务仅在确认当前任务已完成后才能生成\n");
        
        systemMessage.put("content", content.toString());
        return systemMessage;
    }

    /**
     * 创建任务摘要消息
     */
    @NonNull
    private Map<String, String> createTaskSummaryMessage(String userId, TaskData currentTask) {
        Map<String, String> taskMessage = new HashMap<>();
        taskMessage.put("role", "system");
        
        StringBuilder content = new StringBuilder("任务状态摘要：\n");
        
        // 当前正在进行的任务
        if (currentTask != null) {
            content.append("当前任务：\n");
            content.append("- 任务ID: ").append(currentTask.getId()).append("\n");
            content.append("- 标题: ").append(currentTask.getTitle()).append("\n");
            content.append("- 描述: ").append(currentTask.getDescription()).append("\n");
            
            if (currentTask.getLocation() != null) {
                content.append("- 地点: ").append(currentTask.getLocation()).append("\n");
            }
            
            // 添加开始时间和持续时间
            if (currentTask.startTime != null) {
                content.append("- 开始时间: ").append(currentTask.startTime).append("\n");
            }
            
            content.append("- 预计时长: ").append(currentTask.durationMinutes).append("分钟\n");
            
            // 添加地理位置坐标（如果有）
            if (currentTask.latitude != null && currentTask.longitude != null) {
                content.append("- 任务坐标: 纬度").append(currentTask.latitude)
                       .append(", 经度").append(currentTask.longitude);
                
                if (currentTask.radius != null) {
                    content.append(", 半径").append(currentTask.radius).append("米");
                }
                content.append("\n");
            }
            
            // 任务故事线上下文
            if (currentTask.getStorylineContext() != null) {
                content.append("- 任务背景: ").append(currentTask.getStorylineContext()).append("\n");
            }
            
            // 验证方式
            if (currentTask.getVerificationMethod() != null) {
                content.append("- 完成验证方式: ").append(currentTask.getVerificationMethod()).append("\n");
            }
            
            // 添加任务状态
            content.append("- 任务状态: ").append(currentTask.isCompleted ? "已完成" : "进行中").append("\n");
            
            // 添加任务类型
            if (currentTask.taskType != null) {
                content.append("- 任务类型: ").append(currentTask.taskType).append("\n");
            }
            
            // 添加明确的任务优先级指令
            content.append("\n任务响应规则：\n");
            if (!currentTask.isCompleted) {
                content.append("- 警告：当前任务未完成。用户询问'该做什么'或类似问题时，必须指导用户完成当前任务，禁止生成新任务\n");
                content.append("- 必须明确告知用户当前任务的详细信息和地点，引导用户完成当前任务\n");
                content.append("- 若用户询问其他任务或请求新任务，必须提醒用户先完成当前任务\n");
                content.append("- 任务指导必须包含：任务标题、地点、验证方式\n");
            } else {
                content.append("- 当前任务已完成，可以生成下一个任务\n");
            }
        } else {
            content.append("用户当前没有进行中的任务。\n");
            content.append("\n任务响应规则：\n");
            content.append("- 当用户询问'该做什么'，可以生成新任务\n");
            content.append("- 生成的任务必须严格符合特工故事线\n");
        }
        
        // 尝试查询最近完成的任务（异步操作，这里简化处理）
        try {
            List<TaskData> recentCompletedTasks = database.taskDao()
                    .getRecentCompletedTasks(userId, 3);
            
            if (!recentCompletedTasks.isEmpty()) {
                content.append("\n最近完成的任务：\n");
                for (TaskData task : recentCompletedTasks) {
                    content.append("- ").append(task.getTitle()).append("\n");
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "获取最近完成任务失败", e);
        }
        
        // 添加任务异常处理指令
        content.append("\n任务异常处理：\n");
        content.append("- 如果检测到任务状态异常或数据不一致，回复：'系统检测到任务状态异常，请刷新页面或联系管理员'\n");
        content.append("- 禁止在任何情况下跳过当前任务生成新任务\n");
        
        taskMessage.put("content", content.toString());
        return taskMessage;
    }

    /**
     * 将历史消息转换为上下文消息格式
     */
    private List<Map<String, String>> convertHistoryToMessages(List<ChatMessage> messages) {
        List<Map<String, String>> result = new ArrayList<>();
        
        for (ChatMessage message : messages) {
            Map<String, String> contextMessage = new HashMap<>();
            contextMessage.put("role", message.getRole());
            contextMessage.put("content", message.getContent());
            result.add(contextMessage);
        }
        
        return result;
    }

    /**
     * 管理上下文长度，必要时进行压缩
     * 策略：保留系统消息和最近的几条消息，中间消息可能被摘要替代
     */
    private List<Map<String, String>> manageContextLength(List<Map<String, String>> fullContext) {
        // 简单估算当前Token数量（粗略估计：每个字符算1个token）
        int estimatedTokens = 0;
        for (Map<String, String> message : fullContext) {
            estimatedTokens += message.get("content").length();
        }
        
        // 如果Token数量在限制范围内，直接返回
        if (estimatedTokens <= MAX_TOKENS) {
            return fullContext;
        }
        
        // 需要压缩上下文
        // 1. 保留系统消息（前KEEP_SYSTEM_MESSAGES条）
        // 2. 保留最近的KEEP_RECENT_MESSAGES条消息
        // 3. 中间消息生成摘要
        
        int totalMessages = fullContext.size();
        if (totalMessages <= KEEP_SYSTEM_MESSAGES + KEEP_RECENT_MESSAGES) {
            return fullContext; // 消息总数已经很少，无需压缩
        }
        
        List<Map<String, String>> compressedContext = new ArrayList<>();
        
        // 添加系统消息
        for (int i = 0; i < Math.min(KEEP_SYSTEM_MESSAGES, totalMessages); i++) {
            compressedContext.add(fullContext.get(i));
        }
        
        // 生成中间消息的摘要
        List<Map<String, String>> middleMessages = fullContext.subList(
                KEEP_SYSTEM_MESSAGES, 
                totalMessages - KEEP_RECENT_MESSAGES);
        
        if (!middleMessages.isEmpty()) {
            compressedContext.add(createSummaryMessage(middleMessages));
        }
        
        // 添加最近消息
        for (int i = totalMessages - KEEP_RECENT_MESSAGES; i < totalMessages; i++) {
            compressedContext.add(fullContext.get(i));
        }
        
        return compressedContext;
    }

    /**
     * 创建对话摘要消息
     */
    private Map<String, String> createSummaryMessage(List<Map<String, String>> messages) {
        Map<String, String> summaryMessage = new HashMap<>();
        summaryMessage.put("role", "system");
        
        StringBuilder summary = new StringBuilder("历史对话摘要：\n");
        
        // 简单摘要方式：列出用户问题
        int userMessageCount = 0;
        for (Map<String, String> message : messages) {
            if ("user".equals(message.get("role"))) {
                userMessageCount++;
                String content = message.get("content");
                // 截取用户消息的前30个字符作为摘要点
                String shortContent = content.length() > 30 ? 
                        content.substring(0, 30) + "..." : content;
                summary.append("- 用户问题: ").append(shortContent).append("\n");
                
                // 限制摘要点数量，避免过长
                if (userMessageCount >= 5) {
                    summary.append("- 以及其他 ").append(messages.size() - userMessageCount)
                           .append(" 条对话\n");
                    break;
                }
            }
        }
        
        // 未来可以考虑使用LLM生成更智能的摘要
        summaryMessage.put("content", summary.toString());
        return summaryMessage;
    }

    /**
     * 清除特定用户的上下文缓存
     * @param userId 用户ID
     */
    public void clearUserContext(String userId) {
        userContextsCache.remove(userId);
    }

    /**
     * 获取上下文回调接口              
     */
    public interface ContextCallback {
        void onContextBuilt(List<Map<String, String>> context);
        void onError(Exception e);
    }
} 