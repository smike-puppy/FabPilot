package com.fabpilot.mescore.lot.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.fabpilot.mescore.lot.model.LotTransaction;
import org.apache.ibatis.annotations.Mapper;

/** LotTransaction 的只追加履历 Mapper；写侧禁止调用更新或删除能力。 */
@Mapper
public interface LotTransactionMapper extends BaseMapper<LotTransaction> { }
