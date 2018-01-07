package com.newgame.keyboardsdk.util;


import java.util.ArrayList;


public class HexEx {
    public static int[] parseIns(String s) {
        byte[] value = Hex.parse(s);
        int[] rIns = new int[value.length];
        for (int i = 0; i < value.length; i++) {
            rIns[i] = value[i] & 0xff;
        }
        return rIns;
    }

    public static char[] bytes2Chars(byte[] rBytes) {
        char[] rChars = new char[rBytes.length];
        for (int i = 0; i < rBytes.length; i++) {
            rChars[i] = (char) (rBytes[i] & 0xff);
        }
        return rChars;
    }

    public static byte[] ints2Bytes(int[] ints) {
        byte[] rBytes = new byte[ints.length];
        for (int i = 0; i < rBytes.length; i++) {
            rBytes[i] = (byte) (ints[i] & 0xff);
        }
        return rBytes;
    }

    public static int[] bytes2Ints(byte[] recvByte) {
        int[] rInts = new int[recvByte.length];
        for (int i = 0; i < rInts.length; i++) {
            rInts[i] = recvByte[i] & 0xff;
        }
        return rInts;
    }

    public static String toString(int[] receBuffer) {
        return Hex.toString(HexEx.ints2Bytes(receBuffer));
    }

    public static byte[] split(byte[] bytes, int offset) {
        byte[] rbytes = new byte[bytes.length - offset];
        Hex.copy(rbytes, 0, bytes, offset, rbytes.length);
        return rbytes;
    }

    public static byte[] toPrimitive(ArrayList<Byte> mLastData) {
        byte[] rBytes = new byte[mLastData.size()];
        int index = 0;
        for (Byte aByte : mLastData) {
            rBytes[index++] = aByte;
        }
        return rBytes;
    }

    public static byte[] getHex(byte[] availBuff, int bmwHeadLen) {
        byte[] rBytes = new byte[bmwHeadLen];
        Hex.copy(rBytes, 0, availBuff, 0, bmwHeadLen);
        return rBytes;
    }

    public static ArrayList<Byte> toList(byte[] availBuff) {
        ArrayList<Byte> rList = new ArrayList<Byte>();
        for (byte b : availBuff) {
            rList.add(b);
        }
        return rList;
    }

    public static String toString(byte[] rbytes) {
        StringBuilder sb = new StringBuilder();
        for (byte rbyte : rbytes) {
            String s = String.format("%02x ", rbyte);
            sb.append(s);
        }
        return sb.toString().trim();
    }

    public static String toBinaryString(byte[] binary) {
        StringBuilder sb = new StringBuilder();
        for (byte rbyte : binary) {
            String s = String.format("0x%02x,", rbyte);
            sb.append(s);
        }
        sb.deleteCharAt(sb.length() - 1);
        return sb.toString();
    }
}
