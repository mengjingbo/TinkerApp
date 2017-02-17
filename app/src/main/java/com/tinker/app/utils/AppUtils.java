package com.tinker.app.utils;

import android.content.Context;
import android.content.pm.PackageManager;

/**
 * 作者：Gavin 时间：2017/2/17
 * 描述：
 */
public class AppUtils {

    /**
     * 读取AndroidManifest中的信息
     *
     * @param context
     * @return TINKER_ID
     */
    public static String getTinkerIdValue(Context context) {
        String channel = "";
        try {
            channel = context.getPackageManager().getApplicationInfo(context.getPackageName(), PackageManager.GET_META_DATA).metaData.getString("TINKER_ID");
        } catch (PackageManager.NameNotFoundException e) {
            e.printStackTrace();
        }
        return channel;
    }
}
