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
        return queryReplyPageInternal(query, false);
    }

    @Override
    @SuppressWarnings("unchecked")
    public PageDTO<ReplyVO> queryReplyPageAdmin(ReplyPageQuery query) {
        return queryReplyPageInternal(query, true);
    }

    /**
     * 分页查询回复列表的内部方法
     * 
     * @param query   查询条件
     * @param isAdmin 是否是管理端查询：true-管理端（不过滤隐藏、无视匿名），false-用户端（过滤隐藏、尊重匿名）
     */
    private PageDTO<ReplyVO> queryReplyPageInternal(ReplyPageQuery query, boolean isAdmin) {
        // 1.校验参数：questionId和answerId至少要有一个
        Long questionId = query.getQuestionId();
        Long answerId = query.getAnswerId();
        if (questionId == null && answerId == null) {
            throw new BadRequestException("问题id和回答id不能都为空");
        }

        // 2.分页查询回复数据，按点赞数排序
        // 管理端不过滤隐藏的回复，用户端只查询未隐藏的
        Page<InteractionReply> page = lambdaQuery()
                .eq(questionId != null, InteractionReply::getQuestionId, questionId)
                .eq(InteractionReply::getAnswerId, answerId == null ? 0L : answerId)
                .eq(!isAdmin, InteractionReply::getHidden, false)
                .orderByDesc(InteractionReply::getLikedTimes)
                .page(query.toMpPage());
        List<InteractionReply> records = page.getRecords();
        if (CollUtils.isEmpty(records)) {
            return PageDTO.empty(page);
        }

        // 3.收集用户id（回复者id和目标用户id）
        Set<Long> userIds = new HashSet<>();
        for (InteractionReply r : records) {
            // 管理端无视匿名，所有回复都要查询用户信息；用户端只有非匿名才查询
            if (isAdmin || !r.getAnonymity()) {
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

            // 5.1.封装回复者信息（管理端无视匿名，用户端非匿名时才返回）
            if (isAdmin || !r.getAnonymity()) {
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

    @Override
    public void hiddenReply(Long id, Boolean hidden) {
        // 1.查询回复是否存在
        InteractionReply reply = getById(id);
        if (reply == null) {
            throw new BadRequestException("回复不存在");
        }

        // 2.更新当前回复的hidden状态
        InteractionReply updateReply = new InteractionReply();
        updateReply.setId(id);
        updateReply.setHidden(hidden);
        updateById(updateReply);

        // 3.如果是回答（answerId为0或null），则同时隐藏/显示该回答下的所有评论
        if (reply.getAnswerId() == null || reply.getAnswerId() == 0L) {
            lambdaUpdate()
                    .eq(InteractionReply::getAnswerId, id)
                    .set(InteractionReply::getHidden, hidden)
                    .update();
        }
    }
}
