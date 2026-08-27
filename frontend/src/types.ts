export type Role = 'OWNER' | 'EDITOR' | 'VIEWER'

export interface Workspace {
  id: string
  name: string
  ownerUserId: string
  planTier: 'FREE' | 'TEAM' | 'ENTERPRISE'
  currentUserRole: Role
  createdAt: string
}

export interface AgentEvent {
  taskId: string
  agent: string
  status: string
  message: string
  occurredAt: string
}

export interface TaskSummary {
  taskId: string
  question: string
  status: string
  createdAt: string
  updatedAt: string
  attempts: number
}

export interface TaskSnapshot extends TaskSummary {
  report?: string
  error?: string
  pendingApprovalTool?: string
  events: AgentEvent[]
}

export interface Citation {
  number: number
  sourceId?: string
  sourceType: string
  title: string
  url?: string
  excerpt: string
  confidence: number
}

export interface KnowledgeDocument {
  id: string
  workspaceId: string
  title: string
  content: string
  sourceUrl?: string
  createdAt: string
}

export interface Subscription {
  id: number
  workspaceId: string
  name: string
  question: string
  intervalMinutes: number
  enabled: boolean
  nextRunAt: string
  lastRunAt?: string
  lastTaskId?: string
}

export interface Member {
  userId: string
  role: Role
  joinedAt: string
}

export interface UsageSummary {
  workspaceId: string
  planTier: string
  periodStart: string
  usage: Record<string, number>
  limits: Record<string, number>
  estimatedCostUsd: number
}

export interface ReportVersion {
  versionNumber: number
  content: string
  createdBy: string
  createdAt: string
}

export interface Comment {
  id: number
  taskId: string
  authorUserId: string
  content: string
  createdAt: string
}

export interface TraceNode {
  nodeId: string
  agent: string
  status: string
  inputSummary?: string
  outputSummary?: string
  errorSummary?: string
  durationMs: number
  startedAt: string
  externalBoundary?: boolean
  asyncNode?: boolean
}

export interface SystemPlan {
  goal: string
  nodes: { id: string; agent: string; dependsOn: string[] }[]
}

export interface TraceView {
  taskId: string
  plan?: SystemPlan
  nodes: TraceNode[]
}

export type ScenarioStatus = 'SUGGESTED' | 'APPROVED' | 'DISMISSED'

export interface Scenario {
  id: number
  taskId: string
  title: string
  nodeCombination: string
  trigger: string
  injectedData: string
  expectation: string
  risk: string
  status: ScenarioStatus
  sourceType?: string
  injectionRules?: InjectionRule[]
  createdAt: string
}

export interface InjectionRule {
  nodeId: string
  type: 'DELAY' | 'ERROR' | 'EMPTY_RESULT'
  delayMs: number
  message?: string
}

export type ValidationVerdict = 'NEEDS_REVIEW' | 'VERIFIED' | 'DEFECT_FOUND' | 'INVALID'

export interface ValidationRun {
  id: string
  scenarioId: number
  taskId: string
  status: 'QUEUED' | 'RUNNING' | 'COMPLETED' | 'FAILED'
  verdict: ValidationVerdict
  automaticAssessment?: 'EXPECTED_BEHAVIOR' | 'POTENTIAL_DEFECT' | 'INCONCLUSIVE'
  assessmentReason?: string
  assessmentEvidence?: string
  rules: InjectionRule[]
  expectation: string
  actualTraceJson?: string
  outputSummary?: string
  error?: string
  durationMs: number
  createdAt: string
  startedAt?: string
  completedAt?: string
}

export interface ExternalTraceSummary {
  id: string
  workspaceId: string
  name: string
  sourceSystem: string
  status: string
  nodeCount: number
  startedAt?: string
  endedAt?: string
  createdAt: string
}

export interface ExternalTraceView {
  summary: ExternalTraceSummary
  trace: TraceView
}
