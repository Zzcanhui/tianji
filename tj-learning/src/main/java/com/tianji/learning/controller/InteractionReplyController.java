package com.tianji.learning.controller;


import com.tianji.learning.domain.dto.ReplyDTO;
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
@RequestMapping("/replies")
@Api(tags = "互动问题回答或评论相关接口")
@RequiredArgsConstructor
public class InteractionReplyController {

    private final IInteractionReplyService replyService;

    @ApiOperation("新增回答或评论")
    @PostMapping
    public void saveReply(@Valid @RequestBody ReplyDTO replyDTO) {
        replyService.saveReply(replyDTO);
    }

    @ApiOperation("分页查询互动问题的回答或评论")
    @GetMapping("page")
    public PageDTO<ReplyVO> queryReplyPage(ReplyPageQuery query) {
        return replyService.queryReplyPage(query);
    }

}
