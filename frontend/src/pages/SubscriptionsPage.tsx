import { useEffect, useState } from 'react'
import { Clock3, Pause, Play, Plus, RefreshCw } from 'lucide-react'
import { api } from '../api'
import { EmptyState, formatDate, PageHeader } from '../components'
import { useSession } from '../session'
import type { Subscription } from '../types'

export default function SubscriptionsPage() {
  const { userId, workspace } = useSession()
  const [items, setItems] = useState<Subscription[]>([])
  const [open, setOpen] = useState(false)
  const [form, setForm] = useState({ name: '', question: '', intervalMinutes: 10080 })
  const [error, setError] = useState('')
  const load = async () => workspace && setItems(await api<Subscription[]>(`/api/subscriptions?workspaceId=${workspace.id}`, userId))
  useEffect(() => { void load() }, [workspace?.id])

  const create = async () => {
    if (!workspace) return
    try {
      setError('')
      await api('/api/subscriptions', userId, { method: 'POST', body: JSON.stringify({ ...form, workspaceId: workspace.id }) })
      setOpen(false); setForm({ name: '', question: '', intervalMinutes: 10080 }); await load()
    } catch (reason) { setError(reason instanceof Error ? reason.message : '创建失败') }
  }
  const toggle = async (item: Subscription) => { await api(`/api/subscriptions/${item.id}/enabled?value=${!item.enabled}`, userId, { method: 'PUT' }); await load() }
  const run = async (item: Subscription) => { await api(`/api/subscriptions/${item.id}/run`, userId, { method: 'POST' }); await load() }

  return <div>
    <PageHeader eyebrow="INTELLIGENCE LOOPS" title="主题情报订阅" description="让研究团队持续追踪一个问题，而不是每次从零开始。"
      action={workspace?.currentUserRole !== 'VIEWER' ? <button className="primary-button" onClick={() => setOpen(true)}><Plus size={17} /> 新建订阅</button> : undefined} />
    {open && <section className="inline-form"><div><label>订阅名称<input value={form.name} onChange={(e) => setForm({ ...form, name: e.target.value })} /></label><label>周期<select value={form.intervalMinutes} onChange={(e) => setForm({ ...form, intervalMinutes: Number(e.target.value) })}><option value={1440}>每天</option><option value={10080}>每周</option><option value={43200}>每月</option></select></label></div><label>持续追踪的问题<textarea value={form.question} onChange={(e) => setForm({ ...form, question: e.target.value })} /></label>{error && <p className="error-text">{error}</p>}<div className="form-actions"><button onClick={() => setOpen(false)}>取消</button><button className="primary-button" onClick={() => void create()}>创建订阅</button></div></section>}
    {items.length === 0 ? <EmptyState title="没有运行中的情报循环" body="建立订阅后，System Agent 会按周期生成新报告。" /> : <div className="subscription-grid">{items.map((item) => <article className={item.enabled ? 'subscription-card' : 'subscription-card paused'} key={item.id}>
      <div className="subscription-top"><span className="frequency-mark"><RefreshCw /></span><div><strong>{item.name}</strong><p>{item.question}</p></div></div>
      <div className="schedule-line"><Clock3 size={15} /><span>下次运行 {formatDate(item.nextRunAt)}</span></div>
      {workspace?.currentUserRole !== 'VIEWER' && <div className="subscription-actions"><button onClick={() => void toggle(item)}>{item.enabled ? <Pause size={15} /> : <Play size={15} />}{item.enabled ? '暂停' : '恢复'}</button><button onClick={() => void run(item)}><Play size={15} />立即运行</button></div>}
    </article>)}</div>}
  </div>
}
