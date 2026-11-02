import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { api } from './client'
import { useInvalidateMoney } from './hooks'
import type { Account, AdminUser, AuditRow, Category, Entry, EntryRequest, MonthSummary } from './types'

const base = (userId: number) => `/api/admin/users/${userId}`

export const useAdminUsers = () =>
  useQuery({ queryKey: ['admin', 'users'], queryFn: () => api<AdminUser[]>('/api/admin/users') })

export const useAdminAccounts = (userId: number) =>
  useQuery({ queryKey: ['admin', userId, 'accounts'], queryFn: () => api<Account[]>(`${base(userId)}/accounts`) })

export const useAdminCategories = (userId: number) =>
  useQuery({ queryKey: ['admin', userId, 'categories'], queryFn: () => api<Category[]>(`${base(userId)}/categories`) })

export const useAdminEntries = (userId: number, month: string) =>
  useQuery({
    queryKey: ['admin', userId, 'entries', month],
    queryFn: () => api<Entry[]>(`${base(userId)}/entries?month=${month}`),
  })

export const useAdminSummary = (userId: number, month: string) =>
  useQuery({
    queryKey: ['admin', userId, 'summary', month],
    queryFn: () => api<MonthSummary>(`${base(userId)}/summary?month=${month}`),
  })

export const useAdminAudit = (userId: number) =>
  useQuery({ queryKey: ['admin', userId, 'audit'], queryFn: () => api<AuditRow[]>(`${base(userId)}/audit`) })

// An admin may be repairing their own account, so their own cached money goes too.
function useInvalidateAdmin() {
  const qc = useQueryClient()
  const invalidateMoney = useInvalidateMoney()
  return () => Promise.all([qc.invalidateQueries({ queryKey: ['admin'] }), invalidateMoney()])
}

export function useAdminSaveEntry(userId: number) {
  const invalidate = useInvalidateAdmin()
  return useMutation({
    mutationFn: ({ id, ...body }: EntryRequest & { id?: number }) =>
      id
        ? api<Entry>(`${base(userId)}/entries/${id}`, { method: 'PUT', json: body })
        : api<Entry>(`${base(userId)}/entries`, { method: 'POST', json: body }),
    onSuccess: invalidate,
  })
}

export function useAdminDeleteEntry(userId: number) {
  const invalidate = useInvalidateAdmin()
  return useMutation({
    mutationFn: (id: number) => api(`${base(userId)}/entries/${id}`, { method: 'DELETE' }),
    onSuccess: invalidate,
  })
}

export function useAdminSetBalance(userId: number) {
  const invalidate = useInvalidateAdmin()
  return useMutation({
    mutationFn: ({ accountId, balance }: { accountId: number; balance: string }) =>
      api<Account>(`${base(userId)}/accounts/${accountId}/balance`, { method: 'PUT', json: { balance } }),
    onSuccess: invalidate,
  })
}
