package com.researchflow.workspace;

import com.researchflow.billing.PlanTier;
import com.researchflow.persistence.WorkspaceEntity;
import com.researchflow.persistence.WorkspaceMemberEntity;
import com.researchflow.persistence.WorkspaceMemberRepository;
import com.researchflow.persistence.WorkspaceRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class WorkspaceService {
    private final WorkspaceRepository workspaceRepository;
    private final WorkspaceMemberRepository memberRepository;

    public WorkspaceService(WorkspaceRepository workspaceRepository, WorkspaceMemberRepository memberRepository) {
        this.workspaceRepository = workspaceRepository;
        this.memberRepository = memberRepository;
    }

    @Transactional
    public WorkspaceView create(String userId, WorkspaceCreateRequest request) {
        WorkspaceEntity workspace = workspaceRepository.save(new WorkspaceEntity(request.name().trim(), userId));
        memberRepository.save(new WorkspaceMemberEntity(workspace.getId(), userId, WorkspaceRole.OWNER));
        return view(workspace, WorkspaceRole.OWNER);
    }

    @Transactional(readOnly = true)
    public List<WorkspaceView> list(String userId) {
        return memberRepository.findByUserIdOrderByJoinedAtAsc(userId).stream()
                .map(member -> workspaceRepository.findById(member.getWorkspaceId())
                        .map(workspace -> view(workspace, member.getRole())).orElse(null))
                .filter(java.util.Objects::nonNull).toList();
    }

    @Transactional
    public MemberView addOrUpdateMember(String workspaceId, String actorUserId, MemberRequest request) {
        require(workspaceId, actorUserId, WorkspaceRole.OWNER);
        WorkspaceEntity workspace = get(workspaceId);
        if (workspace.getOwnerUserId().equals(request.userId())) {
            throw new IllegalArgumentException("不能修改 Workspace Owner 的角色");
        }
        if (request.role() == WorkspaceRole.OWNER) {
            throw new IllegalArgumentException("不能通过成员接口转移 Workspace 所有权");
        }
        WorkspaceMemberEntity member = memberRepository.findByWorkspaceIdAndUserId(workspaceId, request.userId())
                .orElseGet(() -> new WorkspaceMemberEntity(workspaceId, request.userId(), request.role()));
        member.setRole(request.role());
        memberRepository.save(member);
        return new MemberView(member.getUserId(), member.getRole(), member.getJoinedAt());
    }

    @Transactional(readOnly = true)
    public List<MemberView> members(String workspaceId, String userId) {
        require(workspaceId, userId, WorkspaceRole.VIEWER);
        return memberRepository.findByWorkspaceIdOrderByJoinedAtAsc(workspaceId).stream()
                .map(member -> new MemberView(member.getUserId(), member.getRole(), member.getJoinedAt())).toList();
    }

    @Transactional
    public WorkspaceView changePlan(String workspaceId, String userId, PlanTier tier) {
        require(workspaceId, userId, WorkspaceRole.OWNER);
        WorkspaceEntity workspace = get(workspaceId);
        workspace.setPlanTier(tier);
        return view(workspaceRepository.save(workspace), WorkspaceRole.OWNER);
    }

    @Transactional(readOnly = true)
    public WorkspaceRole require(String workspaceId, String userId, WorkspaceRole required) {
        WorkspaceRole role = memberRepository.findByWorkspaceIdAndUserId(workspaceId, userId)
                .map(WorkspaceMemberEntity::getRole)
                .orElseThrow(() -> new SecurityException("用户无权访问 Workspace: " + workspaceId));
        if (!role.includes(required)) throw new SecurityException("Workspace 权限不足，需要角色: " + required);
        return role;
    }

    @Transactional(readOnly = true)
    public WorkspaceEntity get(String workspaceId) {
        return workspaceRepository.findById(workspaceId)
                .orElseThrow(() -> new IllegalArgumentException("Workspace 不存在: " + workspaceId));
    }

    private WorkspaceView view(WorkspaceEntity workspace, WorkspaceRole role) {
        return new WorkspaceView(workspace.getId(), workspace.getName(), workspace.getOwnerUserId(),
                workspace.getPlanTier(), role, workspace.getCreatedAt());
    }
}
