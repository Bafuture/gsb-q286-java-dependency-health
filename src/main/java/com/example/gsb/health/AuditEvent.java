package com.example.gsb.health;

import java.time.Instant;
import java.util.Set;

/**
 * 降级决策审计事件。
 *
 * @param timestamp        事件发生时间
 * @param type             事件类型：DEGRADED 触发降级 / RECOVERED 解除降级
 * @param dependency       被判定不健康或恢复的依赖
 * @param ownScore         该依赖自身健康分（未传播）
 * @param effectiveScore   含下游传播后的有效健康分
 * @param reason           判定依据（分数、阈值、传播来源、连续恢复次数等）
 * @param impactedFeatures 本次决策影响的业务功能清单
 */
public record AuditEvent(Instant timestamp, Type type, String dependency, double ownScore,
                         double effectiveScore, String reason, Set<String> impactedFeatures) {

    public enum Type {DEGRADED, RECOVERED}

    public AuditEvent {
        impactedFeatures = Set.copyOf(impactedFeatures);
    }
}
