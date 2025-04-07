package com.example.lbsdemo.llm;

import android.content.Context;
import android.util.Log;

import com.example.lbsdemo.chat.ChatMessage;
import com.example.lbsdemo.user.AppDatabase;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

// 删除Unirest导入，恢复OkHttp导入
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

/**
 * LLM服务类
 * 负责与LLM API通信，发送请求并处理响应
 */
public class LLMService {
    private static final String TAG = "LLMService";
    
    // API相关配置 - 更新为DeepSeek API
    private static final String API_ENDPOINT = "https://api.siliconflow.cn/v1/chat/completions";
    private static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");
    private static final int REQUEST_TIMEOUT = 60; // 秒
    
    // DeepSeek API密钥
    private static final String DEFAULT_API_KEY = "sk-sduizqafbjaplqgckedfadixcpgcazmtmaqvbmheoqprmmak";
    private static final String DEFAULT_MODEL = "deepseek-ai/DeepSeek-V3"; // 使用DeepSeek模型
    
    private final Context appContext;
    private final OkHttpClient client;
    private final LLMContextManager contextManager;
    private final AppDatabase database;
    
    private String apiKey = DEFAULT_API_KEY; // 默认使用静态密钥
    private String modelName = DEFAULT_MODEL; //
    
    /**
     * 构造函数
     * @param context 应用上下文
     */
    public LLMService(Context context) {
        this.appContext = context.getApplicationContext();
        this.database = AppDatabase.getInstance(appContext);
        
        // 初始化HTTP客户端
        this.client = new OkHttpClient.Builder()
                .connectTimeout(REQUEST_TIMEOUT, TimeUnit.SECONDS)
                .readTimeout(REQUEST_TIMEOUT, TimeUnit.SECONDS)
                .writeTimeout(REQUEST_TIMEOUT, TimeUnit.SECONDS)
                .build();
        
        // 初始化上下文管理器
        this.contextManager = new LLMContextManager(appContext);
    }
    
    /**
     * 配置API参数
     * @param apiKey API密钥，如果为null则使用默认密钥
     * @param modelName 模型名称，如果为null则使用默认模型
     */
    public void configure(String apiKey, String modelName) {
        this.apiKey = apiKey != null ? apiKey : DEFAULT_API_KEY;
        this.modelName = modelName != null ? modelName : DEFAULT_MODEL;
        Log.d(TAG, "LLM服务配置更新，使用模型：" + this.modelName);
    }
    
    /**
     * 发送消息到LLM并获取回复
     * @param userId 用户ID
     * @param message 用户消息内容
     * @param currentTaskId 当前任务ID (可为null)
     * @param callback 回调函数
     */
    public void sendMessage(String userId, String message, Integer currentTaskId, LLMResponseCallback callback) {
        // 检查API密钥
        if (apiKey == null || apiKey.isEmpty()) {
            callback.onError(new IllegalStateException("API密钥未配置，请先调用configure方法"));
            return;
        }
        
        // 创建当前用户消息
        Map<String, String> userMessage = new HashMap<>();
        userMessage.put("role", "user");
        userMessage.put("content", message);
        
        List<Map<String, String>> currentMessages = new ArrayList<>();
        currentMessages.add(userMessage);
        
        // 使用上下文管理器构建完整上下文
        contextManager.buildContext(userId, currentTaskId, currentMessages, new LLMContextManager.ContextCallback() {
            @Override
            public void onContextBuilt(List<Map<String, String>> context) {
                // 发送请求到LLM API
                sendLLMRequest(context, userId, currentTaskId, callback);
            }
            
            @Override
            public void onError(Exception e) {
                callback.onError(e);
            }
        });
    }
    
    /**
     * 发送请求到LLM API
     */
    private void sendLLMRequest(List<Map<String, String>> messages, String userId, Integer currentTaskId, LLMResponseCallback callback) {
        // 检查网络连接
        if (!isNetworkAvailable()) {
            Log.e(TAG, "网络不可用，无法发送LLM请求");
            callback.onError(new IOException("网络连接不可用，请检查网络设置"));
            return;
        }
        
        try {
            Log.d(TAG, "准备发送LLM请求，消息数量: " + messages.size());
            
            // 构建请求JSON
            JSONObject jsonBody = new JSONObject();
            jsonBody.put("model", modelName);
            
            // 添加消息数组
            JSONArray messagesArray = new JSONArray();
            for (Map<String, String> message : messages) {
                JSONObject messageObj = new JSONObject();
                messageObj.put("role", message.get("role"));
                messageObj.put("content", message.get("content"));
                messagesArray.put(messageObj);
            }
            jsonBody.put("messages", messagesArray);
            
            // 设置其他参数
            jsonBody.put("temperature", 0.7);
            jsonBody.put("max_tokens", 1000);
            
            String requestBody = jsonBody.toString();
            Log.d(TAG, "LLM请求体: " + requestBody.substring(0, Math.min(200, requestBody.length())) + "...");
            
            // 创建请求
            Request request = new Request.Builder()
                    .url(API_ENDPOINT)
                    .addHeader("Content-Type", "application/json")
                    .addHeader("Authorization", "Bearer " + apiKey)
                    .post(RequestBody.create(JSON, requestBody))
                    .build();
            
            Log.d(TAG, "发送LLM请求到: " + API_ENDPOINT);
            
            // 执行异步请求
            client.newCall(request).enqueue(new Callback() {
                @Override
                public void onFailure(Call call, IOException e) {
                    Log.e(TAG, "网络请求失败: " + e.getMessage(), e);
                    
                    // 根据错误类型返回更友好的错误消息
                    String errorMessage;
                    if (e instanceof java.net.UnknownHostException) {
                        errorMessage = "无法连接到API服务器，请检查您的网络连接";
                    } else if (e instanceof java.net.SocketTimeoutException) {
                        errorMessage = "连接超时，服务器响应时间过长";
                    } else if (e instanceof java.net.ConnectException) {
                        errorMessage = "连接被拒绝，无法建立连接";
                    } else {
                        errorMessage = "网络请求失败: " + e.getMessage();
                    }
                    
                    callback.onError(new IOException(errorMessage));
                }
                
                @Override
                public void onResponse(Call call, Response response) throws IOException {
                    try {
                        if (!response.isSuccessful()) {
                            String errorBody = response.body() != null ? response.body().string() : "无响应体";
                            Log.e(TAG, "API请求失败: " + response.code() + ", " + errorBody);
                            
                            // 根据HTTP状态码返回更友好的错误消息
                            String errorMessage;
                            switch (response.code()) {
                                case 401:
                                    errorMessage = "API密钥无效或已过期";
                                    break;
                                case 403:
                                    errorMessage = "API访问被拒绝，请检查权限";
                                    break;
                                case 429:
                                    errorMessage = "API请求次数超限，请稍后再试";
                                    break;
                                case 500:
                                case 502:
                                case 503:
                                case 504:
                                    errorMessage = "API服务器错误，请稍后再试";
                                    break;
                                default:
                                    errorMessage = "API请求失败: " + response.code() + " " + response.message();
                            }
                            
                            callback.onError(new IOException(errorMessage + "\n" + errorBody));
                            return;
                        }
                        
                        String responseBody = response.body().string();
                        Log.d(TAG, "API响应: " + responseBody);
                        
                        // 解析响应JSON
                        JSONObject jsonResponse = new JSONObject(responseBody);
                        
                        // 提取消息内容
                        String assistantMessage = extractMessageFromResponse(jsonResponse);
                        
                        // 保存助手回复到数据库
                        saveAssistantMessage(userId, assistantMessage, currentTaskId);
                        
                        // 返回结果
                        callback.onResponse(assistantMessage);
                    } catch (Exception e) {
                        Log.e(TAG, "解析响应时出错: " + e.getMessage(), e);
                        callback.onError(new Exception("无法解析AI助手的回复: " + e.getMessage()));
                    }
                }
            });
        } catch (Exception e) {
            Log.e(TAG, "构建LLM请求时出错: " + e.getMessage(), e);
            callback.onError(e);
        }
    }
    
    /**
     * 检查网络连接是否可用
     */
    private boolean isNetworkAvailable() {
        try {
            android.net.ConnectivityManager connectivityManager = 
                    (android.net.ConnectivityManager) appContext.getSystemService(Context.CONNECTIVITY_SERVICE);
            if (connectivityManager != null) {
                android.net.NetworkInfo activeNetworkInfo = connectivityManager.getActiveNetworkInfo();
                return activeNetworkInfo != null && activeNetworkInfo.isConnected();
            }
        } catch (Exception e) {
            Log.e(TAG, "检查网络状态时出错: " + e.getMessage(), e);
        }
        return false;
    }
    
    /**
     * 从DeepSeek API响应中提取消息内容
     */
    private String extractMessageFromResponse(JSONObject jsonResponse) throws JSONException {
        try {
            // 适配DeepSeek API的响应格式
            if (jsonResponse.has("choices") && jsonResponse.getJSONArray("choices").length() > 0) {
                JSONObject choice = jsonResponse.getJSONArray("choices").getJSONObject(0);
                
                if (choice.has("message") && choice.getJSONObject("message").has("content")) {
                    return choice.getJSONObject("message").getString("content");
                }
            }
            
            // 如果无法按预期格式解析，尝试查找任何可能的消息内容
            Log.w(TAG, "无法按标准格式解析响应，尝试其他方式");
            return jsonResponse.toString(2); // 返回整个JSON以便调试
            
        } catch (Exception e) {
            Log.e(TAG, "提取消息时出错: " + e.getMessage(), e);
            return "抱歉，我无法理解服务器的响应。";
        }
    }
    
    /**
     * 保存助手回复到数据库
     */
    private void saveAssistantMessage(String userId, String content, Integer taskId) {
        AppDatabase.databaseWriteExecutor.execute(() -> {
            ChatMessage message = new ChatMessage();
            message.userId = userId;
            message.role = "assistant";
            message.content = content;
            message.timestamp = System.currentTimeMillis();
            message.relatedTaskId = taskId;
            message.messageType = "normal";
            
            database.chatMessageDao().insert(message);
        });
    }
    
    /**
     * 清除用户上下文缓存
     * @param userId 用户ID
     */
    public void clearUserContext(String userId) {
        contextManager.clearUserContext(userId);
    }
    
    /**
     * LLM响应回调接口
     */
    public interface LLMResponseCallback {
        void onResponse(String response);
        void onError(Exception e);
    }
    
    /**
     * 直接发送请求到LLM，同步版本
     * @param prompt 完整的提示词
     * @return LLM响应
     */
    public String sendRequest(String prompt) throws IOException {
        // 构建简单消息
        List<Map<String, String>> messages = new ArrayList<>();
        Map<String, String> userMessage = new HashMap<>();
        userMessage.put("role", "user");
        userMessage.put("content", prompt);
        messages.add(userMessage);
        
        // 构建请求JSON
        JSONObject jsonBody = new JSONObject();
        try {
            jsonBody.put("model", modelName);
            
            // 添加消息数组
            JSONArray messagesArray = new JSONArray();
            for (Map<String, String> message : messages) {
                JSONObject messageObj = new JSONObject();
                messageObj.put("role", message.get("role"));
                messageObj.put("content", message.get("content"));
                messagesArray.put(messageObj);
            }
            jsonBody.put("messages", messagesArray);
            
            // 设置其他参数
            jsonBody.put("temperature", 0.7);
            jsonBody.put("max_tokens", 1000);
        } catch (JSONException e) {
            throw new IOException("构建请求JSON失败", e);
        }
        
        String requestBody = jsonBody.toString();
        
        // 创建请求
        Request request = new Request.Builder()
                .url(API_ENDPOINT)
                .addHeader("Content-Type", "application/json")
                .addHeader("Authorization", "Bearer " + apiKey)
                .post(RequestBody.create(JSON, requestBody))
                .build();
        
        // 执行同步请求
        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("请求失败: " + response.code() + " " + response.message());
            }
            
            String responseBody = response.body().string();
            JSONObject jsonResponse = new JSONObject(responseBody);
            return extractMessageFromResponse(jsonResponse);
        } catch (JSONException e) {
            throw new IOException("解析响应失败", e);
        }
    }
} 