package com.example.gsb.health;

import java.util.Map;
import java.util.Set;

/**
 * 单次评估结果。
 *
 * @param ownScores       每个依赖的自身健康分
 * @param effectiveScores 每个依赖含传播的有效健康分
 * @param degradedDeps    本次评估后处于降级状态的依赖
 * @param degradedFeatures 受影响而被降级的业务功能（去重并集）
 * @param featureCauses   每个被降级功能的触发依赖集合（影响面依据）
 * @param newEvents       本次评估新产生的审计事件
 */
public record EvaluationReport(Map<String, Double> ownScores, Map<String, Double> effectiveScores,
                               Set<String> degradedDeps, Set<String> degradedFeatures,
                               Map<String, Set<String>> featureCauses,
                               java.util.List<AuditEvent> newEvents) {
}
