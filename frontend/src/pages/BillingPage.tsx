import { useEffect, useState } from 'react'
import { BarChart3, Coins, FileStack, Gauge, Radio, Sparkles } from 'lucide-react'
import { api } from '../api'
import { PageHeader } from '../components'
import { useSession } from '../session'
import type { UsageSummary } from '../types'

export default function BillingPage() {
  const { userId, workspace } = useSession()
  const [usage, setUsage] = useState<UsageSummary>()
  useEffect(() => { if (workspace) void api<UsageSummary>(`/api/billing/workspaces/${workspace.id}/usage`, userId).then(setUsage) }, [workspace?.id])
  if (!usage) return <div className="loading-inline">读取本月用量…</div>
  const metrics = [
    { key: 'REPORT_CREATED', label: '研究报告', icon: FileStack, limit: usage.limits.monthlyReports },
    { key: 'DOCUMENT_CREATED', label: '知识文档', icon: BarChart3, limit: usage.limits.documents },
    { key: 'SUBSCRIPTION_RUN', label: '订阅运行', icon: Radio, limit: usage.limits.subscriptions },
    { key: 'TOKEN_USED', label: '估算 Token', icon: Sparkles, limit: 0 },
  ]
  return <div>
    <PageHeader eyebrow="USAGE LEDGER" title="用量与套餐" description="每次研究、导出和订阅运行都有可核对的记录。" />
    <section className="billing-hero"><div><p>当前套餐</p><strong>{usage.planTier}</strong><span>本周期自 {new Date(usage.periodStart).toLocaleDateString('zh-CN')} 起</span></div><div className="cost-figure"><Coins /><p>估算模型成本</p><strong>${usage.estimatedCostUsd.toFixed(4)}</strong></div></section>
    <div className="metric-grid">{metrics.map(({ key, label, icon: Icon, limit }) => { const value = usage.usage[key] ?? 0; const percent = limit > 0 ? Math.min(100, value / limit * 100) : 0; return <article key={key}><div className="metric-head"><Icon /><span>{label}</span></div><strong>{value.toLocaleString()}</strong><small>{limit > 0 ? `/ ${limit.toLocaleString()}` : '本月累计'}</small>{limit > 0 && <div className="meter"><i style={{ width: `${percent}%` }} /></div>}</article> })}</div>
    <section className="ledger-note"><Gauge /><div><strong>成本为可解释估算</strong><p>当前按每千 Token $0.001 估算。接入供应商账单后，可替换为真实输入/输出 Token 成本。</p></div></section>
  </div>
}
