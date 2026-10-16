package com.caijy.agent.core.constants;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * @author liguang
 * @date 2022/8/8 星期一 8:43 下午
 */
public interface ClassFilterConstants {

    Set<String> JAVA_PACKAGE_PREFIXES = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
        "java.", "javax.", "sun.", "com.sun.", "jdk."
    )));

    Set<String> MYBATIS_PLUS_IGNORED_METHODS = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
        "getBaseMapper", "lambdaUpdate", "lambdaQuery"
    )));

    Set<String> JDK_IGNORED_METHODS = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
        "toString", "hashCode", "equals", "getClass", "clone", "notify", "notifyAll", "wait", "finalize"
    )));
}
