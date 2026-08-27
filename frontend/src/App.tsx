import { useState } from 'react'
import { Navigate, NavLink, Route, Routes } from 'react-router-dom'
import {
  Activity, Archive, Atom, BookOpen, ChevronDown, CreditCard, FlaskConical,
  LogOut, Network, Radio, Settings2, Users,
} from 'lucide-react'
import { api } from './api'
import { SessionProvider, useSession } from './session'
import ResearchPage from './pages/ResearchPage'
import KnowledgePage from './pages/KnowledgePage'
import ReportsPage from './pages/ReportsPage'
import SubscriptionsPage from './pages/SubscriptionsPage'
import TeamPage from './pages/TeamPage'
import BillingPage from './pages/BillingPage'
import ExternalTracesPage from './pages/ExternalTracesPage'
import type { Workspace } from './types'

const nav = [
  { to: '/research', label: '研究台', icon: FlaskConical },
  { to: '/knowledge', label: '知识库', icon: BookOpen },
  { to: '/reports', label: '报告库', icon: Archive },
  { to: '/traces', label: '链路接入', icon: Network },
  { to: '/subscriptions', label: '情报订阅', icon: Radio },
  { to: '/team', label: '团队', icon: Users },
  { to: '/billing', label: '用量', icon: CreditCard },
]

export default function App() {
  return <SessionProvider><AppContent /></SessionProvider>
}

function AppContent() {
  const session = useSession()
  if (!session.userId) return <IdentityGate onContinue={session.setIdentity} />
  if (session.loading) return <div className="loading-screen"><Atom className="spin" /> 正在装载研究空间</div>
  if (!session.workspace) return <WorkspaceGate />

  return (
    <div className="app-shell">
      <aside className="sidebar">
        <div className="brand-mark"><Atom size={24} /><span>Research<br />Flow</span></div>
        <nav className="primary-nav">
          {nav.map(({ to, label, icon: Icon }) => (
            <NavLink key={to} to={to} className={({ isActive }) => isActive ? 'nav-item active' : 'nav-item'}>
              <Icon size={18} /><span>{label}</span>
            </NavLink>
          ))}
        </nav>
        <div className="sidebar-foot">
          <span className="utility-label">当前身份</span>
          <strong>{session.userId}</strong>
          <button className="text-button" onClick={session.clearIdentity}><LogOut size={15} /> 切换身份</button>
        </div>
      </aside>
      <div className="workspace-frame">
        <header className="topbar">
          <div className="workspace-switch">
            <span className="signal-dot" />
            <select value={session.workspace.id} onChange={(event) => session.setWorkspaceId(event.target.value)}>
              {session.workspaces.map((workspace) => <option key={workspace.id} value={workspace.id}>{workspace.name}</option>)}
            </select>
            <ChevronDown size={15} />
          </div>
          <div className="topbar-meta">
            <span className="role-stamp">{session.workspace.currentUserRole}</span>
            <span>{session.workspace.planTier} PLAN</span>
          </div>
        </header>
        <main className="main-canvas">
          <Routes>
            <Route path="/research" element={<ResearchPage />} />
            <Route path="/knowledge" element={<KnowledgePage />} />
            <Route path="/reports" element={<ReportsPage />} />
            <Route path="/traces" element={<ExternalTracesPage />} />
            <Route path="/subscriptions" element={<SubscriptionsPage />} />
            <Route path="/team" element={<TeamPage />} />
            <Route path="/billing" element={<BillingPage />} />
            <Route path="*" element={<Navigate to="/research" replace />} />
          </Routes>
        </main>
      </div>
    </div>
  )
}

function IdentityGate({ onContinue }: { onContinue: (value: string) => void }) {
  const [value, setValue] = useState('')
  return (
    <div className="gate-page">
      <div className="gate-aside">
        <div className="orbit-diagram"><Atom /><i /><i /><i /></div>
        <p className="eyebrow">MULTI-AGENT RESEARCH SYSTEM</p>
        <h1>把问题交给一支<br />会协作的研究团队。</h1>
        <p>规划、检索、证据、比较与写作在同一条可追踪链路中完成。</p>
      </div>
      <form className="gate-card" onSubmit={(event) => { event.preventDefault(); if (value.trim()) onContinue(value) }}>
        <span className="step-mark">进入研究台</span>
        <h2>你希望以谁的身份工作？</h2>
        <p>当前版本用用户 ID 模拟登录。企业部署将替换为 SSO。</p>
        <label>用户 ID<input autoFocus value={value} onChange={(event) => setValue(event.target.value)} placeholder="例如 wang.zilin" /></label>
        <button className="primary-button" type="submit">继续 <Activity size={17} /></button>
      </form>
    </div>
  )
}

function WorkspaceGate() {
  const session = useSession()
  const [name, setName] = useState('')
  const [error, setError] = useState('')
  const create = async () => {
    try {
      setError('')
      await api<Workspace>('/api/workspaces', session.userId, { method: 'POST', body: JSON.stringify({ name }) })
      await session.refreshWorkspaces()
    } catch (reason) { setError(reason instanceof Error ? reason.message : '创建失败') }
  }
  return (
    <div className="workspace-gate">
      <Settings2 size={34} />
      <p className="eyebrow">FIRST WORKSPACE</p>
      <h1>建立你的第一间研究室</h1>
      <p>知识、报告、成员和用量都归属于一个独立空间。</p>
      <input value={name} onChange={(event) => setName(event.target.value)} placeholder="团队或项目名称" />
      {error && <p className="error-text">{error}</p>}
      <button className="primary-button" onClick={create} disabled={!name.trim()}>创建 Workspace</button>
    </div>
  )
}
