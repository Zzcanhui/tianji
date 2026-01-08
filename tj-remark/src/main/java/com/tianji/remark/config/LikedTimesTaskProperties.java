package com.tianji.remark.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.List;

@Data
@Component
@ConfigurationProperties(prefix = "tj.remark.liked-times-task")
public class LikedTimesTaskProperties {

    /**
     * 点赞业务类型列表
     */
    private List<String> bizTypes = List.of("QA", "NOTE");

    /**
     * 每次处理的最大业务数量
     */
    private int maxBizSize = 50;

    /**
     * 定时任务执行间隔（毫秒）
     */
    private long fixedDelay = 20000;
}
