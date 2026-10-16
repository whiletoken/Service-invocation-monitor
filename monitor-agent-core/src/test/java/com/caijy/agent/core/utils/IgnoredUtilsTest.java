package com.caijy.agent.core.utils;

import com.caijy.agent.core.console.TraceConsoleDTO;
import com.caijy.agent.core.model.TraceSegment;
import com.caijy.agent.core.plugin.context.TrackManager;
import org.junit.Assert;
import org.junit.Test;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

public class IgnoredUtilsTest {

    @Test
    public void testIsJavaClass() {
        Assert.assertTrue(IgnoredUtils.isJavaClass("java.util.concurrent.FutureTask.run"));
        Assert.assertTrue(IgnoredUtils.isJavaClass("java.lang.Thread.run"));
        Assert.assertTrue(IgnoredUtils.isJavaClass("javax.servlet.http.HttpServlet.service"));
        Assert.assertTrue(IgnoredUtils.isJavaClass("sun.misc.Unsafe.park"));
        Assert.assertTrue(IgnoredUtils.isJavaClass("com.sun.proxy.$Proxy0.doSomething"));
        Assert.assertTrue(IgnoredUtils.isJavaClass("jdk.internal.reflect.NativeMethodAccessorImpl.invoke"));

        // 业务类及第三方框架类不应被判定为Java类
        Assert.assertFalse(IgnoredUtils.isJavaClass("com.example.service.UserService.getUser"));
        Assert.assertFalse(IgnoredUtils.isJavaClass("org.springframework.web.bind.annotation.GetMapping"));
        Assert.assertFalse(IgnoredUtils.isJavaClass("org.apache.dubbo.rpc.Invoker.invoke"));
        Assert.assertFalse(IgnoredUtils.isJavaClass(null));
        Assert.assertFalse(IgnoredUtils.isJavaClass(""));
    }

    @Test
    public void testTrackManagerFilterJavaClasses() {
        // 构建调用树: Root -> JavaClassWrapper -> BusinessService
        TraceSegment root = new TraceSegment();
        root.setMethodName("RootService | Result");
        root.setComponentName("ROOT");
        root.setChildren(new LinkedList<>());

        TraceSegment javaWrapper = new TraceSegment();
        javaWrapper.setMethodName("java.util.concurrent.FutureTask.run");
        javaWrapper.setComponentName("JDK_THREAD");
        javaWrapper.setCostTime("15");
        javaWrapper.setChildren(new LinkedList<>());

        TraceSegment bizService = new TraceSegment();
        bizService.setMethodName("com.example.service.PaymentService.pay");
        bizService.setComponentName("SPRING");
        bizService.setCostTime("10");
        bizService.setChildren(new LinkedList<>());

        javaWrapper.getChildren().add(bizService);
        root.getChildren().add(javaWrapper);

        StringBuilder sb = new StringBuilder();
        List<TraceConsoleDTO> dtoList = new ArrayList<>();
        TrackManager.append(sb, root, dtoList);

        String output = sb.toString();
        // 验证输出中屏蔽了 java.util.concurrent.FutureTask.run
        Assert.assertFalse("Java class should not be output in trace", output.contains("FutureTask"));
        // 验证业务方法正常输出并提升到层级中
        Assert.assertTrue("Business method should be preserved", output.contains("PaymentService.pay"));

        // 验证 DTO 列表中也不包含 Java 类
        for (TraceConsoleDTO dto : dtoList) {
            Assert.assertFalse("DTO text should not contain java class",
                    dto.getText() != null && dto.getText().contains("FutureTask"));
        }
    }
}
