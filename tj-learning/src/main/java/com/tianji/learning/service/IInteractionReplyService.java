package com.tianji.learning.service;

import com.tianji.common.domain.dto.PageDTO;
import com.tianji.learning.domain.dto.ReplyDTO;
import com.tianji.learning.domain.po.InteractionReply;
import com.baomidou.mybatisplus.extension.service.IService;
import com.tianji.learning.domain.query.ReplyPageQuery;
import com.tianji.learning.domain.vo.ReplyVO;

import javax.validation.Valid;

/**
 * <p>
 * 互动问题的回答或评论 服务类
 * </p>
 *
 * @author author
 * @since 2026-01-05
 */
public interface IInteractionReplyService extends IService<InteractionReply> {

    void saveReply(@Valid ReplyDTO replyDTO);

    PageDTO<ReplyVO> queryReplyPage(ReplyPageQuery query);

    PageDTO<ReplyVO> queryReplyPageAdmin(ReplyPageQuery query);

    void hiddenReply(Long id, Boolean hidden);
}
