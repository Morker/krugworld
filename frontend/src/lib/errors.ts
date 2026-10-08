import { ApiError } from '../api/client'

/** Turns any thrown error into a sentence for the user. */
export function errorText(error: unknown): string | null {
  if (!error) return null
  if (error instanceof ApiError) {
    const first = Object.values(error.fields)[0]
    return first ? `${error.message} (${first})` : error.message
  }
  return 'Could not reach Coffold. Check your connection.'
}
