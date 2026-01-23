package com.tianji.learning.service.impl;

import com.tianji.common.autoconfigure.mq.RabbitMqHelper;
import com.tianji.common.constants.MqConstants;
import com.tianji.common.exceptions.BizIllegalException;
import com.tianji.common.utils.BooleanUtils;
import com.tianji.common.utils.CollUtils;
import com.tianji.common.utils.DateUtils;
import com.tianji.common.utils.UserContext;
import com.tianji.learning.constants.RedisConstants;
import com.tianji.learning.domain.vo.SignResultVO;
import com.tianji.learning.mq.message.SignInMessage;
import com.tianji.learning.service.ISignRecordService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.connection.BitFieldSubCommands;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class SignRecordServiceImpl implements ISignRecordService {

    private final StringRedisTemplate redisTemplate;
    private final RabbitMqHelper mqHelper;

    @Override
    public SignResultVO addSignRecord() {
        // 1.签到
        // 1.1.获取登录用户
        Long userId = UserContext.getUser();
        // 1.2.获取日期
        LocalDateTime now = LocalDateTime.now();
        // 1.3.拼接key
        String key = RedisConstants.SIGN_RECORD_KEY_PREFIX
                + userId + ":"
                + now.format(DateUtils.SIGN_DATE_SUFFIX_FORMATTER);
        // 1.4.计算offset
        int offset = now.getDayOfMonth() - 1;
        // 1.5.保存签到信息
        Boolean exits = redisTemplate.opsForValue().setBit(key, offset, true);
        // 1.6.判断是否已签到
        if (BooleanUtils.isTrue(exits)) {
            // 已签到，返回失败
            throw new BizIllegalException("不允许重复签到");
        }
        // 2.计算连续签到天数
        int signDays = countSignDays(key, now.getDayOfMonth());
        // 3.计算签到得分
        int rewardPoints = 0;
        switch (signDays) {
            case 7:
                rewardPoints = 10;
                break;
            case 14:
                rewardPoints = 20;
                break;
            case 28:
                rewardPoints = 40;
                break;
        }
        // 4.保存积分明细记录
        mqHelper.send(
                MqConstants.Exchange.LEARNING_EXCHANGE,
                MqConstants.Key.SIGN_IN,
                SignInMessage.of(userId, rewardPoints + 1));
        // 5.封装返回
        SignResultVO vo = new SignResultVO();
        vo.setSignDays(signDays);
        vo.setRewardPoints(rewardPoints);
        return vo;
    }

    @Override
    public List<Integer> querySignRecords() {
        // 1.获取登录用户
        Long userId = UserContext.getUser();
        // 2.获取日期
        LocalDateTime now = LocalDateTime.now();
        int dayOfMonth = now.getDayOfMonth();
        // 3.拼接key
        String key = RedisConstants.SIGN_RECORD_KEY_PREFIX
                + userId + ":"
                + now.format(DateUtils.SIGN_DATE_SUFFIX_FORMATTER);
        // 4.读取本月截止今天的所有签到记录
        List<Long> result = redisTemplate.opsForValue().bitField(key, BitFieldSubCommands.create()
                .get(BitFieldSubCommands.BitFieldType.unsigned(dayOfMonth)).valueAt(0));
        if (CollUtils.isEmpty(result)) {
            return CollUtils.emptyList();
        }
        int num = result.get(0).intValue();
        // 5.循环处理
        List<Integer> list = new ArrayList<>(dayOfMonth);
        for (int i = 0; i < dayOfMonth; i++) {
            // 从最后一位开始取，但是我们要的是从第一天到今天，所以这里需要处理一下顺序
            // 或者我们可以从第一天开始取
            // bitField 的 get(unsigned(len)).valueAt(0) 取出的是从 offset 0 开始的 len 位
            // 比如 len=3, offset 0,1,2 分别是 1,0,1，那么 num 就是 101 (二进制) = 5
            // 对应第1天(offset 0)是1, 第2天(offset 1)是0, 第3天(offset 2)是1
            // 在二进制中，高位对应低 offset。所以 101 中，左边的 1 是 offset 0，右边的 1 是 offset 2
            // 所以我们需要从高位往低位取
            list.add((num >>> (dayOfMonth - 1 - i)) & 1);
        }
        return list;
    }

    private int countSignDays(String key, int len) {
        // 1.获取本月从第一天开始，到今天为止的所有签到记录
        List<Long> result = redisTemplate.opsForValue().bitField(key, BitFieldSubCommands.create()
                .get(BitFieldSubCommands.BitFieldType.unsigned(len)).valueAt(0));
        // 1.1.判断是否为空
        if (CollUtils.isEmpty(result)) {
            return 0;
        }
        // 1.2.获取第一个元素
        int num = result.get(0).intValue();
        // 2.定义一个计数器
        int count = 0;
        // 3.循环，与1做与运算，得到最后一个bit，判断是否为0，为0则终止，为1则继续
        while ((num & 1) == 1) {
            // 4.计数器+！
            count++;
            // 5.把数字右移一位，最后一位被舍弃，倒数第二位成了最后一位
            num >>>= 1;

        }
        return count;

    }
}
