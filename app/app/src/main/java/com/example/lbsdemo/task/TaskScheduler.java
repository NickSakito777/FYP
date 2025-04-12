// TaskScheduler.java
package com.example.lbsdemo.task;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.location.Location;
import android.os.Build;
import android.util.Log;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import com.example.lbsdemo.llm.StorylineManager;
import com.example.lbsdemo.llm.TaskGenerator;
import com.example.lbsdemo.user.AppDatabase;
import com.example.lbsdemo.user.LocationHistoryData;

/**
 * 任务调度器类
 * 负责管理和调度不同类型的任务，提供任务推荐功能
 */
public class TaskScheduler {
    private static final String TAG = "TaskScheduler";
    private final Context context;
    private final AppDatabase database;
    private final TaskGenerator taskGenerator;
    private final StorylineManager storylineManager;

    // 任务推荐加权参数
    private static final double WEIGHT_DISTANCE = 0.4; // 距离权重
    private static final double WEIGHT_PRIORITY = 0.3; // 优先级权重
    private static final double WEIGHT_TIME = 0.2; // 时间匹配权重
    private static final double WEIGHT_STORY = 0.1; // 故事进展权重

    public TaskScheduler(Context context) {
        this.context = context;
        this.database = AppDatabase.getInstance(context);
        this.taskGenerator = new TaskGenerator(context);
        this.storylineManager = new StorylineManager(context);
    }

    /**
     * 生成或更新用户的主线任务
     *
     * @param userId 用户ID
     * @param characterId 角色ID
     * @return 生成的主线任务ID
     */
    public int initializeMainTask(String userId, String characterId) {
        // 先检查用户是否已有主线任务
        List<TaskData> mainTasks = database.taskDao().getTasksByTypeAndUserId(userId, "main");
        if (!mainTasks.isEmpty() && !mainTasks.get(0).isCompleted) {
            // 已有未完成的主线任务，直接返回
            return mainTasks.get(0).id;
        }

        // 生成新的主线任务
        int mainTaskId = taskGenerator.generateMainTask(userId, characterId);

        if (mainTaskId > 0) {
            // 主线任务生成成功，生成子任务
            taskGenerator.generateSubTasks(mainTaskId);
        }

        return mainTaskId;
    }

    /**
     * 生成每日任务
     *
     * @param userId 用户ID
     * @return 生成的每日任务ID列表
     */
    public List<Integer> generateDailyTasks(String userId) {
        // 获取用户所有活跃的子任务
        List<TaskData> subTasks = database.taskDao().getIncompleteTasksByTypeAndUserId(userId, "sub");
        if (subTasks.isEmpty()) {
            Log.e(TAG, "没有可用的子任务，无法生成每日任务");
            return Collections.emptyList();
        }

        // 提取子任务ID
        List<Integer> subTaskIds = new ArrayList<>();
        for (TaskData task : subTasks) {
            subTaskIds.add(task.id);
        }

        // 检查今天是否已生成过每日任务
        String today = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date());
        List<TaskData> todayTasks = database.taskDao().getTasksByDateAndType(userId, today, "daily");

        if (!todayTasks.isEmpty()) {
            // 今天已有任务，直接返回
            List<Integer> taskIds = new ArrayList<>();
            for (TaskData task : todayTasks) {
                taskIds.add(task.id);
            }
            return taskIds;
        }

        // 生成新的每日任务
        return taskGenerator.generateDailyTasks(userId, subTaskIds);
    }

    /**
     * 根据用户当前位置推荐任务
     *
     * @param userId 用户ID
     * @param currentLocation 当前位置
     * @param maxDistance 最大距离(米)
     * @param limit 返回任务数量限制
     * @return 推荐的任务列表
     */
    public List<TaskData> recommendTasksByLocation(String userId, Location currentLocation,
                                                  float maxDistance, int limit) {
        // 获取用户所有未完成的任务
        List<TaskData> allTasks = database.taskDao().getIncompleteTasksByUserId(userId);
        if (allTasks.isEmpty()) {
            return Collections.emptyList();
        }

        // 计算任务评分
        List<ScoredTask> scoredTasks = new ArrayList<>();
        for (TaskData task : allTasks) {
            // 只考虑有位置信息的任务
            if (task.latitude != null && task.longitude != null) {
                double score = calculateTaskScore(task, currentLocation);

                // 只添加在指定范围内的任务
                Location taskLocation = new Location("");
                taskLocation.setLatitude(task.latitude);
                taskLocation.setLongitude(task.longitude);
                float distance = currentLocation.distanceTo(taskLocation);

                if (distance <= maxDistance) {
                    scoredTasks.add(new ScoredTask(task, score, distance));
                }
            }
        }

        // 根据评分排序
        Collections.sort(scoredTasks, new Comparator<ScoredTask>() {
            @Override
            public int compare(ScoredTask t1, ScoredTask t2) {
                return Double.compare(t2.score, t1.score); // 降序排列
            }
        });

        // 提取排序后的任务
        List<TaskData> recommendedTasks = new ArrayList<>();
        int count = 0;
        for (ScoredTask scoredTask : scoredTasks) {
            if (count++ >= limit) break;
            recommendedTasks.add(scoredTask.task);
        }

        return recommendedTasks;
    }

    /**
     * 根据时间段推荐任务
     *
     * @param userId 用户ID
     * @param startHour 开始小时(0-23)
     * @param endHour 结束小时(0-23)
     * @param limit 返回任务数量限制
     * @return 推荐的任务列表
     */
    public List<TaskData> recommendTasksByTimeWindow(String userId, int startHour,
                                                   int endHour, int limit) {
        // 获取用户所有未完成的任务
        List<TaskData> allTasks = database.taskDao().getIncompleteTasksByUserId(userId);
        if (allTasks.isEmpty()) {
            return Collections.emptyList();
        }

        // 过滤适合时间段的任务
        List<TaskData> filteredTasks = new ArrayList<>();
        for (TaskData task : allTasks) {
            // 检查任务的开始时间是否匹配
            if (task.startTime != null && !task.startTime.isEmpty()) {
                try {
                    // 解析时间格式 "HH:mm-HH:mm"
                    String[] timeParts = task.startTime.split("-");
                    if (timeParts.length == 2) {
                        String taskStartTime = timeParts[0].trim();
                        int taskHour = Integer.parseInt(taskStartTime.split(":")[0]);

                        // 检查任务时间是否在指定范围内
                        if (taskHour >= startHour && taskHour <= endHour) {
                            filteredTasks.add(task);
                        }
                    }
                } catch (Exception e) {
                    // 时间格式解析失败，尝试另一种方式
                    if (task.startTime.contains("早上") || task.startTime.contains("上午")) {
                        if (startHour >= 6 && endHour <= 12) {
                            filteredTasks.add(task);
                        }
                    } else if (task.startTime.contains("下午")) {
                        if (startHour >= 12 && endHour <= 18) {
                            filteredTasks.add(task);
                        }
                    } else if (task.startTime.contains("晚上")) {
                        if (startHour >= 18 || endHour <= 6) {
                            filteredTasks.add(task);
                        }
                    } else {
                        // 无法解析时间，按优先级添加
                        filteredTasks.add(task);
                    }
                }
            } else {
                // 没有时间限制的任务，按优先级添加
                filteredTasks.add(task);
            }
        }

        // 根据优先级排序
        Collections.sort(filteredTasks, new Comparator<TaskData>() {
            @Override
            public int compare(TaskData t1, TaskData t2) {
                return Integer.compare(t2.priority, t1.priority); // 降序排列
            }
        });

        // 限制返回数量
        if (filteredTasks.size() > limit) {
            filteredTasks = filteredTasks.subList(0, limit);
        }

        return filteredTasks;
    }

    /**
     * 标记任务完成并更新故事进展
     *
     * @param taskId 任务ID
     * @return 是否成功更新
     */
    public boolean completeTask(int taskId) {
        try {
            // 获取任务
            TaskData task = database.taskDao().getTaskById(taskId);
            if (task == null) {
                Log.e(TAG, "未找到任务: " + taskId);
                return false;
            }

            // 标记任务为已完成
            task.isCompleted = true;
            database.taskDao().updateTask(task);

            // 更新故事进展
            boolean storyUpdated = storylineManager.updateStoryAfterTaskCompletion(taskId);

            // 如果是每日任务，检查关联的子任务是否可以完成
            if ("daily".equals(task.taskType) && task.parentTaskId != null) {
                checkAndCompleteSubTask(task.parentTaskId);
            }

            // 如果是子任务，检查关联的主线任务是否可以完成
            if ("sub".equals(task.taskType) && task.parentTaskId != null) {
                checkAndCompleteMainTask(task.parentTaskId);
            }

            return storyUpdated;

        } catch (Exception e) {
            Log.e(TAG, "完成任务时出错: " + e.getMessage(), e);
            return false;
        }
    }

    /**
     * 检查子任务是否可以完成
     */
    private void checkAndCompleteSubTask(int subTaskId) {
        try {
            // 获取与子任务关联的所有每日任务
            List<TaskData> dailyTasks = database.taskDao().getTasksByParentId(subTaskId);

            // 检查是否所有每日任务都已完成
            boolean allCompleted = true;
            for (TaskData task : dailyTasks) {
                if (!task.isCompleted) {
                    allCompleted = false;
                    break;
                }
            }

            if (allCompleted && !dailyTasks.isEmpty()) {
                // 完成子任务
                TaskData subTask = database.taskDao().getTaskById(subTaskId);
                if (subTask != null && !subTask.isCompleted) {
                    subTask.isCompleted = true;
                    database.taskDao().updateTask(subTask);

                    // 记录子任务完成
                    storylineManager.updateStoryAfterTaskCompletion(subTaskId);
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "检查子任务完成状态时出错: " + e.getMessage(), e);
        }
    }

    /**
     * 检查主线任务是否可以完成
     */
    private void checkAndCompleteMainTask(int mainTaskId) {
        try {
            // 获取与主线任务关联的所有子任务
            List<TaskData> subTasks = database.taskDao().getTasksByParentId(mainTaskId);

            // 检查是否所有子任务都已完成
            boolean allCompleted = true;
            for (TaskData task : subTasks) {
                if (!task.isCompleted) {
                    allCompleted = false;
                    break;
                }
            }

            if (allCompleted && !subTasks.isEmpty()) {
                // 完成主线任务
                TaskData mainTask = database.taskDao().getTaskById(mainTaskId);
                if (mainTask != null && !mainTask.isCompleted) {
                    mainTask.isCompleted = true;
                    database.taskDao().updateTask(mainTask);

                    // 记录主线任务完成
                    storylineManager.updateStoryAfterTaskCompletion(mainTaskId);
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "检查主线任务完成状态时出错: " + e.getMessage(), e);
        }
    }

    /**
     * 验证用户是否在任务指定的位置附近
     *
     * @param taskId 任务ID
     * @param userLocation 用户当前位置
     * @return 是否在任务位置范围内
     */
    public boolean validateTaskLocation(int taskId, Location userLocation) {
        try {
            TaskData task = database.taskDao().getTaskById(taskId);
            if (task == null || task.latitude == null || task.longitude == null) {
                return false;
            }

            // 创建任务位置对象
            Location taskLocation = new Location("");
            taskLocation.setLatitude(task.latitude);
            taskLocation.setLongitude(task.longitude);

            // 计算用户到任务位置的距离
            float distance = userLocation.distanceTo(taskLocation);

            // 检查是否在地理围栏范围内
            int radius = (task.radius != null) ? task.radius : 50; // 默认50米
            return distance <= radius;

        } catch (Exception e) {
            Log.e(TAG, "验证任务位置时出错: " + e.getMessage(), e);
            return false;
        }
    }

    /**
     * 开始记录用户在特定位置的停留时间
     *
     * @param userId 用户ID
     * @param taskId 任务ID
     * @param location 当前位置
     * @return 是否成功开始记录
     */
    public boolean startLocationStayTracking(String userId, int taskId, Location location) {
        try {
            TaskData task = database.taskDao().getTaskById(taskId);
            if (task == null) {
                Log.e(TAG, "无法找到任务: " + taskId);
                return false;
            }

            // 检查是否已经有活跃的停留记录
            LocationHistoryData activeSession = database.locationHistoryDao()
                    .getActiveLocationSession(userId, String.valueOf(taskId));

            if (activeSession != null) {
                Log.d(TAG, "已经存在活跃的停留记录，不需要创建新记录");
                return true; // 已经在记录中
            }

            // 创建新的停留记录
            LocationHistoryData historyData = new LocationHistoryData();
            historyData.userId = userId;
            historyData.buildingId = String.valueOf(taskId); // 使用任务ID作为buildingId
            historyData.locationName = task.location;
            historyData.latitude = location.getLatitude();
            historyData.longitude = location.getLongitude();
            historyData.startTime = System.currentTimeMillis();
            historyData.endTime = 0; // 0表示会话仍在进行中
            historyData.durationMinutes = 0;

            // 设置日期
            SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
            historyData.visitDate = dateFormat.format(new Date());

            // 保存到数据库
            long id = database.locationHistoryDao().insert(historyData);

            return id > 0;

        } catch (Exception e) {
            Log.e(TAG, "开始位置停留记录时出错: " + e.getMessage(), e);
            return false;
        }
    }

    /**
     * 结束用户在特定位置的停留时间记录
     *
     * @param userId 用户ID
     * @param taskId 任务ID
     * @return 停留时间（分钟），如果出错则返回-1
     */
    public int endLocationStayTracking(String userId, int taskId) {
        try {
            // 查找活跃的停留记录
            LocationHistoryData activeSession = database.locationHistoryDao()
                    .getActiveLocationSession(userId, String.valueOf(taskId));

            if (activeSession == null) {
                Log.e(TAG, "没有找到活跃的停留记录");
                return -1;
            }

            // 计算停留时间
            long endTime = System.currentTimeMillis();
            long durationMillis = endTime - activeSession.startTime;
            int durationMinutes = (int) (durationMillis / (60 * 1000));

            // 更新记录
            activeSession.endTime = endTime;
            activeSession.durationMinutes = durationMinutes;

            // 保存到数据库
            database.locationHistoryDao().update(activeSession);

            return durationMinutes;

        } catch (Exception e) {
            Log.e(TAG, "结束位置停留记录时出错: " + e.getMessage(), e);
            return -1;
        }
    }

    /**
     * 验证用户在特定位置的停留时间是否满足任务要求
     *
     * @param userId 用户ID
     * @param taskId 任务ID
     * @return 验证结果对象，包含是否验证通过和相关信息
     */
    public TimeVerificationResult validateStayDuration(String userId, int taskId) {
        TimeVerificationResult result = new TimeVerificationResult();

        try {
            TaskData task = database.taskDao().getTaskById(taskId);
            if (task == null) {
                result.isValid = false;
                result.message = "任务不存在";
                return result;
            }

            // 检查任务是否需要时长验证
            if (!"time".equals(task.verificationMethod)) {
                result.isValid = false;
                result.message = "此任务不需要时长验证";
                return result;
            }

            // 获取用户在此位置的所有停留记录
            List<LocationHistoryData> historyList = database.locationHistoryDao()
                    .getUserLocationHistory(userId);

            // 筛选与当前任务相关的记录
            int totalDuration = 0;
            for (LocationHistoryData history : historyList) {
                if (String.valueOf(taskId).equals(history.buildingId) && history.endTime > 0) {
                    totalDuration += history.durationMinutes;
                }
            }

            // 检查是否满足要求的停留时间
            int requiredDuration = task.durationMinutes;
            result.actualDuration = totalDuration;
            result.requiredDuration = requiredDuration;

            if (totalDuration >= requiredDuration) {
                result.isValid = true;
                result.message = "停留时间验证通过";

                // 如果验证通过，自动完成任务
                if (!task.isCompleted) {
                    task.isCompleted = true;
                    database.taskDao().updateTask(task);
                    result.taskCompleted = true;
                }
            } else {
                result.isValid = false;
                result.message = "停留时间不足，还需要停留 " + (requiredDuration - totalDuration) + " 分钟";
            }

            return result;

        } catch (Exception e) {
            Log.e(TAG, "验证停留时间时出错: " + e.getMessage(), e);
            result.isValid = false;
            result.message = "验证过程出错: " + e.getMessage();
            return result;
        }
    }

    /**
     * 获取用户在特定位置的当前停留时间
     *
     * @param userId 用户ID
     * @param taskId 任务ID
     * @return 当前停留时间（分钟），如果没有活跃会话则返回0
     */
    public int getCurrentStayDuration(String userId, int taskId) {
        try {
            // 查找活跃的停留记录
            LocationHistoryData activeSession = database.locationHistoryDao()
                    .getActiveLocationSession(userId, String.valueOf(taskId));

            if (activeSession == null) {
                return 0; // 没有活跃会话
            }

            // 计算当前停留时间
            long now = System.currentTimeMillis();
            long durationMillis = now - activeSession.startTime;
            return (int) (durationMillis / (60 * 1000));

        } catch (Exception e) {
            Log.e(TAG, "获取当前停留时间时出错: " + e.getMessage(), e);
            return 0;
        }
    }

    /**
     * 时长验证结果类
     */
    public static class TimeVerificationResult {
        public boolean isValid = false;
        public String message = "";
        public int actualDuration = 0;
        public int requiredDuration = 0;
        public boolean taskCompleted = false;
    }

    /**
     * 记录用户在特定位置的停留时间
     *
     * @param userId 用户ID
     * @param location 位置
     * @param taskId 相关任务ID(可选)
     */
    public void recordLocationStay(String userId, Location location, Integer taskId) {
        try {
            LocationHistoryData historyData = new LocationHistoryData();
            historyData.userId = userId;
            historyData.latitude = location.getLatitude();
            historyData.longitude = location.getLongitude();
            historyData.timestamp = System.currentTimeMillis();

            if (taskId != null) {
                // 如果与任务关联，获取任务名称作为位置名称
                TaskData task = database.taskDao().getTaskById(taskId);
                if (task != null) {
                    historyData.locationName = task.location;
                } else {
                    historyData.locationName = "未知位置";
                }
            } else {
                historyData.locationName = "未命名位置";
            }

            // 保存位置历史
            database.locationHistoryDao().insertLocationHistory(historyData);

        } catch (Exception e) {
            Log.e(TAG, "记录位置停留时出错: " + e.getMessage(), e);
        }
    }

    /**
     * 计算任务综合评分
     */
    private double calculateTaskScore(TaskData task, Location userLocation) {
        double distanceScore = 0;
        double priorityScore = 0;
        double timeScore = 0;
        double storyScore = 0;

        try {
            // 距离分数 - 距离越近分数越高
            if (task.latitude != null && task.longitude != null) {
                Location taskLocation = new Location("");
                taskLocation.setLatitude(task.latitude);
                taskLocation.setLongitude(task.longitude);

                float distance = userLocation.distanceTo(taskLocation);
                // 转换为0-1的分数，假设3000米是最大参考距离
                distanceScore = Math.max(0, 1 - (distance / 3000.0));
            }

            // 优先级分数 - 直接使用任务优先级(1-5)
            priorityScore = task.priority / 5.0;

            // 时间匹配分数
            Calendar now = Calendar.getInstance();
            int currentHour = now.get(Calendar.HOUR_OF_DAY);

            if (task.startTime != null && !task.startTime.isEmpty()) {
                try {
                    // 解析时间格式
                    String[] timeParts = task.startTime.split("-");
                    if (timeParts.length == 2) {
                        String taskStartTime = timeParts[0].trim();
                        int taskHour = Integer.parseInt(taskStartTime.split(":")[0]);

                        // 时间越接近，分数越高
                        int hourDiff = Math.abs(currentHour - taskHour);
                        timeScore = Math.max(0, 1 - (hourDiff / 12.0)); // 12小时作为最大差异
                    }
                } catch (Exception e) {
                    // 时间解析失败，采用默认分数
                    timeScore = 0.5;
                }
            } else {
                // 没有时间限制的任务，给予中等时间分数
                timeScore = 0.5;
            }

            // 故事进展分数
            if (task.taskType.equals("main")) {
                storyScore = 1.0; // 主线任务最高
            } else if (task.taskType.equals("sub")) {
                storyScore = 0.8; // 子任务次之
            } else {
                storyScore = 0.6; // 每日任务再次
            }

            // 计算加权综合分数
            return (WEIGHT_DISTANCE * distanceScore) +
                   (WEIGHT_PRIORITY * priorityScore) +
                   (WEIGHT_TIME * timeScore) +
                   (WEIGHT_STORY * storyScore);

        } catch (Exception e) {
            Log.e(TAG, "计算任务分数时出错: " + e.getMessage(), e);
            return 0;
        }
    }

    /**
     * 带评分的任务对象，用于排序
     */
    private static class ScoredTask {
        public TaskData task;
        public double score;
        public float distance;

        public ScoredTask(TaskData task, double score, float distance) {
            this.task = task;
            this.score = score;
            this.distance = distance;
        }
    }
}
