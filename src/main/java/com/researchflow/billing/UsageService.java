package com.researchflow.billing;

import com.researchflow.persistence.KnowledgeDocumentRepository;
import com.researchflow.persistence.UsageRecordEntity;
import com.researchflow.persistence.UsageRecordRepository;
import com.researchflow.workspace.WorkspaceService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.TemporalAdjusters;
import java.util.EnumMap;
import java.util.Map;

@Service
public class UsageService {
    private final UsageRecordRepository usageRepository;
    private final KnowledgeDocumentRepository documentRepository;
    private final WorkspaceService workspaceService;

    public UsageService(UsageRecordRepository usageRepository, KnowledgeDocumentRepository documentRepository,
                        WorkspaceService workspaceService) {
        this.usageRepository = usageRepository;
        this.documentRepository = documentRepository;
        this.workspaceService = workspaceService;
    }

    @Transactional(readOnly = true)
    public void requireReportCapacity(String workspaceId) {
        PlanTier tier = workspaceService.get(workspaceId).getPlanTier();
        long used = usageRepository.countByWorkspaceIdAndUsageTypeAndOccurredAtGreaterThanEqual(
                workspaceId, UsageType.REPORT_CREATED, periodStart());
        if (used >= tier.monthlyReports()) throw new IllegalStateException("本月报告配额已用尽");
    }

    @Transactional(readOnly = true)
    public void requireDocumentCapacity(String workspaceId) {
        PlanTier tier = workspaceService.get(workspaceId).getPlanTier();
        long used = documentRepository.findByWorkspaceIdOrderByCreatedAtDesc(workspaceId).size();
        if (used >= tier.documents()) throw new IllegalStateException("知识文档配额已用尽");
    }

    @Transactional
    public void record(String workspaceId, String userId, UsageType type, String referenceId) {
        record(workspaceId, userId, type, 1, referenceId);
    }

    @Transactional
    public void record(String workspaceId, String userId, UsageType type, long units, String referenceId) {
        usageRepository.save(new UsageRecordEntity(workspaceId, userId, type, units, referenceId));
    }

    @Transactional(readOnly = true)
    public UsageSummary summary(String workspaceId) {
        var workspace = workspaceService.get(workspaceId);
        Map<UsageType, Long> usage = new EnumMap<>(UsageType.class);
        for (UsageType type : UsageType.values()) usage.put(type, 0L);
        usageRepository.findByWorkspaceIdAndOccurredAtGreaterThanEqual(workspaceId, periodStart())
                .forEach(record -> usage.merge(record.getUsageType(), record.getUnits(), Long::sum));
        PlanTier tier = workspace.getPlanTier();
        double estimatedCost = usage.get(UsageType.TOKEN_USED) / 1000.0 * 0.001;
        return new UsageSummary(workspaceId, tier, periodStart(), Map.copyOf(usage), Map.of(
                "monthlyReports", tier.monthlyReports(), "subscriptions", tier.subscriptions(),
                "documents", tier.documents()), estimatedCost);
    }

    private Instant periodStart() {
        LocalDate firstDay = LocalDate.now(ZoneOffset.UTC).with(TemporalAdjusters.firstDayOfMonth());
        return firstDay.atStartOfDay().toInstant(ZoneOffset.UTC);
    }
}
