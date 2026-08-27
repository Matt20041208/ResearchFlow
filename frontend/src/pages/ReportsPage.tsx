import { useEffect, useState } from 'react'
import { Bug, CheckCircle2, CircleDashed, Download, ExternalLink, FlaskConical, GitBranch, MessageSquare, Quote, ScrollText, Send, Sparkles, Split, Trash2, XCircle } from 'lucide-react'
import ReactMarkdown from 'react-markdown'
import { api, download } from '../api'
import { EmptyState, formatDate, PageHeader, StatusChip } from '../components'
import { useSession } from '../session'
import type { Citation, Comment, ReportVersion, Scenario, TaskSnapshot, TaskSummary, TraceView, ValidationRun, ValidationVerdict } from '../types'

type DrawerTab = 'sources' | 'versions' | 'comments' | 'trace' | 'scenarios'

export default function ReportsPage() {
  const { userId, workspace } = useSession()
  const [tasks, setTasks] = useState<TaskSummary[]>([])
  const [selectedId, setSelectedId] = useState(() => new URLSearchParams(location.search).get('task') ?? '')
  const [task, setTask] = useState<TaskSnapshot>()
  const [citations, setCitations] = useState<Citation[]>([])
  const [versions, setVersions] = useState<ReportVersion[]>([])
  const [comments, setComments] = useState<Comment[]>([])
  const [trace, setTrace] = useState<TraceView>()
  const [scenarios, setScenarios] = useState<Scenario[]>([])
  const [validations, setValidations] = useState<Record<number, ValidationRun[]>>({})
  const [generating, setGenerating] = useState(false)
  const [scenarioError, setScenarioError] = useState('')
  const [comment, setComment] = useState('')
  const [tab, setTab] = useState<DrawerTab>('sources')

  useEffect(() => {
    if (!workspace) return
    void api<TaskSummary[]>(`/api/research/tasks?workspaceId=${workspace.id}`, userId).then((items) => {
      setTasks(items)
      if (!selectedId && items[0]) setSelectedId(items[0].taskId)
    })
  }, [workspace?.id])

  const loadDetail = async () => {
    if (!selectedId) return
    const [detail, sourceList, versionList, commentList, traceData, scenarioList] = await Promise.all([
      api<TaskSnapshot>(`/api/research/tasks/${selectedId}`, userId),
      api<Citation[]>(`/api/research/tasks/${selectedId}/citations`, userId),
      api<ReportVersion[]>(`/api/research/tasks/${selectedId}/versions`, userId),
      api<Comment[]>(`/api/research/tasks/${selectedId}/comments`, userId),
      api<TraceView>(`/api/research/tasks/${selectedId}/trace`, userId),
      api<Scenario[]>(`/api/research/tasks/${selectedId}/scenarios`, userId),
    ])
    setTask(detail); setCitations(sourceList); setVersions(versionList); setComments(commentList)
    setTrace(traceData); setScenarios(scenarioList)
    await loadValidations(scenarioList)
  }
  useEffect(() => { void loadDetail() }, [selectedId])

  const addComment = async () => {
    if (!comment.trim()) return
    await api(`/api/research/tasks/${selectedId}/comments`, userId, { method: 'POST', body: JSON.stringify({ content: comment }) })
    setComment(''); await loadDetail()
  }

  const generateScenarios = async () => {
    setGenerating(true); setScenarioError('')
    try {
      const generated = await api<Scenario[]>(`/api/research/tasks/${selectedId}/scenarios/generate`, userId, { method: 'POST' })
      setScenarios(generated)
      setTab('scenarios')
    } catch (reason) { setScenarioError(reason instanceof Error ? reason.message : '场景生成失败') }
    finally { setGenerating(false) }
  }

  const reviewScenario = async (scenario: Scenario, status: 'APPROVED' | 'DISMISSED') => {
    await api(`/api/research/tasks/${selectedId}/scenarios/${scenario.id}/status?status=${status}`, userId, { method: 'PUT' })
    await loadDetail()
  }
  const deleteScenario = async (scenario: Scenario) => {
    await api(`/api/research/tasks/${selectedId}/scenarios/${scenario.id}`, userId, { method: 'DELETE' })
    await loadDetail()
  }

  const loadValidations = async (items: Scenario[] = scenarios) => {
    if (!selectedId) return
    const pairs = await Promise.all(items.map(async (scenario) => [scenario.id,
      await api<ValidationRun[]>(`/api/research/tasks/${selectedId}/scenarios/${scenario.id}/validations`, userId),
    ] as const))
    setValidations(Object.fromEntries(pairs))
  }

  const validateScenario = async (scenario: Scenario) => {
    const run = await api<ValidationRun>(`/api/research/tasks/${selectedId}/scenarios/${scenario.id}/validations`, userId, { method: 'POST' })
    setValidations((current) => ({ ...current, [scenario.id]: [run, ...(current[scenario.id] ?? [])] }))
  }

  const reviewValidation = async (scenario: Scenario, run: ValidationRun, verdict: ValidationVerdict) => {
    await api(`/api/research/tasks/${selectedId}/scenarios/${scenario.id}/validations/${run.id}/verdict?verdict=${verdict}`, userId, { method: 'PUT' })
    await loadValidations()
  }

  const hasActiveValidation = Object.values(validations).flat()
    .some((run) => run.status === 'QUEUED' || run.status === 'RUNNING')
  useEffect(() => {
    if (!hasActiveValidation) return
    const timer = window.setInterval(() => { void loadValidations() }, 1800)
    return () => window.clearInterval(timer)
  }, [hasActiveValidation, selectedId])

  const canEdit = workspace?.currentUserRole !== 'VIEWER'

  return <div>
    <PageHeader eyebrow="REPORT ARCHIVE" title="研究成果库" description="报告正文、证据来源、执行链路和 AI 场景推演保持在同一个可审计记录中。" />
    {tasks.length === 0 ? <EmptyState title="还没有报告" body="从研究台提出第一个问题，完成后报告会出现在这里。" /> :
      <div className="report-layout">
        <aside className="report-index">{tasks.map((item, index) => <button key={item.taskId} className={selectedId === item.taskId ? 'active' : ''} onClick={() => setSelectedId(item.taskId)}>
          <span>{String(index + 1).padStart(2, '0')}</span><div><strong>{item.question}</strong><small>{formatDate(item.updatedAt)}</small></div><StatusChip status={item.status} />
        </button>)}</aside>
        {task && <section className="report-reader">
          <div className="reader-toolbar"><div><span className="utility-label">REPORT ID</span><code>{task.taskId.slice(0, 12)}</code></div><div className="export-group">
            {(['markdown', 'docx', 'pdf'] as const).map((format) => <button key={format} onClick={() => void download(`/api/research/tasks/${task.taskId}/export?format=${format}`, userId, `research-${task.taskId}.${format === 'markdown' ? 'md' : format}`)}><Download size={15} />{format.toUpperCase()}</button>)}
          </div></div>
          <article className="markdown-report"><ReactMarkdown>{task.report ?? '*报告尚未生成。*'}</ReactMarkdown></article>
        </section>}
        {task && <aside className="evidence-drawer">
          <div className="drawer-tabs drawer-tabs-5">
            <button className={tab === 'sources' ? 'active' : ''} onClick={() => setTab('sources')}><Quote />来源</button>
            <button className={tab === 'trace' ? 'active' : ''} onClick={() => setTab('trace')}><GitBranch />链路</button>
            <button className={tab === 'scenarios' ? 'active' : ''} onClick={() => setTab('scenarios')}><Sparkles />场景</button>
            <button className={tab === 'versions' ? 'active' : ''} onClick={() => setTab('versions')}><Split />版本</button>
            <button className={tab === 'comments' ? 'active' : ''} onClick={() => setTab('comments')}><MessageSquare />讨论</button>
          </div>
          {tab === 'sources' && <div className="citation-list">{citations.map((source) => <article key={source.number}><span className="citation-number">[{source.number}]</span><div><strong>{source.title}</strong><p>{source.excerpt}</p><small>{source.sourceType} · 置信度 {(source.confidence * 100).toFixed(0)}%</small>{source.url && <a href={source.url} target="_blank">查看原文 <ExternalLink size={13} /></a>}</div></article>)}</div>}
          {tab === 'trace' && <div className="trace-list">
            {trace?.plan && <div className="trace-plan"><span className="utility-label">DAG</span><strong>{trace.plan.nodes.length} 个节点</strong><p>{trace.plan.nodes.map((node) => node.id).join(' → ')}</p></div>}
            {trace?.nodes.map((node, index) => <details className="trace-node" key={`${node.nodeId}-${index}`}>
              <summary><span className="node-sequence">{String(index + 1).padStart(2, '0')}</span><div><strong>{node.agent.replace('-agent', '')}</strong><small>{node.nodeId} · {node.status}</small></div><span className="duration">{node.durationMs}ms</span></summary>
              <div className="trace-detail">{node.inputSummary && <p><em>输入</em>{node.inputSummary}</p>}{node.outputSummary && <p><em>输出</em>{node.outputSummary}</p>}{node.errorSummary && <p className="trace-error"><em>异常</em>{node.errorSummary}</p>}</div>
            </details>)}
          </div>}
          {tab === 'scenarios' && <div className="scenarios-panel">
            <div className="scenarios-head"><p>AI 基于真实执行链路推演异常组合，人工判断价值后沉淀为回归资产。</p>{canEdit && <button className="primary-button" onClick={() => void generateScenarios()} disabled={generating}><Sparkles size={15} />{generating ? '推演中…' : '生成场景'}</button>}{scenarioError && <p className="error-text">{scenarioError}</p>}</div>
            <div className="scenario-list">{scenarios.map((scenario) => <article className={`scenario-card risk-${scenario.risk.toLowerCase()}`} key={scenario.id}>
              <div className="scenario-top"><span className="risk-seal">{scenario.risk}</span><strong>{scenario.title}</strong></div>
              <p className="scenario-field"><em>组合</em>{scenario.nodeCombination}</p>
              <p className="scenario-field"><em>触发</em>{scenario.trigger}</p>
              <p className="scenario-field"><em>注入</em>{scenario.injectedData}</p>
              <p className="scenario-field"><em>关注</em>{scenario.expectation}</p>
              <div className="scenario-actions">{canEdit && <><button onClick={() => void reviewScenario(scenario, 'APPROVED')}><CheckCircle2 size={14} />采纳</button><button onClick={() => void reviewScenario(scenario, 'DISMISSED')}><XCircle size={14} />忽略</button>{scenario.status === 'APPROVED' && <button className="validate-button" onClick={() => void validateScenario(scenario)}><FlaskConical size={14} />执行验证</button>}<button className="danger" onClick={() => void deleteScenario(scenario)}><Trash2 size={14} /></button></>}<StatusChip status={scenario.status} /></div>
              {(validations[scenario.id] ?? []).map((run) => <div className="validation-run" key={run.id}>
                <div className="validation-head">{run.status === 'RUNNING' || run.status === 'QUEUED' ? <CircleDashed className="spin" /> : run.error ? <Bug /> : <CheckCircle2 />}<strong>{run.status}</strong><span>{run.durationMs ? `${run.durationMs}ms` : '等待执行'}</span><StatusChip status={run.verdict} /></div>
                <div className="injection-rules">{run.rules.map((rule, index) => <code key={`${rule.nodeId}-${rule.type}-${index}`}>{rule.nodeId} · {rule.type}{rule.delayMs ? ` ${rule.delayMs}ms` : ''}</code>)}</div>
                {run.automaticAssessment && <div className={`auto-assessment assessment-${run.automaticAssessment.toLowerCase()}`}><span>自动判定</span><strong>{run.automaticAssessment.replaceAll('_', ' ')}</strong><p>{run.assessmentReason}</p>{run.assessmentEvidence && <small>{run.assessmentEvidence}</small>}</div>}
                {run.outputSummary && <p className="validation-output">{run.outputSummary}</p>}{run.error && <p className="validation-error">{run.error}</p>}
                {canEdit && run.status === 'COMPLETED' && <div className="verdict-actions"><span>开发者结论</span><button onClick={() => void reviewValidation(scenario, run, 'VERIFIED')}>符合预期</button><button onClick={() => void reviewValidation(scenario, run, 'DEFECT_FOUND')}>发现缺陷</button><button onClick={() => void reviewValidation(scenario, run, 'INVALID')}>场景无效</button></div>}
              </div>)}
            </article>)}</div>
            {scenarios.length === 0 && <EmptyState title="还没有场景" body="点击生成场景，让 AI 从这条链路的真实耗时、数据和依赖中推演异常组合。" />}
          </div>}
          {tab === 'versions' && <div className="version-list">{versions.map((version) => <article key={version.versionNumber}><ScrollText /><div><strong>版本 {version.versionNumber}</strong><p>{version.createdBy}</p><small>{formatDate(version.createdAt)}</small></div></article>)}</div>}
          {tab === 'comments' && <div className="comments-panel"><div className="comment-list">{comments.map((item) => <article key={item.id}><strong>{item.authorUserId}</strong><p>{item.content}</p><small>{formatDate(item.createdAt)}</small></article>)}</div><div className="comment-compose"><textarea value={comment} onChange={(event) => setComment(event.target.value)} placeholder="写下审阅意见…" /><button onClick={() => void addComment()}><Send size={16} /></button></div></div>}
        </aside>}
      </div>}
  </div>
}
