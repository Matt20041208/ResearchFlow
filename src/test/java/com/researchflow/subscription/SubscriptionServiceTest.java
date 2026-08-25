package com.researchflow.subscription;

import com.researchflow.billing.UsageService;
import com.researchflow.persistence.TopicSubscriptionRepository;
import com.researchflow.persistence.WorkspaceEntity;
import com.researchflow.service.ResearchTaskService;
import com.researchflow.workspace.WorkspaceService;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SubscriptionServiceTest {
    @Test
    void enforcesSubscriptionQuota() {
        TopicSubscriptionRepository repository = mock(TopicSubscriptionRepository.class);
        WorkspaceService workspaces = mock(WorkspaceService.class);
        SubscriptionService service = new SubscriptionService(repository, workspaces,
                mock(UsageService.class), mock(ResearchTaskService.class));
        when(workspaces.get("workspace")).thenReturn(new WorkspaceEntity("Free", "owner"));
        when(repository.countByWorkspaceIdAndEnabledTrue("workspace")).thenReturn(1L);

        assertThrows(IllegalStateException.class, () -> service.create("owner",
                new SubscriptionRequest("workspace", "Daily", "Research AI", 60)));
    }
}
