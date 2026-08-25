import { createContext, useContext, useEffect, useState, type ReactNode } from 'react'
import { api } from './api'
import type { Workspace } from './types'

interface SessionValue {
  userId: string
  workspaces: Workspace[]
  workspace?: Workspace
  loading: boolean
  setIdentity: (value: string) => void
  clearIdentity: () => void
  setWorkspaceId: (value: string) => void
  refreshWorkspaces: () => Promise<void>
}

const SessionContext = createContext<SessionValue | null>(null)

export function SessionProvider({ children }: { children: ReactNode }) {
  const [userId, setUserId] = useState(() => localStorage.getItem('researchflow.user') ?? '')
  const [workspaceId, setWorkspaceIdState] = useState(() => localStorage.getItem('researchflow.workspace') ?? '')
  const [workspaces, setWorkspaces] = useState<Workspace[]>([])
  const [loading, setLoading] = useState(Boolean(userId))

  const refreshWorkspaces = async () => {
    if (!userId) return
    setLoading(true)
    try {
      const result = await api<Workspace[]>('/api/workspaces', userId)
      setWorkspaces(result)
      const selected = result.some((item) => item.id === workspaceId) ? workspaceId : result[0]?.id ?? ''
      setWorkspaceIdState(selected)
      if (selected) localStorage.setItem('researchflow.workspace', selected)
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => { void refreshWorkspaces() }, [userId])

  const setIdentity = (value: string) => {
    const clean = value.trim()
    localStorage.setItem('researchflow.user', clean)
    setUserId(clean)
  }
  const clearIdentity = () => {
    localStorage.removeItem('researchflow.user')
    localStorage.removeItem('researchflow.workspace')
    setUserId('')
    setWorkspaces([])
  }
  const setWorkspaceId = (value: string) => {
    localStorage.setItem('researchflow.workspace', value)
    setWorkspaceIdState(value)
  }

  return <SessionContext.Provider value={{
    userId, workspaces, workspace: workspaces.find((item) => item.id === workspaceId), loading,
    setIdentity, clearIdentity, setWorkspaceId, refreshWorkspaces,
  }}>{children}</SessionContext.Provider>
}

export function useSession() {
  const value = useContext(SessionContext)
  if (!value) throw new Error('SessionProvider is missing')
  return value
}
