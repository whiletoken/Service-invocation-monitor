package com.caijy.agent.core.utils;

import com.caijy.agent.core.constants.ClassFilterConstants;

/**
 * @author liguang
 * @date 2022/8/8 星期一 8:54 下午
 */
public class IgnoredUtils {

    public static boolean ignoredMethods(String methodName) {
        if (methodName == null || methodName.startsWith("$")) {
            return true;
        }
        if (ClassFilterConstants.JDK_IGNORED_METHODS.contains(methodName)) {
            return true;
        }
        if (ClassFilterConstants.MYBATIS_PLUS_IGNORED_METHODS.contains(methodName)) {
            return true;
        }
        return false;
    }

    public static boolean ignoredSpecificSymbol(String methodName) {
        return methodName != null && methodName.startsWith("$");
    }

    /**
     * 判断类名或全方法名是否属于Java系统类（如 java.*, javax.*, sun.*, com.sun.*, jdk.*）
     *
     * @param classNameOrMethod 类名或带包名的方法名
     * @return true表示属于Java系统类
     */
    public static boolean isJavaClass(String classNameOrMethod) {
        if (classNameOrMethod == null || classNameOrMethod.isEmpty()) {
            return false;
        }
        for (String prefix : ClassFilterConstants.JAVA_PACKAGE_PREFIXES) {
            if (classNameOrMethod.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }
}
