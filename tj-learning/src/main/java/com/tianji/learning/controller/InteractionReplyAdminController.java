package com.tianji.learning.controller;

import com.tianji.common.domain.dto.PageDTO;
import com.tianji.learning.domain.dto.ReplyDTO;
import com.tianji.learning.domain.query.ReplyPageQuery;
import com.tianji.learning.domain.vo.ReplyVO;
import com.tianji.learning.service.IInteractionReplyService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;

/**
 * <p>
 * 互动问题的回答或评论 前端控制器
 * </p>
 *
 * @author author
 * @since 2026-01-05
 */
@RestController
@RequestMapping("/admin/replies")
@Api(tags = "管理端互动问题回答或评论相关接口")
@RequiredArgsConstructor
public class InteractionReplyAdminController {

    private final IInteractionReplyService replyService;

    @ApiOperation("管理端分页查询互动问题的回答或评论")
    @GetMapping("page")
    public PageDTO<ReplyVO> queryReplyPageAdmin(ReplyPageQuery query) {
        return replyService.queryReplyPageAdmin(query);
    }

    @ApiOperation("管理端显示或隐藏评论")
    @PutMapping("{id}/hidden/{hidden}")
    public void hiddenReply(@PathVariable("id") Long id, @PathVariable("hidden") Boolean hidden) {
        replyService.hiddenReply(id, hidden);
    }
}
