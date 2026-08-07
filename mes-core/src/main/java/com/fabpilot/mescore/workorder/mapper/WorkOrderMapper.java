package com.fabpilot.mescore.workorder.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.fabpilot.mescore.workorder.model.WorkOrder;
import org.apache.ibatis.annotations.Mapper;

/** 工单主数据的基础 CRUD Mapper。 */
@Mapper
public interface WorkOrderMapper extends BaseMapper<WorkOrder> { }
