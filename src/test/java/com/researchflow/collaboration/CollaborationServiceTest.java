package com.researchflow.collaboration;

import com.researchflow.persistence.ReportCommentRepository;
import com.researchflow.persistence.ReportVersionEntity;
import com.researchflow.persistence.ReportVersionRepository;
import com.researchflow.persistence.TaskRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CollaborationServiceTest {
    @Test
    void incrementsReportVersionNumbers() {
        ReportVersionRepository versions = mock(ReportVersionRepository.class);
        CollaborationService service = new CollaborationService(versions,
                mock(ReportCommentRepository.class), mock(TaskRepository.class));
        when(versions.countByTaskId("task")).thenReturn(2L);

        service.saveVersion("task", "report", "user");

        ArgumentCaptor<ReportVersionEntity> captor = ArgumentCaptor.forClass(ReportVersionEntity.class);
        verify(versions).save(captor.capture());
        assertEquals(3, captor.getValue().getVersionNumber());
    }
}
