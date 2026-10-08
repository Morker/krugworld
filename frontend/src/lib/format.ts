const amount = new Intl.NumberFormat('en-MY', { minimumFractionDigits: 2, maximumFractionDigits: 2 })

/** RM 1,234.50 — or −RM 12.00 for negatives. */
export function rm(value: number): string {
  const text = `RM ${amount.format(Math.abs(value))}`
  return value < 0 ? `−${text}` : text
}

/** +6,200.00 / −128.40, for entry lists where the sign carries the meaning. */
export function signed(value: number, negative: boolean): string {
  return `${negative ? '−' : '+'}${amount.format(Math.abs(value))}`
}

// Dates are local calendar dates ("2026-10-08"), never instants, so they're
// built from local parts rather than toISOString() (which is UTC).
function pad(n: number) {
  return String(n).padStart(2, '0')
}

export function today(): string {
  const d = new Date()
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`
}

export function currentMonth(): string {
  return today().slice(0, 7)
}

export function shiftMonth(month: string, by: number): string {
  const [y, m] = month.split('-').map(Number)
  const d = new Date(y, m - 1 + by, 1)
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}`
}

export function monthLabel(month: string): string {
  const [y, m] = month.split('-').map(Number)
  return new Date(y, m - 1, 1).toLocaleDateString('en-MY', { month: 'long', year: 'numeric' })
}

export function dayLabel(date: string): string {
  if (date === today()) return 'Today'
  const [y, m, d] = date.split('-').map(Number)
  return new Date(y, m - 1, d).toLocaleDateString('en-MY', { weekday: 'short', day: 'numeric', month: 'short' })
}

export function percent(part: number, whole: number): number {
  if (whole <= 0) return 0
  return Math.max(0, Math.min(100, (part / whole) * 100))
}

/** A local calendar date as YYYY-MM-DD. */
export function iso(d: Date): string {
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`
}

export function parseDate(date: string): Date {
  const [y, m, d] = date.split('-').map(Number)
  return new Date(y, m - 1, d)
}

export function addDays(date: string, n: number): string {
  const d = parseDate(date)
  d.setDate(d.getDate() + n)
  return iso(d)
}

/** "Today", "Tomorrow", "in 3 days" or a short date. */
export function relativeDay(date: string): string {
  const diff = Math.round((parseDate(date).getTime() - parseDate(today()).getTime()) / 86_400_000)
  if (diff === 0) return 'Today'
  if (diff === 1) return 'Tomorrow'
  if (diff > 1 && diff < 7) return `in ${diff} days`
  return dayLabel(date)
}

/** "14:30:00" -> "14:30". */
export function time(t: string | null): string {
  return t ? t.slice(0, 5) : ''
}
