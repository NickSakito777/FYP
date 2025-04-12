package com.example.lbsdemo.llm;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import com.example.lbsdemo.chat.Character;
import com.example.lbsdemo.chat.ChatMessage;
import com.example.lbsdemo.task.TaskData;
import com.example.lbsdemo.user.AppDatabase;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * 故事线管理器
 * 负责记录和管理用户故事线进展
 */
public class StorylineManager {
    private static final String TAG = "StorylineManager";
    private static final String PREF_NAME = "storyline_prefs";
    private static final String KEY_STORY_PROGRESS = "story_progress_";
    private static final String KEY_PLOT_POINTS = "plot_points_";
    private static final String KEY_LAST_SUMMARY = "last_summary_";
    
    private final Context context;
    private final SharedPreferences preferences;
    private final AppDatabase database;
    private final LLMService llmService;
    
    public StorylineManager(Context context) {
        this.context = context;
        this.preferences = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        this.database = AppDatabase.getInstance(context);
        this.llmService = new LLMService(context);
    }
    
    /**
     * 获取用户当前的故事进度
     * 
     * @param userId 用户ID
     * @return 故事进度百分比(0-100)
     */
    public int getStoryProgress(String userId) {
        return preferences.getInt(KEY_STORY_PROGRESS + userId, 0);
    }
    
    /**
     * 更新用户故事进度
     * 
     * @param userId 用户ID
     * @param progress 新的进度值(0-100)
     */
    public void updateStoryProgress(String userId, int progress) {
        preferences.edit().putInt(KEY_STORY_PROGRESS + userId, progress).apply();
    }
    
    /**
     * 记录剧情点
     * 
     * @param userId 用户ID
     * @param plotPoint 剧情点描述
     * @param taskId 关联的任务ID
     */
    public void recordPlotPoint(String userId, String plotPoint, int taskId) {
        try {
            // 获取用户现有剧情点
            String plotPointsJson = preferences.getString(userId + "_plot_points", "[]");
            JSONArray plotPoints = new JSONArray(plotPointsJson);
            
            // 创建新剧情点
            JSONObject newPoint = new JSONObject();
            newPoint.put("description", plotPoint);
            newPoint.put("timestamp", System.currentTimeMillis());
            newPoint.put("task_id", taskId);
            
            // 添加到数组
            plotPoints.put(newPoint);
            
            // 保存更新后的剧情点
            preferences.edit()
                .putString(userId + "_plot_points", plotPoints.toString())
                .apply();
            
            Log.d(TAG, "已记录剧情点: " + plotPoint);
            
        } catch (JSONException e) {
            Log.e(TAG, "记录剧情点时出错: " + e.getMessage(), e);
        }
    }
    
    /**
     * 获取用户剧情点
     * 
     * @param userId 用户ID
     * @return 剧情点列表
     */
    public List<String> getPlotPoints(String userId) {
        List<String> result = new ArrayList<>();
        
        try {
            // 获取用户现有剧情点
            String plotPointsJson = preferences.getString(userId + "_plot_points", "[]");
            JSONArray plotPoints = new JSONArray(plotPointsJson);
            
            // 提取剧情点描述
            for (int i = 0; i < plotPoints.length(); i++) {
                JSONObject point = plotPoints.getJSONObject(i);
                result.add(point.getString("description"));
            }
            
        } catch (JSONException e) {
            Log.e(TAG, "获取剧情点时出错: " + e.getMessage(), e);
        }
        
        return result;
    }
    
    /**
     * 获取用户当前故事阶段
     * 
     * @param userId 用户ID
     * @return 当前故事阶段 (1-3)，默认为1
     */
    public int getCurrentStage(String userId) {
        // 查询用户最新的特工任务
        TaskData latestTask = database.taskDao().getLatestTaskByUserIdAndCharacterId(userId, "agent_zero");
        
        if (latestTask != null && latestTask.description != null) {
            // 根据任务描述判断阶段
            if (latestTask.description.contains("阶段3")) {
                return 3;
            } else if (latestTask.description.contains("阶段2")) {
                return 2;
            }
        }
        
        // 默认返回第一阶段
        return 1;
    }
    
    /**
     * 生成故事摘要
     * 
     * @param userId 用户ID
     * @return 故事摘要文本
     */
    public String generateStorySummary(String userId) {
        List<String> plotPoints = getPlotPoints(userId);
        
        if (plotPoints.isEmpty()) {
            return "故事尚未开始...";
        }
        
        StringBuilder summary = new StringBuilder();
        summary.append("故事摘要:\n\n");
        
        // 添加最多5个最新的剧情点
        int startIndex = Math.max(0, plotPoints.size() - 5);
        for (int i = startIndex; i < plotPoints.size(); i++) {
            summary.append("- ").append(plotPoints.get(i)).append("\n");
        }
        
        return summary.toString();
    }
    
    /**
     * 预测故事下一步发展
     * 
     * @param userId 用户ID
     * @param characterId 角色ID
     * @return 下一步发展预测
     */
    public String predictNextDevelopment(String userId, String characterId) {
        try {
            // 获取最新的故事摘要
            String summary = preferences.getString(KEY_LAST_SUMMARY + userId, "");
            if (summary.isEmpty()) {
                summary = generateStorySummary(userId);
            }
            
            // 获取剧情点
            List<String> plotPoints = getPlotPoints(userId);
            
            // 构建预测提示词
            StringBuilder prompt = new StringBuilder();
            prompt.append("你是一位出色的剧情发展预测专家。基于以下故事摘要和关键剧情点，预测故事可能的下一步发展：\n\n");
            
            // 故事摘要
            prompt.append("故事摘要：\n").append(summary).append("\n\n");
            
            // 关键剧情点
            prompt.append("关键剧情点：\n");
            int pointCount = 0;
            for (String point : plotPoints) {
                if (pointCount++ >= 5) break; // 最多包含5个最新的剧情点
                prompt.append("- ").append(point).append("\n");
            }
            
            // 要求
            prompt.append("\n请预测2-3个可能的故事发展方向，每个30-50字，考虑：\n");
            prompt.append("1. 故事的内在逻辑\n");
            prompt.append("2. 角色的动机和目标\n");
            prompt.append("3. 尚未解决的冲突\n");
            
            // 请求LLM预测
            return llmService.sendRequest(prompt.toString());
            
        } catch (Exception e) {
            Log.e(TAG, "预测故事发展时出错: " + e.getMessage(), e);
            return "";
        }
    }
    
    /**
     * 任务完成后更新故事进展
     * 
     * @param taskId 完成的任务ID
     * @return 是否成功更新
     */
    public boolean updateStoryAfterTaskCompletion(int taskId) {
        try {
            // 获取任务信息
            TaskData task = database.taskDao().getTaskById(taskId);
            if (task == null) {
                Log.e(TAG, "未找到任务: " + taskId);
                return false;
            }
            
            // 获取用户ID和角色ID
            String userId = task.userId;
            String characterId = task.characterId;
            
            // 根据任务类型进行不同的处理
            if ("main".equals(task.taskType)) {
                // 主线任务完成，故事完成
                updateStoryProgress(userId, 100);
                recordPlotPoint(userId, "完成了主线任务: " + task.title, taskId);
            } else if ("sub".equals(task.taskType)) {
                // 子任务完成，记录剧情点并更新进度
                recordPlotPoint(userId, "完成了重要子任务: " + task.title, taskId);
                
                // 查询所有子任务
                List<TaskData> allSubTasks = database.taskDao().getSubTasksByMainTaskId(task.parentTaskId);
                int completedCount = 0;
                for (TaskData subTask : allSubTasks) {
                    if (subTask.isCompleted) {
                        completedCount++;
                    }
                }
                
                // 计算进度百分比
                int progress = (completedCount * 100) / allSubTasks.size();
                updateStoryProgress(userId, progress);
                
            } else if ("daily".equals(task.taskType)) {
                // 每日任务完成，可能记录剧情点
                recordPlotPoint(userId, "完成了任务: " + task.title, taskId);
            }
            
            // 生成新的故事摘要
            generateStorySummary(userId);
            
            return true;
            
        } catch (Exception e) {
            Log.e(TAG, "更新故事进展时出错: " + e.getMessage(), e);
            return false;
        }
    }
} 