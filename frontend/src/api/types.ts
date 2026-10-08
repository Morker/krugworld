// Shapes of what the Spring API sends and accepts. Money arrives as JSON
// numbers (BigDecimal) and is sent back as strings with two decimals, so no
// floating-point rounding ever reaches the database.

export type Skin = 'classic' | 'modern'
export type Theme = 'steel' | 'oak' | 'forest' | 'parchment'

export type Me = { id: number; username: string; skin: Skin; theme: Theme }

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

export type ImportResult = { entries: number; recurring: number; categoriesCreated: number; skipped: number }
