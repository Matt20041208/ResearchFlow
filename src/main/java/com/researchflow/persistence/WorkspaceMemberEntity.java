package com.researchflow.persistence;

import com.researchflow.workspace.WorkspaceRole;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.Instant;

@Entity
@Table(name = "workspace_member", uniqueConstraints = @UniqueConstraint(columnNames = {"workspaceId", "userId"}))
public class WorkspaceMemberEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String workspaceId;
    private String userId;
    @Enumerated(EnumType.STRING)
    private WorkspaceRole role;
    private Instant joinedAt;

    protected WorkspaceMemberEntity() {}

    public WorkspaceMemberEntity(String workspaceId, String userId, WorkspaceRole role) {
        this.workspaceId = workspaceId;
        this.userId = userId;
        this.role = role;
        this.joinedAt = Instant.now();
    }

    public String getWorkspaceId() { return workspaceId; }
    public String getUserId() { return userId; }
    public WorkspaceRole getRole() { return role; }
    public Instant getJoinedAt() { return joinedAt; }
    public void setRole(WorkspaceRole role) { this.role = role; }
}
