package com.s2admin.module.monitor.service;

import org.springframework.stereotype.Service;

import java.io.File;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryUsage;
import java.lang.management.RuntimeMXBean;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class ServerMonitorService {

    public Map<String, Object> snapshot() {
        RuntimeMXBean runtime = ManagementFactory.getRuntimeMXBean();
        MemoryMXBean memory = ManagementFactory.getMemoryMXBean();
        MemoryUsage heap = memory.getHeapMemoryUsage();
        MemoryUsage nonHeap = memory.getNonHeapMemoryUsage();
        File disk = new File(System.getProperty("user.dir", "."));
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("osName", System.getProperty("os.name"));
        body.put("osArch", System.getProperty("os.arch"));
        body.put("javaVersion", System.getProperty("java.version"));
        body.put("jvmName", runtime.getVmName());
        body.put("processors", Runtime.getRuntime().availableProcessors());
        body.put("startTime", DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
                .withZone(ZoneId.systemDefault())
                .format(Instant.ofEpochMilli(runtime.getStartTime())));
        body.put("uptimeSeconds", runtime.getUptime() / 1000);
        body.put("threadCount", ManagementFactory.getThreadMXBean().getThreadCount());
        body.put("heapUsed", heap.getUsed());
        body.put("heapMax", heap.getMax());
        body.put("nonHeapUsed", nonHeap.getUsed());
        body.put("diskTotal", disk.getTotalSpace());
        body.put("diskFree", disk.getUsableSpace());
        body.put("workdir", disk.getAbsolutePath());
        try {
            java.lang.management.OperatingSystemMXBean os = ManagementFactory.getOperatingSystemMXBean();
            body.put("systemLoad", os.getSystemLoadAverage());
        } catch (Exception ignored) {
            body.put("systemLoad", -1);
        }
        return body;
    }
}
