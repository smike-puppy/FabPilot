package com.fabpilot.mescore.lot.service;

import com.fabpilot.mescore.lot.dto.LotCommandResultTO;
import com.fabpilot.mescore.lot.dto.ReleaseLotRequestTO;
import com.fabpilot.mescore.lot.dto.TrackInLotRequestTO;
import com.fabpilot.mescore.lot.dto.TrackOutLotRequestTO;

/** 统一承载 Lot 状态变更的写侧业务入口。 */
public interface LotCommandService {

    /**
     * 将 CREATED 状态的 Lot 释放为 READY，并追加不可变生产履历。
     */
    LotCommandResultTO release(String lotCode, ReleaseLotRequestTO request);

    /** 将 READY Lot 安全上机，更新 Lot、Equipment 快照并追加两类履历。 */
    LotCommandResultTO trackIn(String lotCode, TrackInLotRequestTO request);

    /** 将 RUNNING Lot 安全下机，释放设备并推进至下一 Step 或完成。 */
    LotCommandResultTO trackOut(String lotCode, TrackOutLotRequestTO request);
}