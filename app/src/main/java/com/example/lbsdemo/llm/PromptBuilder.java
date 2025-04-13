package com.example.lbsdemo.llm;

import android.content.Context;
import com.example.lbsdemo.chat.Character;
import com.example.lbsdemo.task.TaskData;
import com.example.lbsdemo.user.AppDatabase;
import com.example.lbsdemo.user.QuestionnaireData;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.util.List;
import java.util.Random;
import android.util.Log;

/**
 * 提示词构建工具类
 * 负责构建发送给LLM的各类任务提示词模板
 */
public class PromptBuilder {
    private final Context context;
    private static final String TAG = "TaskGenerator";
    public PromptBuilder(Context context) {
        this.context = context;
    }

    /**
     * 构建主线任务生成提示词
     *
     * @param character 虚拟角色信息
     * @param userData 用户问卷数据（兴趣爱好等）
     * @return 构建好的提示词
     */
    public String buildMainTaskPrompt(Character character, QuestionnaireData userData) {
        StringBuilder prompt = new StringBuilder();

        // 系统指令部分
        prompt.append("你是一位创意故事任务设计者。请为以下虚拟角色创建一个与大学校园相关的主线任务：\n\n");

        // 角色背景部分
        prompt.append("角色背景：").append(character.background).append("\n");
        prompt.append("角色性格：").append(character.personality).append("\n");

        // 用户信息部分
        prompt.append("用户偏好：\n");
        prompt.append("- 学习兴趣：").append(userData.studyInterests).append("\n");
        prompt.append("- 课外活动：").append(userData.extracurricularInterests).append("\n");
        prompt.append("- 常去校园地点：").append(userData.frequentPlaces).append("\n");

        // 任务要求
        prompt.append("\n创建一个有趣且持续性的主线任务，需满足：\n");
        prompt.append("1. 与角色背景相符\n");
        prompt.append("2. 有明确的总体目标\n");
        prompt.append("3. 可在大学校园内完成\n");
        prompt.append("4. 需要持续1-2周时间\n");
        prompt.append("5. 将学习目标巧妙融入任务中\n");

        // 输出格式
        prompt.append("\n输出格式：\n");
        prompt.append("{\n");
        prompt.append("  \"title\": \"任务标题\",\n");
        prompt.append("  \"description\": \"任务描述\",\n");
        prompt.append("  \"background_story\": \"背景故事\",\n");
        prompt.append("  \"final_goal\": \"最终目标\",\n");
        prompt.append("  \"estimated_days\": 数值,\n");
        prompt.append("  \"learning_elements\": [\"元素1\", \"元素2\"]\n");
        prompt.append("}\n");

        return prompt.toString();
    }

    /**
     * 构建子任务分解提示词
     *
     * @param mainTask 主线任务
     * @param userData 用户问卷数据
     * @return 构建好的提示词
     */
    public String buildSubTasksPrompt(TaskData mainTask, QuestionnaireData userData) {
        StringBuilder prompt = new StringBuilder();

        // 系统指令
        prompt.append("你是一位任务规划专家。请将以下主线任务分解为合理的子任务：\n\n");

        // 主线任务信息
        prompt.append("主线任务：\n");
        prompt.append("标题：").append(mainTask.title).append("\n");
        prompt.append("描述：").append(mainTask.description).append("\n");
        prompt.append("背景故事：").append(mainTask.storylineContext).append("\n");

        // 用户信息
        prompt.append("\n用户课表：").append(userData.schedule).append("\n");
        prompt.append("常去地点：").append(userData.frequentPlaces).append("\n");

        // 要求
        prompt.append("\n将主线任务分解为4个子任务，每个子任务需要：\n");
        prompt.append("1. 在不同的校园地点进行\n");
        prompt.append("2. 有明确的完成标准\n");
        prompt.append("3. 包含1-2个学习或生活元素\n");
        prompt.append("4. 符合故事逻辑顺序\n");

        // 输出格式
        prompt.append("\n输出格式：\n");
        prompt.append("[\n");
        prompt.append("  {\n");
        prompt.append("    \"sub_task_id\": 1,\n");
        prompt.append("    \"title\": \"子任务标题\",\n");
        prompt.append("    \"location\": \"具体校园地点\",\n");
        prompt.append("    \"description\": \"详细描述\",\n");
        prompt.append("    \"completion_criteria\": \"完成标准\",\n");
        prompt.append("    \"storyline_connection\": \"与主线故事的连接点\"\n");
        prompt.append("  },\n");
        prompt.append("  ...\n");
        prompt.append("]\n");

        return prompt.toString();
    }

    public String buildDailyTaskPrompt(String userId, List<TaskData> completedTasks,
                                      List<TaskData> activeTasks, List<String> availableLocations,
                                      List<String> availableTimeSlots) {
        StringBuilder prompt = new StringBuilder();

        // 系统指令
        prompt.append("你是一位日程安排助手。请根据以下信息为用户生成今日任务：\n\n");

        // 当前进度
        prompt.append("当前进度：\n");

        // 已完成的任务
        prompt.append("已完成的任务：\n");
        if (completedTasks.isEmpty()) {
            prompt.append("- 暂无已完成的任务\n");
        } else {
            for (TaskData task : completedTasks) {
                prompt.append("- ").append(task.title).append("\n");
            }
        }

        // 进行中的任务
        prompt.append("\n进行中的任务：\n");
        if (activeTasks.isEmpty()) {
            prompt.append("- 暂无进行中的任务\n");
        } else {
            for (TaskData task : activeTasks) {
                prompt.append("- ").append(task.title).append(" (").append(task.location).append(")\n");
            }
        }

        // 可用位置
        prompt.append("\n可用位置：\n");
        for (String location : availableLocations) {
            prompt.append("- ").append(location).append("\n");
        }

        // 可用时间
        prompt.append("\n可用时间：\n");
        for (String timeSlot : availableTimeSlots) {
            prompt.append("- ").append(timeSlot).append("\n");
        }

        // 任务要求
        prompt.append("\n生成2个适合今天完成的小任务，要求：\n");
        prompt.append("1. 每个任务耗时不超过30分钟\n");
        prompt.append("2. 与用户当天行程兼容\n");
        prompt.append("3. 推进整体故事进展\n");
        prompt.append("4. 明确指出具体地点和行动\n");
        prompt.append("5. 为每个任务选择合适的验证方式（地理围栏、拍照、时长验证）\n");

        // 验证方式说明
        prompt.append("\n验证方式说明：\n");
        prompt.append("- geofence: 用户需要到达指定位置触发地理围栏\n");
        prompt.append("- photo: 用户需要在指定位置拍照上传\n");
        prompt.append("- time: 用户需要在指定位置停留特定时间（适合学习、阅读等任务）\n");

        // 输出格式
        prompt.append("\n输出格式：\n");
        prompt.append("[\n");
        prompt.append("  {\n");
        prompt.append("    \"daily_task_id\": 1,\n");
        prompt.append("    \"parent_task_id\": 对应的子任务ID,\n");
        prompt.append("    \"title\": \"今日任务标题\",\n");
        prompt.append("    \"description\": \"详细任务描述\",\n");
        prompt.append("    \"location\": \"具体位置\",\n");
        prompt.append("    \"latitude\": 纬度坐标,\n");
        prompt.append("    \"longitude\": 经度坐标,\n");
        prompt.append("    \"radius\": 地理围栏半径(米),\n");
        prompt.append("    \"time_window\": \"建议时间段\",\n");
        prompt.append("    \"duration_minutes\": 分钟数,\n");
        prompt.append("    \"action_required\": \"需要的具体行动\",\n");
        prompt.append("    \"verification_method\": \"完成验证方式(geofence/photo/time)\"\n");
        prompt.append("  },\n");
        prompt.append("  ...\n");
        prompt.append("]\n");

        return prompt.toString();
    }

    /**
     * 构建任务验证提示词
     *
     * @param task 需要验证的任务
     * @param verificationData 验证数据（可能是照片描述、位置信息等）
     * @return 构建好的提示词
     */
    public String buildTaskVerificationPrompt(TaskData task, String verificationData) {
        StringBuilder prompt = new StringBuilder();

        // 系统指令
        prompt.append("你是一位任务验证专家。请评估以下验证数据是否满足任务完成条件：\n\n");

        // 任务信息
        prompt.append("任务信息：\n");
        prompt.append("标题：").append(task.title).append("\n");
        prompt.append("描述：").append(task.description).append("\n");
        prompt.append("验证方式：").append(task.verificationMethod).append("\n");

        // 根据验证方式添加特定信息
        if ("time".equals(task.verificationMethod)) {
            prompt.append("要求停留时间：").append(task.durationMinutes).append("分钟\n");
        }

        // 验证数据
        prompt.append("\n提交的验证数据：\n").append(verificationData).append("\n");

        // 要求
        prompt.append("\n请评估验证数据是否满足任务要求，并给出以下输出：\n");

        // 输出格式
        prompt.append("\n输出格式：\n");
        prompt.append("{\n");
        prompt.append("  \"is_valid\": true/false,\n");
        prompt.append("  \"confidence\": 0到100的数值,\n");
        prompt.append("  \"feedback\": \"对用户的反馈\",\n");
        prompt.append("  \"next_hint\": \"如果未完成，给出的下一步提示\"\n");
        prompt.append("}\n");

        return prompt.toString();
    }

    /**
     * 构建特工故事线任务提示词
     *
     * @param userId 用户ID
     * @param characterId 虚拟角色ID
     * @param stage 任务阶段 (1-3)
     * @return 构建好的提示词
     */
    public String buildAgentTaskPrompt(String userId, String characterId, int stage) {
        StringBuilder prompt = new StringBuilder();

        // 定义所有可能的教学楼代码
        String[] buildingCodes = {"SB", "CB", "SD", "FB", "SC", "SA", "PB", "MA", "MB", "EB", "EE"};

        // 随机选择一个教学楼代码
        Random random = new Random();
        String selectedBuilding = buildingCodes[random.nextInt(buildingCodes.length)];

        // 系统指令
        prompt.append("你是凤凰社的任务分配者，负责为霍格沃茨特工生成合适的任务。\n");
        prompt.append("请根据以下信息生成一个任务：\n\n");

        // 添加选定的教学楼信息
        prompt.append("指定教学楼：").append(selectedBuilding).append("\n\n");

        // 辅助理解任务的现实操作示例
        String realWorldExample = "";
        switch (stage) {
            case 1:
                // 阶段1：新手任务 - 初入魔法世界
                prompt.append("阶段1：新手任务 - D.A.初步训练\n\n");
                prompt.append("任务背景：\n");
                prompt.append("用户刚刚被凤凰社招募，需要完成第一个简单任务来证明自己的能力。\n");
                prompt.append("我们需要用户前往").append(selectedBuilding).append("，在任意楼层寻找一个贴有特殊标志（例如：带有羽毛图案的贴纸，或者特定颜色的标志）的公告栏或者海报。\n\n");

                prompt.append("任务要求：\n");
                prompt.append("1. 任务应该简单明确，适合新手\n");
                prompt.append("2. 任务地点必须是").append(selectedBuilding).append("教学楼内的某一层楼，请勿具体到教室\n");
                prompt.append("3. 优先生成关键行动任务类型\n");
                prompt.append("4. 任务完成后应该有明确的后续线索\n\n");
                realWorldExample = "例如，你需要在 " + selectedBuilding + " 的二楼寻找一个贴有凤凰图案的公告栏，并记下公告栏上的文字内容，作为后续任务的线索。";
                break;

            case 2:
                // 阶段2：进阶任务 - 对抗乌姆里奇
                prompt.append("阶段2：进阶任务 - 渗透调查\n\n");
                prompt.append("任务背景：\n");
                prompt.append("用户已经掌握了基本咒语，现在需要深入调查乌姆里奇的活动。\n");
                prompt.append("情报显示乌姆里奇隐藏了一些关键证据在").append(selectedBuilding).append("的某个楼层。我们需要你秘密潜入，寻找一份被伪装成普通书籍的报告。\n\n");

                prompt.append("任务要求：\n");
                prompt.append("1. 任务难度应该适中，比第一阶段更具挑战性\n");
                prompt.append("2. 任务地点必须是").append(selectedBuilding).append("教学楼内的某一层楼，请勿具体到教室\n");
                prompt.append("3. 可根据故事发展选择关键行动任务或支援任务类型\n");
                prompt.append("4. 任务应该与学术或研究相关\n\n");
                realWorldExample = "例如，你需要前往 " + selectedBuilding + " 的三楼，寻找一本封面是《高等变形术理论》但实际上内容是关于教育令修改草案的书籍。";
                break;

            case 3:
                // 阶段3：高级任务 - 破解黑魔法
                prompt.append("阶段3：高级任务 - 最终对抗\n\n");
                prompt.append("任务背景：\n");
                prompt.append("用户已经掌握了足够的魔法技能，现在需要阻止黑雾组织的阴谋。\n");
                prompt.append("情报显示黑雾组织计划在").append(selectedBuilding).append("的某个楼层进行某种秘密仪式。你需要找到他们藏匿仪式材料的地点，并拍照取证。\n\n");

                prompt.append("任务要求：\n");
                prompt.append("1. 任务应该充满挑战性和紧迫感\n");
                prompt.append("2. 任务地点必须是").append(selectedBuilding).append("内的某一楼层，请勿具体到教室\n");
                prompt.append("3. 应该优先考虑关键行动任务，但可以包含一个支援任务作为准备\n");
                prompt.append("4. 任务应该有明确的最终结果和反馈\n\n");
                realWorldExample = "例如，前往 " + selectedBuilding + " 的顶楼（或者指定的楼层），寻找一个被黑布遮盖的祭坛，祭坛上可能摆放着蜡烛、骨骸或者其他神秘物品。你需要拍摄祭坛的照片。";
                break;

            default:
                // 默认任务 - 日常魔法练习
                prompt.append("日常魔法练习\n\n");
                prompt.append("任务背景：\n");
                prompt.append("为了保持警觉并继续提升魔法技能，我们需要用户进行日常练习。\n");
                prompt.append("本次练习地点为").append(selectedBuilding).append("的某个楼层，练习寻找魔法能量波动。\n\n");

                prompt.append("任务要求：\n");
                prompt.append("1. 任务应该相对简单，可在30分钟内完成\n");
                prompt.append("2. 任务地点必须是").append(selectedBuilding).append("内的某一楼层，请勿具体到教室\n");
                prompt.append("3. 可以生成关键行动任务或支援任务，根据用户历史完成情况调整\n");
                prompt.append("4. 任务应该提供一些新的情报线索\n\n");
                realWorldExample = "例如，前往 " + selectedBuilding + " 的任意楼层，静下心来，尝试感知周围是否存在异常的能量波动。如果感觉到任何异样，记录下你的感受和位置。";
                break;
        }

        // 添加辅助理解的现实操作示例
        prompt.append("现实操作提示：").append(realWorldExample).append("\n\n");

        // 新增：任务类型与验证方式选择说明
        prompt.append("任务类型与验证方式选择说明：\n");
        prompt.append("请根据你生成的任务内容，在以下两种任务类型中选择最合适的一种填入 `agent_task_type` 字段：\n");
        prompt.append("1. `crucial_action`（关键行动任务）：通常涉及深入调查、解密、需要花费一定时间在特定地点完成的核心任务。这类任务的 `verification_method` 通常应选择 `time+geofence`。\n");
        prompt.append("2. `support`（支援任务）：通常涉及快速的资源收集、信息获取或简单的确认操作，地点要求相对宽松。这类任务的 `verification_method` 通常应选择 `photo`。\n");
        prompt.append("请自主判断任务性质，选择最恰当的 `agent_task_type` 和 `verification_method`。\n\n");

        // 输出格式
        prompt.append("输出格式（JSON）：\n");
        prompt.append("{\n");
        prompt.append("  \"title\": \"魔法任务标题\",\n");
        prompt.append("  \"description\": \"详细的任务描述\",\n");
        prompt.append("  \"location\": \"校园位置（必须包含教学楼代码和楼层信息，例如: MB三楼）\",\n");
        prompt.append("  \"agent_task_type\": \"根据任务内容选择 crucial_action 或 support\",\n");
        prompt.append("  \"verification_method\": \"根据任务内容和类型选择 time+geofence 或 photo\",\n");
        prompt.append("  \"photo_verification_prompt\": \"照片验证提示词（注意：仅当 verification_method 为 'photo' 时，此字段才是必须的，否则请省略此字段）\",\n");
        prompt.append("  \"duration_minutes\": 任务预计完成时间(分钟，仅当 verification_method 包含 'time' 时建议提供具体数值，否则可为0或省略）,\n");
        prompt.append("  \"storyline_context\": \"任务与整体故事线的关联\"\n");
        prompt.append("}\n");

        return prompt.toString();
    }
} 