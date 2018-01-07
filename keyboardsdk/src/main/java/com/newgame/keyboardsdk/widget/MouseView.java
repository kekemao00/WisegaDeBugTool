package com.newgame.keyboardsdk.widget;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.view.View;

/**
 * 鼠标控件，支持鼠标的绘制及全画布范围内的移动，并可获取鼠标当前的坐标。
 *
 * @author 李剑波
 * @date 17/3/16
 */

public class MouseView extends View {

    /**
     * 鼠标x坐标
     */
    private float mX;
    /**
     * 鼠标y坐标
     */
    private float mY;
    /**
     * 鼠标图片资源
     */
    private Bitmap mBitmap;

    public MouseView(Context context, Bitmap bitmap) {
        super(context);
        mBitmap = bitmap;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        // 第一次执行start()的绘制时，view的getWidth()和getHeight()接口可能会返回0，导致getXmax()和getYmax()返回负值，
        // 需要主动修正为居中显示
        if (mX < 0 && mY < 0) {
            mX = (canvas.getWidth() - mBitmap.getWidth()) / 2;
            mY = (canvas.getHeight() - mBitmap.getHeight()) / 2;
        }
        canvas.drawBitmap(mBitmap, mX, mY, null);
    }

    /**
     * 开始鼠标操作（鼠标居中显示）
     */
    public void start() {
        mX = getXmax() / 2;
        mY = getYmax() / 2;
        postInvalidate();
    }

    /**
     * 移动鼠标，当移动后的坐标超出显示区域时会自动对坐标进行修正，以确保鼠标始终可见
     *
     * @param dx x轴移动距离
     * @param dy y轴移动距离
     */
    public void move(float dx, float dy) {
        mX += dx;
        if (mX < 0) {
            mX = 0;
        } else if (mX > getXmax()) {
            mX = getXmax();
        }
        mY += dy;
        if (mY < 0) {
            mY = 0;
        } else if (mY > getYmax()) {
            mY = getYmax();
        }
        postInvalidate();
    }

    /**
     * 获取x轴最大值
     */
    private float getXmax() {
        return getWidth() - mBitmap.getWidth() / 3;
    }

    /**
     * 获取y轴最大值
     */
    private float getYmax() {
        return getHeight() - mBitmap.getHeight() / 3;
    }

    /**
     * 获取鼠标x坐标
     */
    public float getPointerX() {
        return mX;
    }

    /**
     * 获取鼠标y坐标
     */
    public float getPointerY() {
        return mY;
    }

}
