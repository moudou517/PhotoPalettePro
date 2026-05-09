package com.example.photopalettepro.helper;

import android.animation.ValueAnimator;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import android.os.Vibrator;
import android.view.MotionEvent;
import android.widget.TextView;
import android.widget.Toast;

import com.example.photopalettepro.AboutActivity;

/**
 * 标题长按助手类
 * 职责：处理标题栏的长按检测、动画、振动反馈和页面跳转
 */
public class TitleLongPressHelper {

    private static final long LONG_PRESS_DURATION = 5000; // 5秒长按阈值
    
    private final Context context;
    private final TextView tvAppTitle;
    private final Vibrator vibrator;
    private final Handler longPressHandler;
    
    private ValueAnimator scaleAnimator;
    private long pressStartTime = 0;
    private boolean isPressing = false;
    private boolean hasJumped = false;
    private Runnable longPressRunnable;

    public TitleLongPressHelper(Context context, TextView tvAppTitle) {
        this.context = context;
        this.tvAppTitle = tvAppTitle;
        this.vibrator = (Vibrator) context.getSystemService(Context.VIBRATOR_SERVICE);
        this.longPressHandler = new Handler(Looper.getMainLooper());
    }

    public void setup() {
        if (tvAppTitle == null) return;

        tvAppTitle.post(() -> {
            tvAppTitle.setOnTouchListener((v, event) -> handleTouchEvent(v, event));
        });
    }

    private boolean handleTouchEvent(android.view.View v, MotionEvent event) {
        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                onPressDown(v);
                break;
            case MotionEvent.ACTION_MOVE:
                onPressMove();
                break;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                onPressUp(v);
                break;
        }
        return true;
    }

    private void onPressDown(android.view.View v) {
        pressStartTime = System.currentTimeMillis();
        isPressing = true;
        hasJumped = false;
        
        // 启动缩放动画
        startScaleAnimation(0.96f, 0.90f, 0.96f);
        
        // 初始震动
        vibrateIfAvailable(150);
        
        // 启动长按检测
        startLongPressDetection(v);
    }

    private void onPressMove() {
        if (!isPressing) return;
        
        long elapsed = System.currentTimeMillis() - pressStartTime;
        
        // 提供渐进式的反馈
        if (elapsed >= 1000 && elapsed < 1100) {
            vibrateIfAvailable(80);
        } else if (elapsed >= 2000 && elapsed < 2100) {
            vibrateIfAvailable(80);
        } else if (elapsed >= 3000 && elapsed < 3100) {
            vibrateIfAvailable(100);
        } else if (elapsed >= 4000 && elapsed < 4100) {
            vibrateIfAvailable(120);
        }
    }

    private void onPressUp(android.view.View v) {
        isPressing = false;
        
        // 清除长按检测
        if (longPressHandler != null && longPressRunnable != null) {
            longPressHandler.removeCallbacks(longPressRunnable);
        }
        
        // 停止动画
        if (scaleAnimator != null && scaleAnimator.isRunning()) {
            scaleAnimator.cancel();
        }
        
        // 恢复缩放
        v.animate().scaleX(1.0f).scaleY(1.0f).setDuration(150).start();
        
        // 反馈
        if (!hasJumped) {
            vibrateIfAvailable(100);
        }
        
        // 轻点提示
        if (!hasJumped && (System.currentTimeMillis() - pressStartTime) < LONG_PRESS_DURATION) {
            showToast("长按有惊喜");
        }
    }

    private void startLongPressDetection(android.view.View v) {
        longPressRunnable = new Runnable() {
            @Override
            public void run() {
                if (isPressing && !hasJumped) {
                    long elapsed = System.currentTimeMillis() - pressStartTime;
                    if (elapsed >= LONG_PRESS_DURATION) {
                        performJump(v);
                    } else {
                        longPressHandler.postDelayed(this, 100);
                    }
                }
            }
        };
        longPressHandler.postDelayed(longPressRunnable, LONG_PRESS_DURATION);
    }

    private void performJump(android.view.View v) {
        hasJumped = true;
        
        // 停止动画
        if (scaleAnimator != null && scaleAnimator.isRunning()) {
            scaleAnimator.cancel();
        }
        
        // 恢复缩放
        v.animate().scaleX(1.0f).scaleY(1.0f).setDuration(100).start();
        
        // 最终反馈
        vibrateIfAvailable(250);
        
        // 跳转到 About 页面
        try {
            Intent intent = new Intent(context, AboutActivity.class);
            context.startActivity(intent);
        } catch (Exception e) {
            e.printStackTrace();
            showToast("跳转失败: " + e.getMessage());
        }
    }

    private void startScaleAnimation(float start, float mid, float end) {
        if (scaleAnimator != null && scaleAnimator.isRunning()) {
            scaleAnimator.cancel();
        }
        scaleAnimator = ValueAnimator.ofFloat(start, mid, end);
        scaleAnimator.setDuration(1200);
        scaleAnimator.addUpdateListener(animation -> {
            float value = (float) animation.getAnimatedValue();
            tvAppTitle.setScaleX(value);
            tvAppTitle.setScaleY(value);
        });
        scaleAnimator.start();
    }

    private void vibrateIfAvailable(long duration) {
        if (vibrator == null) return;
        
        try {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                vibrator.vibrate(android.os.VibrationEffect.createOneShot(duration, 
                        android.os.VibrationEffect.DEFAULT_AMPLITUDE));
            } else {
                vibrator.vibrate(duration);
            }
        } catch (Exception e) {
            // 忽略震动错误
        }
    }

    private void showToast(String message) {
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show();
    }

    public void cleanup() {
        if (longPressHandler != null && longPressRunnable != null) {
            longPressHandler.removeCallbacks(longPressRunnable);
        }
        if (scaleAnimator != null && scaleAnimator.isRunning()) {
            scaleAnimator.cancel();
        }
    }
}

