package com.example.gsb.health;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** 决策审计日志：保存全部降级/恢复事件以及已完成的恢复耗时。 */
public class AuditTrail {

    private final List<AuditEvent> events = new ArrayList<>();
    private final List<Duration> recoveryTimes = new ArrayList<>();
    private int degradationTriggerCount;

    void recordDegradation(AuditEvent event) {
        events.add(event);
        degradationTriggerCount++;
    }

    void recordRecovery(AuditEvent event, Duration recoveryTime) {
        events.add(event);
        recoveryTimes.add(recoveryTime);
    }

    public List<AuditEvent> events() {
        return Collections.unmodifiableList(events);
    }

    public List<AuditEvent> eventsFor(String dependency) {
        return events.stream().filter(e -> e.dependency().equals(dependency)).toList();
    }

    public int degradationTriggerCount() {
        return degradationTriggerCount;
    }

    public List<Duration> recoveryTimes() {
        return Collections.unmodifiableList(recoveryTimes);
    }
}
