package com.tianji.remark.task;

import com.tianji.remark.config.LikedTimesTaskProperties;
import com.tianji.remark.service.ILikedRecordService;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class LikedTimesCheckTask {

    private final ILikedRecordService recordService;
    private final LikedTimesTaskProperties properties;

    @Scheduled(fixedDelayString = "${tj.remark.liked-times-task.fixed-delay:20000}")
    public void checkLikedTimes() {
        for (String bizType : properties.getBizTypes()) {
            recordService.readLikedTimesAndSendMessage(bizType, properties.getMaxBizSize());
        }
    }
}
