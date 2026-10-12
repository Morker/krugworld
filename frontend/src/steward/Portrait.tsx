// Gaby, the Steward, as 20×20 pixel art. Each letter is one pixel's colour;
// eyes and mouth are drawn as separate layers so they can blink and talk.
// Same cap, feather, spectacles and coat as the first steward, so she's
// recognisably the same character family: auburn braids, rosy cheeks, a smile.

const ART = [
  '.............FF.....',
  '............FF......',
  '.....KKKKKKKFKK.....',
  '....KHHHHHHHHHHK....',
  '...KHHHHHHHHHHHHK...',
  '..KRhhhhhhhhhhhhRK..',
  '..KRRSSSSSSSSSSRRK..',
  '..KRSGGGGSSGGGGSRK..',
  '..KRSGEWGGGGEWGSRK..',
  '..KRSGGGGSSGGGGSRK..',
  '..KRSPSSSssSSSPSRK..',
  '..KRRSSSMMMMSSSRRK..',
  '..KRRKSSSSSSSSKRRK..',
  '..KRRRKSSSSSSKRRRK..',
  '..KRRRKKSSSSKKRRRK..',
  '..KRrRKAAAAAAKRrRK..',
  '.KCRrRAAALLAAARrRCK.',
  'KCCRRCCKAAAAKCCRRCCK',
  'KCCCCLCCKAAKCCLCCCCK',
  'KCCcCLCCCKKCCCLCcCCK',
]

const COLOURS: Record<string, string> = {
  K: '#1b1410', // outline
  H: '#7a2e3a', // cap
  h: '#561f28', // cap band
  F: 'var(--accent)', // feather
  R: '#a4492a', // hair
  r: '#7a3220', // braid shadow
  S: '#f1c79e', // skin
  s: '#d9a37a', // skin shade
  P: '#e8908a', // blush
  G: '#c9a227', // spectacles
  M: '#b8484a', // smile
  A: '#ece8de', // lace collar
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

// Under the eyes and mouth sits plain skin, so blinking shows a closed eye, not a hole.
const BODY = runs((ch) => ch !== '.', (ch) => (ch === 'E' || ch === 'W' || ch === 'M' ? 'S' : ch))
const EYES = runs((ch) => ch === 'E' || ch === 'W', (ch) => (ch === 'W' ? '#ffffff' : '#1b1410'))
const MOUTH = runs((ch) => ch === 'M')

export type Mood = 'idle' | 'thinking' | 'talking'

export function Portrait({ size = 64, mood = 'idle', title = 'Gaby the Steward' }: {
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
