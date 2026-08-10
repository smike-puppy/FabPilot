package com.fabpilot.mescore.equipment.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.fabpilot.mescore.equipment.model.EquipmentEventDefinition;
import org.apache.ibatis.annotations.Mapper;

/** 设备事件定义 Mapper；事件规则由数据库配置，写侧服务只负责严格执行。 */
@Mapper
public interface EquipmentEventDefinitionMapper extends BaseMapper<EquipmentEventDefinition> { }