package com.researchflow.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface WorkspaceMemberRepository extends JpaRepository<WorkspaceMemberEntity, Long> {
    Optional<WorkspaceMemberEntity> findByWorkspaceIdAndUserId(String workspaceId, String userId);
    List<WorkspaceMemberEntity> findByWorkspaceIdOrderByJoinedAtAsc(String workspaceId);
    List<WorkspaceMemberEntity> findByUserIdOrderByJoinedAtAsc(String userId);
}
