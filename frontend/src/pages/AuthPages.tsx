import { useState } from 'react'
import type { FormEvent } from 'react'
import { Link } from 'react-router-dom'
import { ApiError } from '../api/client'
import { useLogin, useRegister } from '../api/hooks'
import { ErrorLine, Field } from '../components/ui'

export function LoginPage() {
  const login = useLogin()
  const [username, setUsername] = useState('')
  const [password, setPassword] = useState('')

  function submit(e: FormEvent) {
    e.preventDefault()
    login.mutate({ username: username.trim(), password })
  }

  const wrong = login.error instanceof ApiError && login.error.status === 401
  return (
    <div className="auth">
      <section className="panel">
        <h1>Coffold</h1>
        <p className="muted" style={{ textAlign: 'center', margin: '6px 0 0' }}>Your coffer of gold awaits</p>
        <form onSubmit={submit}>
          <Field label="Name">
            <input autoComplete="username" autoFocus value={username} onChange={(e) => setUsername(e.target.value)} required />
          </Field>
          <Field label="Passphrase">
            <input type="password" autoComplete="current-password" value={password}
                   onChange={(e) => setPassword(e.target.value)} required />
          </Field>
          {wrong ? <p className="field-error" role="alert">That name and passphrase don't match.</p> : <ErrorLine error={login.error} />}
          <button type="submit" className="primary" disabled={login.isPending} style={{ justifyContent: 'center' }}>
            {login.isPending ? 'Opening the door…' : 'Enter'}
          </button>
          <p className="small muted" style={{ textAlign: 'center', margin: 0 }}>
            You'll stay logged in on this device. New here? <Link to="/register">Create an account</Link>
          </p>
        </form>
      </section>
    </div>
  )
}

export function RegisterPage() {
  const register = useRegister()
  const [username, setUsername] = useState('')
  const [password, setPassword] = useState('')
  const fields = register.error instanceof ApiError ? register.error.fields : {}

  function submit(e: FormEvent) {
    e.preventDefault()
    register.mutate({ username: username.trim(), password })
  }

  return (
    <div className="auth">
      <section className="panel">
        <h1>Coffold</h1>
        <p className="muted" style={{ textAlign: 'center', margin: '6px 0 0' }}>Carve your name into the ledger</p>
        <form onSubmit={submit}>
          <Field label="Name (3–40 letters, digits, _ or -)" error={fields.username}>
            <input autoComplete="username" autoFocus value={username} onChange={(e) => setUsername(e.target.value)}
                   pattern="[A-Za-z0-9_\-]{3,40}" required />
          </Field>
          <Field label="Passphrase (at least 8 characters)" error={fields.password}>
            <input type="password" autoComplete="new-password" minLength={8} value={password}
                   onChange={(e) => setPassword(e.target.value)} required />
          </Field>
          {Object.keys(fields).length === 0 && <ErrorLine error={register.error} />}
          <button type="submit" className="primary" disabled={register.isPending} style={{ justifyContent: 'center' }}>
            {register.isPending ? 'Carving…' : 'Create account'}
          </button>
          <p className="small muted" style={{ textAlign: 'center', margin: 0 }}>
            Already have one? <Link to="/login">Log in</Link>
          </p>
        </form>
      </section>
    </div>
  )
}
