package com.caijy.agent.core.utils;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.stream.Collectors;

import cn.hutool.core.io.FileUtil;
import cn.hutool.json.JSONUtil;
import com.caijy.agent.core.console.TraceConsoleDTO;

/**
 * @author liguang
 * @date 2022/12/9 星期五 3:58 下午
 */
public class FileCache {
    /**
     * 往日志文件追加trace日志
     *
     * @param agentJarPath:
     * @param projectCode:
     * @param consoleDTOList:
     * @return
     * @author liguang
     * @date 2022/12/15 3:38 下午
     **/
    public static void appendLines(String agentJarPath, String projectCode, List<TraceConsoleDTO> consoleDTOList) {
        if (consoleDTOList == null || consoleDTOList.isEmpty()) {
            return;
        }
        String logFilePath = getLogFilePath(agentJarPath, projectCode);
        if (logFilePath == null) {
            return;
        }
        List<String> list = consoleDTOList.stream().map(JSONUtil::toJsonStr).collect(Collectors.toList());
        FileUtil.appendLines(list, logFilePath, StandardCharsets.UTF_8);
    }

    public static String getLogFilePath(String agentJarPath, String projectCode) {
        if (agentJarPath == null || projectCode == null) {
            return null;
        }
        agentJarPath = agentJarPath.trim();
        if (agentJarPath.startsWith("\"") && agentJarPath.endsWith("\"") && agentJarPath.length() >= 2) {
            agentJarPath = agentJarPath.substring(1, agentJarPath.length() - 1);
        }
        File agentJar = new File(agentJarPath);
        if (agentJar.exists()) {
            File parent = agentJar.getParentFile();
            if (parent == null) {
                return null;
            }
            File grandParent = parent.getParentFile();
            String projectDir = grandParent != null ? grandParent.getAbsolutePath() : parent.getAbsolutePath();
            // 防止路径穿越攻击 (CWE-22)，仅保留合法的文件名字符
            String safeProjectCode = projectCode.replaceAll("[^a-zA-Z0-9_.-]", "_");
            File logsDir = new File(projectDir, "logs");
            if (!logsDir.exists()) {
                logsDir.mkdirs();
            }
            return new File(logsDir, safeProjectCode + ".log").getAbsolutePath();
        }
        return null;
    }

}
