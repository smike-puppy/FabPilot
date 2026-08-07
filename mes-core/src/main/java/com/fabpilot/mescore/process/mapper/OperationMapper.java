package com.fabpilot.mescore.process.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.fabpilot.mescore.process.model.Operation;
import org.apache.ibatis.annotations.Mapper;

/** 可复用工序定义的查询 Mapper。 */
@Mapper
public interface OperationMapper extends BaseMapper<Operation> { }
