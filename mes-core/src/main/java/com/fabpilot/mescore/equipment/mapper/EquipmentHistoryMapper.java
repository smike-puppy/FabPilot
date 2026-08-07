package com.fabpilot.mescore.equipment.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.fabpilot.mescore.equipment.model.EquipmentHistory;
import org.apache.ibatis.annotations.Mapper;

/** EquipmentHistory 的事件履历 Mapper，用于诊断追溯。 */
@Mapper
public interface EquipmentHistoryMapper extends BaseMapper<EquipmentHistory> { }
