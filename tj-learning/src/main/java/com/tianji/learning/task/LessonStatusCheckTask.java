package com.tianji.learning.task;

import com.tianji.learning.service.ILearningLessonService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import com.xxl.job.core.handler.annotation.XxlJob;
import org.springframework.stereotype.Component;

@Component
@Slf4j
@RequiredArgsConstructor
public class LessonStatusCheckTask {

    private final ILearningLessonService lessonService;

    @XxlJob("checkLessonStatusJob")
    public void checkLessonStatus() {
        log.info("开始执行课表状态检查定时任务...");
        lessonService.checkLessonStatus();
        log.info("课表状态检查定时任务执行结束。");
    }
}


