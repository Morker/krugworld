import { useState } from 'react'
import type { FormEvent } from 'react'
import { IconArchive, IconBuildingBank, IconCash, IconCreditCard, IconDeviceMobile, IconPencil, IconPlus, IconRestore, IconWallet } from '@tabler/icons-react'
import type { Icon } from '@tabler/icons-react'
import { money } from '../api/client'
import { useAccounts, useArchiveAccount, useSaveAccount } from '../api/hooks'
import type { Account, AccountKind } from '../api/types'
import { ErrorLine, Field, Loading, Modal, Money, Panel } from '../components/ui'

const KINDS: { id: AccountKind; label: string; icon: Icon }[] = [
  { id: 'BANK', label: 'Bank', icon: IconBuildingBank },
  { id: 'CASH', label: 'Cash', icon: IconCash },
  { id: 'EWALLET', label: 'E-wallet', icon: IconDeviceMobile },
  { id: 'CARD', label: 'Card', icon: IconCreditCard },
  { id: 'OTHER', label: 'Other', icon: IconWallet },
]

/** Where the gold actually is: Maybank, cash, Touch 'n Go... */
export function AccountsPage() {
  const accounts = useAccounts()
  const archive = useArchiveAccount()
  const [editing, setEditing] = useState<Account | 'new' | null>(null)

  const list = accounts.data ?? []
  const total = list.reduce((s, a) => s + a.balance, 0)

  return (
    <>
      <div className="page-head">
        <div>
          <h1>Accounts</h1>
          <div className="sub">Where your gold is kept</div>
        </div>
        <button className="primary" onClick={() => setEditing('new')}><IconPlus size={18} aria-hidden="true" /> Add account</button>
      </div>

      <Panel raised>
        <div className="stat"><span className="label">Total gold</span><Money value={total} className="value gold" /></div>
      </Panel>

      <div className="grid grid-3" style={{ marginTop: 14 }}>
        {accounts.isLoading && <Loading />}
        {list.map((a) => {
          const Glyph = KINDS.find((k) => k.id === a.kind)?.icon ?? IconWallet
          return (
            <Panel key={a.id}>
              <div style={{ opacity: a.archived ? 0.55 : 1 }}>
                <div className="row">
                  <span className="row-start"><Glyph size={20} aria-hidden="true" /> {a.name}</span>
                  {a.archived && <span className="chip">archived</span>}
                </div>
                <div className="stat" style={{ marginTop: 6 }}><Money value={a.balance} className={`value ${a.balance < 0 ? 'neg' : ''}`} /></div>
              </div>
              <div className="row-start" style={{ marginTop: 8, justifyContent: 'flex-end' }}>
                {a.archived ? (
                  <button className="ghost" onClick={() => archive.mutate({ id: a.id, restore: true })}><IconRestore size={16} /> Restore</button>
                ) : (
                  <>
                    <button className="ghost" aria-label={`Edit ${a.name}`} onClick={() => setEditing(a)}><IconPencil size={16} /></button>
                    <button className="ghost danger" aria-label={`Archive ${a.name}`}
                            onClick={() => window.confirm(`Remove ${a.name}? If it has entries it's archived instead, so its history stays.`) && archive.mutate({ id: a.id })}>
                      <IconArchive size={16} />
                    </button>
                  </>
                )}
              </div>
            </Panel>
          )
        })}
      </div>
      <ErrorLine error={accounts.error ?? archive.error} />

      {editing && <AccountForm account={editing === 'new' ? undefined : editing} onClose={() => setEditing(null)} />}
    </>
  )
}

function AccountForm({ account, onClose }: { account?: Account; onClose: () => void }) {
  const save = useSaveAccount()
  const [name, setName] = useState(account?.name ?? '')
  const [kind, setKind] = useState<AccountKind>(account?.kind ?? 'BANK')
  const [opening, setOpening] = useState(account ? String(account.openingBalance) : '0')
  const [problem, setProblem] = useState<string | null>(null)

  function submit(e: FormEvent) {
    e.preventDefault()
    if (!name.trim()) return setProblem('Name the account.')
    if (!Number.isFinite(Number(opening))) return setProblem('Starting balance must be a number.')
    setProblem(null)
    save.mutate({ id: account?.id, name, kind, openingBalance: money(opening || 0) }, { onSuccess: onClose })
  }

  return (
    <Modal title={account ? `Edit ${account.name}` : 'Add account'} onClose={onClose}>
      <form onSubmit={submit} className="stack" style={{ gap: 12 }}>
        <Field label="Name"><input value={name} maxLength={60} autoFocus placeholder="Maybank" onChange={(e) => setName(e.target.value)} /></Field>
        <div className="form-grid">
          <Field label="Kind">
            <select value={kind} onChange={(e) => setKind(e.target.value as AccountKind)}>
              {KINDS.map((k) => <option key={k.id} value={k.id}>{k.label}</option>)}
            </select>
          </Field>
          <Field label="Balance before KrugWorld (RM)">
            <input inputMode="decimal" value={opening} onChange={(e) => setOpening(e.target.value)} />
          </Field>
        </div>
        {problem && <p className="field-error" role="alert">{problem}</p>}
        <ErrorLine error={save.error} />
        <div className="row"><span /><button type="submit" className="primary" disabled={save.isPending}>{save.isPending ? 'Saving…' : 'Save'}</button></div>
      </form>
    </Modal>
  )
}
