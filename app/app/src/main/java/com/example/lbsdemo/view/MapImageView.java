package com.example.lbsdemo.view;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.util.Log;
import android.view.MotionEvent;
import android.widget.Toast;

import androidx.appcompat.widget.AppCompatImageView;

import com.example.lbsdemo.R;

import java.util.ArrayList;
import java.util.List;

/**
 用于显示地图并管理用户位置和标记。
 用于接收MapT所计算的地图的像素坐标系，来绘制圆点和marker
 */
public class    MapImageView extends AppCompatImageView {

    private static final String TAG = "MapImageView";

    // 用户当前位置的坐标（像素）
    private float userX = -1;
    private float userY = -1;

    // 标记列表
    private List<CustomMarker> marks = new ArrayList<>();

    private Paint paint;

    private int imageViewWidth;
    private int imageViewHeight;
    private int imageWidth;
    private int imageHeight;

    private float density;

    // 调整后的坐标
    private float adjustedX = -1;
    private float adjustedY = -1;

    // 当前缩放和位移
    private float currentScale = 1f;
    private float currentTranslateX = 0f;
    private float currentTranslateY = 0f;

    // 地图和标记图片的宽高
    private int bwidth;
    private int bheight;
    private int markeWidth;
    private int markeHeight;
    private Bitmap markBitmap;

    // 标记点击回调接口
    private OnMarkerClickListener markerClickListener;
    private PositionUpdateListener positionListener;

    public List<CustomMarker> getMarks() {
        return marks;
    }


    public class CustomMarker {
        float x;      // 标记的x坐标（像素）
        float y;      // 标记的y坐标（像素）
        public String id;    // 标记的唯一标识符

        CustomMarker(float x, float y, String id) {
            this.x = x;
            this.y = y;
            this.id = id;
        }
    }


    public MapImageView(Context context) {
        super(context);
        init(context);
    }

    public MapImageView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    public MapImageView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context);
    }

    /**
     * 初始化方法，设置画笔和标记图片。
     */
    private void init(Context context) {
        paint = new Paint();
        paint.setColor(Color.RED);
        paint.setStyle(Paint.Style.FILL);

        density = context.getResources().getDisplayMetrics().density;

        // 加载标记图片
        markBitmap = BitmapFactory.decodeResource(getResources(), R.mipmap.marker);
        markeWidth = markBitmap.getWidth();
        markeHeight = markBitmap.getHeight();
    }

    // 设置用户当前位置。
    public interface PositionUpdateListener {
        void onPositionChanged(float x, float y);
    }

    public void setPositionUpdateListener(PositionUpdateListener listener) {
        this.positionListener = listener;
    }

    public void setUserPosition(float x, float y) {
        this.userX = x;
        this.userY = y;
        invalidate(); // 触发重绘
    }

    //添加一个标记
    public void addMark(float x, float y, String id) {
        this.marks.add(new CustomMarker(x, y, id));
        invalidate(); // 触发重绘
    }

    // 获取调整后的x坐标。

    public float getAdjustedX() {
        return adjustedX;
    }

    // 获取调整后的y坐标。

    public float getAdjustedY() {
        return adjustedY;
    }

    //监听器
    public void setOnMarkerClickListener(OnMarkerClickListener listener) {
        this.markerClickListener = listener;
    }

    //视图大小改变时调用，获取ImageView和地图图片的尺寸。
    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        Log.d(TAG, "onSizeChanged: w=" + w + ", h=" + h);
        super.onSizeChanged(w, h, oldw, oldh);

        imageViewWidth = w;
        imageViewHeight = h;

        Drawable drawable = getDrawable();
        if (drawable instanceof BitmapDrawable) {
            Bitmap bitmap = ((BitmapDrawable) drawable).getBitmap();
            imageWidth = bitmap.getWidth();
            imageHeight = bitmap.getHeight();
            Log.d(TAG, "地图图片尺寸: imageWidth=" + imageWidth + ", imageHeight=" + imageHeight);
        } else {
            Log.e(TAG, "Drawable不是BitmapDrawable");
        }

        // 加载另一张地图图片用于其他用途（根据您的需求）
        Bitmap bitmap = BitmapFactory.decodeResource(getResources(), R.mipmap.img2);
        bwidth = bitmap.getWidth();
        bheight = bitmap.getHeight();
        Log.d(TAG, "img2尺寸: bwidth=" + bwidth + ", bheight=" + bheight);
    }

    //绘制视图
    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        drawUser(canvas);
        drawMarkers(canvas);
    }


    //绘制用户当前位置，用圆点来表示
    private void drawUser(Canvas canvas) {
        if (userX >= 0 && userY >= 0 && imageWidth > 0 && imageHeight > 0) {
            float imageX = userX * imageViewWidth / imageWidth;
            float imageY = userY * imageViewHeight / imageHeight;
            adjustedX = imageX;
            adjustedY = imageY;
            canvas.drawCircle(imageX, imageY, 4 * density, paint);
            Log.d(TAG, "绘制用户位置: (" + imageX + ", " + imageY + ")");
            if (positionListener != null) {
                positionListener.onPositionChanged(imageX, imageY);
            }
        }
    }

    //绘制所有标记
    private void drawMarkers(Canvas canvas) {
        for (CustomMarker marker : marks) {
            if (marker.x >= 0 && marker.y >= 0 && imageWidth > 0 && imageHeight > 0) {
                float imageX = marker.x * imageViewWidth / imageWidth;
                float imageY = marker.y * imageViewHeight / imageHeight;
                canvas.drawBitmap(markBitmap, imageX - markeWidth / 2, imageY - markeHeight / 2, paint);
                Log.d(TAG, "绘制标记: ID=" + marker.id + " 位置=(" + imageX + ", " + imageY + ")");
            }
        }
    }

   //设置触摸事件，对于按下指定的那三个地点来说
    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (event.getAction() == MotionEvent.ACTION_DOWN) {
            float touchX = event.getX();
            float touchY = event.getY();
            Log.d(TAG, "触摸位置: (" + touchX + ", " + touchY + ")");

            for (CustomMarker marker : marks) {
                float imageX = marker.x * imageViewWidth / imageWidth;
                float imageY = marker.y * imageViewHeight / imageHeight;

                float radius = markeWidth / 2;

                float dx = touchX - imageX;
                float dy = touchY - imageY;
                float distance = (float) Math.sqrt(dx * dx + dy * dy);

                if (distance <= radius) {
                    // 标记被点击
                    Log.d(TAG, "标记被点击: ID=" + marker.id);
                    handleMarkClick(marker);
                    return true; // 事件已处理
                }
            }
        }
        return super.onTouchEvent(event);
    }

    //处理按下事件
    private void handleMarkClick(CustomMarker marker) {
        if (markerClickListener != null) {
            markerClickListener.onMarkerClick(marker);
        } else {
            // 默认行为
            Toast.makeText(getContext(), "点击了标记: " + marker.id, Toast.LENGTH_SHORT).show();
            Log.d(TAG, "默认点击行为: 标记ID=" + marker.id);
        }
    }

    public interface OnMarkerClickListener {
        void onMarkerClick(CustomMarker marker);
    }
}
