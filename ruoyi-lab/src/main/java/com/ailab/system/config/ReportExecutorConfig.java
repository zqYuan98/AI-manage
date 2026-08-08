package com.ailab.system.config;

import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadPoolExecutor;
import org.apache.commons.lang3.concurrent.BasicThreadFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Keeps short report lease heartbeats independent from long-running export work. */
@Configuration
public class ReportExecutorConfig {
    @Bean(name="reportHeartbeatExecutor",destroyMethod="shutdown")
    public ScheduledExecutorService reportHeartbeatExecutor(){
        ScheduledThreadPoolExecutor executor=new ScheduledThreadPoolExecutor(4,new BasicThreadFactory.Builder().namingPattern("report-heartbeat-%d").daemon(true).build(),new ThreadPoolExecutor.AbortPolicy());
        executor.setRemoveOnCancelPolicy(true);return executor;
    }
}
