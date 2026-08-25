export class ApiError extends Error {
  status: number
  constructor(status: number, message: string) {
    super(message)
    this.status = status
  }
}

export async function api<T>(path: string, userId: string, init: RequestInit = {}): Promise<T> {
  const headers = new Headers(init.headers)
  if (userId) headers.set('X-User-Id', userId)
  if (init.body && !(init.body instanceof FormData)) headers.set('Content-Type', 'application/json')
  const response = await fetch(path, { ...init, headers })
  if (!response.ok) {
    let message = `请求失败 ${path} (${response.status})`
    try {
      const body = await response.json()
      if (body.message) message = `${path}: ${body.message}`
    } catch { /* response is not JSON */ }
    console.error('[ResearchFlow]', response.status, path, message)
    throw new ApiError(response.status, message)
  }
  if (response.status === 204) return undefined as T
  return response.json() as Promise<T>
}

export async function download(path: string, userId: string, filename: string) {
  const response = await fetch(path, { headers: { 'X-User-Id': userId } })
  if (!response.ok) throw new ApiError(response.status, '导出失败')
  const url = URL.createObjectURL(await response.blob())
  const anchor = document.createElement('a')
  anchor.href = url
  anchor.download = filename
  anchor.click()
  URL.revokeObjectURL(url)
}
