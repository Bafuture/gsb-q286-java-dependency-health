package com.example.gsb.health;

import java.time.Duration;
import java.util.Set;

/**
 * 组件统计快照。
 *
 * @param dependencyCount           已登记依赖数
 * @param currentlyDegraded         当前处于降级状态的依赖集合
 * @param degradationTriggerCount   累计降级触发次数（重复触发分别计数）
 * @param completedRecoveryCount    已完成的恢复次数
 * @param averageRecoveryTime       平均恢复时间（从触发降级到解除降级），尚无完成恢复时为 null
 */
public record DegradationStats(int dependencyCount, Set<String> currentlyDegraded,
                               int degradationTriggerCount, int completedRecoveryCount,
                               Duration averageRecoveryTime) {

    public DegradationStats {
        currentlyDegraded = Set.copyOf(currentlyDegraded);
    }
}
