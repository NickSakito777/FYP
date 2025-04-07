package com.example.lbsdemo.chat;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ProgressBar;
import android.widget.Spinner;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.lbsdemo.R;
import com.example.lbsdemo.llm.LLMManager;
import com.example.lbsdemo.user.AppDatabase;
import com.example.lbsdemo.task.TaskData;

import java.util.ArrayList;
import java.util.List;

public class ChatFragment extends Fragment {
    
    private RecyclerView chatRecyclerView;
    private EditText messageInput;
    private ImageButton sendButton;
    private ProgressBar loadingIndicator;
    private Spinner characterSpinner;
    
    private ChatMessageAdapter adapter;
    private LLMManager llmManager;
    private AppDatabase database;
    private String userId;
    private Character selectedCharacter;
    private List<Character> characters = new ArrayList<>();
    
    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        // 注意：这里我们不需要inflate一个新的视图，而是假设ActivitySelection已经包含了对话界面
        // 返回null，表示不需要自己的视图
        return null;
    }
    
    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        // 由于我们是修改ActivitySelection已有的视图，而不是创建新视图，所以需要从activity获取已有的视图
        if (getActivity() == null) {
            Log.e("ChatFragment", "onViewCreated: getActivity()返回null");
            return;
        }
        
        try {
            Log.d("ChatFragment", "onViewCreated: 开始查找content_page2视图");
            View contentPage2 = getActivity().findViewById(R.id.content_page2);
            if (contentPage2 != null) {
                Log.d("ChatFragment", "找到content_page2视图，初始化控件");
                initViews(contentPage2);
                initData();
                setupListeners();
            } else {
                // 如果找不到content_page2，尝试直接从Activity获取控件
                Log.w("ChatFragment", "找不到content_page2视图，尝试直接从Activity获取控件");
                initViews(getActivity().getWindow().getDecorView());
                initData();
                setupListeners();
            }
            
            // 添加调试消息，确认Fragment已创建
            Log.d("ChatFragment", "ChatFragment已创建并初始化");
        } catch (Exception e) {
            Log.e("ChatFragment", "onViewCreated执行出错: " + e.getMessage(), e);
        }
    }
    
    private void initViews(View rootView) {
        if (rootView == null) {
            Log.e("ChatFragment", "initViews: rootView为null");
            return;
        }
        
        Log.d("ChatFragment", "开始初始化视图控件");
        
        try {
            chatRecyclerView = rootView.findViewById(R.id.chatRecyclerView);
            messageInput = rootView.findViewById(R.id.messageInput);
            sendButton = rootView.findViewById(R.id.sendButton);
            loadingIndicator = rootView.findViewById(R.id.loadingIndicator);
            characterSpinner = rootView.findViewById(R.id.characterSpinner);
            
            // 记录控件初始化状态
            Log.d("ChatFragment", "控件初始化状态: chatRecyclerView=" + (chatRecyclerView == null ? "null" : "ok") + 
                    ", messageInput=" + (messageInput == null ? "null" : "ok") + 
                    ", sendButton=" + (sendButton == null ? "null" : "ok") + 
                    ", loadingIndicator=" + (loadingIndicator == null ? "null" : "ok") + 
                    ", characterSpinner=" + (characterSpinner == null ? "null" : "ok"));
            
            // 如果任何必需的控件为null，尝试从整个活动中查找
            if (messageInput == null || sendButton == null) {
                Log.w("ChatFragment", "尝试从Activity中查找控件");
                if (getActivity() != null) {
                    if (messageInput == null) messageInput = getActivity().findViewById(R.id.messageInput);
                    if (sendButton == null) sendButton = getActivity().findViewById(R.id.sendButton);
                    
                    Log.d("ChatFragment", "从Activity中查找控件后: messageInput=" + (messageInput == null ? "null" : "ok") + 
                            ", sendButton=" + (sendButton == null ? "null" : "ok"));
                }
            }
            
            // 设置RecyclerView
            if (chatRecyclerView != null) {
                chatRecyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
                Log.d("ChatFragment", "RecyclerView设置完成");
            } else {
                Log.e("ChatFragment", "RecyclerView为null，无法设置LayoutManager");
            }
        } catch (Exception e) {
            Log.e("ChatFragment", "初始化视图控件时出错: " + e.getMessage(), e);
        }
    }
    
    private void initData() {
        if (getActivity() == null || getContext() == null) return;
        
        // 获取数据库实例
        database = AppDatabase.getInstance(requireContext());
        
        // 获取用户ID
        SharedPreferences prefs = requireActivity().getSharedPreferences("user_prefs", Context.MODE_PRIVATE);
        userId = prefs.getString("user_id", "default_user");
        
        // 初始化LLMManager
        llmManager = LLMManager.getInstance(requireContext());
        llmManager.initialize(null, null); // 使用默认配置
        Log.d("ChatFragment", "LLMManager已初始化");
        
        // 设置消息适配器
        List<ChatMessage> emptyMessages = new ArrayList<>();
        adapter = new ChatMessageAdapter(requireContext(), emptyMessages, new ChatMessageAdapter.ChatMessageListener() {
            @Override
            public void onTaskAccepted(int taskId) {
                // 处理任务接受事件
                Toast.makeText(requireContext(), "任务已接受: " + taskId, Toast.LENGTH_SHORT).show();
            }
            
            @Override
            public void onTaskRejected(int taskId) {
                // 处理任务拒绝事件
                Toast.makeText(requireContext(), "任务已拒绝: " + taskId, Toast.LENGTH_SHORT).show();
            }
            
            @Override
            public TaskData getTaskById(int taskId) {
                // 从数据库获取任务
                return database.taskDao().getTaskById(taskId);
            }
            
            @Override
            public void onAgentTaskCardClicked(TaskData task) {
                // 处理特工任务卡片点击
                Toast.makeText(requireContext(), "开始特工任务: " + task.title, Toast.LENGTH_SHORT).show();
            }
        });
        
        if (chatRecyclerView != null) {
            chatRecyclerView.setAdapter(adapter);
        }
        
        // 加载角色列表
        loadCharacters();
        
        // 加载聊天历史
        loadChatHistory();
    }
    
    private void setupListeners() {
        // 检查必要的组件是否初始化
        if (sendButton == null || messageInput == null || characterSpinner == null) {
            Log.e("ChatFragment", "setupListeners: 组件未初始化，sendButton=" + (sendButton == null ? "null" : "ok") + 
                    ", messageInput=" + (messageInput == null ? "null" : "ok") + 
                    ", characterSpinner=" + (characterSpinner == null ? "null" : "ok"));
            return;
        }
        
        Log.d("ChatFragment", "设置发送按钮点击监听器");
        // 发送按钮点击事件
        sendButton.setOnClickListener(v -> {
            String message = messageInput.getText().toString().trim();
            if (!message.isEmpty()) {
                sendMessage(message);
                messageInput.setText("");
            }
        });
        
        // 角色选择下拉框监听
        characterSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (position >= 0 && position < characters.size() && adapter != null) {
                    selectedCharacter = characters.get(position);
                    
                    // 添加系统消息，表示角色切换
                    if (adapter.getItemCount() > 0) {
                        ChatMessage systemMessage = new ChatMessage();
                        systemMessage.userId = userId;
                        systemMessage.role = "system";
                        systemMessage.content = "已切换到角色：" + selectedCharacter.getName();
                        systemMessage.timestamp = System.currentTimeMillis();
                        systemMessage.messageType = "system";
                        
                        adapter.addMessage(systemMessage);
                        scrollToBottom();
                    }
                }
            }
            
            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                // 不做任何处理
            }
        });
    }
    
    private void loadCharacters() {
        // 确保数据库已初始化
        if (database == null) return;
        
        // 从数据库加载角色
        // 在实际应用中，应该使用异步查询
        AppDatabase.databaseWriteExecutor.execute(() -> {
            List<Character> dbCharacters = database.characterDao().getAllCharacters();
            
            // 如果数据库中没有角色，创建默认角色
            if (dbCharacters == null || dbCharacters.isEmpty()) {
                Character defaultCharacter = Character.createDefaultCharacter();
                
                // 添加几个示例角色
                List<Character> sampleCharacters = new ArrayList<>();
                sampleCharacters.add(defaultCharacter);
                
                Character character2 = new Character(
                        "char_history",
                        "历史教授",
                        "拥有深厚的历史知识，擅长讲述历史事件和故事。",
                        "严谨、风趣、博学",
                        "prof_history",
                        "历史、文化、古代文明"
                );
                
                Character character3 = new Character(
                        "char_science",
                        "科学家",
                        "物理学和化学领域的专家，喜欢解释自然现象。",
                        "理性、好奇、深入浅出",
                        "scientist",
                        "科学、实验、自然探索"
                );
                
                sampleCharacters.add(character2);
                sampleCharacters.add(character3);
                
                // 保存到数据库
                for (Character character : sampleCharacters) {
                    database.characterDao().insert(character);
                }
                
                dbCharacters = sampleCharacters;
            }
            
            // 更新UI必须在主线程，先检查Activity是否存在
            if (getActivity() == null) return;
            
            final List<Character> finalCharacters = dbCharacters;
            getActivity().runOnUiThread(() -> {
                characters.clear();
                characters.addAll(finalCharacters);
                
                // 设置角色选择适配器，先检查是否为null
                if (characterSpinner != null && getContext() != null) {
                    CharacterAdapter characterAdapter = new CharacterAdapter(getContext(), characters);
                    characterSpinner.setAdapter(characterAdapter);
                }
                
                // 默认选择第一个角色
                if (!characters.isEmpty()) {
                    selectedCharacter = characters.get(0);
                }
            });
        });
    }
    
    private void loadChatHistory() {
        // 显示加载指示器（先检查是否为null）
        if (loadingIndicator != null) {
            loadingIndicator.setVisibility(View.VISIBLE);
        }
        
        // 从数据库异步加载聊天历史
        AppDatabase.databaseWriteExecutor.execute(() -> {
            List<ChatMessage> messages = database.chatMessageDao().getRecentMessagesByUserId(userId, 50);
            
            // 更新UI必须在主线程
            if (getActivity() != null) {
                getActivity().runOnUiThread(() -> {
                    adapter.setMessages(messages);
                    
                    // 滚动到底部
                    if (!messages.isEmpty()) {
                        chatRecyclerView.smoothScrollToPosition(messages.size() - 1);
                    }
                    
                    // 隐藏加载指示器（再次检查是否为null）
                    if (loadingIndicator != null) {
                        loadingIndicator.setVisibility(View.GONE);
                    }
                });
            }
        });
    }
    
    private void sendMessage(String message) {
        if (message.isEmpty()) {
            return;
        }
        
        // 显示用户消息
        ChatMessage userMessage = new ChatMessage();
        userMessage.userId = userId;
        userMessage.role = "user";
        userMessage.content = message;
        userMessage.timestamp = System.currentTimeMillis();
        adapter.addMessage(userMessage);
        scrollToBottom();
        
        // 保存用户消息到数据库
        new Thread(() -> {
            database.chatMessageDao().insert(userMessage);
        }).start();
        
        // 显示加载指示器
        loadingIndicator.setVisibility(View.VISIBLE);
        
        // 记录发送消息的日志
        Log.d("ChatFragment", "发送消息到LLM: " + message);
        
        // 发送消息到LLM
        llmManager.sendMessage(userId, message, null, new LLMManager.LLMResponseCallback() {
            @Override
            public void onResponse(String response) {
                // 在UI线程处理响应
                requireActivity().runOnUiThread(() -> {
                    // 隐藏加载指示器
                    loadingIndicator.setVisibility(View.GONE);
                    
                    // 显示AI回复
                    ChatMessage aiMessage = new ChatMessage();
                    aiMessage.userId = userId;
                    aiMessage.role = "assistant";
                    aiMessage.content = response;
                    aiMessage.timestamp = System.currentTimeMillis();
                    adapter.addMessage(aiMessage);
                    scrollToBottom();
                    
                    Log.d("ChatFragment", "收到LLM响应: " + response.substring(0, Math.min(50, response.length())) + "...");
                });
            }
            
            @Override
            public void onError(Exception e) {
                // 在UI线程处理错误
                requireActivity().runOnUiThread(() -> {
                    // 隐藏加载指示器
                    loadingIndicator.setVisibility(View.GONE);
                    
                    // 显示错误消息
                    String errorMsg = "无法获取回复: " + e.getMessage();
                    ChatMessage errorMessage = new ChatMessage();
                    errorMessage.userId = userId;
                    errorMessage.role = "system";
                    errorMessage.content = errorMsg;
                    errorMessage.messageType = "error";
                    errorMessage.timestamp = System.currentTimeMillis();
                    adapter.addMessage(errorMessage);
                    scrollToBottom();
                    
                    Log.e("ChatFragment", "LLM错误: " + e.getMessage(), e);
                });
            }
        });
    }
    
    private void scrollToBottom() {
        if (adapter != null && chatRecyclerView != null && adapter.getItemCount() > 0) {
            chatRecyclerView.smoothScrollToPosition(adapter.getItemCount() - 1);
        }
    }
    
    @Override
    public void onResume() {
        super.onResume();
        // 当Fragment恢复时，仅在控件初始化完成后刷新聊天记录
        if (loadingIndicator != null && chatRecyclerView != null && adapter != null) {
            loadChatHistory();
        }
    }
} 