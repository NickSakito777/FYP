package com.example.lbsdemo.activity;

import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import com.example.lbsdemo.R;
import com.example.lbsdemo.map.FloatWindowManager;

public class WebViewActivity extends AppCompatActivity {
    private static final String TAG = "WebViewActivity";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_web_view);
        
        // 隐藏浮动窗口
        FloatWindowManager.get().hideToolView();

        // 设置Toolbar
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        getSupportActionBar().setTitle("西浦码");

        // 设置返回按钮点击事件
        toolbar.setNavigationOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                onBackPressed();
            }
        });

        // 设置WebView
        WebView webView = findViewById(R.id.webView);
        
        // 增强WebView配置
        WebSettings webSettings = webView.getSettings();
        webSettings.setJavaScriptEnabled(true);
        webSettings.setDomStorageEnabled(true);
        webSettings.setAllowFileAccess(true);
        webSettings.setJavaScriptCanOpenWindowsAutomatically(true);
        webSettings.setLoadsImagesAutomatically(true);
        webSettings.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
        webSettings.setCacheMode(WebSettings.LOAD_NO_CACHE);
        
        // 设置UserAgent
        String userAgent = webSettings.getUserAgentString();
        webSettings.setUserAgentString(userAgent + " AndroidApp");
        
        // 添加WebViewClient来处理页面加载
        webView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                Log.d(TAG, "页面加载完成: " + url);
            }
            
            @Override
            public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
                super.onReceivedError(view, request, error);
                Log.e(TAG, "页面加载错误: " + request.getUrl());
                Toast.makeText(WebViewActivity.this, "页面加载失败，请检查网络连接", Toast.LENGTH_SHORT).show();
            }
            
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                String url = request.getUrl().toString();
                Log.d(TAG, "shouldOverrideUrlLoading: " + url);

                if (url.startsWith("http://") || url.startsWith("https://")) {
                    // Standard HTTP/HTTPS links, load in WebView
                    view.loadUrl(url);
                    return true;
                } else {
                    // Try to handle other schemes (like custom schemes or intents) with an Intent
                    try {
                        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                        startActivity(intent);
                        return true;
                    } catch (ActivityNotFoundException e) {
                        Log.e(TAG, "No activity found to handle URL: " + url);
                        // Optionally, show a toast to the user
                        Toast.makeText(WebViewActivity.this, "无法打开此链接", Toast.LENGTH_SHORT).show();
                        return true; // Indicate that we've handled the URL (by trying and failing)
                    }
                }
            }
        });
        
        // 加载网页
        String url = "https://eid.xjtlu.edu.cn/f1-space/web/wechat.html#/login"; // 修改为西浦 e-hall 链接
        Log.d(TAG, "开始加载URL: " + url);
        webView.loadUrl(url);
    }

    @Override
    public void onBackPressed() {
        WebView webView = findViewById(R.id.webView);
        if (webView.canGoBack()) {
            webView.goBack();
        } else {
            finish();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // 恢复浮动窗口的可见性
        FloatWindowManager.get().visibleToolView();
    }
} 