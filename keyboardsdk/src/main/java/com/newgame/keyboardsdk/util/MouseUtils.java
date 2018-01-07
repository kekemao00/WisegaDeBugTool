package com.newgame.keyboardsdk.util;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.PixelFormat;
import android.os.Build;
import android.os.Handler;
import android.os.Message;
import android.view.View;
import android.view.WindowManager;

import com.newgame.keyboardsdk.R;
import com.newgame.keyboardsdk.widget.MouseView;

import java.util.Timer;
import java.util.TimerTask;

import static android.content.Context.WINDOW_SERVICE;

/**
 * 鼠标工具类，通过悬浮窗口的方式实现虚拟鼠标功能，封装了鼠标的相关操作，如开启、关闭、隐藏、移动等。
 *
 * @author 李剑波
 * @date 17/3/16
 */

public class MouseUtils {

    /**
     * 默认单次移动步长（单位：像素）
     */
    public static final int DEFAULT_MOVE_STEP = 15;
    /**
     * 默认移动频率（单位：毫秒）
     */
    public static final int DEFAULT_MOVE_FREQUENCY = 20;
    /**
     * 自动隐藏鼠标的延迟时间
     */
    public static final int TIME_AUTO_DISMISS_MOUSE_DELAY = 5000;
    /**
     * 消息-自动隐藏鼠标
     */
    public static final int MSG_AUTO_DISMISS_MOUSE = 1;
    /**
     * 鼠标是否开启
     */
    public static boolean isMouseViewAadded;
    /**
     * 鼠标控件
     */
    private static MouseView mMouseView;
    private static WindowManager mWindowManager;
    private static WindowManager.LayoutParams mLayoutParams;
    /**
     * 状态栏高度
     */
    private static int mStatusBarHight = -1;
    /**
     * 摇杆x值
     */
    private static float mAxisX;
    /**
     * 摇杆y值
     */
    private static float mAxisY;
    /**
     * 定时器，用于实现鼠标移动功能
     */
    private static Timer mTimer = new Timer();
    /**
     * 定时任务，用于实现鼠标移动功能
     */
    private static TimerTask mTimerTask;
    private static Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case MSG_AUTO_DISMISS_MOUSE:
//                    mMouseView.setVisibility(View.GONE);
                    break;
                default:
                    super.handleMessage(msg);
                    break;
            }
        }
    };

    /**
     * 获取鼠标图片资源
     */
    private static Bitmap getMouseBitmap(Resources res) {
        return BitmapFactory.decodeResource(res, R.mipmap.pointer_arrow);
    }

    /**
     * 开启鼠标
     */
    public static void addMouse(Context context) {
        if (isMouseViewAadded) {
            return;
        }

        if (mMouseView == null) {
            mMouseView = new MouseView(context.getApplicationContext(), getMouseBitmap(context.getResources()));
        }
        if (mWindowManager == null) {
            mWindowManager = (WindowManager) context.getApplicationContext().getSystemService(WINDOW_SERVICE);
        }
        if (mLayoutParams == null) {
            mLayoutParams = new WindowManager.LayoutParams();
//            mLayoutParams.type = WindowManager.LayoutParams.TYPE_TOAST;
            //2017-12-23 Android 8.0 悬浮窗适配
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
//            Log.e("mk", "getParams:  into  Build :8.0");
                mLayoutParams.type = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY;
            } else {
//            Log.e("mk", "getParams:  Not into  Build :8.0");
                mLayoutParams.type = WindowManager.LayoutParams.TYPE_SYSTEM_ERROR;
            }
//            mLayoutParams.type = WindowManager.LayoutParams.TYPE_SYSTEM_ERROR;
            // 不响应按键事件和触屏事件**********
            mLayoutParams.flags = WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                    | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                    | WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE;
            // 默认格式会导致重影，所以需要设置为其他格式
            mLayoutParams.format = PixelFormat.RGBA_8888;
            mLayoutParams.width = WindowManager.LayoutParams.MATCH_PARENT;
            mLayoutParams.height = WindowManager.LayoutParams.MATCH_PARENT;
        }
        if (mStatusBarHight < 0) {
            mStatusBarHight = getStatusBarHight(context.getResources());
        }
        try {
            mWindowManager.addView(mMouseView, mLayoutParams);
        } catch (Exception e) {
            e.printStackTrace();
        }
        startTimer();
        isMouseViewAadded = true;
//        Toast.makeText(context, context.getText(R.string.mouse_added), Toast.LENGTH_SHORT).show();
    }

    /**
     * 关闭鼠标
     */
    public static void removeMouse(Context context) {
        if (!isMouseAdded()) {
            return;
        }

        stopTimer();
        try {
            mWindowManager.removeView(mMouseView);
        } catch (Exception e) {
            e.printStackTrace();
        }
        isMouseViewAadded = false;
//        Toast.makeText(context, context.getText(R.string.mouse_removed), Toast.LENGTH_SHORT).show();
    }

    /**
     * 鼠标是否开启
     */
    public static boolean isMouseAdded() {
        return isMouseViewAadded;
    }

    /**
     * 开始移动，定时读取摇杆的参数（值域：-1.0~1.0）进行移动，每次移动的距离为：步长 * 摇杆参数
     */
    private static void startTimer() {
        // 重置鼠标的位置（居中显示）
        mMouseView.start();
        // 重置摇杆参数
        setAxis(0, 0);
        // 定时任务为一次性任务，所以需要重新创建定时任务
        mTimerTask = new TimerTask() {
            @Override
            public void run() {
                // 移动鼠标
                mMouseView.move(DEFAULT_MOVE_STEP * mAxisX, DEFAULT_MOVE_STEP * mAxisY);
            }
        };
        // 鼠标第一次添加时无法获取到控件的宽高，需要在onDraw中进行修正，因此需要延迟足够的时间后再执行定时任务，
        // 否则鼠标的第一次move会修改鼠标的坐标值，导致鼠标无法居中显示而是显示在左上角（0，0）
        mTimer.schedule(mTimerTask, 200, DEFAULT_MOVE_FREQUENCY);
    }

    /**
     * 停止移动
     */
    private static void stopTimer() {
        mTimerTask.cancel();
    }

    /**
     * 设置摇杆参数
     *
     * @param x 摇杆的x值
     * @param y 摇杆的y值
     */
    public static void setAxis(float x, float y) {
        mAxisX = x;
        mAxisY = y;
        // 长时间不操作鼠标时，需主动隐藏鼠标，否则有些界面切换会黑屏（如微信扫码完成后回到游戏）
        mMouseView.setVisibility(View.VISIBLE);
        mHandler.removeMessages(MSG_AUTO_DISMISS_MOUSE);
        mHandler.sendEmptyMessageDelayed(MSG_AUTO_DISMISS_MOUSE, TIME_AUTO_DISMISS_MOUSE_DELAY);
    }

    /**
     * 移动鼠标
     *
     * @param x
     * @param y
     */
    public static void move(int x, int y) {
        mMouseView.move(x, y);
    }

    /**
     * 获取鼠标的x坐标
     */
    public static float getPointerX() {
        return mMouseView.getPointerX();
    }

    /**
     * 获取鼠标的y坐标
     */
    public static float getPointerY() {
        float y = mMouseView.getPointerY();
        // 手机和平板有状态栏，电视和机顶盒没有，因为悬浮窗口的绘制区域不包含状态栏，所以有状态栏时y坐标需要加上状态栏的高度
//        if (!DeviceUtils.isTv(mMouseView.getContext())) {
//            y += mStatusBarHight;
//        }
        return y;
    }

    /**
     * 通过获取系统资源的方式获取状态栏的高度
     */
    public static int getStatusBarHight(Resources res) {
        int hight = 0;
        int id = res.getIdentifier("status_bar_height", "dimen", "android");
        if (id > 0) {
            hight = res.getDimensionPixelSize(id);
        }
        return hight;
    }

}
