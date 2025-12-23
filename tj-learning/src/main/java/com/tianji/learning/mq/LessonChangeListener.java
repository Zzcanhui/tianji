package com.tianji.learning.mq;

import com.tianji.api.dto.trade.OrderBasicDTO;
import com.tianji.common.constants.MqConstants;
import com.tianji.common.utils.CollUtils;
import com.tianji.learning.domain.po.LearningLesson;
import com.tianji.learning.service.ILearningLessonService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.ExchangeTypes;
import org.springframework.amqp.rabbit.annotation.Exchange;
import org.springframework.amqp.rabbit.annotation.Queue;
import org.springframework.amqp.rabbit.annotation.QueueBinding;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class LessonChangeListener {
    private final ILearningLessonService lessonService;

    @RabbitListener(bindings = @QueueBinding(
            value = @Queue(value = "learning.lesson.pay.queue",durable = "true"),
            exchange = @Exchange(name = MqConstants.Exchange.ORDER_EXCHANGE, type = ExchangeTypes.TOPIC),
            key = MqConstants.Key.ORDER_PAY_KEY
    ))
    public void listenLessonChange(OrderBasicDTO order) {
        // 1.健壮性处理
        if(order == null || order.getUserId() == null || CollUtils.isEmpty(order.getCourseIds())) {
            // 数据有误，无需处理
            log.error("接受到的MQ消息数据有误，order:{}",order);
            return;
        }
        // 2.添加课程
        log.debug("监听到用户{}的订单{},需要添加课程{}到课表",order.getUserId(),order.getOrderId(),order.getCourseIds());
        lessonService.addUserLesson(order.getUserId(), order.getCourseIds());

    }

    @RabbitListener(bindings = @QueueBinding(
            value = @Queue(value = "learning.lesson.refund.queue", durable = "true"),
            exchange = @Exchange(name = MqConstants.Exchange.ORDER_EXCHANGE, type = ExchangeTypes.TOPIC),
            key = MqConstants.Key.ORDER_REFUND_KEY
    ))
    public void listenLessonRefund(OrderBasicDTO order) {
        // 1.健壮性处理
        if(order == null || order.getUserId() == null || CollUtils.isEmpty(order.getCourseIds())) {
            // 数据有误，无需处理
            log.error("接受到的退款MQ消息数据有误，order:{}", order);
            return;
        }
        // 2.删除课程
        log.debug("监听到用户{}的退款订单{},需要从课表删除课程{}", order.getUserId(), order.getOrderId(), order.getCourseIds());
        lessonService.deleteCourseFromLesson(order.getUserId(), order.getCourseIds().get(0));
    }
}
