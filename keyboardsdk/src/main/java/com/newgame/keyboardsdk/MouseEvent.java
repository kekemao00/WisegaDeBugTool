package com.newgame.keyboardsdk;

/**
 * 鼠标事件。
 *
 * @author 李剑波
 * @date 2017/12/3
 */

public class MouseEvent {

    final long eventTime;
    final int deviceId;
    final int x;
    final int y;
    final int w;

    public MouseEvent(long eventTime, int deviceId, int x, int y, int w) {
        this.eventTime = eventTime;
        this.deviceId = deviceId;
        this.x = x;
        this.y = y;
        this.w = w;
    }

    public long getEventTime() {
        return eventTime;
    }

    public int getDeviceId() {
        return deviceId;
    }

    public int getX() {
        return x;
    }

    public int getY() {
        return y;
    }

    public int getW() {
        return w;
    }

}
