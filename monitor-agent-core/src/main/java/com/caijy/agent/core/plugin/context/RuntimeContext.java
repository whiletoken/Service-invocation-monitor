package com.caijy.agent.core.plugin.context;

import com.caijy.agent.core.MonitorException;
import com.caijy.agent.core.model.TraceSegment;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @author liguang
 * @date 2022/12/21 星期三 4:47 下午
 */
@Slf4j
public class RuntimeContext {
    private final ThreadLocal<RuntimeContext> contextThreadLocal;
    private final Map<String, Object> contextMap = new ConcurrentHashMap<>();
    /**
     * k-> traceId v-> traceModels (线程安全集合)
     **/
    private static final Map<String, List<TraceModel>> traceModelsMap = new ConcurrentHashMap<>();

    public static TraceSegment getTraceSegment(String traceId) {
        List<TraceModel> traceInfos = getTraceInfos(traceId);
        try {
            TraceModel bannerModel = new TraceModel();
            bannerModel.setName("Service Invocation Monitor | Result ");
            bannerModel.setSpanId("0");
            bannerModel.setCostTime(0L);

            // 预先建立 parentSpanId -> children 索引，将构建树复杂度从 O(N^2) 降至 O(N)
            Map<String, List<TraceModel>> childrenByParent = new HashMap<>();
            synchronized (traceInfos) {
                traceInfos.add(bannerModel);
                for (TraceModel m : traceInfos) {
                    if (m.getParentSpanId() != null) {
                        childrenByParent.computeIfAbsent(m.getParentSpanId(), k -> new ArrayList<>()).add(m);
                    }
                }
            }

            return fillingEachSegment(bannerModel, 0, childrenByParent);
        } catch (Throwable ex) {
            MonitorException.error(RuntimeContext.class, ex);
        } finally {
            traceInfos.clear();
        }
        return null;
    }

    private static TraceSegment fillingEachSegment(TraceModel model, int depth, Map<String, List<TraceModel>> childrenByParent) {
        TraceSegment segment = new TraceSegment();
        segment.setComponentName(model.getComponent());
        segment.setCostTime(model.getCostTime() == null ? null : model.getCostTime().toString());
        segment.setMethodName(model.getName());
        segment.setEnterTimeStamp(model.getStart());
        segment.setExitTimeStamp(model.getEnd());
        segment.setDepth(depth);
        String spanId = model.getSpanId();
        List<TraceModel> models = childrenByParent.get(spanId);
        if (models != null && !models.isEmpty()) {
            List<TraceSegment> children = new LinkedList<>();
            for (TraceModel child : models) {
                children.add(fillingEachSegment(child, depth + 1, childrenByParent));
            }
            segment.setChildren(new LinkedList<>(children));
        }
        return segment;
    }

    public void set(String k, Object v) {
        contextMap.put(k, v);
    }

    public Object get(String k) {
        return contextMap.get(k);
    }

    public RuntimeContext(ThreadLocal<RuntimeContext> contextThreadLocal) {
        this.contextThreadLocal = contextThreadLocal;
    }

    public static void registerTraceInfo(TraceModel model) {
        List<TraceModel> traceInfos = getTraceInfos(model.getTraceId());
        traceInfos.add(model);
    }

    private static List<TraceModel> getTraceInfos(String traceId) {
        if (traceId == null) {
            return Collections.synchronizedList(new ArrayList<>());
        }
        return traceModelsMap.computeIfAbsent(traceId, k -> Collections.synchronizedList(new ArrayList<>()));
    }

    public static void exit(String traceId, String spanId) {
        if (traceId != null && spanId != null) {
            long end = System.currentTimeMillis();
            List<TraceModel> traceInfos = getTraceInfos(traceId);
            synchronized (traceInfos) {
                for (TraceModel m : traceInfos) {
                    if (spanId.equals(m.getSpanId())) {
                        m.end(end).calcCostTime();
                        break;
                    }
                }
            }
        }
    }

    public static void clearTrace(String traceId) {
        if (traceId != null) {
            traceModelsMap.remove(traceId);
        }
    }

    @Data
    static class TraceModel {
        private String traceId;

        private boolean isRoot;

        private String spanId;

        private String parentSpanId;

        private String name;

        private String component;

        private Long start;

        private Long end;

        private Long costTime;

        private int depth;

        private TraceModel end(Long end) {
            this.end = end;
            return this;
        }

        private void calcCostTime() {
            this.costTime = (end != null && start != null) ? (end - start) : 0L;
        }
    }
}
