package com.hexad.bluezime;

/**
 * Desc.
 *
 * @author 李剑波
 * @date 2017/12/2
 */

public class NublerUtil {
    /**
     * 通过byte数组取到short
     *
     * @param b
     * @param index 第几位开始取
     * @return
     */
    public static short getShort(byte[] b, int index) {
        return (short) (((b[index + 1] << 8) | b[index + 0] & 0xff));
    }

    public static short getShort2(byte[] b, int index) {
        return (short) (((b[index + 0] << 8) | b[index + 1] & 0xff));
    }
}
