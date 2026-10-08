import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import { BrowserRouter } from 'react-router-dom'
import { QueryCache, QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { ApiError } from './api/client'
import App from './App.tsx'
import { applySkin, readStoredSkin } from './skin/skin'
import './styles/skins.css'
import './styles/app.css'

// Wear the last-used skin from the very first paint.
applySkin(readStoredSkin())

const queryClient: QueryClient = new QueryClient({
  // If the server says we're no longer logged in (session and remember-me
  // both gone), drop back to the login page instead of showing errors.
  queryCache: new QueryCache({
    onError: (error) => {
      if (error instanceof ApiError && error.status === 401) queryClient.setQueryData(['me'], null)
    },
  }),
  defaultOptions: {
    queries: {
      retry: (count, error) => !(error instanceof ApiError && error.status < 500) && count < 2,
      refetchOnWindowFocus: false,
    },
  },
})

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <QueryClientProvider client={queryClient}>
      <BrowserRouter>
        <App />
      </BrowserRouter>
    </QueryClientProvider>
  </StrictMode>,
)
