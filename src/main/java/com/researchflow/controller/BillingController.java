package com.researchflow.controller;

import com.researchflow.billing.UsageService;
import com.researchflow.billing.UsageSummary;
import com.researchflow.workspace.WorkspaceRole;
import com.researchflow.workspace.WorkspaceService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/billing")
public class BillingController {
    private final UsageService usageService;
    private final WorkspaceService workspaceService;

    public BillingController(UsageService usageService, WorkspaceService workspaceService) {
        this.usageService = usageService;
        this.workspaceService = workspaceService;
    }

    @GetMapping("/workspaces/{workspaceId}/usage")
    public UsageSummary usage(@PathVariable String workspaceId,
                              @RequestHeader("X-User-Id") String userId) {
        workspaceService.require(workspaceId, userId, WorkspaceRole.VIEWER);
        return usageService.summary(workspaceId);
    }
}
