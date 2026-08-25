package com.researchflow.billing;

import com.researchflow.persistence.KnowledgeDocumentRepository;
import com.researchflow.persistence.UsageRecordRepository;
import com.researchflow.persistence.WorkspaceEntity;
import com.researchflow.workspace.WorkspaceService;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class UsageServiceTest {
    private final UsageRecordRepository usage = mock(UsageRecordRepository.class);
    private final KnowledgeDocumentRepository documents = mock(KnowledgeDocumentRepository.class);
    private final WorkspaceService workspaces = mock(WorkspaceService.class);
    private final UsageService service = new UsageService(usage, documents, workspaces);

    @Test
    void enforcesMonthlyReportQuota() {
        WorkspaceEntity workspace = new WorkspaceEntity("Free", "owner");
        when(workspaces.get("workspace")).thenReturn(workspace);
        when(usage.countByWorkspaceIdAndUsageTypeAndOccurredAtGreaterThanEqual(
                eq("workspace"), eq(UsageType.REPORT_CREATED), any())).thenReturn(10L);

        assertThrows(IllegalStateException.class, () -> service.requireReportCapacity("workspace"));
    }

    @Test
    void allowsReportsBelowQuota() {
        WorkspaceEntity workspace = new WorkspaceEntity("Free", "owner");
        when(workspaces.get("workspace")).thenReturn(workspace);
        when(usage.countByWorkspaceIdAndUsageTypeAndOccurredAtGreaterThanEqual(
                eq("workspace"), eq(UsageType.REPORT_CREATED), any())).thenReturn(2L);

        assertDoesNotThrow(() -> service.requireReportCapacity("workspace"));
    }
}
