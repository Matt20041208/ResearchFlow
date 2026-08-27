import { useEffect, useState } from 'react'
import { Activity, CheckCircle2, Clock3, FileJson, Network, Plus, Sparkles, Trash2, Upload } from 'lucide-react'
import { api } from '../api'
import { EmptyState, formatDate, PageHeader, StatusChip } from '../components'
import { useSession } from '../session'
import type { ExternalTraceSummary, ExternalTraceView, Scenario } from '../types'

const sample = {
  name: '车载 Agent 请求链路',
  sourceSystem: 'AIOS-AgentRuntime',
  startedAt: '2026-08-25T08:00:00Z',
  endedAt: '2026-08-25T08:00:03Z',
  nodes: [
    { nodeId: 'intent', agent: 'intent-agent', dependsOn: [], status: 'SUCCESS', input: '用户请求', output: '导航意图', durationMs: 42, externalBoundary: false, asyncNode: false },
    { nodeId: 'map-api', agent: 'map-service', dependsOn: ['intent'], status: 'SUCCESS', input: '导航意图', output: '路线结果', durationMs: 930, externalBoundary: true, asyncNode: true },
    { nodeId: 'response', agent: 'response-agent', dependsOn: ['map-api'], status: 'SUCCESS', input: '路线结果', output: '语音回复', durationMs: 75, externalBoundary: false, asyncNode: false },
  ],
}

export default function ExternalTracesPage() {
  const { userId, workspace } = useSession()
  const [traces, setTraces] = useState<ExternalTraceSummary[]>([])
  const [selectedId, setSelectedId] = useState('')
  const [detail, setDetail] = useState<ExternalTraceView>()
  const [scenarios, setScenarios] = useState<Scenario[]>([])
  const [editor, setEditor] = useState(false)
  const [payload, setPayload] = useState(JSON.stringify(sample, null, 2))
  const [error, setError] = useState('')
  const [generating, setGenerating] = useState(false)

  const load = async () => {
    if (!workspace) return
    const items = await api<ExternalTraceSummary[]>(`/api/external-traces?workspaceId=${workspace.id}`, userId)
    setTraces(items)
    if (!selectedId && items[0]) setSelectedId(items[0].id)
  }
  useEffect(() => { void load() }, [workspace?.id])
  useEffect(() => {
    if (!selectedId) return
    void Promise.all([
      api<ExternalTraceView>(`/api/external-traces/${selectedId}`, userId),
      api<Scenario[]>(`/api/external-traces/${selectedId}/scenarios`, userId),
    ]).then(([trace, items]) => { setDetail(trace); setScenarios(items) })
  }, [selectedId])

  const ingest = async () => {
    if (!workspace) return
    try {
      setError('')
      const body = { ...JSON.parse(payload), workspaceId: workspace.id }
      const result = await api<ExternalTraceView>('/api/external-traces', userId, {
        method: 'POST', body: JSON.stringify(body),
      })
      setEditor(false); await load(); setSelectedId(result.summary.id)
    } catch (reason) { setError(reason instanceof Error ? reason.message : '链路接入失败') }
  }
  const upload = async (file: File) => {
    setPayload(await file.text()); setEditor(true)
  }
  const generate = async () => {
    setGenerating(true)
    try { setScenarios(await api<Scenario[]>(`/api/external-traces/${selectedId}/scenarios/generate`, userId, { method: 'POST' })) }
    finally { setGenerating(false) }
  }
  const review = async (scenario: Scenario, status: 'APPROVED' | 'DISMISSED') => {
    await api(`/api/external-traces/${selectedId}/scenarios/${scenario.id}/status?status=${status}`, userId, { method: 'PUT' })
    setScenarios(await api<Scenario[]>(`/api/external-traces/${selectedId}/scenarios`, userId))
  }
  const remove = async () => {
    await api(`/api/external-traces/${selectedId}`, userId, { method: 'DELETE' })
    setSelectedId(''); setDetail(undefined); await load()
  }
  const canEdit = workspace?.currentUserRole !== 'VIEWER'

  return <div>
    <PageHeader eyebrow="TRACE INGESTION" title="外部 Agent 链路" description="接入真实任务节点、依赖、输入输出和时序，让 AI 在运行上下文上推演未知组合。"
      action={canEdit ? <button className="primary-button" onClick={() => setEditor(true)}><Plus size={17} />接入链路</button> : undefined} />
    {editor && <section className="trace-ingest-editor">
      <div className="ingest-head"><FileJson /><div><strong>结构化链路 JSON</strong><p>workspaceId 将由当前空间自动注入。最多 200 个节点。</p></div><label className="upload-small"><Upload size={15} />读取 JSON<input type="file" accept=".json" hidden onChange={(event) => event.target.files?.[0] && void upload(event.target.files[0])} /></label></div>
      <textarea value={payload} onChange={(event) => setPayload(event.target.value)} spellCheck={false} />
      {error && <p className="error-text">{error}</p>}
      <div className="form-actions"><button onClick={() => setEditor(false)}>取消</button><button className="primary-button" onClick={() => void ingest()}>校验并接入</button></div>
    </section>}
    {traces.length === 0 ? <EmptyState title="还没有外部链路" body="粘贴 SDK 或工具链上报的结构化 JSON，开始 AI 场景推演。" /> : <div className="external-trace-layout">
      <aside className="external-trace-index">{traces.map((trace) => <button key={trace.id} className={trace.id === selectedId ? 'active' : ''} onClick={() => setSelectedId(trace.id)}><span className="trace-system">{trace.sourceSystem}</span><strong>{trace.name}</strong><small>{trace.nodeCount} nodes · {formatDate(trace.createdAt)}</small><StatusChip status={trace.status} /></button>)}</aside>
      {detail && <section className="external-trace-detail">
        <header><div><p className="eyebrow">{detail.summary.sourceSystem}</p><h2>{detail.summary.name}</h2></div><div><StatusChip status={detail.summary.status} />{canEdit && <button className="icon-button danger" onClick={() => void remove()}><Trash2 /></button>}</div></header>
        <div className="external-node-list">{detail.trace.nodes.map((node, index) => <details key={node.nodeId} className="external-node"><summary><span>{String(index + 1).padStart(2, '0')}</span><div><strong>{node.nodeId}</strong><small>{node.agent}</small></div><div className="node-flags">{node.externalBoundary && <i>EXTERNAL</i>}{node.asyncNode && <i>ASYNC</i>}<b>{node.durationMs}ms</b></div></summary><div><p><em>输入</em>{node.inputSummary || '—'}</p><p><em>输出</em>{node.outputSummary || '—'}</p>{node.errorSummary && <p className="error-text"><em>异常</em>{node.errorSummary}</p>}</div></details>)}</div>
      </section>}
      {detail && <aside className="external-scenarios"><div className="external-scenario-head"><div><Sparkles /><strong>AI 场景推演</strong></div>{canEdit && <button onClick={() => void generate()} disabled={generating}>{generating ? '推演中…' : '生成场景'}</button>}</div>{scenarios.map((scenario) => <article key={scenario.id}><div><span className={`risk-seal risk-${scenario.risk.toLowerCase()}`}>{scenario.risk}</span><StatusChip status={scenario.status} /></div><strong>{scenario.title}</strong><p>{scenario.trigger}</p><div className="strict-rules">{scenario.injectionRules?.map((rule, index) => <code key={index}>{rule.nodeId} · {rule.type}</code>)}</div>{canEdit && <footer><button onClick={() => void review(scenario, 'APPROVED')}><CheckCircle2 />采纳</button><button onClick={() => void review(scenario, 'DISMISSED')}>忽略</button></footer>}</article>)}{scenarios.length === 0 && <div className="scenario-empty"><Activity /><p>等待 AI 读取链路</p></div>}</aside>}
    </div>}
  </div>
}
