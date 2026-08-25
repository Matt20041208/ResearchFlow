package com.researchflow.workspace;

public enum WorkspaceRole {
    VIEWER(1),
    EDITOR(2),
    OWNER(3);

    private final int level;

    WorkspaceRole(int level) { this.level = level; }

    public boolean includes(WorkspaceRole required) { return level >= required.level; }
}
