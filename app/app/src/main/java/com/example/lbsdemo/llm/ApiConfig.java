package com.example.lbsdemo.llm;

/**
 * API配置类
 * 用于存储API密钥和URL等配置信息
 */
public class ApiConfig {
    // 文本LLM模型API配置
    public static final String TEXT_LLM_API_URL = "https://api.siliconflow.cn/v1/chat/completions";
    public static final String TEXT_LLM_API_KEY = "sk-sduizqafbjaplqgckedfadixcpgcazmtmaqvbmheoqprmmak"; // TODO: 替换为实际的API密钥
    public static final String TEXT_LLM_MODEL_NAME = "deepseek-ai/DeepSeek-V2.5";
    
    // 视觉LLM模型API配置
    public static final String VISION_LLM_API_URL = "https://api.siliconflow.cn/v1/chat/completions";
    public static final String VISION_LLM_API_KEY = "sk-sduizqafbjaplqgckedfadixcpgcazmtmaqvbmheoqprmmak"; // TODO: 替换为实际的API密钥
    public static final String VISION_LLM_MODEL_NAME = "deepseek-ai/deepseek-vl2";
}