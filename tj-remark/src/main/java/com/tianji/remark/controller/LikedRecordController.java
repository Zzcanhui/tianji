package com.tianji.remark.controller;


import com.tianji.remark.service.ILikedRecordService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RequestMapping;

import org.springframework.web.bind.annotation.RestController;

/**
 * <p>
 * 点赞记录表 前端控制器
 * </p>
 *
 * @author author
 * @since 2026-01-06
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/likedRecord")
public class LikedRecordController {

    private final ILikedRecordService likedRecordService;
}
