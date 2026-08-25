package com.researchflow.workspace;

import com.researchflow.persistence.WorkspaceEntity;
import com.researchflow.persistence.WorkspaceMemberEntity;
import com.researchflow.persistence.WorkspaceMemberRepository;
import com.researchflow.persistence.WorkspaceRepository;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class WorkspaceServiceTest {
    private final WorkspaceRepository workspaces = mock(WorkspaceRepository.class);
    private final WorkspaceMemberRepository members = mock(WorkspaceMemberRepository.class);
    private final WorkspaceService service = new WorkspaceService(workspaces, members);

    @Test
    void ownerIncludesEditorPermissions() {
        when(members.findByWorkspaceIdAndUserId("workspace", "owner"))
                .thenReturn(Optional.of(new WorkspaceMemberEntity("workspace", "owner", WorkspaceRole.OWNER)));

        assertEquals(WorkspaceRole.OWNER, service.require("workspace", "owner", WorkspaceRole.EDITOR));
    }

    @Test
    void viewerCannotEditWorkspace() {
        when(members.findByWorkspaceIdAndUserId("workspace", "viewer"))
                .thenReturn(Optional.of(new WorkspaceMemberEntity("workspace", "viewer", WorkspaceRole.VIEWER)));

        assertThrows(SecurityException.class,
                () -> service.require("workspace", "viewer", WorkspaceRole.EDITOR));
    }
}
