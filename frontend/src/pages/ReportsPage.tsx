import { useEffect, useState } from 'react'
import { Download, ExternalLink, MessageSquare, Quote, ScrollText, Send, Split } from 'lucide-react'
import ReactMarkdown from 'react-markdown'
import { api, download } from '../api'
import { EmptyState, formatDate, PageHeader, StatusChip } from '../components'
import { useSession } from '../session'
import type { Citation, Comment, ReportVersion, TaskSnapshot, TaskSummary } from '../types'

export default function ReportsPage() {
  const { userId, workspace } = useSession()
  const [tasks, setTasks] = useState<TaskSummary[]>([])
  const [selectedId, setSelectedId] = useState(() => new URLSearchParams(location.search).get('task') ?? '')
  const [task, setTask] = useState<TaskSnapshot>()
  const [citations, setCitations] = useState<Citation[]>([])
  const [versions, setVersions] = useState<ReportVersion[]>([])
  const [comments, setComments] = useState<Comment[]>([])
  const [comment, setComment] = useState('')
  const [tab, setTab] = useState<'sources' | 'versions' | 'comments'>('sources')

  useEffect(() => {
    if (!workspace) return
    void api<TaskSummary[]>(`/api/research/tasks?workspaceId=${workspace.id}`, userId).then((items) => {
      setTasks(items)
      if (!selectedId && items[0]) setSelectedId(items[0].taskId)
    })
  }, [workspace?.id])

  const loadDetail = async () => {
    if (!selectedId) return
    const [detail, sourceList, versionList, commentList] = await Promise.all([
      api<TaskSnapshot>(`/api/research/tasks/${selectedId}`, userId),
      api<Citation[]>(`/api/research/tasks/${selectedId}/citations`, userId),
      api<ReportVersion[]>(`/api/research/tasks/${selectedId}/versions`, userId),
      api<Comment[]>(`/api/research/tasks/${selectedId}/comments`, userId),
    ])
    setTask(detail); setCitations(sourceList); setVersions(versionList); setComments(commentList)
  }
  useEffect(() => { void loadDetail() }, [selectedId])

  const addComment = async () => {
    if (!comment.trim()) return
    await api(`/api/research/tasks/${selectedId}/comments`, userId, { method: 'POST', body: JSON.stringify({ content: comment }) })
    setComment(''); await loadDetail()
  }

  return <div>
    <PageHeader eyebrow="REPORT ARCHIVE" title="研究成果库" description="报告正文、证据来源和团队讨论保持在同一个可审计记录中。" />
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
          <div className="drawer-tabs">
            <button className={tab === 'sources' ? 'active' : ''} onClick={() => setTab('sources')}><Quote />来源</button>
            <button className={tab === 'versions' ? 'active' : ''} onClick={() => setTab('versions')}><Split />版本</button>
            <button className={tab === 'comments' ? 'active' : ''} onClick={() => setTab('comments')}><MessageSquare />讨论</button>
          </div>
          {tab === 'sources' && <div className="citation-list">{citations.map((source) => <article key={source.number}><span className="citation-number">[{source.number}]</span><div><strong>{source.title}</strong><p>{source.excerpt}</p><small>{source.sourceType} · 置信度 {(source.confidence * 100).toFixed(0)}%</small>{source.url && <a href={source.url} target="_blank">查看原文 <ExternalLink size={13} /></a>}</div></article>)}</div>}
          {tab === 'versions' && <div className="version-list">{versions.map((version) => <article key={version.versionNumber}><ScrollText /><div><strong>版本 {version.versionNumber}</strong><p>{version.createdBy}</p><small>{formatDate(version.createdAt)}</small></div></article>)}</div>}
          {tab === 'comments' && <div className="comments-panel"><div className="comment-list">{comments.map((item) => <article key={item.id}><strong>{item.authorUserId}</strong><p>{item.content}</p><small>{formatDate(item.createdAt)}</small></article>)}</div><div className="comment-compose"><textarea value={comment} onChange={(event) => setComment(event.target.value)} placeholder="写下审阅意见…" /><button onClick={() => void addComment()}><Send size={16} /></button></div></div>}
        </aside>}
      </div>}
  </div>
}
