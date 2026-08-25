package com.researchflow.subscription;

import com.researchflow.billing.UsageService;
import com.researchflow.billing.UsageType;
import com.researchflow.model.ResearchRequest;
import com.researchflow.persistence.TopicSubscriptionEntity;
import com.researchflow.persistence.TopicSubscriptionRepository;
import com.researchflow.service.ResearchTaskService;
import com.researchflow.workspace.WorkspaceService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Service
public class SubscriptionService {
    private final TopicSubscriptionRepository repository;
    private final WorkspaceService workspaceService;
    private final UsageService usageService;
    private final ResearchTaskService taskService;

    public SubscriptionService(TopicSubscriptionRepository repository, WorkspaceService workspaceService,
                               UsageService usageService, ResearchTaskService taskService) {
        this.repository = repository;
        this.workspaceService = workspaceService;
        this.usageService = usageService;
        this.taskService = taskService;
    }

    @Transactional
    public SubscriptionView create(String userId, SubscriptionRequest request) {
        var tier = workspaceService.get(request.workspaceId()).getPlanTier();
        if (repository.countByWorkspaceIdAndEnabledTrue(request.workspaceId()) >= tier.subscriptions()) {
            throw new IllegalStateException("主题订阅配额已用尽");
        }
        return view(repository.save(new TopicSubscriptionEntity(request.workspaceId(), request.name().trim(),
                request.question().trim(), request.intervalMinutes(), userId)));
    }

    @Transactional(readOnly = true)
    public List<SubscriptionView> list(String workspaceId) {
        return repository.findByWorkspaceIdOrderByCreatedAtDesc(workspaceId).stream().map(this::view).toList();
    }

    @Transactional
    public SubscriptionView setEnabled(Long id, boolean enabled) {
        TopicSubscriptionEntity entity = get(id);
        if (enabled && !entity.isEnabled()) {
            var tier = workspaceService.get(entity.getWorkspaceId()).getPlanTier();
            if (repository.countByWorkspaceIdAndEnabledTrue(entity.getWorkspaceId()) >= tier.subscriptions()) {
                throw new IllegalStateException("主题订阅配额已用尽");
            }
        }
        entity.setEnabled(enabled);
        return view(repository.save(entity));
    }

    @Transactional
    public String run(Long id) {
        TopicSubscriptionEntity entity = get(id);
        Instant now = Instant.now();
        String taskId = taskService.create(new ResearchRequest(entity.getQuestion(), entity.getWorkspaceId()),
                entity.getCreatedBy(), "SUBSCRIPTION");
        entity.recordRun(taskId, now);
        repository.save(entity);
        usageService.record(entity.getWorkspaceId(), entity.getCreatedBy(), UsageType.SUBSCRIPTION_RUN, String.valueOf(id));
        return taskId;
    }

    @Transactional
    public void defer(Long id) {
        TopicSubscriptionEntity entity = get(id);
        entity.scheduleNext(Instant.now());
        repository.save(entity);
    }

    public TopicSubscriptionEntity get(Long id) {
        return repository.findById(id).orElseThrow(() -> new IllegalArgumentException("主题订阅不存在: " + id));
    }

    private SubscriptionView view(TopicSubscriptionEntity entity) {
        return new SubscriptionView(entity.getId(), entity.getWorkspaceId(), entity.getName(), entity.getQuestion(),
                entity.getIntervalMinutes(), entity.isEnabled(), entity.getNextRunAt(), entity.getLastRunAt(),
                entity.getLastTaskId());
    }
}
