package com.tianji.remark.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.tianji.api.dto.remark.LikeTimesDTO;
import com.tianji.common.autoconfigure.mq.RabbitMqHelper;
import com.tianji.common.utils.StringUtils;
import com.tianji.common.utils.UserContext;
import com.tianji.remark.constants.RedisConstants;
import com.tianji.remark.domain.dto.LikeRecordFormDTO;
import com.tianji.remark.domain.po.LikedRecord;
import com.tianji.remark.mapper.LikedRecordMapper;
import com.tianji.remark.service.ILikedRecordService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static com.tianji.common.constants.MqConstants.Exchange.LIKE_RECORD_EXCHANGE;
import static com.tianji.common.constants.MqConstants.Key.LIKED_TIMES_KEY_TEMPLATE;

/**
 * <p>
 * 点赞记录表 服务实现类
 * </p>
 *
 * @author author
 * @since 2026-01-06
 */
@Service
@RequiredArgsConstructor
public class LikedRecordServiceRedisImpl extends ServiceImpl<LikedRecordMapper, LikedRecord> implements ILikedRecordService {

    private final RabbitMqHelper mqHelper;
    private final StringRedisTemplate redisTemplate;

    @Override
    public void addLikeRecord(LikeRecordFormDTO recordDTO) {
        // 1.基于前端的参数，判断是执行点赞还是取消点赞
        boolean success = recordDTO.getLiked() ? like(recordDTO) : unlike(recordDTO);
        // 2.判断是否执行成功，如果失败，则直接结束
        if (!success) {
            return;
        }
        // 3.如果执行成功，统计点赞总数
        Long likeTimes = redisTemplate.opsForSet()
                .size(RedisConstants.LIKES_BIZ_KEY_PREFIX +  recordDTO.getBizId());
        if (likeTimes == null) {
            return;
        }
        // 4.缓存点赞总数到Redis
        redisTemplate.opsForZSet().add(
                RedisConstants.LIKES_TIMER_KEY_PREFIX + recordDTO.getBizType(),
                recordDTO.getBizId().toString(),
                likeTimes
        );

    }

    @Override
    public Set<Long> isBizLiked(List<Long> bizIds) {
        // 1.获取登录用户id
        Long userId = UserContext.getUser();

        // 2.查询点赞状态
        List<LikedRecord> list = lambdaQuery()
                .in(LikedRecord::getBizId, bizIds)
                .eq(LikedRecord::getUserId, userId)
                .list();
        // 3.返回结果
        return list.stream().map(LikedRecord::getBizId).collect(Collectors.toSet());
    }

    private boolean unlike(LikeRecordFormDTO recordDTO) {
        // 1.获取用户id
        Long userId = UserContext.getUser();
        // 2.获取Key
        String key = RedisConstants.LIKES_BIZ_KEY_PREFIX + recordDTO.getBizType() + recordDTO.getBizId();
        // 3.执行SREM命令
        Long result = redisTemplate.opsForSet().remove(key, userId.toString());
        return result != null && result > 0;
    }

    private boolean like(LikeRecordFormDTO recordDTO) {
        // 1.获取用户id
        Long userId = UserContext.getUser();
        // 2.获取Key
        String key = RedisConstants.LIKES_BIZ_KEY_PREFIX + recordDTO.getBizType() + recordDTO.getBizId();
        // 3.执行SADD命令
        Long result = redisTemplate.opsForSet().add(key, userId.toString());
        return result != null && result > 0;
    }
}
