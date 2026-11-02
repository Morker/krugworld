// Shapes of what the Spring API sends and accepts. Money arrives as JSON
// numbers (BigDecimal) and is sent back as strings with two decimals, so no
// floating-point rounding ever reaches the database.

export type Skin = 'classic' | 'modern'
export type Theme = 'steel' | 'oak' | 'forest' | 'parchment'

export type WeatherPlace = { place: string; lat: number; lon: number }
export type Me = {
  id: number
  username: string
  email: string | null
  skin: Skin
  theme: Theme
  calorieGoal: number
  tempUnit: 'C' | 'F'
  weather: WeatherPlace | null
  admin: boolean
}

export type AdminUser = { id: number; username: string; admin: boolean; createdAt: string; totalBalance: number }
// What a row looked like, as saved in the audit: an Entry or an Account.
export type AuditState = { amount?: number; type?: EntryType; note?: string; balance?: number; name?: string }
export type AuditRow = {
  id: number
  admin: string
  action: 'ENTRY_CREATED' | 'ENTRY_UPDATED' | 'ENTRY_DELETED' | 'BALANCE_SET'
  refId: number
  before: AuditState | null
  after: AuditState | null
  at: string
}

export type AccountKind = 'BANK' | 'CASH' | 'EWALLET' | 'CARD' | 'OTHER'
export type Account = {
  id: number
  name: string
  kind: AccountKind
  openingBalance: number
  balance: number
  archived: boolean
}

export type CategoryKind = 'EXPENSE' | 'INCOME'
export type Category = {
  id: number
  name: string
  kind: CategoryKind
  icon: string
  color: string
  monthlyBudget: number | null
  archived: boolean
}

export type EntryType = 'INCOME' | 'EXPENSE' | 'TRANSFER'
export type Ref = { id: number; name: string }
export type CategoryRef = { id: number; name: string; icon: string; color: string }
export type Entry = {
  id: number
  type: EntryType
  amount: number
  occurredOn: string
  note: string
  account: Ref
  toAccount: Ref | null
  category: CategoryRef | null
  recurring: boolean
}
export type EntryRequest = {
  type: EntryType
  amount: string
  accountId: number
  toAccountId?: number | null
  categoryId?: number | null
  occurredOn: string
  note: string
}

export type MonthSummary = {
  month: string
  income: number
  expense: number
  net: number
  spendingByCategory: { category: CategoryRef; total: number }[]
}

export type CategoryBudget = { category: CategoryRef; budget: number; spent: number; left: number }
export type MonthBudget = {
  month: string
  daysLeft: number
  totalBudget: number
  totalSpent: number
  left: number
  unbudgetedSpent: number
  categories: CategoryBudget[]
}

export type Rule = {
  id: number
  type: 'INCOME' | 'EXPENSE'
  amount: number
  account: Ref
  category: CategoryRef
  note: string
  dayOfMonth: number
  nextRunOn: string
  active: boolean
}

export type Quest = {
  id: number
  name: string
  icon: string
  targetAmount: number
  savedAmount: number
  deadline: string | null
  completedAt: string | null
  xpReward: number
}

export type Badge = { id: string; name: string; description: string; icon: string; earned: boolean }

export type CharacterSheet = {
  username: string
  level: number
  title: string
  xp: number
  levelXp: number
  levelXpNeeded: number
  gold: number
  incomeThisMonth: number
  spentThisMonth: number
  budget: MonthBudget
  streak: { current: number; longest: number; last7: boolean[] }
  badges: Badge[]
  quests: Quest[]
  loot: Entry[]
}

export type ImportResult = {
  entries: number
  recurring: number
  categoriesCreated: number
  events: number
  meals: number
  workouts: number
  ideas: number
  skipped: number
}

// ---------------- life ----------------

export type CalendarEvent = { id: number; occursOn: string; atTime: string | null; title: string }

export type IdeaStatus = 'TODO' | 'DOING' | 'DONE'
export type Idea = { id: number; title: string; notes: string; status: IdeaStatus; updatedAt: string }

export type Meal = { id: number; name: string; calories: number }
export type MealDay = {
  date: string
  goal: number
  total: number
  meals: Meal[]
  week: { date: string; calories: number }[]
}

export type Weekday = 'MONDAY' | 'TUESDAY' | 'WEDNESDAY' | 'THURSDAY' | 'FRIDAY' | 'SATURDAY' | 'SUNDAY'
export type Workout = {
  id: number
  name: string
  days: Weekday[]
  scheduled: boolean
  done: boolean
  streak: number
  totalDone: number
}

export type LifeToday = {
  date: string
  calories: number
  calorieGoal: number
  workouts: { id: number; name: string; done: boolean }[]
  upcoming: CalendarEvent[]
  ideasInProgress: number
  ideasWaiting: number
}

// ---------------- steward ----------------

export type StewardStatus = { ready: boolean; provider: string; remaining: number; limit: number }
export type StewardTurn = { role: 'user' | 'assistant'; text: string; subtitle?: string | null; error?: boolean }
export type StewardReply = { reply: string; subtitle: string | null; remaining: number }

// ---------------- desk devices ----------------

export type Device = { id: number; name: string; createdAt: string; lastSeenAt: string | null }
export type PairingCode = { code: string; expiresAt: string }
export type PushDevice = { id: number; label: string; endpoint: string; createdAt: string; lastSentAt: string | null }
export type PushDelivery = { sent: number; devices: number }
