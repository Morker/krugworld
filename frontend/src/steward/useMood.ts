import { useEffect, useState } from 'react'
import { useConversation } from './conversation'
import type { Mood } from './Portrait'

/** Gaby's mood: thinking while a reply is on its way, talking for a moment after one lands. */
export function useMood(): Mood {
  const { pending, repliedAt } = useConversation()
  const [quietSince, setQuietSince] = useState(repliedAt)
  useEffect(() => {
    if (!repliedAt) return
    const t = setTimeout(() => setQuietSince(repliedAt), 1600)
    return () => clearTimeout(t)
  }, [repliedAt])
  const talking = repliedAt !== 0 && quietSince !== repliedAt
  return pending ? 'thinking' : talking ? 'talking' : 'idle'
}
