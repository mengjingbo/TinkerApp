package com.tinker.app.info;


import com.tinker.app.utils.AppUtils;
import com.tinker.app.utils.ApplicationContext;

/**
 * we add BaseBuildInfo to loader pattern, so it won't change with patch!
 */
public class BaseBuildInfo {
    public static String TEST_MESSAGE = "I won't change with tinker patch!";
    public static String BASE_TINKER_ID = AppUtils.getTinkerIdValue(ApplicationContext.application);
}
