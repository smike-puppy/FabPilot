package com.fabpilot.mescore.lot.service;

import com.fabpilot.mescore.lot.dto.HoldLotRequestTO;
import com.fabpilot.mescore.lot.dto.LotCommandResultTO;
import com.fabpilot.mescore.lot.dto.ReleaseHoldLotRequestTO;
import com.fabpilot.mescore.lot.dto.ReleaseLotRequestTO;
import com.fabpilot.mescore.lot.dto.ScrapLotRequestTO;
import com.fabpilot.mescore.lot.dto.TrackInLotRequestTO;
import com.fabpilot.mescore.lot.dto.TrackOutLotRequestTO;

/** Lot 双状态机的统一写侧入口；每个方法都在单一事务内更新快照并追加履历。 */
public interface LotCommandService {
    /** CREATED + RELEASED → READY + RELEASED，并定位路线首 Step。 */
    LotCommandResultTO release(String lotCode, ReleaseLotRequestTO request);
    /** READY + RELEASED → RUNNING + RELEASED，同时绑定并占用目标设备。 */
    LotCommandResultTO trackIn(String lotCode, TrackInLotRequestTO request);
    /** RUNNING + RELEASED → 下一 Step READY 或末工序 COMPLETED，同时释放当前设备。 */
    LotCommandResultTO trackOut(String lotCode, TrackOutLotRequestTO request);
    /** READY/RUNNING + RELEASED → 原执行状态 + HELD；不操作设备。 */
    LotCommandResultTO hold(String lotCode, HoldLotRequestTO request);
    /** READY/RUNNING + HELD → 原执行状态 + RELEASED；不推进工艺。 */
    LotCommandResultTO releaseHold(String lotCode, ReleaseHoldLotRequestTO request);
    /** 任意非终态 → SCRAPPED + RELEASED，并解除可能存在的设备绑定。 */
    LotCommandResultTO scrap(String lotCode, ScrapLotRequestTO request);
}