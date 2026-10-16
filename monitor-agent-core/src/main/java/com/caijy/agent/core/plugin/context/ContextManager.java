package com.caijy.agent.core.plugin.context;

import cn.hutool.core.lang.UUID;
import com.caijy.agent.core.boot.BootService;
import com.caijy.agent.core.config.Config;
import com.caijy.agent.core.console.TraceConsoleDTO;
import com.caijy.agent.core.constants.AgentConstant;
import com.caijy.agent.core.enums.ConsoleColorEnum;
import com.caijy.agent.core.model.TraceSegment;
import com.caijy.agent.core.plugin.span.LocalSpan;
import com.caijy.agent.core.trace.ComponentDefine;
import com.caijy.agent.core.utils.ConfigBanner;
import com.caijy.agent.core.utils.FileCache;
import com.google.common.collect.Lists;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @author caijy
 * @description 上下文管理
 * @date 2024/1/26 星期五 4:04 下午
 */
@Slf4j
public class ContextManager implements BootService {

    /**
     * 追踪链路对应的活跃 span 集合 (k: traceId, v: activeSpanIds)
     * 使用并发集合避免多线程环境下的数据错乱和死循环
     */
    private static final ConcurrentHashMap<String, Set<String>> ACTIVE_SPANS_MAP = new ConcurrentHashMap<>();

    private static final ThreadLocal<Deque<String>> STACK_THREAD_LOCAL = ThreadLocal.withInitial(ArrayDeque::new);

    private static final ThreadLocal<String> LOCAL_TRACE_ID = new ThreadLocal<>();

    public static void createSpan(ComponentDefine component, String operatorName) {
        createSpan(component, operatorName, null);
    }

    public static void createSpan(ComponentDefine component, String operatorName, ContextSnapshot snapshot) {
        long start = System.currentTimeMillis();
        String traceId = getOrCreateTraceId(snapshot);
        LocalSpan localSpan = LocalSpan.builder()
                .isRoot(isRoot(snapshot))
                .traceId(traceId)
                .operatorName(operatorName)
                .componentDefine(component)
                .spanId(UUID.randomUUID().toString())
                .parentSpanId(getParentSpanId(snapshot))
                .build();

        getStack().push(localSpan.getSpanId());
        ACTIVE_SPANS_MAP.computeIfAbsent(traceId, k -> ConcurrentHashMap.newKeySet()).add(localSpan.getSpanId());
        startSpan(localSpan, start);
    }

    private static Deque<String> getStack() {
        return STACK_THREAD_LOCAL.get();
    }

    private static void startSpan(LocalSpan localSpan, Long start) {
        RuntimeContext.TraceModel model = new RuntimeContext.TraceModel();
        model.setRoot(localSpan.getIsRoot());
        model.setComponent(localSpan.getComponentDefine().name());
        model.setName(localSpan.getOperatorName());
        model.setStart(start);
        model.setTraceId(localSpan.getTraceId());
        model.setSpanId(localSpan.getSpanId());
        model.setParentSpanId(localSpan.getParentSpanId());

        if (localSpan.getIsRoot()) {
            model.setParentSpanId("0");
        }
        RuntimeContext.registerTraceInfo(model);
    }

    public static void stopSpan() {
        String traceId = LOCAL_TRACE_ID.get();
        Deque<String> stack = getStack();
        if (!stack.isEmpty()) {
            String spanId = stack.pop();
            RuntimeContext.exit(traceId, spanId);
            if (traceId != null) {
                Set<String> activeSpans = ACTIVE_SPANS_MAP.get(traceId);
                if (activeSpans != null) {
                    activeSpans.remove(spanId);
                    if (activeSpans.isEmpty()) {
                        ACTIVE_SPANS_MAP.remove(traceId);
                        printInformation(traceId);
                        clear(traceId);
                    }
                }
            }
        }
        if (stack.isEmpty()) {
            STACK_THREAD_LOCAL.remove();
            LOCAL_TRACE_ID.remove();
        }
    }

    private static void clear(String traceId) {
        if (traceId != null) {
            RuntimeContext.clearTrace(traceId);
            ACTIVE_SPANS_MAP.remove(traceId);
        }
    }

    public static ContextSnapshot capture() {
        Deque<String> stack = getStack();
        String currentSpanId = stack.isEmpty() ? null : stack.peek();
        return new ContextSnapshot(currentSpanId, LOCAL_TRACE_ID.get());
    }

    public static boolean isRoot() {
        return LOCAL_TRACE_ID.get() == null;
    }

    public static boolean isRoot(ContextSnapshot contextSnapshot) {
        return LOCAL_TRACE_ID.get() == null && contextSnapshot == null;
    }

    private static String getParentSpanId(ContextSnapshot contextSnapshot) {
        if (contextSnapshot != null) {
            return contextSnapshot.getSpanId();
        }
        Deque<String> stack = getStack();
        if (!stack.isEmpty()) {
            return stack.peek();
        }
        return null;
    }

    public static boolean isActive() {
        String traceId = LOCAL_TRACE_ID.get();
        if (traceId == null) {
            return false;
        }
        Set<String> activeSpans = ACTIVE_SPANS_MAP.get(traceId);
        return activeSpans != null && !activeSpans.isEmpty();
    }

    private static void printInformation(String traceId) {
        TraceSegment traceSegment = RuntimeContext.getTraceSegment(traceId);
        if (traceSegment == null) {
            return;
        }
        List<TraceConsoleDTO> consoleDTOList = Lists.newArrayList();
        StringBuilder builder = new StringBuilder();
        TrackManager.append(builder, traceSegment, consoleDTOList);
        String colorString = ConfigBanner.toColorString(ConsoleColorEnum.GREEN, builder);
        System.out.println(colorString);

        Object sampleRateObj = Config.get(AgentConstant.MONITOR_SAMPLE_RATE);
        int sampleRate = sampleRateObj != null ? Integer.parseInt(sampleRateObj.toString()) : 10000;
        Object path = Config.get(AgentConstant.MONITOR_AGENT_PATH);
        Object projectCode = Config.get(AgentConstant.MONITOR_PROJECT_CODE);
        if (path != null && projectCode != null) {
            if (consoleDTOList.size() <= sampleRate) {
                FileCache.appendLines(path.toString(), projectCode.toString(), consoleDTOList);
            }
        }
    }

    public static String getOrCreateTraceId(ContextSnapshot contextSnapshot) {
        String traceId = LOCAL_TRACE_ID.get();
        if (traceId == null) {
            if (contextSnapshot != null && contextSnapshot.getTraceId() != null) {
                traceId = contextSnapshot.getTraceId();
            } else {
                traceId = UUID.fastUUID().toString();
            }
            LOCAL_TRACE_ID.set(traceId);
        }
        return traceId;
    }

    @Override
    public void boot() throws Throwable {

    }

    @Override
    public void shutdown() throws Throwable {
        ACTIVE_SPANS_MAP.clear();
        STACK_THREAD_LOCAL.remove();
        LOCAL_TRACE_ID.remove();
    }
}
