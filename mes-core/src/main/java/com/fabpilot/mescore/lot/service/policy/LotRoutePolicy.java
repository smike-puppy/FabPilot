package com.fabpilot.mescore.lot.service.policy;

import com.fabpilot.mescore.lot.model.Lot;
import com.fabpilot.mescore.process.model.RouteStep;

/** Lot 当前工艺位置的公共一致性规则。 */
public final class LotRoutePolicy {
    private LotRoutePolicy() {
    }

    /** 当前 Step 必须存在、属于 Lot 的 Route，并配置所需设备组。 */
    public static boolean isCurrentStepValid(Lot lot, RouteStep routeStep) {
        return routeStep != null
                && lot.getRouteId().equals(routeStep.getRouteId())
                && routeStep.getRequiredEquipmentGroupId() != null;
    }
}