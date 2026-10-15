import { useEffect, useState } from 'react'
import type { FormEvent } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { ApiError } from '../api/client'
import { useConfirmReset, useLogin, useRegister, useRequestReset } from '../api/hooks'
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
        <h1>KrugWorld</h1>
        <p className="muted" style={{ textAlign: 'center', margin: '6px 0 0' }}>Your realm awaits</p>
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
            <Link to="/forgot-password">Forgot your passphrase?</Link>
          </p>
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
  const [email, setEmail] = useState('')
  const fields = register.error instanceof ApiError ? register.error.fields : {}

  function submit(e: FormEvent) {
    e.preventDefault()
    register.mutate({ username: username.trim(), password, email: email.trim() || undefined })
  }

  return (
    <div className="auth">
      <section className="panel">
        <h1>KrugWorld</h1>
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
          <Field label="Email (optional: lets you reset a forgotten passphrase)" error={fields.email}>
            <input type="email" autoComplete="email" value={email} onChange={(e) => setEmail(e.target.value)} />
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

export function ForgotPage() {
  const request = useRequestReset()
  const [email, setEmail] = useState('')

  function submit(e: FormEvent) {
    e.preventDefault()
    request.mutate(email.trim())
  }

  return (
    <div className="auth">
      <section className="panel">
        <h1>KrugWorld</h1>
        <p className="muted" style={{ textAlign: 'center', margin: '6px 0 0' }}>Lost thy passphrase?</p>
        {request.isSuccess ? (
          <div className="stack" style={{ marginTop: 14 }}>
            <p role="status" style={{ margin: 0 }}>
              If an account uses <b>{email.trim()}</b>, a reset link is on its way. It works once and lasts 30 minutes.
              Check your spam folder if it doesn't show up.
            </p>
            <p className="small muted" style={{ textAlign: 'center', margin: 0 }}>
              <Link to="/login">Back to the door</Link>
            </p>
          </div>
        ) : (
          <form onSubmit={submit}>
            <Field label="The email on your account">
              <input type="email" autoComplete="email" autoFocus value={email}
                     onChange={(e) => setEmail(e.target.value)} required />
            </Field>
            <ErrorLine error={request.error} />
            <button type="submit" className="primary" disabled={request.isPending} style={{ justifyContent: 'center' }}>
              {request.isPending ? 'Sending a raven…' : 'Send reset link'}
            </button>
            <p className="small muted" style={{ textAlign: 'center', margin: 0 }}>
              No email on your account? Add one in Settings next time you're in. <Link to="/login">Back</Link>
            </p>
          </form>
        )}
      </section>
    </div>
  )
}

export function ResetPage() {
  const confirm = useConfirmReset()
  const navigate = useNavigate()
  // The link's token is read once and then wiped from the address bar, so it
  // doesn't linger in the history or get copied along with the page address.
  const [token] = useState(() => new URLSearchParams(window.location.search).get('token') ?? '')
  const [password, setPassword] = useState('')
  const [again, setAgain] = useState('')
  useEffect(() => {
    if (window.location.search) window.history.replaceState(null, '', window.location.pathname)
  }, [])

  const mismatch = again !== '' && again !== password

  function submit(e: FormEvent) {
    e.preventDefault()
    if (password === again) confirm.mutate({ token, password })
  }

  return (
    <div className="auth">
      <section className="panel">
        <h1>KrugWorld</h1>
        <p className="muted" style={{ textAlign: 'center', margin: '6px 0 0' }}>Choose a new passphrase</p>
        {confirm.isSuccess ? (
          <div className="stack" style={{ marginTop: 14 }}>
            <p role="status" style={{ margin: 0 }}>
              Done. Thy new passphrase is set. Other devices will ask you to log in again.
            </p>
            <button className="primary" style={{ justifyContent: 'center' }}
                    onClick={() => navigate('/login', { replace: true })}>
              To the door
            </button>
          </div>
        ) : !token ? (
          <div className="stack" style={{ marginTop: 14 }}>
            <p className="field-error" role="alert" style={{ margin: 0 }}>This page needs the link from your email.</p>
            <p className="small muted" style={{ textAlign: 'center', margin: 0 }}>
              <Link to="/forgot-password">Ask for a new link</Link>
            </p>
          </div>
        ) : (
          <form onSubmit={submit}>
            <Field label="New passphrase (at least 8 characters)">
              <input type="password" autoComplete="new-password" autoFocus minLength={8} value={password}
                     onChange={(e) => setPassword(e.target.value)} required />
            </Field>
            <Field label="Again" error={mismatch ? "These don't match." : undefined}>
              <input type="password" autoComplete="new-password" minLength={8} value={again}
                     onChange={(e) => setAgain(e.target.value)} required />
            </Field>
            <ErrorLine error={confirm.error} />
            {confirm.error instanceof ApiError && confirm.error.code === 'reset_invalid' && (
              <p className="small" style={{ margin: 0 }}><Link to="/forgot-password">Ask for a new link</Link></p>
            )}
            <button type="submit" className="primary" disabled={confirm.isPending || mismatch}
                    style={{ justifyContent: 'center' }}>
              {confirm.isPending ? 'Carving…' : 'Set passphrase'}
            </button>
          </form>
        )}
      </section>
    </div>
  )
}
