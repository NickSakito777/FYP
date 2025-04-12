package com.example.lbsdemo.activity;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;

import com.example.lbsdemo.R;
import com.example.lbsdemo.chat.Character;
import com.example.lbsdemo.llm.TaskGenerator;
import com.example.lbsdemo.task.TaskData;
import com.example.lbsdemo.task.TaskVerificationData;
import com.example.lbsdemo.task.TaskVerificationManager;
import com.example.lbsdemo.user.AppDatabase;
import com.example.lbsdemo.user.UserViewModel;

/**
 * 特工任务活动
 * 用于展示和管理特工故事线任务
 */
public class AgentTaskActivity extends AppCompatActivity {
    private static final String TAG = "AgentTaskActivity";
    public static final int REQUEST_TASK_TIMER = 1001; // 修改为public，使其可以被其他类访问
    
    private UserViewModel userViewModel;
    private AppDatabase database;
    private TaskGenerator taskGenerator;
    private TaskVerificationManager verificationManager;
    
    private TextView tvTaskTitle;
    private TextView tvTaskDescription;
    private TextView tvTaskLocation;
    private Button btnStartTask;
    private Button btnNextStage;
    
    private String userId;
    private String characterId = "agent_zero";
    private int currentStage = 1; // 默认从第一阶段开始
    private int currentTaskId = -1;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_agent_task);
        
        // 初始化视图模型和数据库
        userViewModel = new ViewModelProvider(this).get(UserViewModel.class);
        database = AppDatabase.getInstance(this);
        taskGenerator = new TaskGenerator(this);
        verificationManager = TaskVerificationManager.getInstance(this);
        
        // 初始化UI组件
        tvTaskTitle = findViewById(R.id.tv_task_title);
        tvTaskDescription = findViewById(R.id.tv_task_description);
        tvTaskLocation = findViewById(R.id.tv_task_location);
        btnStartTask = findViewById(R.id.btn_start_task);
        btnNextStage = findViewById(R.id.btn_next_stage);
        
        // 获取当前用户ID
        userId = userViewModel.getCurrentUserId();
        if (userId == null || userId.isEmpty()) {
            Toast.makeText(this, "用户未登录，请先登录", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
        
        // 检查特工角色是否存在，不存在则创建
        checkAndCreateAgentCharacter();
        
        // 加载当前任务
        loadCurrentTask();
        
        // 设置按钮点击事件
        btnStartTask.setOnClickListener(v -> startTask());
        btnNextStage.setOnClickListener(v -> moveToNextStage());
    }
    
    /**
     * 检查并创建特工角色
     */
    private void checkAndCreateAgentCharacter() {
        AppDatabase.databaseWriteExecutor.execute(() -> {
            Character agentZero = database.characterDao().getCharacterById(characterId);
            if (agentZero == null) {
                // 创建特工角色
                agentZero = Character.createAgentZero();
                database.characterDao().insertCharacter(agentZero);
                Log.d(TAG, "特工角色Zero已创建");
            }
        });
    }
    
    /**
     * 加载当前任务
     */
    private void loadCurrentTask() {
        AppDatabase.databaseWriteExecutor.execute(() -> {
            // 查询当前用户的特工任务
            TaskData task = database.taskDao().getLatestTaskByUserIdAndCharacterId(userId, characterId);
            
            runOnUiThread(() -> {
                if (task != null) {
                    // 更新UI显示任务信息
                    currentTaskId = task.id;
                    tvTaskTitle.setText(task.title);
                    tvTaskDescription.setText(task.description);
                    tvTaskLocation.setText("地点: " + task.location);
                    
                    // 根据任务状态更新按钮
                    if (task.isCompleted) {
                        btnStartTask.setVisibility(View.GONE);
                        btnNextStage.setVisibility(View.VISIBLE);
                    } else {
                        btnStartTask.setVisibility(View.VISIBLE);
                        btnNextStage.setVisibility(View.GONE);
                    }
                } else {
                    // 没有任务，生成第一个任务
                    generateNewTask();
                }
            });
        });
    }
    
    /**
     * 生成新任务
     */
    private void generateNewTask() {
        // 显示加载提示
        Toast.makeText(this, "正在生成任务...", Toast.LENGTH_SHORT).show();
        
        // 在后台线程生成任务
        new Thread(() -> {
            int taskId = taskGenerator.generateAgentTask(userId, characterId, currentStage);
            
            runOnUiThread(() -> {
                if (taskId != -1) {
                    // 任务生成成功，加载任务
                    currentTaskId = taskId;
                    loadCurrentTask();
                } else {
                    // 任务生成失败
                    Toast.makeText(this, "任务生成失败，请重试", Toast.LENGTH_SHORT).show();
                }
            });
        }).start();
    }
    
    /**
     * 开始任务
     */
    private void startTask() {
        if (currentTaskId == -1) {
            Toast.makeText(this, "没有可用任务", Toast.LENGTH_SHORT).show();
            return;
        }
        
        // 获取任务详情
        AppDatabase.databaseWriteExecutor.execute(() -> {
            TaskData task = database.taskDao().getTaskById(currentTaskId);
            
            if (task != null) {
                runOnUiThread(() -> {
                    // 显示任务详情对话框
                    showTaskDetailDialog(task);
                });
            } else {
                runOnUiThread(() -> {
                    Toast.makeText(this, "无法加载任务详情", Toast.LENGTH_SHORT).show();
                });
            }
        });
    }
    
    /**
     * 显示任务详情对话框
     */
    private void showTaskDetailDialog(TaskData task) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(task.title);
        
        // 创建对话框视图
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_task_detail, null);
        TextView tvDescription = dialogView.findViewById(R.id.tv_task_description);
        TextView tvLocation = dialogView.findViewById(R.id.tv_task_location);
        TextView tvVerificationMethod = dialogView.findViewById(R.id.tv_verification_method);
        Button btnVerify = dialogView.findViewById(R.id.btn_verify_task);
        
        // 设置任务详情
        tvDescription.setText(task.description);
        tvLocation.setText("地点: " + task.location);
        
        String verificationText = "验证方式: ";
        if ("photo".equals(task.verificationMethod)) {
            verificationText += "拍照验证";
        } else if ("geofence".equals(task.verificationMethod)) {
            verificationText += "位置验证";
        } else if ("time".equals(task.verificationMethod)) {
            verificationText += "时长验证 (" + task.durationMinutes + "分钟)";
        } else if ("time+geofence".equals(task.verificationMethod)) {
            verificationText += "时长+地理围栏验证";
        } else {
            verificationText += task.verificationMethod;
        }
        tvVerificationMethod.setText(verificationText);
        
        builder.setView(dialogView);
        
        // 添加关闭按钮
        builder.setNegativeButton("关闭", (dialog, which) -> dialog.dismiss());
        
        AlertDialog dialog = builder.create();
        
        // 设置验证按钮点击事件
        btnVerify.setOnClickListener(v -> {
            dialog.dismiss();
            verifyTask(task);
        });
        
        dialog.show();
    }
    
    /**
     * 验证任务
     */
    private void verifyTask(TaskData task) {
        verificationManager.startVerification(task, new TaskVerificationManager.VerificationCallback() {
            @Override
            public void onVerificationSuccess(TaskVerificationData result) {
                // 验证成功，标记任务完成
                verificationManager.markTaskAsCompleted(task.id, new TaskVerificationManager.CompletionCallback() {
                    @Override
                    public void onTaskCompleted(TaskData completedTask) {
                        runOnUiThread(() -> {
                            Toast.makeText(AgentTaskActivity.this, "任务完成！", Toast.LENGTH_SHORT).show();
                            loadCurrentTask();
                        });
                    }
                    
                    @Override
                    public void onCompletionFailed(String error) {
                        runOnUiThread(() -> {
                            Toast.makeText(AgentTaskActivity.this, error, Toast.LENGTH_SHORT).show();
                        });
                    }
                });
            }
            
            @Override
            public void onVerificationFailed(String error) {
                runOnUiThread(() -> {
                    Toast.makeText(AgentTaskActivity.this, error, Toast.LENGTH_SHORT).show();
                });
            }
        });
    }
    
    /**
     * 进入下一阶段
     */
    private void moveToNextStage() {
        currentStage++;
        if (currentStage > 3) {
            // 所有阶段已完成
            Toast.makeText(this, "恭喜！您已完成所有特工任务", Toast.LENGTH_LONG).show();
            finish();
            return;
        }
        
        // 生成下一阶段任务
        generateNewTask();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_TASK_TIMER && resultCode == RESULT_OK && data != null) {
            int taskId = data.getIntExtra("task_id", -1);
            boolean isCompleted = data.getBooleanExtra("is_completed", false);
            
            if (taskId != -1 && isCompleted) {
                verificationManager.markTaskAsCompleted(taskId, new TaskVerificationManager.CompletionCallback() {
                    @Override
                    public void onTaskCompleted(TaskData task) {
                        runOnUiThread(() -> {
                            Toast.makeText(AgentTaskActivity.this, "任务完成！", Toast.LENGTH_SHORT).show();
                            loadCurrentTask();
                        });
                    }
                    
                    @Override
                    public void onCompletionFailed(String error) {
                        runOnUiThread(() -> {
                            Toast.makeText(AgentTaskActivity.this, error, Toast.LENGTH_SHORT).show();
                        });
                    }
                });
            }
        }
    }
} 