package com.tianji.learning.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.tianji.api.client.course.CourseClient;
import com.tianji.api.dto.course.CourseSimpleInfoDTO;
import com.tianji.common.domain.dto.PageDTO;
import com.tianji.common.domain.query.PageQuery;
import com.tianji.common.exceptions.BadRequestException;
import com.tianji.common.utils.BeanUtils;
import com.tianji.common.utils.CollUtils;
import com.tianji.common.utils.UserContext;
import com.tianji.learning.domain.po.LearningLesson;
import com.tianji.learning.domain.vo.LearningLessonVO;
import com.tianji.learning.enums.LessonStatus;
import com.tianji.learning.enums.PlanStatus;
import com.tianji.learning.mapper.LearningLessonMapper;
import com.tianji.learning.service.ILearningLessonService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * <p>
 * 学生课程表 服务实现类
 * </p>
 *
 * @author author
 * @since 2025-12-18
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class LearningLessonServiceImpl extends ServiceImpl<LearningLessonMapper, LearningLesson> implements ILearningLessonService {

    private final CourseClient courseClient;

    @Override
    @Transactional
    public void addUserLesson(Long userId, List<Long> courseIds) {
        // 1.查询课程有效期
        List<CourseSimpleInfoDTO> cInfoList = courseClient.getSimpleInfoList(courseIds);
        if(CollUtils.isEmpty(cInfoList)) {
            // 课程不存在,无法添加
            log.error("课程不存在,无法添加到课表,userId:{},courseIds:{}",userId,courseIds);
            return;
        }

        // 2.循环遍历，处理LearningLesson数据
        List<LearningLesson> list = new ArrayList<>(cInfoList.size());
        for (CourseSimpleInfoDTO cInfo : cInfoList) {
            LearningLesson lesson = new LearningLesson();
            // 2.1 获取过期时间
            Integer validDuration = cInfo.getValidDuration();
            if (validDuration!=null&&validDuration>0) {
                LocalDateTime now = LocalDateTime.now();
                lesson.setCreateTime(now);
                lesson.setExpireTime(now.plusDays(validDuration));
            }
            // 2.2 填充userId和courseId
            lesson.setUserId(userId);
            lesson.setCourseId(cInfo.getId());
            list.add(lesson);
        }

        // 3.批量新增
        saveBatch(list);

    }

    @Override
    public PageDTO<LearningLessonVO> queryMyLesson(PageQuery query) {
        // 1.获取当前登录用户
        Long userId = UserContext.getUser();
        // 2.分页查询
        // 2.1 分页查询
        Page<LearningLesson> page = lambdaQuery()
                .eq(LearningLesson::getUserId, userId)
                .page(query.toMpPage("latest_learn_time",false));
        // 2.2 转换为VO
        List<LearningLesson> records = page.getRecords();
        if (CollUtils.isEmpty(records)){
            return PageDTO.empty(page);
        }

        // 3.查询课程信息
        // 3.1.获取课程id
        List<Long> cIds = records.stream().map(LearningLesson::getCourseId).collect(Collectors.toList());
        // 3.2.查询课程信息
        List<CourseSimpleInfoDTO> cInfoList = courseClient.getSimpleInfoList(cIds);
        if (CollUtils.isEmpty(cInfoList)){
            //课程不存在，无法添加
            throw new BadRequestException("课程信息不存在!");
        }
        // 3.3.把课程集合处理成Map,key是courseId，值是course本身
        Map<Long, CourseSimpleInfoDTO> cMap = cInfoList.stream()
                .collect(Collectors.toMap(CourseSimpleInfoDTO::getId, c-> c));

        // 4.封装VO返回
        List<LearningLessonVO> list =new ArrayList<>(records.size());
        // 4.1.循环遍历，把LearningLesson转为VO
        for (LearningLesson r : records) {
            // 4.2.拷贝基础属性到vo
            LearningLessonVO vo = BeanUtils.copyBean(r, LearningLessonVO.class);
            // 4.3.获取课程信息，填充到vo
            CourseSimpleInfoDTO cInfo = cMap.get(r.getCourseId());
            vo.setCourseName(cInfo.getName());
            vo.setCourseCoverUrl(cInfo.getCoverUrl());
            vo.setSections(cInfo.getSectionNum());
            list.add(vo);
        }

        return PageDTO.of(page,list);
    }

    /**
     * 删除课表中课程
     * @param userId 用户id
     * @param courseId 课程id
     */
    public void deleteCourseFromLesson(Long userId, Long courseId) {
        //1.判断当前登录用户id是否为null
        //调用这个方法有两种情况：用户直接删除已失效的课程 -> 在controller中调用，没有获取用户id，只传了null值
        //                      用户退款后触发课表自动删除 -> 在listener中调用，直接获取了OrderBasicDTO中的用户id
        //listenCourseRefund已有健壮性判断，这里目的是在直接删除已失效的课程时，获取用户id
        if (userId == null) {
            userId = UserContext.getUser();
        }

        //2.删除课程
        //第一种写法：直接通过Wrappers.<LearningLesson>lambdaQuery()创建LambdaQueryWrapper对象，直接简洁
        remove(Wrappers.<LearningLesson>lambdaQuery()
                .eq(LearningLesson::getUserId, userId)
                .eq(LearningLesson::getCourseId, courseId));

        //第二种写法：先创建普通QueryWrapper对象，再通过.lambda()方法转换成LambdaQueryWrapper对象，多一次转换，冗余
//        remove(new QueryWrapper<LearningLesson>().lambda()
//                        .eq(LearningLesson::getUserId, userId)
//                        .in(LearningLesson::getCourseId, courseId));
    }

    /**
     * 校验当前用户是否可以学习当前课程
     * @param courseId 课程id
     * @return lessonId，如果是报名了则返回lessonId，否则返回空
     */
    public Long isLessonValid(Long courseId) {
        //1.获取当前登录用户id
        Long userId = UserContext.getUser();

        //2.查询当前用户的课表learning_lesson    条件:user_id  course_id
        LearningLesson lesson = lambdaQuery()
                .eq(LearningLesson::getUserId, userId)
                .eq(LearningLesson::getCourseId, courseId)
                .one();//联合唯一索引，最多只能查出一条数据
        if (lesson == null) {
            return null;
        }

        //3.校验课程是否过期
        LocalDateTime expireTime = lesson.getExpireTime();
        LocalDateTime now = LocalDateTime.now();
        if (now.isAfter(expireTime)) {  //当前时间已经在过期时间之后，即过期
            return null;
        }

        return lesson.getId();
    }

    /**
     * 查询用户课表中指定课程状态
     * @param courseId 课程id
     * @return 课程状态
     */
    public LearningLessonVO queryLessonByCourseId(Long courseId) {
        //1.获取当前登录用户id
        Long userId = UserContext.getUser();

        //2.查询当前用户的课表learning_lesson    条件:user_id  course_id
        LearningLesson lesson = lambdaQuery()
                .eq(LearningLesson::getUserId, userId)
                .eq(LearningLesson::getCourseId, courseId)
                .one();//联合唯一索引，最多只能查出一条数据
        if (lesson == null) {
            return null;
        }

        //3.po转vo返回
        LearningLessonVO vo = BeanUtils.copyBean(lesson, LearningLessonVO.class);
        return vo;
    }

    /**
     * 统计课程学习人数
     * @param courseId 课程id
     * @return 学习人数
     */
    public Integer countLearningLessonByCourse(Long courseId) {
        //统计课程学习人数就不需要获取当前登录用户id了，因为统计的课程学习人数不需要区分用户
        return lambdaQuery()
                .eq(LearningLesson::getCourseId, courseId)
                .in(LearningLesson::getStatus,  //把未学习、学习中、已学完的状态都算上
                        LessonStatus.NOT_BEGIN.getValue(),
                        LessonStatus.LEARNING.getValue(),
                        LessonStatus.FINISHED.getValue())
                .count();
    }
}
