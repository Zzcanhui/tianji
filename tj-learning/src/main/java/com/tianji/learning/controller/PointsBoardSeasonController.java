package com.tianji.learning.controller;


import com.tianji.learning.domain.po.PointsBoardSeason;
import com.tianji.learning.service.IPointsBoardSeasonService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * <p>
 * 赛季相关接口
 * </p>
 *
 * @author canhui
 * @since 2026-01-22
 */
@RestController
@RequestMapping("/boards")
@RequiredArgsConstructor
@Api(tags = "赛季相关接口")
public class PointsBoardSeasonController {

    private final IPointsBoardSeasonService seasonService;

    @GetMapping("/seasons/list")
    @ApiOperation("查询赛季列表")
    public List<PointsBoardSeason> querySeasonList() {
        return seasonService.list();
    }
}
