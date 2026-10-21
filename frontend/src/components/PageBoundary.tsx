import { Component, Suspense } from 'react'
import type { ErrorInfo, ReactNode } from 'react'
import { Loading } from './ui'

/**
 * Pages load as separate chunks. If one can't be fetched (offline mid-visit,
 * or a deploy replaced the file), show a way back instead of a blank screen.
 */
class ChunkErrorBoundary extends Component<{ children: ReactNode }, { failed: boolean }> {
  state = { failed: false }

  static getDerivedStateFromError() {
    return { failed: true }
  }

  componentDidCatch(error: Error, info: ErrorInfo) {
    console.error('Page failed to load', error, info.componentStack)
  }

  render() {
    if (!this.state.failed) return this.props.children
    return (
      <section className="panel page-error" role="alert">
        <h2>This page wandered off</h2>
        <p className="muted">It could not be loaded. Check your connection and try again.</p>
        <button onClick={() => window.location.reload()}>Reload</button>
      </section>
    )
  }
}

/** Keeps the menu on screen while a page's code arrives, with skeleton bars in its place. */
export function PageBoundary({ children }: { children: ReactNode }) {
  return (
    <ChunkErrorBoundary>
      <Suspense fallback={<Loading rows={4} />}>{children}</Suspense>
    </ChunkErrorBoundary>
  )
}
