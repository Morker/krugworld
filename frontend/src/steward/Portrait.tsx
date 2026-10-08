// Barnaby, the Steward, as 20×20 pixel art. Each letter is one pixel's colour;
// eyes and mouth are drawn as separate layers so they can blink and talk.

const ART = [
  '.............FF.....',
  '............FF......',
  '.....KKKKKKKFKK.....',
  '....KHHHHHHHHHHK....',
  '...KHHHHHHHHHHHHK...',
  '...KhhhhhhhhhhhhK...',
  '...KSSSSSSSSSSSSK...',
  '...KSGGGGSSGGGGSK...',
  '...KSGEWGGGGEWGSK...',
  '...KSGGGGSSGGGGSK...',
  '..KSSSSSSssSSSSSSK..',
  '..KsSBBBBssBBBBSsK..',
  '...KBBBBBMMBBBBBK...',
  '...KBBBBBBBBBBBBK...',
  '...KbBBBBBBBBBBbK...',
  '....KbBBBBBBBBbK....',
  '..KKCKbbBBBBbbKCKK..',
  '.KCCCCKbbBBbbKCCCCK.',
  'KCCCCLCKKbbKKCLCCCCK',
  'KCCcCLCCCKKCCCLCcCCK',
]

const COLOURS: Record<string, string> = {
  K: '#1b1410', // outline
  H: '#7a2e3a', // cap
  h: '#561f28', // cap band
  F: 'var(--accent)', // feather
  S: '#f1c79e', // skin
  s: '#d9a37a', // skin shade
  G: '#c9a227', // spectacles
  B: '#ece8de', // beard
  b: '#b8b2a6', // beard shade
  M: '#9b3d2e', // mouth
  C: '#2f5d62', // coat
  c: '#23474b', // coat shade
  L: 'var(--accent)', // gold trim
}

type Run = { x: number; y: number; w: number; fill: string }

/** Neighbouring same-coloured pixels in a row become one rect: ~120 rects instead of ~300. */
function runs(keep: (ch: string) => boolean, recolour?: (ch: string) => string): Run[] {
  const out: Run[] = []
  ART.forEach((row, y) => {
    let x = 0
    while (x < row.length) {
      const raw = row[x]
      const ch = keep(raw) ? (recolour ? recolour(raw) : raw) : '.'
      let w = 1
      while (x + w < row.length && (keep(row[x + w]) ? (recolour ? recolour(row[x + w]) : row[x + w]) : '.') === ch) w++
      if (ch !== '.') out.push({ x, y, w, fill: COLOURS[ch] ?? ch })
      x += w
    }
  })
  return out
}

// Under the eyes and mouth sits plain skin and beard, so blinking shows a closed eye, not a hole.
const BODY = runs((ch) => ch !== '.', (ch) => (ch === 'E' || ch === 'W' ? 'S' : ch === 'M' ? 'B' : ch))
const EYES = runs((ch) => ch === 'E' || ch === 'W', (ch) => (ch === 'W' ? '#ffffff' : '#1b1410'))
const MOUTH = runs((ch) => ch === 'M')

export type Mood = 'idle' | 'thinking' | 'talking'

export function Portrait({ size = 64, mood = 'idle', title = 'Barnaby the Steward' }: {
  size?: number
  mood?: Mood
  title?: string
}) {
  return (
    <svg className={`portrait mood-${mood}`} width={size} height={size} viewBox="0 0 20 20"
         shapeRendering="crispEdges" role="img" aria-label={title}>
      {BODY.map((r, i) => <rect key={i} x={r.x} y={r.y} width={r.w} height={1} fill={r.fill} />)}
      {/* closed-eye line, visible only mid-blink */}
      <rect className="lids" x={6} y={8} width={2} height={0.5} fill="#1b1410" />
      <rect className="lids" x={12} y={8} width={2} height={0.5} fill="#1b1410" />
      <g className="eyes">
        {EYES.map((r, i) => <rect key={i} x={r.x} y={r.y} width={r.w} height={1} fill={r.fill} />)}
      </g>
      <g className="mouth">
        {MOUTH.map((r, i) => <rect key={i} x={r.x} y={r.y} width={r.w} height={1} fill={r.fill} />)}
      </g>
    </svg>
  )
}
