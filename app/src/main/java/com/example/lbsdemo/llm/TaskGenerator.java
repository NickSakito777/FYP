package com.example.lbsdemo.llm;

import android.content.Context;
import android.util.Log;

import com.example.lbsdemo.chat.Character;
import com.example.lbsdemo.task.TaskData;
import com.example.lbsdemo.user.AppDatabase;
import com.example.lbsdemo.user.QuestionnaireData;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 任务生成器类
 * 负责解析LLM响应并生成不同层级的任务
 */
public class TaskGenerator {
    private static final String TAG = "TaskGenerator";
    
    private final Context context;
    private final LLMService llmService;
    private final PromptBuilder promptBuilder;
    private final AppDatabase database;
    
    public TaskGenerator(Context context) {
        this.context = context;
        this.llmService = new LLMService(context);
        this.promptBuilder = new PromptBuilder(context);
        this.database = AppDatabase.getInstance(context);
    }
    
    /**
     * 生成主线任务
     * 
     * @param userId 用户ID
     * @param characterId 虚拟角色ID
     * @return 生成的主线任务ID，失败返回-1
     */
    public int generateMainTask(String userId, String characterId) {
        try {
            // 获取角色信息
            Character character = database.characterDao().getCharacterById(characterId);
            if (character == null) {
                Log.e(TAG, "未找到角色信息: " + characterId);
                return -1;
            }
            
            // 获取用户问卷数据
            QuestionnaireData userData = database.questionnaireDao().getByUserId(userId);
            if (userData == null) {
                Log.e(TAG, "未找到用户问卷数据: " + userId);
                return -1;
            }
            
            // 构建提示词
            String prompt = promptBuilder.buildMainTaskPrompt(character, userData);
            
            // 请求LLM生成主线任务
            String llmResponse = llmService.sendRequest(prompt);
            
            // 解析响应并创建主线任务
            TaskData mainTask = parseMainTaskResponse(llmResponse, userId, characterId);
            if (mainTask == null) {
                Log.e(TAG, "解析主线任务响应失败");
                return -1;
            }
            
            // 保存主线任务到数据库
            long taskId = database.taskDao().insertTask(mainTask);
            return (int) taskId;
            
        } catch (Exception e) {
            Log.e(TAG, "生成主线任务时出错: " + e.getMessage(), e);
            return -1;
        }
    }
    
    /**
     * 为主线任务生成子任务
     * 
     * @param mainTaskId 主线任务ID
     * @return 生成的子任务ID列表，失败返回空列表
     */
    public List<Integer> generateSubTasks(int mainTaskId) {
        List<Integer> subTaskIds = new ArrayList<>();
        
        try {
            // 获取主线任务
            TaskData mainTask = database.taskDao().getTaskById(mainTaskId);
            if (mainTask == null) {
                Log.e(TAG, "未找到主线任务: " + mainTaskId);
                return subTaskIds;
            }
            
            // 获取用户问卷数据
            QuestionnaireData userData = database.questionnaireDao().getByUserId(mainTask.userId);
            if (userData == null) {
                Log.e(TAG, "未找到用户问卷数据: " + mainTask.userId);
                return subTaskIds;
            }
            
            // 构建提示词
            String prompt = promptBuilder.buildSubTasksPrompt(mainTask, userData);
            
            // 请求LLM生成子任务
            String llmResponse = llmService.sendRequest(prompt);
            
            // 解析响应并创建子任务
            List<TaskData> subTasks = parseSubTasksResponse(llmResponse, mainTask);
            if (subTasks.isEmpty()) {
                Log.e(TAG, "解析子任务响应失败");
                return subTaskIds;
            }
            
            // 保存子任务到数据库
            for (TaskData subTask : subTasks) {
                long subTaskId = database.taskDao().insertTask(subTask);
                subTaskIds.add((int) subTaskId);
            }
            
            return subTaskIds;
            
        } catch (Exception e) {
            Log.e(TAG, "生成子任务时出错: " + e.getMessage(), e);
            return subTaskIds;
        }
    }
    
    /**
     * 生成每日任务
     * 
     * @param userId 用户ID
     * @param activeSubTaskIds 活跃的子任务ID列表
     * @return 生成的每日任务ID列表，失败返回空列表
     */
    public List<Integer> generateDailyTasks(String userId, List<Integer> activeSubTaskIds) {
        List<Integer> dailyTaskIds = new ArrayList<>();
        
        try {
            // 获取活跃的子任务
            List<TaskData> subTasks = new ArrayList<>();
            for (int subTaskId : activeSubTaskIds) {
                TaskData task = database.taskDao().getTaskById(subTaskId);
                if (task != null && !task.isCompleted) {
                    subTasks.add(task);
                }
            }
            
            if (subTasks.isEmpty()) {
                Log.e(TAG, "没有活跃的子任务");
                return dailyTaskIds;
            }
            
            // 获取最近完成的任务（按完成时间排序）
            List<TaskData> completedTasks = database.taskDao().getRecentCompletedTasks(userId, 3);
            
            // 获取用户问卷数据
            QuestionnaireData userData = database.questionnaireDao().getByUserId(userId);
            if (userData == null) {
                Log.e(TAG, "未找到用户问卷数据: " + userId);
                return dailyTaskIds;
            }
            
            // 构建提示词
            // 准备可用位置列表
            List<String> availableLocations = new ArrayList<>();
            if (userData.frequentPlaces != null && !userData.frequentPlaces.isEmpty()) {
                String[] places = userData.frequentPlaces.split(",");
                for (String place : places) {
                    availableLocations.add(place.trim());
                }
            } else {
                availableLocations.add("图书馆");
                availableLocations.add("教学楼");
                availableLocations.add("食堂");
            }
            
            // 准备可用时间段列表
            List<String> availableTimeSlots = new ArrayList<>();
            if (userData.availableTime != null && !userData.availableTime.isEmpty()) {
                String[] times = userData.availableTime.split(",");
                for (String time : times) {
                    availableTimeSlots.add(time.trim());
                }
            } else {
                availableTimeSlots.add("上午9:00-11:00");
                availableTimeSlots.add("下午14:00-16:00");
                availableTimeSlots.add("晚上19:00-21:00");
            }
            
            String prompt = promptBuilder.buildDailyTaskPrompt(userId, completedTasks, subTasks, availableLocations, availableTimeSlots);
            
            // 请求LLM生成每日任务
            String llmResponse = llmService.sendRequest(prompt);
            
            // 解析响应并创建每日任务
            List<TaskData> dailyTasks = parseDailyTasksResponse(llmResponse, userId, subTasks);
            if (dailyTasks.isEmpty()) {
                Log.e(TAG, "解析每日任务响应失败");
                return dailyTaskIds;
            }
            
            // 保存每日任务到数据库
            for (TaskData dailyTask : dailyTasks) {
                long dailyTaskId = database.taskDao().insertTask(dailyTask);
                dailyTaskIds.add((int) dailyTaskId);
            }
            
            return dailyTaskIds;
            
        } catch (Exception e) {
            Log.e(TAG, "生成每日任务时出错: " + e.getMessage(), e);
            return dailyTaskIds;
        }
    }
    
    /**
     * 生成特工故事线任务
     * 
     * @param userId 用户ID
     * @param characterId 虚拟角色ID
     * @param stage 任务阶段 (1-3)
     * @return 生成的任务ID，失败返回-1
     */
    public int generateAgentTask(String userId, String characterId, int stage) {
        try {
            // 获取角色信息
            Character character = database.characterDao().getCharacterById(characterId);
            if (character == null) {
                Log.e(TAG, "未找到角色信息: " + characterId);
                return -1;
            }
            
            // 检查用户最近任务完成情况，决定任务类型
            boolean shouldGenerateSupportTask = false;
            
            // 获取用户已完成的特工任务
            List<TaskData> completedTasks = database.taskDao().getCompletedTasksByUserIdAndTaskType(userId, "main");
            
            // 检查最近48小时内是否有完成的任务
            long fortyEightHoursAgo = System.currentTimeMillis() - (48 * 60 * 60 * 1000);
            boolean hasRecentCompletedTask = false;
            
            for (TaskData task : completedTasks) {
                if (task.creationTimestamp > fortyEightHoursAgo) {
                    hasRecentCompletedTask = true;
                    break;
                }
            }
            
            // 如果48小时内没有完成任务，生成支援任务
            if (!hasRecentCompletedTask && !completedTasks.isEmpty()) {
                shouldGenerateSupportTask = true;
                Log.d(TAG, "用户48小时内未完成任务，生成支援任务");
            }
            
            // 获取所有任务并计算关键行动任务比例
            List<TaskData> allTasks = database.taskDao().getTasksByUserId(userId);
            int crucialActionCount = 0;
            int totalTaskCount = allTasks.size();
            
            for (TaskData task : allTasks) {
                if ("crucial_action".equals(task.agentTaskType)) {
                    crucialActionCount++;
                }
            }
            
            // 计算关键行动任务占比
            float crucialActionRatio = totalTaskCount > 0 ? (float) crucialActionCount / totalTaskCount : 0;
            
            // 如果关键行动任务占比已超过70%，生成支援任务
            if (crucialActionRatio > 0.7) {
                shouldGenerateSupportTask = true;
                Log.d(TAG, "关键行动任务占比已超过70%，生成支援任务");
            }
            
            // 构建提示词，可选择性传递任务类型提示
            String prompt;
            if (shouldGenerateSupportTask) {
                // 添加一个提示，建议生成支援任务
                prompt = promptBuilder.buildAgentTaskPrompt(userId, characterId, stage) + 
                         "\n注意：根据当前用户情况，强烈建议生成一个支援任务(support)类型。";
            } else {
                prompt = promptBuilder.buildAgentTaskPrompt(userId, characterId, stage);
            }
            
            // 请求LLM生成特工任务
            String llmResponse = llmService.sendRequest(prompt);
            
            // 解析响应并创建特工任务
            TaskData agentTask = parseAgentTaskResponse(llmResponse, userId, characterId, stage);
            if (agentTask == null) {
                Log.e(TAG, "解析特工任务响应失败");
                return -1;
            }
            
            // 保存特工任务到数据库
            long taskId = database.taskDao().insertTask(agentTask);
            
            // 记录剧情点
            StorylineManager storylineManager = new StorylineManager(context);
            String plotPoint = "开始阶段" + stage + "任务: " + agentTask.title;
            storylineManager.recordPlotPoint(userId, plotPoint, (int)taskId);
            
            return (int) taskId;
            
        } catch (Exception e) {
            Log.e(TAG, "生成特工任务时出错: " + e.getMessage(), e);
            return -1;
        }
    }
    
    /**
     * 解析主线任务响应
     */
    private TaskData parseMainTaskResponse(String llmResponse, String userId, String characterId) {
        try {
            // 提取JSON格式内容
            JSONObject taskObj = extractJsonObjectFromResponse(llmResponse);
            if (taskObj == null) {
                return null;
            }
            
            // 创建新的主线任务
            TaskData mainTask = new TaskData();
            mainTask.userId = userId;
            mainTask.title = taskObj.getString("title");
            mainTask.description = taskObj.getString("description");
            mainTask.taskType = "main"; // 标记为主线任务
            mainTask.characterId = characterId;
            mainTask.storylineContext = taskObj.getString("background_story");
            mainTask.isCompleted = false;
            mainTask.status = "accepted";
            
            // 设置创建时间
            SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
            mainTask.creationDate = dateFormat.format(new Date());
            mainTask.creationTimestamp = System.currentTimeMillis();
            
            // 设置任务持续时间（以分钟为单位，通常主线任务会设置较长时间）
            int estimatedDays = taskObj.getInt("estimated_days");
            mainTask.durationMinutes = estimatedDays * 24 * 60; // 转换为分钟
            
            return mainTask;
            
        } catch (JSONException e) {
            Log.e(TAG, "解析主线任务时出错: " + e.getMessage(), e);
            return null;
        }
    }
    
    /**
     * 解析子任务响应
     */
    private List<TaskData> parseSubTasksResponse(String llmResponse, TaskData mainTask) {
        List<TaskData> subTasks = new ArrayList<>();
        
        try {
            // 提取JSON数组内容
            JSONArray tasksArray = extractJsonArrayFromResponse(llmResponse);
            if (tasksArray == null) {
                return subTasks;
            }
            
            // 遍历任务数组创建子任务
            for (int i = 0; i < tasksArray.length(); i++) {
                JSONObject taskObj = tasksArray.getJSONObject(i);
                
                TaskData subTask = new TaskData();
                subTask.userId = mainTask.userId;
                subTask.title = taskObj.getString("title");
                subTask.description = taskObj.getString("description");
                subTask.location = taskObj.getString("location");
                subTask.taskType = "sub"; // 标记为子任务
                subTask.parentTaskId = mainTask.id; // 设置主线任务ID
                subTask.characterId = mainTask.characterId;
                subTask.storylineContext = taskObj.getString("storyline_connection");
                subTask.isCompleted = false;
                subTask.status = "pending";
                
                // 设置创建时间
                SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
                subTask.creationDate = dateFormat.format(new Date());
                subTask.creationTimestamp = System.currentTimeMillis();
                
                // 添加到列表
                subTasks.add(subTask);
            }
            
            return subTasks;
            
        } catch (JSONException e) {
            Log.e(TAG, "解析子任务时出错: " + e.getMessage(), e);
            return subTasks;
        }
    }
    
    /**
     * 解析每日任务响应
     */
    private List<TaskData> parseDailyTasksResponse(String llmResponse, String userId, List<TaskData> subTasks) {
        List<TaskData> dailyTasks = new ArrayList<>();
        
        try {
            // 提取JSON数组内容
            JSONArray tasksArray = extractJsonArrayFromResponse(llmResponse);
            if (tasksArray == null) {
                return dailyTasks;
            }
            
            // 遍历任务数组创建每日任务
            for (int i = 0; i < tasksArray.length(); i++) {
                JSONObject taskObj = tasksArray.getJSONObject(i);
                
                TaskData dailyTask = new TaskData();
                dailyTask.userId = userId;
                dailyTask.title = taskObj.getString("title");
                dailyTask.description = taskObj.optString("description", taskObj.optString("action_required", ""));
                dailyTask.location = taskObj.getString("location");
                dailyTask.taskType = "daily"; // 标记为每日任务
                
                // 设置父任务ID（子任务）
                int parentTaskId = taskObj.getInt("parent_task_id");
                dailyTask.parentTaskId = parentTaskId;
                
                // 从子任务中获取角色ID和故事线上下文
                for (TaskData subTask : subTasks) {
                    if (subTask.id == parentTaskId) {
                        dailyTask.characterId = subTask.characterId;
                        dailyTask.storylineContext = subTask.storylineContext;
                        break;
                    }
                }
                
                // 设置时间和持续时间
                dailyTask.startTime = taskObj.optString("time_window", "");
                dailyTask.durationMinutes = taskObj.getInt("duration_minutes");
                
                // 设置验证方法
                dailyTask.verificationMethod = taskObj.optString("verification_method", "geofence");
                
                // 如果验证方法是"time"，确保设置了足够的停留时间
                if ("time".equals(dailyTask.verificationMethod) && dailyTask.durationMinutes < 5) {
                    // 时长验证任务至少需要5分钟
                    dailyTask.durationMinutes = Math.max(5, dailyTask.durationMinutes);
                    Log.d(TAG, "时长验证任务时间过短，已调整为: " + dailyTask.durationMinutes + "分钟");
                }
                
                // 如果提供了位置坐标，则设置地理围栏信息
                if (taskObj.has("latitude") && taskObj.has("longitude")) {
                    dailyTask.latitude = taskObj.getDouble("latitude");
                    dailyTask.longitude = taskObj.getDouble("longitude");
                    dailyTask.radius = taskObj.optInt("radius", 50); // 默认50米半径
                }
                
                // 设置状态和完成标志
                dailyTask.isCompleted = false;
                dailyTask.status = "pending";
                
                // 设置创建时间
                SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
                dailyTask.creationDate = dateFormat.format(new Date());
                dailyTask.creationTimestamp = System.currentTimeMillis();
                
                // 设置优先级 (1-5)
                dailyTask.priority = i + 1;
                
                // 添加到列表
                dailyTasks.add(dailyTask);
            }
            
            return dailyTasks;
            
        } catch (JSONException e) {
            Log.e(TAG, "解析每日任务时出错: " + e.getMessage(), e);
            return dailyTasks;
        }
    }
    
    /**
     * 解析特工任务响应
     */
    private TaskData parseAgentTaskResponse(String llmResponse, String userId, String characterId, int stage) {
        try {
            // 提取JSON格式内容
            JSONObject taskObj = extractJsonObjectFromResponse(llmResponse);
            if (taskObj == null) {
                return null;
            }
            
            // 创建新的特工任务
            TaskData agentTask = new TaskData();
            agentTask.userId = userId;
            agentTask.title = taskObj.getString("title");
            agentTask.description = taskObj.getString("description");
            
            // 获取位置并检查是否包含教学楼代码
            String location = taskObj.getString("location");
            String[] buildingCodes = {"SB", "CB", "SD", "FB", "SC", "SA", "PB", "MA", "MB", "EB", "EE"};
            boolean containsCode = false;
            String detectedCode = null;
            
            // 检查是否包含任何教学楼代码
            for (String code : buildingCodes) {
                if (location.contains(code)) {
                    containsCode = true;
                    detectedCode = code;
                    Log.d(TAG, "检测到位置包含教学楼代码: " + code);
                    break;
                }
            }
            
            // 如果位置不包含任何教学楼代码，添加一个随机代码
            if (!containsCode) {
                // 使用更好的随机数生成方式
                // 使用纳秒级时间戳+任务标题哈希值作为随机种子，确保更好的随机性
                long seed = System.nanoTime() + taskObj.getString("title").hashCode();
                Random random = new Random(seed);
                
                // 打乱建筑代码数组顺序
                List<String> buildingCodeList = new ArrayList<>(Arrays.asList(buildingCodes));
                Collections.shuffle(buildingCodeList, random);
                
                // 选择第一个元素（已随机打乱）
                String randomCode = buildingCodeList.get(0);
                
                // 修改格式，使教学楼代码更明显
                location = randomCode + "楼: " + location;
                Log.d(TAG, "位置不包含教学楼代码，已添加随机代码: " + randomCode);
            } else if (detectedCode != null) {
                // 即使已经包含代码，也调整格式使其更明显，避免重复
                // 首先检查是否已经是"XX楼:"格式
                String pattern = detectedCode + "楼:";
                if (!location.contains(pattern)) {
                    // 移除可能的旧代码，避免重复
                    String cleanLocation = location.replace("(" + detectedCode + "楼)", "").trim();
                    cleanLocation = cleanLocation.replace(detectedCode + "楼", "").trim();
                    // 重新设置为统一格式
                    location = detectedCode + "楼: " + cleanLocation;
                    Log.d(TAG, "优化了位置格式，突出教学楼代码: " + detectedCode);
                }
            }
            
            // 添加随机的具体位置信息，使任务更加多样化
            String[] locationDetails = {
                "教室", "走廊", "实验室", "自习室", "会议室", "演讲厅", 
                "一楼大厅", "二楼电梯旁", "三楼休息区", "四楼研讨室"
            };
            
            // 使用更好的随机数生成，避免连续调用时生成相同的随机位置
            long detailSeed = System.nanoTime() + location.hashCode();
            Random detailRandom = new Random(detailSeed);
            String randomDetail = locationDetails[detailRandom.nextInt(locationDetails.length)];
            
            // 检查是否已经包含了详细位置信息，避免重复
            if (!location.contains(randomDetail)) {
                location += " " + randomDetail;
            }
            
            agentTask.location = location;
            
            // 获取和设置任务类型，默认为关键行动任务
            String taskType = taskObj.optString("agent_task_type", "crucial_action");
            agentTask.agentTaskType = taskType;

            // 根据任务类型设置不同的验证方式
            if ("crucial_action".equals(taskType)) {
                // 关键行动任务: 设置为time验证 + geofence验证
                agentTask.verificationMethod = "time+geofence"; // 组合验证
                agentTask.positionID = 1; // 必须设置地理位置验证
            } else if ("support".equals(taskType)) {
                // 支援任务: 设置为photo验证，无地理位置要求
                agentTask.verificationMethod = "photo"; // 仅拍照验证
                agentTask.positionID = 0; // 无需地理位置验证
            } else {
                // 如果没有指定或无效类型，使用LLM返回的验证方法
                agentTask.verificationMethod = taskObj.getString("verification_method");
                agentTask.positionID = 1; // 默认需要位置验证
            }

            agentTask.photoVerificationPrompt = taskObj.getString("photo_verification_prompt");
            agentTask.durationMinutes = taskObj.getInt("duration_minutes");
            agentTask.storylineContext = taskObj.getString("storyline_context");
            
            // 设置任务类型和状态
            agentTask.taskType = "main"; // 主线任务
            agentTask.characterId = characterId;
            agentTask.isCompleted = false;
            agentTask.status = "accepted";
            
            // 设置创建时间
            SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
            agentTask.creationDate = dateFormat.format(new Date());
            agentTask.creationTimestamp = System.currentTimeMillis();
            
            // 设置任务优先级
            agentTask.priority = 5; // 最高优先级
            
            return agentTask;
            
        } catch (Exception e) {
            Log.e(TAG, "解析特工任务响应失败: " + e.getMessage(), e);
            return null;
        }
    }
    
    /**
     * 从LLM响应中提取JSON对象
     */
    private JSONObject extractJsonObjectFromResponse(String response) {
        try {
            // 使用正则表达式提取JSON对象
            Pattern pattern = Pattern.compile("\\{.*\\}", Pattern.DOTALL);
            Matcher matcher = pattern.matcher(response);
            
            if (matcher.find()) {
                String jsonString = matcher.group(0);
                return new JSONObject(jsonString);
            }
            
            // 如果没有找到匹配项，尝试解析整个响应
            return new JSONObject(response);
        } catch (JSONException e) {
            Log.e(TAG, "从响应中提取JSON对象时出错: " + e.getMessage(), e);
            return null;
        }
    }
    
    /**
     * 从LLM响应中提取JSON数组
     */
    private JSONArray extractJsonArrayFromResponse(String response) {
        try {
            // 使用正则表达式提取JSON数组
            Pattern pattern = Pattern.compile("\\[.*\\]", Pattern.DOTALL);
            Matcher matcher = pattern.matcher(response);
            
            if (matcher.find()) {
                String jsonString = matcher.group(0);
                return new JSONArray(jsonString);
            }
            
            // 如果没有找到匹配项，尝试解析整个响应
            return new JSONArray(response);
        } catch (JSONException e) {
            Log.e(TAG, "从响应中提取JSON数组时出错: " + e.getMessage(), e);
            return null;
        }
    }
    
    /**
     * 验证任务有效性
     * 
     * @param task 要验证的任务
     * @return 验证结果，包含是否有效和问题描述
     */
    public TaskValidationResult validateTask(TaskData task) {
        TaskValidationResult result = new TaskValidationResult();
        
        // 验证基本字段
        if (task.title == null || task.title.isEmpty()) {
            result.isValid = false;
            result.issues.add("任务标题不能为空");
        }
        
        if (task.description == null || task.description.isEmpty()) {
            result.isValid = false;
            result.issues.add("任务描述不能为空");
        }
        
        // 验证任务类型
        if (task.taskType == null || 
            (!task.taskType.equals("main") && !task.taskType.equals("sub") && !task.taskType.equals("daily"))) {
            result.isValid = false;
            result.issues.add("任务类型无效，必须是 main、sub 或 daily");
        }
        
        // 验证父任务关系
        if ((task.taskType.equals("sub") || task.taskType.equals("daily")) && task.parentTaskId == null) {
            result.isValid = false;
            result.issues.add("子任务或每日任务必须有父任务ID");
        }
        
        // 验证地理信息（对于需要地理围栏验证的任务）
        if (task.verificationMethod != null && 
            (task.verificationMethod.equals("geofence") || task.verificationMethod.equals("time"))) {
            if (task.latitude == null || task.longitude == null) {
                result.isValid = false;
                result.issues.add("地理围栏或时长验证任务必须有经纬度坐标");
            }
            
            if (task.radius == null || task.radius <= 0) {
                result.isValid = false;
                result.issues.add("地理围栏或时长验证任务的半径必须大于0");
            }
        }
        
        // 验证时长任务的持续时间
        if (task.verificationMethod != null && task.verificationMethod.equals("time")) {
            if (task.durationMinutes <= 0) {
                result.isValid = false;
                result.issues.add("时长验证任务必须指定有效的持续时间");
            } else if (task.durationMinutes < 5) {
                result.isValid = false;
                result.issues.add("时长验证任务的持续时间不应少于5分钟");
            }
        }
        
        return result;
    }
    
    /**
     * 任务验证结果类
     */
    public static class TaskValidationResult {
        public boolean isValid = true;
        public List<String> issues = new ArrayList<>();
    }
} 