package com.example.lbsdemo.map;

import android.content.Context;
import android.content.Intent;
import android.graphics.PixelFormat;
import android.os.Build;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.WindowManager;

import com.example.lbsdemo.R;
import com.example.lbsdemo.view.CartoonMapView;

import java.util.HashMap;

/*此乃地图的第三层的悬浮窗实现，具体功能如下：

1. 单例模式：确保只有一个 FloatWindowManager，确保浮动窗口的视线。
2. 显示浮动窗口：加载布局并显示浮动窗口。
3. 拖动浮动窗口：通过触摸事件实现窗口的拖动功能。
4. 点击事件：若未移动浮动窗口，则触发点击事件。
6. 版本兼容性：根据 Android 版本选择合适的窗口类型。
*/

public class FloatWindowManager {
    private static FloatWindowManager instance = new FloatWindowManager();
    private final WindowManager windowManager;
    private final LayoutInflater inflater;
    private View toolView;
    private HashMap<View,String> toolViewMap = new HashMap<>();
    private WindowManager.LayoutParams layoutParams;
    private View.OnClickListener onClickListener;

    public static FloatWindowManager get() {
        return instance;
    }

    private FloatWindowManager() {
        inflater = LayoutInflater.from(BaiduInitialization.application);
        windowManager = (WindowManager) BaiduInitialization.application.getSystemService(Context.WINDOW_SERVICE);
    }

    public void showToolView(String key, View.OnClickListener onClickListener) {
        this.onClickListener  = onClickListener;
        if (toolView != null) {
            toolViewMap.put(toolView,key);
            return;
        }
        toolView = inflater.inflate(R.layout.float_window_tool_view, null, false);
        toolViewMap.put(toolView,key);
        toolView.setOnTouchListener(new View.OnTouchListener() {
            private float lastX, lastY;
            private boolean isMove;

            @Override
            public boolean onTouch(View v, MotionEvent event) {
                if (toolView == null) {
                    return false;
                }
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        int[] location = new int[2];
                        toolView.getLocationOnScreen(location);
                        layoutParams.x = location[0];
                        layoutParams.y = location[1] - BaiduInitialization.getStatusBarHeight();
                        lastX = event.getRawX();
                        lastY = event.getRawY();
                        isMove = false;
                        return true;
                    case MotionEvent.ACTION_MOVE:
                        float rawX = event.getRawX();
                        float rawY = event.getRawY();
                        float distanceX = (rawX - lastX);
                        float distanceY = (rawY - lastY);
                        if (Math.abs(distanceX) > ViewConfiguration.get(BaiduInitialization.application).getScaledTouchSlop() ||
                                Math.abs(distanceY) > ViewConfiguration.get(BaiduInitialization.application).getScaledTouchSlop()
                        ) {
                            lastX = rawX;
                            lastY = rawY;
                            layoutParams.x += (int) distanceX;
                            layoutParams.y += (int) distanceY;
                            layoutParams.gravity = Gravity.TOP | Gravity.START;
                            windowManager.updateViewLayout(toolView, layoutParams);
                            isMove = true;
                        }
                        return true;
                    case MotionEvent.ACTION_UP:
                        if (!isMove && FloatWindowManager.this.onClickListener != null) {
                            FloatWindowManager.this.onClickListener.onClick(toolView);
                        }
                        return true;
                    default:
                        return false;
                }
            }
        });
        layoutParams = new WindowManager.LayoutParams();
        layoutParams.width = WindowManager.LayoutParams.WRAP_CONTENT;
        layoutParams.height = WindowManager.LayoutParams.WRAP_CONTENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            layoutParams.type = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY;
        } else {
            layoutParams.type = WindowManager.LayoutParams.TYPE_PHONE;
        }
        layoutParams.format = PixelFormat.TRANSLUCENT;
        layoutParams.flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE;
        layoutParams.gravity = Gravity.START | Gravity.CENTER_VERTICAL;
        windowManager.addView(toolView, layoutParams);
    }

    public void hindToolView(String key) {
        if (toolView != null && toolViewMap.get(toolView).equals(key)) {
            windowManager.removeView(toolView);
            toolView.setOnTouchListener(null);
            toolView = null;
        }
    }
    public void showNavigationWindow(String viewTag, Context context) {
        showToolView(viewTag, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                dismiss();
                Intent intent = new Intent(context, CartoonMapView.class); // 参照资料3声明[^3]
                context.startActivity(intent);
            }
        });
    }
    public View getToolView() {
        return toolView;
    }
}
