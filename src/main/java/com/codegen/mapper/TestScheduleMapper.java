package com.codegen.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.codegen.entity.TestSchedule;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface TestScheduleMapper extends BaseMapper<TestSchedule> {

    /**
     * 行锁读取单条任务：在事务内调用可对目标行加 FOR UPDATE，
     * 配合 {@code SELECT ... FOR UPDATE} 实现多实例下「认领即独占」，避免重复触发。
     */
    @Select("SELECT * FROM test_schedule WHERE id = #{id} FOR UPDATE")
    TestSchedule selectByIdForUpdate(@Param("id") Long id);
}
