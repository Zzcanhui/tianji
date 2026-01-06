package com.tianji.learning.mapper;

import com.tianji.learning.domain.po.InteractionQuestion;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

/**
 * <p>
 * 互动提问的问题表 Mapper 接口
 * </p>
 *
 * @author author
 * @since 2026-01-05
 */
public interface InteractionQuestionMapper extends BaseMapper<InteractionQuestion> {

    @Update("UPDATE interaction_question SET answer_times = answer_times + 1, latest_answer_id = #{answerId} WHERE id = #{questionId}")
    void updateAnswerInfo(@Param("questionId") Long questionId, @Param("answerId") Long answerId);
}
