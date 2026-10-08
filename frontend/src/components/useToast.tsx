import { useEffect, useState } from 'react'

/** A brief confirmation message, e.g. "Logged · +5 XP". Render `toast` anywhere on the page. */
export function useToast() {
  const [text, setText] = useState<string | null>(null)
  useEffect(() => {
    if (!text) return
    const t = setTimeout(() => setText(null), 2200)
    return () => clearTimeout(t)
  }, [text])
  return {
    toast: text ? <div className="toast" role="status">{text}</div> : null,
    showToast: setText,
  }
}
