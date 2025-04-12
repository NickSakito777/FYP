package com.example.lbsdemo.llm;

import android.content.Context;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

/**
 * 文本LLM管理器
 * 专门用于处理文本对话的LLM模型
 */
public class TextLLMManager {
    private static final String TAG = "TextLLMManager";
    private static TextLLMManager instance;
    private final ExecutorService executorService;
    private Context context;
    
    // API配置
    private static final String API_URL = ApiConfig.TEXT_LLM_API_URL;
    private static final String API_KEY = ApiConfig.TEXT_LLM_API_KEY;
    private static final String MODEL_NAME = ApiConfig.TEXT_LLM_MODEL_NAME;
    
    // 单例模式
    public static synchronized TextLLMManager getInstance(Context context) {
        if (instance == null) {
            instance = new TextLLMManager(context);
        }
        return instance;
    }
    
    private TextLLMManager(Context context) {
        this.context = context.getApplicationContext();
        this.executorService = Executors.newSingleThreadExecutor();
    }
    
    /**
     * 处理文本对话请求
     * @param userId 用户ID
     * @param message 用户消息
     * @param callback 回调接口
     */
    public void sendMessage(String userId, String message, TextLLMCallback callback) {
        executorService.execute(() -> {
            try {
                // 调用文本LLM API
                String response = callTextLLMAPI(message);
                
                // 回调成功结果
                if (callback != null) {
                    callback.onSuccess(response);
                }
            } catch (Exception e) {
                Log.e(TAG, "处理文本对话失败", e);
                if (callback != null) {
                    callback.onError(e);
                }
            }
        });
    }
    
    /**
     * 调用文本LLM API
     * @param message 用户消息
     * @return API响应
     */
    private String callTextLLMAPI(String message) throws IOException, JSONException {
        // 创建OkHttp客户端
        OkHttpClient client = new OkHttpClient();
        
        // 构建请求体JSON
        JSONObject requestJson = new JSONObject();
        requestJson.put("model", MODEL_NAME);
        
        // 构建消息数组
        JSONArray messagesArray = new JSONArray();
        
        // 添加用户消息
        JSONObject messageObject = new JSONObject();
        messageObject.put("role", "user");
        messageObject.put("content", message);
        messagesArray.put(messageObject);
        
        // 将消息数组添加到请求体
        requestJson.put("messages", messagesArray);
        
        // 创建请求
        RequestBody body = RequestBody.create(
            MediaType.parse("application/json"), requestJson.toString());
        
        Request request = new Request.Builder()
            .url(API_URL)
            .addHeader("Authorization", "Bearer " + API_KEY)
            .addHeader("Content-Type", "application/json")
            .post(body)
            .build();
        
        // 发送请求并获取响应
        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("API调用失败: " + response.code() + " " + response.message());
            }
            
            // 解析响应
            String responseBody = response.body().string();
            JSONObject responseJson = new JSONObject(responseBody);
            
            // 提取回复内容
            JSONArray choices = responseJson.getJSONArray("choices");
            JSONObject choice = choices.getJSONObject(0);
            JSONObject responseMessage = choice.getJSONObject("message");
            String content = responseMessage.getString("content");
            
            return content;
        } catch (Exception e) {
            Log.e(TAG, "API调用异常", e);
            // 如果API调用失败，返回模拟响应
            return "很抱歉，对话服务暂时不可用。请稍后再试。";
        }
    }
    
    /**
     * 文本LLM回调接口
     */
    public interface TextLLMCallback {
        /**
         * 成功回调
         * @param response 分析结果
         */
        void onSuccess(String response);
        
        /**
         * 错误回调
         * @param e 异常
         */
        void onError(Exception e);
    }

    /**
     * 生成照片验证任务的描述和提示词
     * 使用LLM生成任务文本和验证提示词
     * @param callback 回调接口，返回任务描述和验证提示词
     */
    public void generatePhotoVerificationTaskText(PhotoVerificationTextCallback callback) {
        executorService.execute(() -> {
            try {
                // 创建提示词，指导LLM生成任务描述和验证提示词
                String prompt = "请生成一个照片验证任务的描述和验证提示词。\n" +
                        "任务应该是让学生拍摄一张照片进行验证。请根据用户的偏好和兴趣，随机生成一个有创意的任务。\n" +
                        "可以是以下类型的任务之一（请随机选择）：\n" +
                        "1. 学习环境照片：拍摄学习场所、桌面、书本等\n" +
                        "2. 学习成果照片：拍摄笔记、作业、项目等\n" +
                        "3. 校园环境照片：拍摄校园建筑、风景等\n" +
                        "4. 创意学习照片：用创意方式展示学习内容\n" +
                        "5. 其他你认为适合的照片验证主题\n\n" +
                        "验证提示词需要具体说明照片应该包含什么内容，以便验证算法能够判断照片是否符合要求。\n" +
                        "请使纯粹随机的方式选择任务类型，不要遵循固定模式，每次生成的任务应该有所不同。\n" +
                        "要求返回JSON格式，包含以下字段：\n" +
                        "1. title: 任务标题\n" +
                        "2. description: 任务描述\n" +
                        "3. verification_prompt: 验证照片用的提示词\n" +
                        "JSON示例格式如下：\n" +
                        "{\n" +
                        "  \"title\": \"任务标题\",\n" +
                        "  \"description\": \"任务描述\",\n" +
                        "  \"verification_prompt\": \"验证提示词\"\n" +
                        "}";
                
                // 调用LLM API生成文本
                String response = callTextLLMAPI(prompt);
                
                try {
                    // 解析JSON响应
                    // 查找JSON部分（如果响应中包含其他内容）
                    String jsonText = extractJsonFromText(response);
                    JSONObject json = new JSONObject(jsonText);
                    
                    // 提取任务文本
                    String title = json.getString("title");
                    String description = json.getString("description");
                    String verificationPrompt = json.getString("verification_prompt");
                    
                    // 回调成功结果
                    if (callback != null) {
                        callback.onSuccess(title, description, verificationPrompt);
                    }
                } catch (JSONException e) {
                    Log.e(TAG, "解析LLM响应JSON失败", e);
                    // 使用默认文本作为回退
                    if (callback != null) {
                        callback.onSuccess(
                            "照片验证测试任务",
                            "这是一个测试照片验证功能的任务。请拍摄一张照片来验证。",
                            "请拍摄一张能够证明你正在完成测试任务的照片。可以是你的学习环境、正在使用的学习材料，或者正在进行的学习活动。请确保照片清晰，并能够展示与任务相关的内容。"
                        );
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "生成照片验证任务文本失败", e);
                if (callback != null) {
                    callback.onError(e);
                }
            }
        });
    }

    /**
     * 从文本中提取JSON部分
     * @param text 包含JSON的文本
     * @return JSON字符串
     */
    private String extractJsonFromText(String text) {
        // 查找第一个{和最后一个}之间的内容
        int start = text.indexOf('{');
        int end = text.lastIndexOf('}');
        
        if (start >= 0 && end > start) {
            return text.substring(start, end + 1);
        }
        
        // 如果找不到JSON，返回原文本
        return text;
    }

    /**
     * 照片验证文本回调接口
     */
    public interface PhotoVerificationTextCallback {
        /**
         * 成功回调
         * @param title 任务标题
         * @param description 任务描述
         * @param verificationPrompt 验证提示词
         */
        void onSuccess(String title, String description, String verificationPrompt);
        
        /**
         * 错误回调
         * @param e 异常
         */
        void onError(Exception e);
    }
} 