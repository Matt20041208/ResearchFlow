package com.researchflow.collaboration;

import com.researchflow.persistence.ReportCommentEntity;
import com.researchflow.persistence.ReportCommentRepository;
import com.researchflow.persistence.ReportVersionEntity;
import com.researchflow.persistence.ReportVersionRepository;
import com.researchflow.persistence.TaskRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class CollaborationService {
    private final ReportVersionRepository versionRepository;
    private final ReportCommentRepository commentRepository;
    private final TaskRepository taskRepository;

    public CollaborationService(ReportVersionRepository versionRepository,
                                ReportCommentRepository commentRepository, TaskRepository taskRepository) {
        this.versionRepository = versionRepository;
        this.commentRepository = commentRepository;
        this.taskRepository = taskRepository;
    }

    @Transactional
    public void saveVersion(String taskId, String content, String createdBy) {
        int nextVersion = Math.toIntExact(versionRepository.countByTaskId(taskId) + 1);
        versionRepository.save(new ReportVersionEntity(taskId, nextVersion, content, createdBy));
    }

    @Transactional(readOnly = true)
    public List<ReportVersionView> versions(String taskId) {
        requireTask(taskId);
        return versionRepository.findByTaskIdOrderByVersionNumberDesc(taskId).stream()
                .map(version -> new ReportVersionView(version.getVersionNumber(), version.getContent(),
                        version.getCreatedBy(), version.getCreatedAt())).toList();
    }

    @Transactional
    public CommentView comment(String taskId, String workspaceId, String userId, CommentRequest request) {
        requireTask(taskId);
        return view(commentRepository.save(new ReportCommentEntity(taskId, workspaceId, userId,
                request.content().trim())));
    }

    @Transactional(readOnly = true)
    public List<CommentView> comments(String taskId) {
        requireTask(taskId);
        return commentRepository.findByTaskIdOrderByCreatedAtAsc(taskId).stream().map(this::view).toList();
    }

    private void requireTask(String taskId) {
        if (!taskRepository.existsById(taskId)) throw new IllegalArgumentException("研究任务不存在: " + taskId);
    }

    private CommentView view(ReportCommentEntity comment) {
        return new CommentView(comment.getId(), comment.getTaskId(), comment.getAuthorUserId(),
                comment.getContent(), comment.getCreatedAt());
    }
}
