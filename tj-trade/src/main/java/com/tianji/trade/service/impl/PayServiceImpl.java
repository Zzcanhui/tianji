package com.tianji.trade.service.impl;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.tianji.common.autoconfigure.mq.RabbitMqHelper;
import com.tianji.common.exceptions.BadRequestException;
import com.tianji.common.exceptions.BizIllegalException;
import com.tianji.common.utils.AssertUtils;
import com.tianji.common.utils.BeanUtils;
import com.tianji.common.utils.CollUtils;
import com.tianji.pay.sdk.client.PayClient;
import com.tianji.pay.sdk.dto.PayChannelDTO;
import com.tianji.pay.sdk.dto.PayResultDTO;
import com.tianji.trade.config.TradeProperties;
import com.tianji.trade.constants.OrderStatus;
import com.tianji.trade.constants.TradeErrorInfo;
import com.tianji.trade.domain.dto.OrderDelayQueryDTO;
import com.tianji.trade.domain.dto.PayApplyFormDTO;
import com.tianji.trade.domain.po.Order;
import com.tianji.trade.domain.po.OrderDetail;
import com.tianji.trade.domain.vo.PayChannelVO;
import com.tianji.trade.service.IOrderDetailService;
import com.tianji.trade.service.IOrderService;
import com.tianji.trade.service.IPayService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

import static com.tianji.common.constants.MqConstants.Exchange.TRADE_DELAY_EXCHANGE;
import static com.tianji.common.constants.MqConstants.Key.ORDER_DELAY_KEY;
import static com.tianji.trade.constants.TradeErrorInfo.ORDER_NOT_EXISTS;

@Slf4j
@Service
@RequiredArgsConstructor
public class PayServiceImpl implements IPayService {

    private final PayClient payClient;
    private final IOrderService orderService;
    private final IOrderDetailService detailService;
    private final TradeProperties tradeProperties;
    private final RabbitMqHelper mqHelper;

    @Override
    public List<PayChannelVO> queryPayChannels() {
        List<PayChannelDTO> list = payClient.listAllPayChannels();
        if (list == null) {
            return CollUtils.emptyList();
        }
        return list.stream()
                .filter(p -> p.getStatus() == 1)
                .map(p -> BeanUtils.copyBean(p, PayChannelVO.class))
                .collect(Collectors.toList());
    }

    @Override
    public String applyPayOrder(PayApplyFormDTO payApply) {
        Long orderId = payApply.getOrderId();
        Order order = orderService.getById(orderId);
        if (order == null) {
            throw new BadRequestException(ORDER_NOT_EXISTS);
        }
        if (!OrderStatus.NO_PAY.equalsValue(order.getStatus())) {
            throw new BizIllegalException(TradeErrorInfo.ORDER_ALREADY_FINISH);
        }
        if (order.getCreateTime().plusMinutes(tradeProperties.getPayOrderTTLMinutes()).isBefore(LocalDateTime.now())) {
            throw new BizIllegalException(TradeErrorInfo.ORDER_OVER_TIME);
        }
        List<OrderDetail> details = detailService.queryByOrderId(orderId);
        AssertUtils.isNotEmpty(details, ORDER_NOT_EXISTS);

        // Skip real QR payment and reuse the normal payment-success flow.
        PayResultDTO payResult = PayResultDTO.builder()
                .status(PayResultDTO.SUCCESS)
                .msg(PayResultDTO.OK)
                .bizOrderId(orderId)
                .payOrderNo(IdWorker.getId())
                .payChannel(payApply.getPayChannelCode())
                .successTime(LocalDateTime.now())
                .build();
        orderService.handlePaySuccess(payResult);
        return PayResultDTO.OK;
    }

    private void sendDelayQueryMessage(OrderDelayQueryDTO message) {
        mqHelper.sendDelayMessage(
                TRADE_DELAY_EXCHANGE,
                ORDER_DELAY_KEY,
                message,
                Duration.ofMillis(message.removeFirst()));
    }

    @Override
    public void queryPayResult(OrderDelayQueryDTO message) {
        Long orderId = message.getOrderId();
        Order order = orderService.getById(orderId);
        if (order == null) {
            log.error("Order to query pay result does not exist, orderId={}", orderId);
            return;
        }
        if (!OrderStatus.NO_PAY.equalsValue(order.getStatus())) {
            return;
        }
        PayResultDTO payResult = payClient.queryPayResult(orderId);
        int status = payResult.getStatus();
        if (PayResultDTO.SUCCESS != status) {
            if (message.getDelayMillis().isEmpty()) {
                return;
            }
            sendDelayQueryMessage(message);
            return;
        }
        orderService.handlePaySuccess(payResult);
    }
}
