package com.example.lbsdemo.chat;

import android.content.Context;
import android.net.Uri;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.cardview.widget.CardView;
import androidx.recyclerview.widget.RecyclerView;

import com.example.lbsdemo.R;
import com.example.lbsdemo.task.TaskData;
import com.example.lbsdemo.user.AppDatabase;
import com.example.lbsdemo.activity.ActivitySelection;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import android.content.SharedPreferences;

/**
 * 聊天消息适配器
 * 支持文本消息和任务卡片等多种消息类型
 */
public class ChatMessageAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    // 消息类型常量
    private static final int TYPE_TEXT_MESSAGE = 1;
    private static final int TYPE_TASK_CARD = 2;
    private static final int TYPE_SYSTEM_NOTIFICATION = 3;
    private static final int TYPE_IMAGE_MESSAGE = 4;

    // 添加特工任务类型的常量
    public static final int VIEW_TYPE_ASSISTANT = 1;
    public static final int VIEW_TYPE_USER = 2;
    public static final int VIEW_TYPE_SYSTEM = 3;
    public static final int VIEW_TYPE_TASK_CARD = 4;
    public static final int VIEW_TYPE_IMAGE = 5;
    public static final int VIEW_TYPE_AGENT_TASK = 6;  // 特工任务消息
    public static final int VIEW_TYPE_AGENT_TASK_CARD = 7; // 特工任务卡片
    public static final int VIEW_TYPE_NEXT_STAGE_BUTTON = 8; // 下一阶段按钮

    private List<ChatMessage> messageList;
    private final String userId;
    private TaskCardListener taskCardListener;
    
    public interface ChatMessageListener extends TaskCardListener {
        void onAgentTaskCardClicked(TaskData task);
    }

    private ChatMessageListener listener;

    public ChatMessageAdapter(Context context, List<ChatMessage> messages, ChatMessageListener listener) {
        this.messageList = new ArrayList<>(messages);
        this.userId = context.getSharedPreferences("user_prefs", Context.MODE_PRIVATE)
                .getString("user_id", "default_user");
        this.listener = listener;
        // 对于向后兼容性，设置taskCardListener为listener
        this.taskCardListener = listener;
    }

    /**
     * 设置任务卡片监听器
     * @param listener 监听器
     */
    public void setTaskCardListener(TaskCardListener listener) {
        this.taskCardListener = listener;
    }

    @Override
    public int getItemViewType(int position) {
        ChatMessage message = messageList.get(position);
        
        if ("user".equals(message.role)) {
            return VIEW_TYPE_USER;
        } else if ("assistant".equals(message.role)) {
            if (message.messageType != null && message.messageType.equals("agent_task")) {
                return VIEW_TYPE_AGENT_TASK;
            }
            return VIEW_TYPE_ASSISTANT;
        } else if ("system".equals(message.role)) {
            if (message.messageType != null) {
                switch (message.messageType) {
                    case "task_card":
                        return VIEW_TYPE_TASK_CARD;
                    case "image":
                        return VIEW_TYPE_IMAGE;
                    case "agent_task_card":
                        return VIEW_TYPE_AGENT_TASK_CARD;
                    case "next_stage_button":
                        return VIEW_TYPE_NEXT_STAGE_BUTTON;
                    default:
                        return VIEW_TYPE_SYSTEM;
                }
            }
            return VIEW_TYPE_SYSTEM;
        }
        
        return VIEW_TYPE_ASSISTANT;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inflater = LayoutInflater.from(parent.getContext());
        
        switch (viewType) {
            case VIEW_TYPE_USER:
                View textView = inflater.inflate(R.layout.item_chat_message, parent, false);
                return new MessageViewHolder(textView);
            case VIEW_TYPE_ASSISTANT:
                View textViewAssistant = inflater.inflate(R.layout.item_chat_message, parent, false);
                return new MessageViewHolder(textViewAssistant);
            case VIEW_TYPE_SYSTEM:
                View systemView = inflater.inflate(R.layout.item_chat_message, parent, false);
                return new MessageViewHolder(systemView);
            case VIEW_TYPE_TASK_CARD:
                View taskView = inflater.inflate(R.layout.item_chat_task_card, parent, false);
                return new TaskCardViewHolder(taskView);
            case VIEW_TYPE_IMAGE:
                View imageView = inflater.inflate(R.layout.item_chat_message, parent, false);
                return new MessageViewHolder(imageView);
            case VIEW_TYPE_AGENT_TASK:
                View agentTaskView = inflater.inflate(R.layout.item_agent_message, parent, false);
                return new AgentTaskViewHolder(agentTaskView);
            case VIEW_TYPE_AGENT_TASK_CARD:
                View agentTaskCardView = inflater.inflate(R.layout.item_agent_task_card, parent, false);
                return new AgentTaskCardViewHolder(agentTaskCardView);
            case VIEW_TYPE_NEXT_STAGE_BUTTON:
                View nextStageButtonView = inflater.inflate(R.layout.item_next_stage_button, parent, false);
                return new NextStageButtonViewHolder(nextStageButtonView);
            default:
                View textViewDefault = inflater.inflate(R.layout.item_chat_message, parent, false);
                return new MessageViewHolder(textViewDefault);
        }
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        ChatMessage message = messageList.get(position);
        
        switch (holder.getItemViewType()) {
            case VIEW_TYPE_USER:
                ((MessageViewHolder) holder).bind(message);
                break;
            case VIEW_TYPE_ASSISTANT:
                ((MessageViewHolder) holder).bind(message);
                break;
            case VIEW_TYPE_SYSTEM:
                ((MessageViewHolder) holder).bindSystemMessage(message);
                break;
            case VIEW_TYPE_TASK_CARD:
                ((TaskCardViewHolder) holder).bind(message);
                break;
            case VIEW_TYPE_IMAGE:
                ((MessageViewHolder) holder).bindImageMessage(message);
                break;
            case VIEW_TYPE_AGENT_TASK:
                AgentTaskViewHolder agentHolder = (AgentTaskViewHolder) holder;
                agentHolder.bind(message);
                break;
            case VIEW_TYPE_AGENT_TASK_CARD:
                AgentTaskCardViewHolder agentCardHolder = (AgentTaskCardViewHolder) holder;
                agentCardHolder.bind(message);
                break;
            case VIEW_TYPE_NEXT_STAGE_BUTTON:
                NextStageButtonViewHolder nextStageHolder = (NextStageButtonViewHolder) holder;
                nextStageHolder.bind();
                break;
        }
    }

    @Override
    public int getItemCount() {
        return messageList.size();
    }

    public void addMessage(ChatMessage message) {
        if (message == null) {
            return;
        }
        
        this.messageList.add(message);
        notifyItemInserted(this.messageList.size() - 1);
    }
    
    /**
     * 添加任务卡片消息
     * @param taskData 任务数据
     */
    public void addTaskCard(TaskData taskData) {
        if (taskData == null) {
            return;
        }
        
        // 创建任务卡片消息
        ChatMessage taskMessage = new ChatMessage();
        taskMessage.userId = userId;
        taskMessage.role = "assistant";
        taskMessage.content = "这是一个任务卡片: " + taskData.title; // 备用文本
        taskMessage.timestamp = System.currentTimeMillis();
        taskMessage.relatedTaskId = taskData.id;
        taskMessage.messageType = "task_card";
        
        addMessage(taskMessage);
    }
    
    /**
     * 删除指定位置的消息
     * @param position 消息位置
     */
    public void removeMessageAt(int position) {
        if (position >= 0 && position < messageList.size()) {
            messageList.remove(position);
            notifyItemRemoved(position);
        }
    }

    /**
     * 清空所有消息
     */
    public void clearMessages() {
        int size = messageList.size();
        messageList.clear();
        notifyItemRangeRemoved(0, size);
    }

    /**
     * 设置消息列表
     * @param messages 消息列表
     */
    public void setMessages(List<ChatMessage> messages) {
        this.messageList = new ArrayList<>(messages);
        notifyDataSetChanged();
    }

    /**
     * 文本消息ViewHolder
     */
    class MessageViewHolder extends RecyclerView.ViewHolder {
        private final CardView messageCard;
        private final TextView tvSender;
        private final TextView tvContent;
        private final TextView tvTimestamp;

        public MessageViewHolder(@NonNull View itemView) {
            super(itemView);
            messageCard = itemView.findViewById(R.id.messageCard);
            tvSender = itemView.findViewById(R.id.tvSender);
            tvContent = itemView.findViewById(R.id.tvMessageContent);
            tvTimestamp = itemView.findViewById(R.id.tvTimestamp);
        }
        
        /**
         * 格式化时间戳为时间字符串
         * @param timestamp 时间戳
         * @return 格式化后的时间字符串
         */
        private String formatTimestamp(long timestamp) {
            SimpleDateFormat sdf = new SimpleDateFormat("HH:mm", Locale.getDefault());
            return sdf.format(new Date(timestamp));
        }

        public void bind(ChatMessage message) {
            // 设置消息内容
            tvContent.setText(message.getContent());
            
            // 设置时间戳
            tvTimestamp.setText(formatTimestamp(message.getTimestamp()));
            
            // 根据消息发送者调整样式
            if ("user".equals(message.getRole())) {
                // 用户消息 - 靠右显示，蓝色背景
                messageCard.setCardBackgroundColor(itemView.getContext().getResources().getColor(android.R.color.holo_blue_light));
                tvSender.setText("我");
                tvSender.setTextColor(itemView.getContext().getResources().getColor(android.R.color.white));
                tvContent.setTextColor(itemView.getContext().getResources().getColor(android.R.color.white));
                tvTimestamp.setTextColor(itemView.getContext().getResources().getColor(android.R.color.white));
                
                // 调整布局位置到右侧
                LinearLayout.LayoutParams params = (LinearLayout.LayoutParams) messageCard.getLayoutParams();
                params.gravity = Gravity.END;
                params.setMarginStart(80);
                params.setMarginEnd(8);
                messageCard.setLayoutParams(params);
            } else {
                // AI助手消息 - 靠左显示，白色背景
                messageCard.setCardBackgroundColor(itemView.getContext().getResources().getColor(android.R.color.white));
                tvSender.setText("AI助手");
                tvSender.setTextColor(itemView.getContext().getResources().getColor(android.R.color.holo_blue_dark));
                tvContent.setTextColor(itemView.getContext().getResources().getColor(android.R.color.black));
                tvTimestamp.setTextColor(itemView.getContext().getResources().getColor(android.R.color.darker_gray));
                
                // 调整布局位置到左侧
                LinearLayout.LayoutParams params = (LinearLayout.LayoutParams) messageCard.getLayoutParams();
                params.gravity = Gravity.START;
                params.setMarginStart(8);
                params.setMarginEnd(80);
                messageCard.setLayoutParams(params);
            }
        }
        
        public void bindSystemMessage(ChatMessage message) {
            // 设置消息内容
            tvContent.setText(message.getContent());
            
            // 设置时间戳
            tvTimestamp.setText(formatTimestamp(message.getTimestamp()));
            
            // 系统消息 - 居中显示，灰色背景
            messageCard.setCardBackgroundColor(itemView.getContext().getResources().getColor(android.R.color.darker_gray));
            tvSender.setText("系统通知");
            tvSender.setTextColor(itemView.getContext().getResources().getColor(android.R.color.white));
            tvContent.setTextColor(itemView.getContext().getResources().getColor(android.R.color.white));
            tvTimestamp.setTextColor(itemView.getContext().getResources().getColor(android.R.color.white));
            
            // 调整布局位置到中间
            LinearLayout.LayoutParams params = (LinearLayout.LayoutParams) messageCard.getLayoutParams();
            params.gravity = Gravity.CENTER;
            params.setMarginStart(40);
            params.setMarginEnd(40);
            messageCard.setLayoutParams(params);
        }
        
        /**
         * 绑定图片消息
         * @param message 消息对象
         */
        public void bindImageMessage(ChatMessage message) {
            // 设置基本信息
            tvTimestamp.setText(formatTimestamp(message.getTimestamp()));
            
            // 根据消息发送者调整样式
            if ("user".equals(message.getRole())) {
                // 用户消息 - 靠右显示，蓝色背景
                messageCard.setCardBackgroundColor(itemView.getContext().getResources().getColor(R.color.user_message_background));
                tvSender.setText("我");
                tvSender.setTextColor(itemView.getContext().getResources().getColor(android.R.color.white));
                tvContent.setTextColor(itemView.getContext().getResources().getColor(android.R.color.white));
                tvTimestamp.setTextColor(itemView.getContext().getResources().getColor(android.R.color.white));
                
                // 调整布局位置到右侧
                LinearLayout.LayoutParams params = (LinearLayout.LayoutParams) messageCard.getLayoutParams();
                params.gravity = Gravity.END;
                params.setMarginStart(80);
                params.setMarginEnd(8);
                messageCard.setLayoutParams(params);
            } else {
                // AI助手消息 - 靠左显示，白色背景
                messageCard.setCardBackgroundColor(itemView.getContext().getResources().getColor(R.color.assistant_message_background));
                tvSender.setText("AI助手");
                tvSender.setTextColor(itemView.getContext().getResources().getColor(android.R.color.holo_blue_dark));
                tvContent.setTextColor(itemView.getContext().getResources().getColor(android.R.color.black));
                tvTimestamp.setTextColor(itemView.getContext().getResources().getColor(android.R.color.darker_gray));
                
                // 调整布局位置到左侧
                LinearLayout.LayoutParams params = (LinearLayout.LayoutParams) messageCard.getLayoutParams();
                params.gravity = Gravity.START;
                params.setMarginStart(8);
                params.setMarginEnd(80);
                messageCard.setLayoutParams(params);
            }
            
            // 显示图片消息提示文本
            tvContent.setText("图片消息");
            
            // 查找并显示图片
            if (message.getImageUri() != null && !message.getImageUri().isEmpty()) {
                // 获取ImageView
                ImageView ivMessageImage = itemView.findViewById(R.id.ivMessageImage);
                if (ivMessageImage != null) {
                    // 显示图片视图
                    ivMessageImage.setVisibility(View.VISIBLE);
                    
                    try {
                        // 从URI加载图片
                        Uri imageUri = Uri.parse(message.getImageUri());
                        ivMessageImage.setImageURI(imageUri);
                        
                        // 设置点击事件可查看大图
                        ivMessageImage.setOnClickListener(v -> {
                            Toast.makeText(itemView.getContext(), "查看大图", Toast.LENGTH_SHORT).show();
                            // 这里可以添加打开大图查看的逻辑
                        });
                    } catch (Exception e) {
                        // 图片加载失败
                        Log.e("ChatMessageAdapter", "加载图片失败: " + e.getMessage());
                        ivMessageImage.setVisibility(View.GONE);
                    }
                }
            } else {
                // 没有图片URI，隐藏图片视图
                ImageView ivMessageImage = itemView.findViewById(R.id.ivMessageImage);
                if (ivMessageImage != null) {
                    ivMessageImage.setVisibility(View.GONE);
                }
            }
            
            // 设置整个消息卡片的点击事件
            messageCard.setOnClickListener(v -> {
                if (message.getImageUri() != null) {
                    Toast.makeText(itemView.getContext(), "查看大图", Toast.LENGTH_SHORT).show();
                    // 这里可以添加打开大图查看的逻辑
                }
            });
        }
    }
    
    /**
     * 任务卡片ViewHolder
     */
    class TaskCardViewHolder extends RecyclerView.ViewHolder {
        private final CardView taskCard;
        private final TextView tvTaskTitle;
        private final TextView tvTaskDescription;
        private final TextView tvTaskLocation;
        private final TextView tvTaskDuration;
        private final Button btnAcceptTask;
        private final Button btnRejectTask;
        private final TextView tvTimestamp;
        private ChatMessage message;
        
        public TaskCardViewHolder(@NonNull View itemView) {
            super(itemView);
            taskCard = itemView.findViewById(R.id.taskCard);
            tvTaskTitle = itemView.findViewById(R.id.tvTaskTitle);
            tvTaskDescription = itemView.findViewById(R.id.tvTaskDescription);
            tvTaskLocation = itemView.findViewById(R.id.tvTaskLocation);
            tvTaskDuration = itemView.findViewById(R.id.tvTaskDuration);
            btnAcceptTask = itemView.findViewById(R.id.btnAcceptTask);
            btnRejectTask = itemView.findViewById(R.id.btnRejectTask);
            tvTimestamp = itemView.findViewById(R.id.tvTimestamp);
        }
        
        /**
         * 格式化时间戳为时间字符串
         * @param timestamp 时间戳
         * @return 格式化后的时间字符串
         */
        private String formatTimestamp(long timestamp) {
            SimpleDateFormat sdf = new SimpleDateFormat("HH:mm", Locale.getDefault());
            return sdf.format(new Date(timestamp));
        }
        
        public void bind(ChatMessage message) {
            // 保存消息引用
            this.message = message;
            
            // 从ChatMessage中获取任务信息
            Integer taskId = message.relatedTaskId;
            
            // 设置时间戳
            tvTimestamp.setText(formatTimestamp(message.getTimestamp()));
            
            // 绑定接受按钮点击事件
            btnAcceptTask.setOnClickListener(v -> {
                if (taskCardListener != null && taskId != null) {
                    taskCardListener.onTaskAccepted(taskId);
                    
                    // 按钮点击后显示已接受状态
                    btnAcceptTask.setText("已接受");
                    btnAcceptTask.setEnabled(false);
                    btnRejectTask.setEnabled(false);
                    
                    // 不再使用全局SharedPreferences，而是直接在数据库中更新任务状态
                    // 在onTaskAccepted回调方法中处理
                    
                    // 可以添加提示
                    Toast.makeText(itemView.getContext(), "任务已接受", Toast.LENGTH_SHORT).show();
                }
            });
            
            // 绑定拒绝按钮点击事件
            btnRejectTask.setOnClickListener(v -> {
                if (taskCardListener != null && taskId != null) {
                    taskCardListener.onTaskRejected(taskId);
                    
                    // 按钮点击后显示已拒绝状态
                    btnRejectTask.setText("已拒绝");
                    btnRejectTask.setEnabled(false);
                    btnAcceptTask.setEnabled(false);
                    
                    // 不再使用全局SharedPreferences，而是直接在数据库中更新任务状态
                    // 在onTaskRejected回调方法中处理
                    
                    // 可以添加提示
                    Toast.makeText(itemView.getContext(), "任务已拒绝", Toast.LENGTH_SHORT).show();
                }
            });
            
            // 如果有关联的任务ID，则异步加载任务数据
            if (taskId != null) {
                loadTaskData(taskId);
            } else {
                // 没有关联任务ID，显示默认内容
                tvTaskTitle.setText("无效任务");
                tvTaskDescription.setText("无法加载任务详情");
                tvTaskLocation.setText("地点: 未知");
                tvTaskDuration.setText("时长: 未知");
                btnAcceptTask.setEnabled(false);
                btnRejectTask.setEnabled(false);
            }
        }
        
        /**
         * 异步加载任务数据
         */
        private void loadTaskData(int taskId) {
            // 设置加载状态
            tvTaskTitle.setText("加载中...");
            tvTaskDescription.setText("正在加载任务详情");
            
            // 使用异步方式加载任务数据
            new Thread(() -> {
                TaskData task = null;
                
                if (taskId > 0 && taskCardListener != null) {
                    // 如果任务ID有效，从数据库获取
                    task = taskCardListener.getTaskById(taskId);
                } else if (taskId == 0) {
                    // 如果是临时ID=0的任务，直接使用消息中的内容显示
                    String title = this.message.content.replace("这是一个任务卡片: ", "");
                    task = new TaskData();
                    task.id = 0;
                    task.title = title;
                    task.description = "正在保存任务详情...";
                    task.location = "校园内";
                    task.durationMinutes = 30;
                    task.status = "pending"; // 添加初始状态
                    task.isCompleted = false; // 确保未完成状态
                }
                
                // 保存最终结果以在UI线程中使用
                final TaskData finalTask = task;
                
                // 在UI线程上更新UI
                itemView.post(() -> {
                    if (finalTask != null) {
                        displayTaskData(finalTask);
                    } else {
                        tvTaskTitle.setText("任务未找到");
                        tvTaskDescription.setText("无法找到任务详情");
                        tvTaskLocation.setText("地点: 未知");
                        tvTaskDuration.setText("时长: 未知");
                        btnAcceptTask.setEnabled(false);
                    }
                });
            }).start();
        }
        
        /**
         * 显示任务数据
         */
        private void displayTaskData(TaskData task) {
            tvTaskTitle.setText(task.title);
            tvTaskDescription.setText(task.description);
            tvTaskLocation.setText("地点: " + task.location);
            tvTaskDuration.setText("时长: " + task.durationMinutes + "分钟");
            
            // 只使用任务自身的status字段而不是全局SharedPreferences
            // 根据任务状态设置按钮
            if (task.isCompleted) {
                btnAcceptTask.setText("已完成");
                btnAcceptTask.setEnabled(false);
                btnRejectTask.setEnabled(false);
            } else if ("accepted".equals(task.status)) {
                btnAcceptTask.setText("已接受");
                btnAcceptTask.setEnabled(false);
                btnRejectTask.setEnabled(false);
            } else if ("rejected".equals(task.status)) {
                btnRejectTask.setText("已拒绝");
                btnAcceptTask.setEnabled(false);
                btnRejectTask.setEnabled(false);
            } else {
                // 恢复默认状态
                btnAcceptTask.setText("接受任务");
                btnAcceptTask.setEnabled(true);
                btnRejectTask.setText("拒绝任务");
                btnRejectTask.setEnabled(true);
            }
        }
    }
    
    /**
     * 任务卡片监听器接口
     */
    public interface TaskCardListener {
        /**
         * 当任务被接受时调用
         * @param taskId 任务ID
         */
        void onTaskAccepted(int taskId);
        
        /**
         * 当任务被拒绝时调用
         * @param taskId 任务ID
         */
        void onTaskRejected(int taskId);
        
        /**
         * 获取指定ID的任务
         * @param taskId 任务ID
         * @return 任务数据
         */
        TaskData getTaskById(int taskId);
    }

    // 添加特工任务ViewHolder
    public class AgentTaskViewHolder extends RecyclerView.ViewHolder {
        private TextView tvAgentName;
        private TextView tvAgentMessage;
        private ImageView ivAgentAvatar;

        public AgentTaskViewHolder(View itemView) {
            super(itemView);
            tvAgentName = itemView.findViewById(R.id.tv_agent_name);
            tvAgentMessage = itemView.findViewById(R.id.tv_agent_message);
            ivAgentAvatar = itemView.findViewById(R.id.iv_agent_avatar);
        }

        public void bind(ChatMessage message) {
            tvAgentName.setText(message.senderName != null ? message.senderName : "特工Zero");
            tvAgentMessage.setText(message.content);
            // 设置特工头像，使用系统默认资源
            ivAgentAvatar.setImageResource(android.R.drawable.ic_dialog_info);
        }
    }

    // 添加特工任务卡片ViewHolder
    public class AgentTaskCardViewHolder extends RecyclerView.ViewHolder {
        private TextView tvTaskTitle;
        private TextView tvTaskDescription;
        private TextView tvTaskLocation;
        private TextView tvVerificationMethod;
        private Button btnStartTask;
        private TaskData task;

        public AgentTaskCardViewHolder(View itemView) {
            super(itemView);
            tvTaskTitle = itemView.findViewById(R.id.tv_task_title);
            tvTaskDescription = itemView.findViewById(R.id.tv_task_description);
            tvTaskLocation = itemView.findViewById(R.id.tv_task_location);
            tvVerificationMethod = itemView.findViewById(R.id.tv_verification_method);
            btnStartTask = itemView.findViewById(R.id.btn_start_task);
        }

        public void bind(ChatMessage message) {
            // 设置初始加载状态
            tvTaskTitle.setText("加载中...");
            tvTaskDescription.setText("正在加载任务详情");
            tvTaskLocation.setText("地点: 加载中");
            tvVerificationMethod.setText("验证方式: 加载中");
            
            if (message.relatedTaskId > 0 && taskCardListener != null) {
                // 在后台线程中获取任务信息
                new Thread(() -> {
                    // 从数据库获取任务信息
                    TaskData taskData = taskCardListener.getTaskById(message.relatedTaskId);
                    
                    // 在UI线程更新界面
                    itemView.post(() -> {
                        if (taskData != null) {
                            task = taskData;
                            tvTaskTitle.setText(task.title);
                            tvTaskDescription.setText(task.description);
                            tvTaskLocation.setText("地点: " + task.location);
                            
                            String verificationText = "验证方式: ";
                            if ("photo".equals(task.verificationMethod)) {
                                verificationText += "拍照验证";
                            } else if ("geofence".equals(task.verificationMethod)) {
                                verificationText += "位置验证";
                            } else if ("time".equals(task.verificationMethod)) {
                                verificationText += "时长验证 (" + task.durationMinutes + "分钟)";
                            } else {
                                verificationText += task.verificationMethod;
                            }
                            tvVerificationMethod.setText(verificationText);
                            
                            // 根据任务状态设置按钮文本
                            if (task.isCompleted) {
                                btnStartTask.setText("已完成");
                                btnStartTask.setEnabled(false);
                            } else {
                                btnStartTask.setText("开始任务");
                                btnStartTask.setEnabled(true);
                            }
                            
                            // 设置按钮点击事件
                            btnStartTask.setOnClickListener(v -> {
                                if (listener != null) {
                                    listener.onAgentTaskCardClicked(task);
                                }
                            });
                        } else {
                            // 处理任务不存在的情况
                            tvTaskTitle.setText("任务不存在");
                            tvTaskDescription.setText("无法加载任务信息");
                            tvTaskLocation.setText("地点: 未知");
                            tvVerificationMethod.setText("验证方式: 未知");
                            btnStartTask.setEnabled(false);
                        }
                    });
                }).start();
            } else {
                // 处理无效任务ID
                tvTaskTitle.setText("无效任务");
                tvTaskDescription.setText("无法加载任务详情");
                tvTaskLocation.setText("地点: 未知");
                tvVerificationMethod.setText("验证方式: 未知");
                btnStartTask.setEnabled(false);
            }
        }
    }

    // 添加下一阶段按钮ViewHolder
    public class NextStageButtonViewHolder extends RecyclerView.ViewHolder {
        private Button btnNextStage;

        public NextStageButtonViewHolder(View itemView) {
            super(itemView);
            btnNextStage = itemView.findViewById(R.id.btn_next_stage);
        }

        public void bind() {
            btnNextStage.setOnClickListener(v -> {
                // 处理进入下一阶段
                Context context = itemView.getContext();
                if (context instanceof ActivitySelection) {
                    ((ActivitySelection) context).moveToNextAgentStage();
                }
            });
        }
    }
} 