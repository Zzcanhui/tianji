package com.tianji.learning.utils;

import com.tianji.common.utils.JsonUtils;
import com.tianji.common.utils.StringUtils;
import com.tianji.learning.domain.po.LearningLesson;
import com.tianji.learning.domain.po.LearningRecord;
import com.tianji.learning.mapper.LearningRecordMapper;
import com.tianji.learning.service.ILearningLessonService;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.DelayQueue;

@Slf4j
@Component
@RequiredArgsConstructor
public class LearningRecordDelayTaskHandler {

    private final StringRedisTemplate redisTemplate;
    private final LearningRecordMapper recordMapper;
    private final ILearningLessonService lessonService;
    private final DelayQueue<DelayTask<RecordTaskDate>> queue = new DelayQueue<>();
    private final static String REDIS_KEY_TEMPLATE = "learning:record:{}";
    private static volatile boolean begin =true;


    @PostConstruct
    public void init(){
        CompletableFuture.runAsync(this::handleDelayTask);
    }

    @PreDestroy
    public void destroy(){
        begin = false;
        log.debug("延迟任务停止执行!");
    }

    public void handleDelayTask() {
        while (begin){
            try {
                // 1.获取到期的延迟任务
                DelayTask<RecordTaskDate> task = queue.take();
                RecordTaskDate date = task.getData();
                // 2.查询Redis缓存
                LearningRecord record = readRecordCache(date.getLessonId(), date.getSectionId());
                if (record==null) {
                    continue;
                }
                // 3.比较数据,moment
                if (!Objects.equals(date.getMoment(), record.getMoment())) {
                    // 不一致，说明用户还在持续提交播放进度，放弃旧数据
                    continue;
                }
                // 4.一致，说明用户已经停止提交播放进度，更新播放进度到数据库
                // 4.1.更新学习记录的moment
                record.setFinished(null);
                recordMapper.updateById(record);
                // 4.2.更新课表最近学习信息
                LearningLesson lesson = new LearningLesson();
                lesson.setId(date.getLessonId());
                lesson.setLatestSectionId(date.getSectionId());
                lesson.setLatestLearnTime(LocalDateTime.now());

                lessonService.updateById(lesson);

            } catch (Exception e) {
                log.error("处理学习记录延迟任务异常", e);
            }

        }
    }

    public void addLearningRecordTask(LearningRecord record) {
        // 1.添加数据到Redis缓存
        writeRecordCache(record);
        // 2.提交延迟任务到延迟队列DelayQueue
        queue.add(new DelayTask<>(new RecordTaskDate(record), Duration.ofSeconds(20)));

    }

    public void writeRecordCache(LearningRecord record) {
        log.debug("更新学习记录缓存，record:{}", record);
        try {
            // 1.数据转换
            String json = JsonUtils.toJsonStr(new RecordCacheDate(record));
            // 2.写入redis
            String key = StringUtils.format(REDIS_KEY_TEMPLATE, record.getLessonId());
            redisTemplate.opsForHash().put(key, record.getSectionId().toString(),json);
            // 3.添加缓存过期时间
            redisTemplate.expire(key, Duration.ofMinutes(1));
        } catch (Exception e) {
            log.error("更新学习记录缓存异常，record:{}", record, e);
        }
    }

    public LearningRecord readRecordCache(Long lessonId, Long sectionId) {
        Object cacheDate = null;
        try {
            // 1.读取redis数据
            String key = StringUtils.format(REDIS_KEY_TEMPLATE, lessonId);
            cacheDate = redisTemplate.opsForHash().get(key, sectionId.toString());
            if (cacheDate == null) {
                return null;
            }
            // 2.数据检查和转换
            return JsonUtils.toBean(cacheDate.toString(), LearningRecord.class);
        } catch (Exception e) {
            log.error("读取学习记录缓存异常，lessonId:{}, sectionId:{}", lessonId, sectionId, e);
            return null;
        }

    }

    public void cleanRecordCache(Long lessonId, Long sectionId) {
            // 删除redis数据
            String key = StringUtils.format(REDIS_KEY_TEMPLATE, lessonId);
            redisTemplate.opsForHash().delete(key, sectionId.toString());
    }

    @Data
    @NoArgsConstructor
    private static class RecordCacheDate {
        private Long id;
        private Integer moment;
        private Boolean finished;

        public RecordCacheDate(LearningRecord record) {
            this.id = record.getId();
            this.moment = record.getMoment();
            this.finished = record.getFinished();
        }
    }

    @Data
    @NoArgsConstructor
    private static class RecordTaskDate{
        private Long lessonId;
        private Long sectionId;
        private Integer moment;

        public RecordTaskDate(LearningRecord record) {
            this.lessonId = record.getLessonId();
            this.sectionId = record.getSectionId();
            this.moment = record.getMoment();
        }
    }
}
