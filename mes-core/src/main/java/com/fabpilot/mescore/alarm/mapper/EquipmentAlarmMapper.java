package com.fabpilot.mescore.alarm.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.fabpilot.mescore.alarm.model.EquipmentAlarm;
import org.apache.ibatis.annotations.Mapper;

/** 设备告警快照的数据访问入口；业务状态规则只能由告警领域服务决定。 */
@Mapper
public interface EquipmentAlarmMapper extends BaseMapper<EquipmentAlarm> {
}