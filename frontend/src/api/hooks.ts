import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { api } from './client'
import type {
  Account,
  Category,
  CharacterSheet,
  Device,
  Entry,
  EntryRequest,
  ImportResult,
  Me,
  MonthBudget,
  MonthSummary,
  PairingCode,
  Quest,
  Rule,
  Skin,
  Theme,
} from './types'

// React Query caches each GET under a key. After a change, we "invalidate"
// the keys whose data it affects and they refetch — e.g. logging an entry
// changes balances, budgets, the month summary and the character sheet.
const MONEY_KEYS = [['entries'], ['accounts'], ['summary'], ['budgets'], ['character'], ['recurring']]

function useInvalidateMoney() {
  const qc = useQueryClient()
  return () => Promise.all(MONEY_KEYS.map((queryKey) => qc.invalidateQueries({ queryKey })))
}

// ---------------- auth ----------------

// Switching who's logged in: set the new "me" and drop every other cached
// answer (it belonged to the previous user). clear() would also detach the
// "me" query the app is watching, so the switch would never be noticed.
function switchUser(qc: ReturnType<typeof useQueryClient>, me: Me | null) {
  qc.setQueryData(['me'], me)
  qc.removeQueries({ predicate: (q) => q.queryKey[0] !== 'me' })
}

export function useMe() {
  return useQuery({
    queryKey: ['me'],
    queryFn: async () => {
      try {
        return await api<Me>('/api/auth/me')
      } catch (e) {
        if ((e as { status?: number }).status === 401) return null
        throw e
      }
    },
    staleTime: Infinity,
  })
}

export function useLogin() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: async ({ username, password }: { username: string; password: string }) => {
      await api('/api/auth/login', { method: 'POST', form: new URLSearchParams({ username, password }) })
      return api<Me>('/api/auth/me')
    },
    onSuccess: (me) => switchUser(qc, me),
  })
}

export function useRegister() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (body: { username: string; password: string }) =>
      api<Me>('/api/auth/register', { method: 'POST', json: body }),
    onSuccess: (me) => switchUser(qc, me),
  })
}

export function useLogout() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: () => api('/api/auth/logout', { method: 'POST' }),
    onSettled: () => switchUser(qc, null),
  })
}

export function useSavePreferences() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (prefs: {
      skin?: Skin
      theme?: Theme
      calorieGoal?: number
      tempUnit?: 'C' | 'F'
      weather?: { place: string; lat?: number; lon?: number }
    }) =>
      api<Me>('/api/auth/me/preferences', { method: 'PATCH', json: prefs }),
    onSuccess: (me) => {
      qc.setQueryData(['me'], me)
      return qc.invalidateQueries({ queryKey: ['meals'] })
    },
  })
}

// ---------------- reads ----------------

export const useCharacter = () =>
  useQuery({ queryKey: ['character'], queryFn: () => api<CharacterSheet>('/api/character') })

export const useAccounts = () => useQuery({ queryKey: ['accounts'], queryFn: () => api<Account[]>('/api/accounts') })

export const useCategories = () =>
  useQuery({ queryKey: ['categories'], queryFn: () => api<Category[]>('/api/categories') })

export const useEntries = (month: string) =>
  useQuery({ queryKey: ['entries', month], queryFn: () => api<Entry[]>(`/api/entries?month=${month}`) })

export const useSummary = (month: string) =>
  useQuery({ queryKey: ['summary', month], queryFn: () => api<MonthSummary>(`/api/ledger/summary?month=${month}`) })

export const useBudgets = (month: string) =>
  useQuery({ queryKey: ['budgets', month], queryFn: () => api<MonthBudget>(`/api/budgets?month=${month}`) })

export const useRules = () => useQuery({ queryKey: ['recurring'], queryFn: () => api<Rule[]>('/api/recurring') })

export const useQuests = () => useQuery({ queryKey: ['quests'], queryFn: () => api<Quest[]>('/api/quests') })

// ---------------- writes ----------------

export function useSaveEntry() {
  const invalidate = useInvalidateMoney()
  return useMutation({
    mutationFn: ({ id, ...body }: EntryRequest & { id?: number }) =>
      id
        ? api<Entry>(`/api/entries/${id}`, { method: 'PUT', json: body })
        : api<Entry>('/api/entries', { method: 'POST', json: body }),
    onSuccess: invalidate,
  })
}

export function useDeleteEntry() {
  const qc = useQueryClient()
  const invalidate = useInvalidateMoney()
  return useMutation({
    mutationFn: (id: number) => api(`/api/entries/${id}`, { method: 'DELETE' }),
    // The row leaves the list at once; it comes back if the server refuses.
    onMutate: async (id) => {
      await qc.cancelQueries({ queryKey: ['entries'] })
      const before = qc.getQueriesData<Entry[]>({ queryKey: ['entries'] })
      qc.setQueriesData<Entry[]>({ queryKey: ['entries'] }, (old) => old?.filter((e) => e.id !== id))
      return { before }
    },
    onError: (_error, _id, ctx) => ctx?.before.forEach(([key, data]) => qc.setQueryData(key, data)),
    onSettled: invalidate,
  })
}

export type AccountInput = { name: string; kind: string; openingBalance: string }

export function useSaveAccount() {
  const invalidate = useInvalidateMoney()
  return useMutation({
    mutationFn: ({ id, ...body }: AccountInput & { id?: number }) =>
      id
        ? api<Account>(`/api/accounts/${id}`, { method: 'PUT', json: body })
        : api<Account>('/api/accounts', { method: 'POST', json: body }),
    onSuccess: invalidate,
  })
}

export function useArchiveAccount() {
  const invalidate = useInvalidateMoney()
  return useMutation({
    mutationFn: ({ id, restore }: { id: number; restore?: boolean }) =>
      restore
        ? api(`/api/accounts/${id}/restore`, { method: 'POST' })
        : api(`/api/accounts/${id}`, { method: 'DELETE' }),
    onSuccess: invalidate,
  })
}

export type CategoryInput = {
  name: string
  kind: string
  icon: string
  color: string
  monthlyBudget: string | null
}

export function useSaveCategory() {
  const qc = useQueryClient()
  const invalidate = useInvalidateMoney()
  return useMutation({
    mutationFn: ({ id, ...body }: CategoryInput & { id?: number }) =>
      id
        ? api<Category>(`/api/categories/${id}`, { method: 'PUT', json: body })
        : api<Category>('/api/categories', { method: 'POST', json: body }),
    onSuccess: () => Promise.all([qc.invalidateQueries({ queryKey: ['categories'] }), invalidate()]),
  })
}

export function useArchiveCategory() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: ({ id, restore }: { id: number; restore?: boolean }) =>
      restore
        ? api(`/api/categories/${id}/restore`, { method: 'POST' })
        : api(`/api/categories/${id}`, { method: 'DELETE' }),
    onSuccess: () =>
      Promise.all([
        qc.invalidateQueries({ queryKey: ['categories'] }),
        qc.invalidateQueries({ queryKey: ['budgets'] }),
      ]),
  })
}

export type RuleInput = {
  type: string
  amount: string
  accountId: number
  categoryId: number
  dayOfMonth: number
  startOn?: string
  note: string
  active?: boolean
}

export function useSaveRule() {
  const invalidate = useInvalidateMoney()
  return useMutation({
    mutationFn: ({ id, ...body }: RuleInput & { id?: number }) =>
      id
        ? api<Rule>(`/api/recurring/${id}`, { method: 'PUT', json: body })
        : api<Rule>('/api/recurring', { method: 'POST', json: body }),
    onSuccess: invalidate,
  })
}

export function useDeleteRule() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (id: number) => api(`/api/recurring/${id}`, { method: 'DELETE' }),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['recurring'] }),
  })
}

export type QuestInput = { name: string; icon: string; targetAmount: string; deadline: string | null }

function useInvalidateQuests() {
  const qc = useQueryClient()
  return () =>
    Promise.all([
      qc.invalidateQueries({ queryKey: ['quests'] }),
      qc.invalidateQueries({ queryKey: ['character'] }),
    ])
}

export function useSaveQuest() {
  const invalidate = useInvalidateQuests()
  return useMutation({
    mutationFn: ({ id, ...body }: QuestInput & { id?: number }) =>
      id
        ? api<Quest>(`/api/quests/${id}`, { method: 'PUT', json: body })
        : api<Quest>('/api/quests', { method: 'POST', json: body }),
    onSuccess: invalidate,
  })
}

export function useContribute() {
  const invalidate = useInvalidateQuests()
  return useMutation({
    mutationFn: ({ id, amount }: { id: number; amount: string }) =>
      api<Quest>(`/api/quests/${id}/contributions`, { method: 'POST', json: { amount } }),
    onSuccess: invalidate,
  })
}

export function useDeleteQuest() {
  const invalidate = useInvalidateQuests()
  return useMutation({
    mutationFn: (id: number) => api(`/api/quests/${id}`, { method: 'DELETE' }),
    onSuccess: invalidate,
  })
}

export function useTavernImport() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (payload: unknown) => api<ImportResult>('/api/import/tavern', { method: 'POST', json: payload }),
    onSuccess: () => qc.invalidateQueries(),
  })
}

// ---------------- desk devices ----------------

export function useDevices() {
  return useQuery({ queryKey: ['devices'], queryFn: () => api<Device[]>('/api/devices') })
}

export function useNewPairingCode() {
  return useMutation({
    mutationFn: () => api<PairingCode>('/api/devices/pairing-code', { method: 'POST' }),
  })
}

export function useRevokeDevice() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (id: number) => api(`/api/devices/${id}`, { method: 'DELETE' }),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['devices'] }),
  })
}
