import { useEffect, useState } from 'react'
import { Crown, Plus, ShieldCheck, UserRound } from 'lucide-react'
import { api } from '../api'
import { formatDate, PageHeader } from '../components'
import { useSession } from '../session'
import type { Member, Role } from '../types'

export default function TeamPage() {
  const { userId, workspace, refreshWorkspaces } = useSession()
  const [members, setMembers] = useState<Member[]>([])
  const [newUser, setNewUser] = useState('')
  const [role, setRole] = useState<Role>('VIEWER')
  const [error, setError] = useState('')
  const load = async () => workspace && setMembers(await api<Member[]>(`/api/workspaces/${workspace.id}/members`, userId))
  useEffect(() => { void load() }, [workspace?.id])
  const add = async () => {
    if (!workspace) return
    try { setError(''); await api(`/api/workspaces/${workspace.id}/members`, userId, { method: 'PUT', body: JSON.stringify({ userId: newUser, role }) }); setNewUser(''); await load() }
    catch (reason) { setError(reason instanceof Error ? reason.message : '添加失败') }
  }
  const setPlan = async (tier: string) => {
    if (!workspace) return
    await api(`/api/workspaces/${workspace.id}/plan?tier=${tier}`, userId, { method: 'PUT' }); await refreshWorkspaces()
  }
  return <div>
    <PageHeader eyebrow="TEAM SPACE" title="成员与访问边界" description="角色决定谁能阅读证据、发起研究和批准高风险发布。" />
    <section className="team-summary"><div><span className="plan-seal">{workspace?.planTier}</span><div><strong>{workspace?.name}</strong><p>{members.length} 位成员 · {workspace?.currentUserRole}</p></div></div>{workspace?.currentUserRole === 'OWNER' && <select value={workspace.planTier} onChange={(e) => void setPlan(e.target.value)}><option>FREE</option><option>TEAM</option><option>ENTERPRISE</option></select>}</section>
    {workspace?.currentUserRole === 'OWNER' && <section className="member-add"><input value={newUser} onChange={(e) => setNewUser(e.target.value)} placeholder="用户 ID" /><select value={role} onChange={(e) => setRole(e.target.value as Role)}><option value="VIEWER">Viewer</option><option value="EDITOR">Editor</option></select><button onClick={() => void add()} disabled={!newUser.trim()}><Plus size={16} /> 添加成员</button>{error && <p className="error-text">{error}</p>}</section>}
    <div className="member-list">{members.map((member) => <article key={member.userId}><span className={`avatar role-${member.role.toLowerCase()}`}>{member.role === 'OWNER' ? <Crown /> : member.role === 'EDITOR' ? <ShieldCheck /> : <UserRound />}</span><div><strong>{member.userId}</strong><p>加入于 {formatDate(member.joinedAt)}</p></div><span className="role-stamp">{member.role}</span></article>)}</div>
  </div>
}
