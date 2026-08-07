package com.fabpilot.mescore.equipment.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.fabpilot.mescore.equipment.model.Equipment;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/** Equipment 当前快照的基础 CRUD Mapper。 */
@Mapper
public interface EquipmentMapper extends BaseMapper<Equipment> {

    /** 判断设备是否属于当前 Step 要求的能力组。 */
    @Select("""
            SELECT COUNT(*)
            FROM equipment_group_member
            WHERE equipment_group_id = #{equipmentGroupId}
              AND equipment_id = #{equipmentId}
            """)
    int countGroupMembership(
            @Param("equipmentGroupId") Long equipmentGroupId,
            @Param("equipmentId") Long equipmentId);
}
