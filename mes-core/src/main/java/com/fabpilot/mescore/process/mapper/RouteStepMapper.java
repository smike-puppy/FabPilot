package com.fabpilot.mescore.process.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.fabpilot.mescore.process.model.RouteStep;
import org.apache.ibatis.annotations.Mapper;

/** 路线 Step 的查询 Mapper，写侧会据此校验当前工序和设备能力组。 */
@Mapper
public interface RouteStepMapper extends BaseMapper<RouteStep> { }
