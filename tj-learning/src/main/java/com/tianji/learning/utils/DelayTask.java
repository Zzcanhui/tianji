package com.tianji.learning.utils;

import lombok.Data;

import java.time.Duration;
import java.util.concurrent.Delayed;
import java.util.concurrent.TimeUnit;

/**
 * 延迟任务类，实现了Delayed接口，用于支持延迟队列(DelayQueue)
 * <p>
 * 该类采用泛型设计，可以封装任意类型的数据，支持按截止时间排序
 * </p>
 *
 * @param <D> 任务携带的数据类型
 */
@Data
public class DelayTask<D> implements Delayed {

    /**
     * 任务携带的数据对象
     */
    private D data;

    /**
     * 任务的截止时间（纳秒时间戳）
     */
    private Long deadlineNanos;

    /**
     * 构造一个延迟任务
     *
     * @param data     任务携带的数据
     * @param deadline 任务的延迟时长，从当前时间开始计算
     */
    public DelayTask(D data, Duration deadline) {
        this.data = data;
        this.deadlineNanos = System.nanoTime() + deadline.toNanos();
    }

    /**
     * 获取任务剩余的延迟时间
     * <p>
     * 该方法会计算当前时间到任务截止时间的剩余时间，并转换为指定的时间单位
     * 如果任务已经到期，返回0
     * </p>
     *
     * @param unit 时间单位
     * @return 剩余延迟时间，转换为指定的时间单位
     */
    @Override
    public long getDelay(TimeUnit unit) {
        return unit.convert(Math.max(0, deadlineNanos - System.nanoTime()), TimeUnit.NANOSECONDS);
    }

    /**
     * 比较两个延迟任务的优先级
     * <p>
     * 按照任务的剩余延迟时间进行排序，剩余时间少的任务优先级高
     * </p>
     *
     * @param o 要比较的另一个延迟任务
     * @return 如果当前任务的剩余时间大于o，返回1；如果小于o，返回-1；如果相等，返回0
     */
    @Override
    public int compareTo(Delayed o) {
        long l = getDelay(TimeUnit.NANOSECONDS) - o.getDelay(TimeUnit.NANOSECONDS);
        if (l > 0) {
            return 1;
        } else if (l < 0) {
            return -1;
        } else {
            return 0;
        }
    }
}