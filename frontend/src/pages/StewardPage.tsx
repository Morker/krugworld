import { IconRefresh } from '@tabler/icons-react'
import { useStewardStatus } from '../api/lifeHooks'
import { Panel } from '../components/ui'
import { conversation } from '../steward/conversation'
import { Portrait } from '../steward/Portrait'
import { StewardChat } from '../steward/StewardChat'
import { useMood } from '../steward/useMood'

/** Barnaby, full size: the same conversation as the docked panel, with room to breathe. */
export function StewardPage() {
  const status = useStewardStatus().data
  const mood = useMood()

  return (
    <>
      <div className="page-head">
        <div className="row-start">
          <div className="dock-portrait"><Portrait size={88} mood={mood} /></div>
          <div>
            <h1>Barnaby, the Steward</h1>
            <div className="sub">{status?.ready ? `Serving via ${status.provider}` : 'Keeper of thy ledger, almanac, feast and training'}</div>
          </div>
        </div>
        <button className="ghost" onClick={() => conversation.reset()}><IconRefresh size={16} /> New conversation</button>
      </div>
      <Panel>
        <StewardChat />
      </Panel>
    </>
  )
}
