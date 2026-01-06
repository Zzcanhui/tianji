package com.tianji.learning.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.tianji.common.utils.BeanUtils;
import com.tianji.common.utils.UserContext;
import com.tianji.learning.domain.dto.ReplyDTO;
import com.tianji.learning.domain.po.InteractionQuestion;
import com.tianji.learning.domain.po.InteractionReply;
import com.tianji.learning.enums.QuestionStatus;
import com.tianji.learning.mapper.InteractionQuestionMapper;
import com.tianji.learning.mapper.InteractionReplyMapper;
import com.tianji.learning.service.IInteractionReplyService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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

    //解决循环依赖问题，因为InteractionReplyServiceImpl依赖InteractionQuestionServiceImpl，而InteractionQuestionServiceImpl依赖InteractionReplyServiceImpl
    private final InteractionQuestionMapper questionMapper;

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
}
