package com.tianji.learning.service.impl;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.tianji.api.client.user.UserClient;
import com.tianji.api.dto.user.UserDTO;
import com.tianji.common.domain.dto.PageDTO;
import com.tianji.common.exceptions.BadRequestException;
import com.tianji.common.utils.BeanUtils;
import com.tianji.common.utils.CollUtils;
import com.tianji.common.utils.UserContext;
import com.tianji.learning.domain.dto.ReplyDTO;
import com.tianji.learning.domain.po.InteractionQuestion;
import com.tianji.learning.domain.po.InteractionReply;
import com.tianji.learning.domain.query.ReplyPageQuery;
import com.tianji.learning.domain.vo.ReplyVO;
import com.tianji.learning.enums.QuestionStatus;
import com.tianji.learning.mapper.InteractionQuestionMapper;
import com.tianji.learning.mapper.InteractionReplyMapper;
import com.tianji.learning.service.IInteractionReplyService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

/**
 * <p>
 * 互动问题的回答或评论 服务实现类
 * </p>
 *
 * @author author
 * @since 2026-01-05
 */
@Service
@RequiredArgsConstructor
public class InteractionReplyServiceImpl extends ServiceImpl<InteractionReplyMapper, InteractionReply>
        implements IInteractionReplyService {

    // 解决循环依赖问题，因为InteractionReplyServiceImpl依赖InteractionQuestionServiceImpl，而InteractionQuestionServiceImpl依赖InteractionReplyServiceImpl
    private final InteractionQuestionMapper questionMapper;
    private final UserClient userClient;

    @Override
    @Transactional
    public void saveReply(ReplyDTO replyDTO) {
        // 1.获取当前登录用户
        Long userId = UserContext.getUser();

        // 2.保存回复到数据库
        InteractionReply reply = BeanUtils.copyBean(replyDTO, InteractionReply.class);
        reply.setUserId(userId);
        save(reply);

        // 3.判断是否是回答（answerId为空则是回答，不为空则是评论）
        Long answerId = replyDTO.getAnswerId();
        if (answerId == null) {
            // 3.1.这是一个回答，需要更新问题表：累加回答次数，更新最新回答id
            questionMapper.updateAnswerInfo(replyDTO.getQuestionId(), reply.getId());
        } else {
            // 3.2.这是一个评论，需要累加回答下的评论次数
            lambdaUpdate()
                    .eq(InteractionReply::getId, answerId)
                    .setSql("reply_times = reply_times + 1")
                    .update();
        }

        // 4.判断是否是学生提交，如果是则标记问题状态为未查看
        if (Boolean.TRUE.equals(replyDTO.getIsStudent())) {
            InteractionQuestion updateQuestion = new InteractionQuestion();
            updateQuestion.setId(replyDTO.getQuestionId());
            updateQuestion.setStatus(QuestionStatus.UN_CHECK);
            questionMapper.updateById(updateQuestion);
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public PageDTO<ReplyVO> queryReplyPage(ReplyPageQuery query) {
        // 1.校验参数：questionId和answerId至少要有一个
        Long questionId = query.getQuestionId();
        Long answerId = query.getAnswerId();
        if (questionId == null && answerId == null) {
            throw new BadRequestException("问题id和回答id不能都为空");
        }

        // 2.分页查询回复数据，按点赞数排序
        Page<InteractionReply> page = lambdaQuery()
                .eq(questionId != null, InteractionReply::getQuestionId, questionId)
                .eq(InteractionReply::getAnswerId, answerId == null ? 0L : answerId)
                .eq(InteractionReply::getHidden, false)
                .orderByDesc(InteractionReply::getLikedTimes)
                .page(query.toMpPage());
        List<InteractionReply> records = page.getRecords();
        if (CollUtils.isEmpty(records)) {
            return PageDTO.empty(page);
        }

        // 3.收集用户id（回复者id和目标用户id）
        Set<Long> userIds = new HashSet<>();
        for (InteractionReply r : records) {
            if (!r.getAnonymity()) { // 非匿名才需要查询用户信息
                userIds.add(r.getUserId());
            }
            if (r.getTargetUserId() != null) {
                userIds.add(r.getTargetUserId());
            }
        }

        // 4.查询用户信息
        userIds.remove(null);
        Map<Long, UserDTO> userMap = new HashMap<>(userIds.size());
        if (CollUtils.isNotEmpty(userIds)) {
            List<UserDTO> users = userClient.queryUserByIds(userIds);
            userMap = users.stream().collect(Collectors.toMap(UserDTO::getId, u -> u));
        }

        // 5.封装VO
        List<ReplyVO> voList = new ArrayList<>(records.size());
        for (InteractionReply r : records) {
            ReplyVO vo = BeanUtils.copyBean(r, ReplyVO.class);
            voList.add(vo);

            // 5.1.封装回复者信息（非匿名时）
            if (!r.getAnonymity()) {
                UserDTO user = userMap.get(r.getUserId());
                if (user != null) {
                    vo.setUserName(user.getName());
                    vo.setUserIcon(user.getIcon());
                    vo.setUserType(user.getType());
                }
            }

            // 5.2.封装目标用户昵称（评论特有）
            if (r.getTargetUserId() != null) {
                UserDTO targetUser = userMap.get(r.getTargetUserId());
                if (targetUser != null) {
                    vo.setTargetUserName(targetUser.getName());
                }
            }
        }

        return PageDTO.of(page, voList);
    }
}
