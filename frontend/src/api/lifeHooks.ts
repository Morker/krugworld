import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { api } from './client'
import type {
  CalendarEvent,
  Idea,
  IdeaStatus,
  LifeToday,
  Meal,
  MealDay,
  StewardReply,
  StewardStatus,
  StewardTurn,
  Weekday,
  Workout,
} from './types'

// Anything here can change the Hall (XP, streak, today's panel), so writes
// refresh it along with their own list.
function useRefresh(...keys: string[]) {
  const qc = useQueryClient()
  return () =>
    Promise.all([...keys, 'character', 'life'].map((k) => qc.invalidateQueries({ queryKey: [k] })))
}

export const useLifeToday = () =>
  useQuery({ queryKey: ['life'], queryFn: () => api<LifeToday>('/api/life/today') })

// ---------------- calendar ----------------

export const useEvents = (from: string, to: string) =>
  useQuery({
    queryKey: ['events', from, to],
    queryFn: () => api<CalendarEvent[]>(`/api/events?from=${from}&to=${to}`),
  })

export type EventInput = { occursOn: string; atTime: string | null; title: string }

export function useSaveEvent() {
  const refresh = useRefresh('events')
  return useMutation({
    mutationFn: ({ id, ...body }: EventInput & { id?: number }) =>
      id
        ? api<CalendarEvent>(`/api/events/${id}`, { method: 'PUT', json: body })
        : api<CalendarEvent>('/api/events', { method: 'POST', json: body }),
    onSuccess: refresh,
  })
}

export function useDeleteEvent() {
  const refresh = useRefresh('events')
  return useMutation({
    mutationFn: (id: number) => api(`/api/events/${id}`, { method: 'DELETE' }),
    onSuccess: refresh,
  })
}

// ---------------- workshop ----------------

export const useIdeas = () => useQuery({ queryKey: ['ideas'], queryFn: () => api<Idea[]>('/api/ideas') })

export type IdeaInput = { title: string; notes: string; status?: IdeaStatus }

export function useSaveIdea() {
  const refresh = useRefresh('ideas')
  return useMutation({
    mutationFn: ({ id, ...body }: IdeaInput & { id?: number }) =>
      id
        ? api<Idea>(`/api/ideas/${id}`, { method: 'PUT', json: body })
        : api<Idea>('/api/ideas', { method: 'POST', json: body }),
    onSuccess: refresh,
  })
}

export function useDeleteIdea() {
  const refresh = useRefresh('ideas')
  return useMutation({
    mutationFn: (id: number) => api(`/api/ideas/${id}`, { method: 'DELETE' }),
    onSuccess: refresh,
  })
}

// ---------------- meals ----------------

export const useMeals = (date: string) =>
  useQuery({ queryKey: ['meals', date], queryFn: () => api<MealDay>(`/api/meals?date=${date}`) })

export function useAddMeal() {
  const refresh = useRefresh('meals')
  return useMutation({
    mutationFn: (body: { name: string; calories: number; eatenOn: string }) =>
      api<Meal>('/api/meals', { method: 'POST', json: body }),
    onSuccess: refresh,
  })
}

export function useDeleteMeal() {
  const refresh = useRefresh('meals')
  return useMutation({
    mutationFn: (id: number) => api(`/api/meals/${id}`, { method: 'DELETE' }),
    onSuccess: refresh,
  })
}

// ---------------- training ----------------

export const useWorkouts = (date: string) =>
  useQuery({ queryKey: ['workouts', date], queryFn: () => api<Workout[]>(`/api/workouts?date=${date}`) })

export function useSaveWorkout() {
  const refresh = useRefresh('workouts')
  return useMutation({
    mutationFn: ({ id, ...body }: { id?: number; name: string; days: Weekday[] }) =>
      id
        ? api<Workout>(`/api/workouts/${id}`, { method: 'PUT', json: body })
        : api<Workout>('/api/workouts', { method: 'POST', json: body }),
    onSuccess: refresh,
  })
}

export function useDeleteWorkout() {
  const refresh = useRefresh('workouts')
  return useMutation({
    mutationFn: (id: number) => api(`/api/workouts/${id}`, { method: 'DELETE' }),
    onSuccess: refresh,
  })
}

type Snapshot = [readonly unknown[], unknown][]

// Ticking a workout is the app's most repeated tap, so it answers at once:
// the box flips on screen, the request follows, and if the server says no the
// box flips back. Whatever happens, the real numbers (XP, streak) are
// refetched afterwards.
export function useSetWorkoutDone() {
  const qc = useQueryClient()
  const refresh = useRefresh('workouts')
  return useMutation({
    mutationFn: ({ id, date, done }: { id: number; date: string; done: boolean }) =>
      api<Workout>(`/api/workouts/${id}/done`, { method: 'POST', json: { date, done } }),
    onMutate: async ({ id, done }) => {
      await Promise.all([qc.cancelQueries({ queryKey: ['workouts'] }), qc.cancelQueries({ queryKey: ['life'] })])
      const before: Snapshot = [
        ...qc.getQueriesData({ queryKey: ['workouts'] }),
        ...qc.getQueriesData({ queryKey: ['life'] }),
      ]
      qc.setQueriesData<Workout[]>({ queryKey: ['workouts'] }, (old) =>
        old?.map((w) => (w.id === id ? { ...w, done } : w)))
      qc.setQueriesData<LifeToday>({ queryKey: ['life'] }, (old) =>
        old && { ...old, workouts: old.workouts.map((w) => (w.id === id ? { ...w, done } : w)) })
      return { before }
    },
    onError: (_error, _vars, ctx) => ctx?.before.forEach(([key, data]) => qc.setQueryData(key, data)),
    onSettled: refresh,
  })
}

// ---------------- steward ----------------

export const useStewardStatus = () =>
  useQuery({ queryKey: ['steward'], queryFn: () => api<StewardStatus>('/api/steward/status') })

export function useStewardChat() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: ({ message, history }: { message: string; history: StewardTurn[] }) =>
      api<StewardReply>('/api/steward/chat', {
        method: 'POST',
        json: { message, history: history.filter((t) => !t.error).map(({ role, text }) => ({ role, text })) },
      }),
    // The Steward may have changed anything: refresh it all.
    onSettled: () => qc.invalidateQueries({ predicate: (q) => q.queryKey[0] !== 'me' }),
  })
}
