import type { ReactNode } from 'react'

export function PageHeader({ eyebrow, title, description, action }: {
  eyebrow: string; title: string; description: string; action?: ReactNode
}) {
  return <header className="page-header"><div><p className="eyebrow">{eyebrow}</p><h1>{title}</h1><p>{description}</p></div>{action}</header>
}

export function StatusChip({ status }: { status: string }) {
  return <span className={`status-chip status-${status.toLowerCase()}`}>{status.replaceAll('_', ' ')}</span>
}

export function EmptyState({ title, body }: { title: string; body: string }) {
  return <div className="empty-state"><span>∅</span><h3>{title}</h3><p>{body}</p></div>
}

export function formatDate(value?: string) {
  if (!value) return '—'
  return new Intl.DateTimeFormat('zh-CN', { month: 'short', day: 'numeric', hour: '2-digit', minute: '2-digit' }).format(new Date(value))
}
