package com.example.lbsdemo.llm;

import android.content.Context;
import android.util.Log;

/**
 * LLM管理器类
 * 作为应用层使用LLM服务的集中管理类，简化接口
 */
public class LLMManager {
    private static final String TAG = "LLMManager";
    
    private static volatile LLMManager INSTANCE;
    
    private final LLMService llmService;
    private boolean isInitialized = false;
    
    /**
     * 获取单例实例
     */
    public static LLMManager getInstance(Context context) {
        if (INSTANCE == null) {
            synchronized (LLMManager.class) {
                if (INSTANCE == null) {
                    INSTANCE = new LLMManager(context);
                }
            }
        }
        return INSTANCE;
    }
    
    /**
     * 私有构造函数
     */
    private LLMManager(Context context) {
        llmService = new LLMService(context);
    }
    
    /**
     * 初始化LLM服务
     * @param apiKey API密钥，如果为null则使用默认密钥
     * @param modelName 模型名称，如果为null则使用默认模型
     */
    public void initialize(String apiKey, String modelName) {
        // 添加日志记录初始化过程
        Log.d(TAG, "正在初始化LLM服务...");
        
        // 如果apiKey为null，使用默认值
        if (apiKey == null) {
            apiKey = "sk-sduizqafbjaplqgckedfadixcpgcazmtmaqvbmheoqprmmak"; // 使用默认API密钥
            Log.d(TAG, "使用默认API密钥");
        }
        
        // 如果modelName为null，使用默认值
        if (modelName == null) {
            modelName = "deepseek-ai/DeepSeek-V3"; // 使用默认模型
            Log.d(TAG, "使用默认模型: " + modelName);
        }
        
        llmService.configure(apiKey, modelName);
        isInitialized = true;
        Log.d(TAG, "LLM服务已初始化完成，使用模型：" + modelName);
    }
    
    /**
     * 发送消息到LLM
     * @param userId 用户ID
     * @param message 用户消息
     * @param currentTaskId 当前任务ID
     * @param callback 回调函数
     */
    public void sendMessage(String userId, String message, Integer currentTaskId, LLMResponseCallback callback) {
        Log.d(TAG, "准备发送消息到LLM，用户ID: " + userId);
        
        // 检查是否初始化
        if (!isInitialized) {
            Log.e(TAG, "LLM服务未初始化，正在尝试使用默认配置初始化");
            initialize(null, null); // 使用默认配置初始化
        }
        
        llmService.sendMessage(userId, message, currentTaskId, new LLMService.LLMResponseCallback() {
            @Override
            public void onResponse(String response) {
                Log.d(TAG, "收到LLM响应: " + response.substring(0, Math.min(50, response.length())) + "...");
                if (callback != null) {
                    callback.onResponse(response);
                }
            }
            
            @Override
            public void onError(Exception e) {
                Log.e(TAG, "LLM请求失败: " + e.getMessage(), e);
                if (callback != null) {
                    callback.onError(e);
                }
            }
        });
    }
    
    /**
     * 清除用户上下文
     * @param userId 用户ID
     */
    public void clearUserContext(String userId) {
        llmService.clearUserContext(userId);
    }
    
    /**
     * LLM响应回调接口
     */
    public interface LLMResponseCallback {
        void onResponse(String response);
        void onError(Exception e);
    }
    
    /**
     * 使用示例
     * 
     * LLMManager llmManager = LLMManager.getInstance(context);
     * 
     * // 初始化（通常在应用启动时或从配置中加载API密钥）
     * llmManager.initialize("your-api-key", "gpt-3.5-turbo");
     * 
     * // 发送消息
     * llmManager.sendMessage(
     *     "user123",
     *     "我需要完成今天的任务，请给我一些建议",
     *     currentTaskId,
     *     new LLMManager.LLMResponseCallback() {
     *         @Override
     *         public void onResponse(String response) {
     *             // 处理LLM返回的消息
     *             displayMessage(response);
     *         }
     *         
     *         @Override
     *         public void onError(Exception e) {
     *             // 处理错误
     *             showErrorMessage(e.getMessage());
     *         }
     *     }
     * );
     */
} 