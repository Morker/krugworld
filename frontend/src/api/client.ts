// The one place that talks HTTP. Every request goes to our own origin
// (/api/...), which Vite proxies to Spring in dev and Spring serves directly
// in production.

export class ApiError extends Error {
  readonly status: number
  readonly code: string
  readonly fields: Record<string, string>

  constructor(status: number, code: string, message: string, fields: Record<string, string> = {}) {
    super(message)
    this.status = status
    this.code = code
    this.fields = fields
  }
}

// Spring Security sends the CSRF token in a readable XSRF-TOKEN cookie and
// expects it back in this header on every request that changes something.
function csrfToken(): string {
  const match = document.cookie.match(/(?:^|;\s*)XSRF-TOKEN=([^;]+)/)
  return match ? decodeURIComponent(match[1]) : ''
}

type Options = { method?: string; json?: unknown; form?: URLSearchParams }

export async function api<T>(path: string, { method = 'GET', json, form }: Options = {}): Promise<T> {
  const headers: Record<string, string> = { Accept: 'application/json' }
  if (method !== 'GET') headers['X-XSRF-TOKEN'] = csrfToken()

  let body: BodyInit | undefined
  if (json !== undefined) {
    headers['Content-Type'] = 'application/json'
    body = JSON.stringify(json)
  } else if (form) {
    body = form
  }

  const res = await fetch(path, { method, headers, body, credentials: 'same-origin' })

  if (!res.ok) {
    let data: { code?: string; message?: string; fields?: Record<string, string> } = {}
    try {
      data = await res.json()
    } catch {
      // Some errors (401 from Spring Security) have no body.
    }
    const fallback = res.status === 401 ? 'Please log in.' : 'Something went wrong.'
    throw new ApiError(res.status, data.code ?? `http_${res.status}`, data.message ?? fallback, data.fields)
  }
  if (res.status === 204 || res.headers.get('Content-Length') === '0') return undefined as T
  const text = await res.text()
  return (text ? JSON.parse(text) : undefined) as T
}

/** Money goes over the wire as a string with exactly two decimals. */
export function money(value: string | number): string {
  return Number(value).toFixed(2)
}
