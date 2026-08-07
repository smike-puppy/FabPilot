package com.fabpilot.mescore.lot.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.fabpilot.mescore.lot.model.Lot;
import org.apache.ibatis.annotations.Mapper;

/** Lot 当前快照的基础 CRUD Mapper；复杂诊断查询应新增专用方法而非放入 Controller。 */
@Mapper
public interface LotMapper extends BaseMapper<Lot> { }
