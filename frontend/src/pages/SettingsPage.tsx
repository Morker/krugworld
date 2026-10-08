import { useState } from 'react'
import { IconCheck, IconLogout, IconUpload } from '@tabler/icons-react'
import { useLogout, useMe, useSavePreferences, useTavernImport } from '../api/hooks'
import type { Skin, Theme } from '../api/types'
import { ErrorLine, Panel } from '../components/ui'
import { applySkin, THEMES } from '../skin/skin'

export function SettingsPage() {
  const me = useMe().data
  const save = useSavePreferences()
  const logout = useLogout()
  if (!me) return null

  function choose(skin: Skin, theme: Theme = me!.theme) {
    applySkin({ skin, theme }) // instant, before the server confirms
    save.mutate({ skin, theme })
  }

  return (
    <>
      <div className="page-head">
        <div>
          <h1>Settings</h1>
          <div className="sub">Logged in as {me.username}</div>
        </div>
        <button onClick={() => logout.mutate()}><IconLogout size={18} aria-hidden="true" /> Log out</button>
      </div>

      <div className="grid grid-2">
        <Panel title="Skin">
          <div className="stack" style={{ gap: 10 }}>
            <SkinOption active={me.skin === 'classic'} onClick={() => choose('classic')}
                        title="Classic Krugger" text="Krugger's Tavern as it was: pixel fonts, teal stone, scanlines." />
            <SkinOption active={me.skin === 'modern'} onClick={() => choose('modern')}
                        title="KrugWorld" text="Pirata One and Barlow Condensed, with a background of your choosing." />
          </div>
          {me.skin === 'modern' && (
            <>
              <h2 style={{ marginTop: 16 }}>Background</h2>
              <div className="row-start wrap">
                {THEMES.map((t) => (
                  <button key={t.id} aria-pressed={me.theme === t.id} onClick={() => choose('modern', t.id)}
                          className={me.theme === t.id ? 'primary' : ''}>
                    <span style={{ width: 14, height: 14, borderRadius: '50%', background: t.swatch, border: '1px solid var(--line)' }} />
                    {t.name}
                  </button>
                ))}
              </div>
            </>
          )}
          <ErrorLine error={save.error} />
        </Panel>

        <TavernImport />
      </div>
    </>
  )
}

function SkinOption({ active, title, text, onClick }: { active: boolean; title: string; text: string; onClick: () => void }) {
  return (
    <button onClick={onClick} aria-pressed={active}
            style={{ textAlign: 'left', justifyContent: 'space-between', borderColor: active ? 'var(--accent)' : undefined }}>
      <span>
        <span className="display" style={{ display: 'block', fontSize: 'var(--fs-h2)' }}>{title}</span>
        <span className="small muted">{text}</span>
      </span>
      {active && <IconCheck size={20} color="var(--accent)" aria-hidden="true" />}
    </button>
  )
}

/** Bring a Krugger's Tavern ledger across, once. */
function TavernImport() {
  const run = useTavernImport()
  const [problem, setProblem] = useState<string | null>(null)

  async function onFile(file: File | undefined) {
    if (!file) return
    setProblem(null)
    try {
      run.mutate(JSON.parse(await file.text()))
    } catch {
      setProblem("That file isn't JSON. Save the page from step 2 as a .json file and try again.")
    }
  }

  const done = run.data
  return (
    <Panel title="Import from Krugger's Tavern">
      <ol className="small muted" style={{ paddingLeft: 18, margin: '0 0 12px' }}>
        <li>Log in to Krugger's Tavern in this browser.</li>
        <li>Open <a href="https://tavern.veteryr.online/api/kv" target="_blank" rel="noreferrer">tavern.veteryr.online/api/kv</a> and save the page (Ctrl+S) as a .json file.</li>
        <li>Choose that file here. Your ledger goes into a new "Tavern purse" account, and standing dues become recurring entries.</li>
      </ol>
      <label className="btn" style={{ cursor: 'pointer' }}>
        <IconUpload size={18} aria-hidden="true" /> {run.isPending ? 'Importing…' : 'Choose file'}
        <input type="file" accept=".json,application/json" className="sr-only" onChange={(e) => onFile(e.target.files?.[0])} />
      </label>
      {problem && <p className="field-error" role="alert">{problem}</p>}
      <ErrorLine error={run.error} />
      {done && (
        <p role="status">
          Imported {done.entries} entries and {done.recurring} recurring dues
          {done.categoriesCreated > 0 && `, with ${done.categoriesCreated} new categories`}
          {done.skipped > 0 && ` (${done.skipped} unreadable rows skipped)`}.
        </p>
      )}
    </Panel>
  )
}
