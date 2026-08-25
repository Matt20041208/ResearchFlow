import { useEffect, useState } from 'react'
import { FileText, Search, Trash2, UploadCloud } from 'lucide-react'
import { api } from '../api'
import { EmptyState, formatDate, PageHeader } from '../components'
import { useSession } from '../session'
import type { KnowledgeDocument } from '../types'

export default function KnowledgePage() {
  const { userId, workspace } = useSession()
  const [documents, setDocuments] = useState<KnowledgeDocument[]>([])
  const [query, setQuery] = useState('')
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')

  const load = async () => {
    if (!workspace) return
    setDocuments(await api<KnowledgeDocument[]>(`/api/knowledge/documents?workspaceId=${workspace.id}`, userId))
  }
  useEffect(() => { void load() }, [workspace?.id])

  const upload = async (file: File) => {
    if (!workspace) return
    const body = new FormData()
    body.append('file', file); body.append('workspaceId', workspace.id)
    setBusy(true); setError('')
    try { await api('/api/knowledge/documents/upload', userId, { method: 'POST', body }); await load() }
    catch (reason) { setError(reason instanceof Error ? reason.message : '上传失败') }
    finally { setBusy(false) }
  }

  const remove = async (id: string) => { await api(`/api/knowledge/documents/${id}`, userId, { method: 'DELETE' }); await load() }
  const visible = documents.filter((item) => `${item.title} ${item.content}`.toLowerCase().includes(query.toLowerCase()))

  return <div>
    <PageHeader eyebrow="PRIVATE KNOWLEDGE" title="团队证据库" description="上传内部资料，让 Private Knowledge Agent 与公开论文并行检索。"
      action={workspace?.currentUserRole !== 'VIEWER' ? <label className="upload-button"><UploadCloud size={17} />{busy ? '解析中…' : '上传文档'}<input type="file" accept=".txt,.md,.markdown,.docx,.pdf" hidden onChange={(event) => event.target.files?.[0] && void upload(event.target.files[0])} /></label> : undefined} />
    <div className="toolbar-search"><Search size={17} /><input value={query} onChange={(event) => setQuery(event.target.value)} placeholder="筛选标题或内容" /><span>{documents.length} documents</span></div>
    {error && <p className="error-text">{error}</p>}
    {visible.length === 0 ? <EmptyState title="知识库还是空的" body="上传 PDF、Word 或 Markdown，为研究 Agent 提供内部证据。" /> :
      <div className="document-list">{visible.map((document) => <article className="document-row" key={document.id}>
        <div className="document-icon"><FileText /></div><div className="document-main"><strong>{document.title}</strong><p>{document.content.slice(0, 180)}{document.content.length > 180 ? '…' : ''}</p><span>{formatDate(document.createdAt)} · PRIVATE</span></div>
        {workspace?.currentUserRole !== 'VIEWER' && <button className="icon-button danger" onClick={() => void remove(document.id)}><Trash2 size={17} /></button>}
      </article>)}</div>}
  </div>
}
