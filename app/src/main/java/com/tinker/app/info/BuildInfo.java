package com.tinker.app.info;


import com.tinker.app.BuildConfig;
import com.tinker.app.utils.AppUtils;
import com.tinker.app.utils.ApplicationContext;

/**
 * we use BuildInfo instead of {@link BuildInfo} to make less change
 */
public class BuildInfo {
    /**
     * they are not final, so they won't change with the BuildConfig values!
     */
    public static boolean DEBUG        = BuildConfig.DEBUG;
    public static String VERSION_NAME = BuildConfig.VERSION_NAME;
    public static int     VERSION_CODE = BuildConfig.VERSION_CODE;

    public static String MESSAGE       = BuildConfig.MESSAGE;
    public static String TINKER_ID     = AppUtils.getTinkerIdValue(ApplicationContext.application);
    public static String PLATFORM      = BuildConfig.PLATFORM;

}
