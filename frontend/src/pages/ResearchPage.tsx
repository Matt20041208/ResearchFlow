import { useEffect, useMemo, useState } from 'react'
import { ArrowRight, Check, CircleDashed, GitBranch, Play, ShieldAlert } from 'lucide-react'
import { api } from '../api'
import { PageHeader, StatusChip } from '../components'
import { useSession } from '../session'
import type { AgentEvent, TaskSnapshot } from '../types'

const suggestions = [
  '分析企业采用多 Agent 系统的主要风险与治理方法',
  '研究大模型在医疗知识管理中的应用，并比较主要方案',
  '梳理生成式 AI 监管趋势，输出对企业的行动建议',
]

export default function ResearchPage() {
  const { userId, workspace } = useSession()
  const [question, setQuestion] = useState('')
  const [task, setTask] = useState<TaskSnapshot>()
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')

  useEffect(() => {
    if (!task || ['COMPLETED', 'FAILED', 'CANCELLED', 'WAITING_APPROVAL'].includes(task.status)) return
    const timer = window.setInterval(async () => {
      try { setTask(await api<TaskSnapshot>(`/api/research/tasks/${task.taskId}`, userId)) } catch { /* retry next tick */ }
    }, 1300)
    return () => window.clearInterval(timer)
  }, [task?.taskId, task?.status, userId])

  const launch = async () => {
    if (!workspace || !question.trim()) return
    setBusy(true); setError('')
    try {
      const { taskId } = await api<{ taskId: string }>('/api/research/tasks', userId, {
        method: 'POST', body: JSON.stringify({ question, workspaceId: workspace.id }),
      })
      setTask(await api<TaskSnapshot>(`/api/research/tasks/${taskId}`, userId))
    } catch (reason) { setError(reason instanceof Error ? reason.message : '任务创建失败') }
    finally { setBusy(false) }
  }

  return (
    <div>
      <PageHeader eyebrow="RESEARCH DESK" title="提出一个值得追踪的问题" description="System Agent 会组织检索、证据、比较和写作 Agent 完成研究。" />
      <section className="research-grid">
        <div className="question-panel">
          <div className="question-index">RQ</div>
          <textarea value={question} onChange={(event) => setQuestion(event.target.value)}
            placeholder="例如：分析企业采用多 Agent 系统的风险、成本和治理方法……" />
          <div className="suggestion-row">
            {suggestions.map((item) => <button key={item} onClick={() => setQuestion(item)}>{item}</button>)}
          </div>
          {error && <p className="error-text">{error}</p>}
          <button className="primary-button launch-button" onClick={launch} disabled={busy || !question.trim() || workspace?.currentUserRole === 'VIEWER'}>
            {busy ? <CircleDashed className="spin" size={18} /> : <Play size={17} />} 启动研究
          </button>
          {workspace?.currentUserRole === 'VIEWER' && <p className="permission-hint">Viewer 可以阅读和评论报告，Editor 才能发起研究。</p>}
        </div>
        <AgentRoute task={task} />
      </section>
      {task && <TaskResult task={task} userId={userId} canApprove={workspace?.currentUserRole === 'OWNER'} />}
    </div>
  )
}

function AgentRoute({ task }: { task?: TaskSnapshot }) {
  const nodes = useMemo(() => {
    const latest = new Map<string, AgentEvent>()
    task?.events.filter((event) => !['system', 'system-agent', 'approval'].includes(event.agent))
      .forEach((event) => latest.set(event.agent, event))
    return [...latest.values()]
  }, [task?.events])
  return (
    <aside className="agent-route">
      <div className="route-heading"><GitBranch size={19} /><span>Agent route</span>{task && <StatusChip status={task.status} />}</div>
      {!task ? <div className="route-idle"><div className="route-pulse" /><p>等待研究问题</p><span>路线将在任务启动后实时显现</span></div> :
        <div className="route-list">{nodes.map((node, index) => (
          <div className={`route-node ${node.status.toLowerCase()}`} key={node.agent}>
            <span className="node-sequence">{String(index + 1).padStart(2, '0')}</span>
            <div><strong>{friendlyAgent(node.agent)}</strong><p>{node.message}</p></div>
            {node.status === 'COMPLETED' ? <Check size={17} /> : <span className="node-live" />}
          </div>
        ))}</div>}
    </aside>
  )
}

function TaskResult({ task, userId, canApprove }: { task: TaskSnapshot; userId: string; canApprove: boolean }) {
  const approve = async () => {
    await api(`/api/research/tasks/${task.taskId}/approve`, userId, {
      method: 'POST', body: JSON.stringify({ tool: task.pendingApprovalTool }),
    })
    window.location.reload()
  }
  if (task.status === 'WAITING_APPROVAL') return <section className="approval-banner"><ShieldAlert /><div><strong>需要人工批准</strong><p>{task.error}</p></div>{canApprove && <button onClick={approve}>批准 {task.pendingApprovalTool}</button>}</section>
  if (task.status === 'FAILED') return <section className="error-banner"><strong>研究任务失败</strong><p>{task.error}</p></section>
  if (task.status !== 'COMPLETED') return <section className="working-strip"><span /> 多 Agent 正在协作，已产生 {task.events.length} 条执行记录</section>
  return <section className="completion-strip"><div><Check /><span>研究完成</span></div><p>报告已进入报告库，可查看引用、评论、版本并导出。</p><a href={`/reports?task=${task.taskId}`}>打开报告 <ArrowRight size={16} /></a></section>
}

function friendlyAgent(value: string) {
  return value.replace('-agent', '').replaceAll('-', ' ')
}
