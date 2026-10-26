import { useState } from 'react'
import type { FormEvent } from 'react'
import { IconPlayerSkipForward, IconRosetteDiscountCheck } from '@tabler/icons-react'
import { money } from '../api/client'
import { useConfirmRule, useSkipRule } from '../api/hooks'
import type { Rule } from '../api/types'
import { dayLabel } from '../lib/format'
import { ErrorLine, Field, Modal } from './ui'

/**
 * Confirming a recurring entry that waits for you: check (or correct) this
 * time's amount, then seal it into the ledger, or let this one pass.
 */
export function SealDialog({ rule, onClose, onSealed }: { rule: Rule; onClose: () => void; onSealed?: () => void }) {
  const confirm = useConfirmRule()
  const skip = useSkipRule()
  const [amount, setAmount] = useState(String(rule.amount))
  const [problem, setProblem] = useState<string | null>(null)
  const busy = confirm.isPending || skip.isPending

  function submit(e: FormEvent) {
    e.preventDefault()
    if (!(Number(amount) > 0)) return setProblem('Enter an amount above zero.')
    setProblem(null)
    // A promise rather than mutate's onSuccess: sealing the last one makes the
    // Hall's panel (and this dialog) unmount, which would skip that callback.
    confirm.mutateAsync({ id: rule.id, on: rule.nextRunOn, amount: money(amount) })
      .then(() => {
        onSealed?.()
        onClose()
      })
      .catch(() => {}) // shown by <ErrorLine error={confirm.error} />

  }

  return (
    <Modal title={`Seal: ${rule.note || rule.category.name}`} onClose={onClose}>
      <form onSubmit={submit} className="stack" style={{ gap: 12 }}>
        <p className="small muted" style={{ margin: 0 }}>
          {rule.type === 'INCOME' ? 'Comes in' : 'Goes out'} on {dayLabel(rule.nextRunOn)} · {rule.account.name} · {rule.category.name}.
          Change the amount if it differs this time.
        </p>
        <Field label="Amount this time (RM)">
          <input inputMode="decimal" value={amount} autoFocus onChange={(e) => setAmount(e.target.value)} />
        </Field>
        {problem && <p className="field-error" role="alert">{problem}</p>}
        <ErrorLine error={confirm.error ?? skip.error} />
        <div className="row">
          <button type="button" className="ghost" disabled={busy}
                  onClick={() => skip.mutate({ id: rule.id, on: rule.nextRunOn }, { onSuccess: onClose })}>
            <IconPlayerSkipForward size={16} aria-hidden="true" /> Skip this time
          </button>
          <button type="submit" className="primary" disabled={busy}>
            <IconRosetteDiscountCheck size={18} aria-hidden="true" /> {confirm.isPending ? 'Sealing…' : 'Confirm'}
          </button>
        </div>
      </form>
    </Modal>
  )
}
