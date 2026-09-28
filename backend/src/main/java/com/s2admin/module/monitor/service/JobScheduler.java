package com.s2admin.module.monitor.service;

import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class JobScheduler {

    private final JobService jobService;

    @Scheduled(fixedDelay = 15000, initialDelay = 20000)
    public void tick() {
        jobService.tick();
    }
}
