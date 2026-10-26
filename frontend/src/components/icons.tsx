import {
  IconBarbell,
  IconBowl,
  IconBriefcase,
  IconBulb,
  IconCar,
  IconCoffee,
  IconCoin,
  IconCoins,
  IconCrown,
  IconDeviceMobile,
  IconDice5,
  IconFlame,
  IconGift,
  IconHeartHandshake,
  IconHeartbeat,
  IconHome,
  IconMoon,
  IconPaw,
  IconPigMoney,
  IconPlane,
  IconReceipt,
  IconScale,
  IconSchool,
  IconShieldCheck,
  IconShoppingBag,
  IconToolsKitchen2,
  IconTrophy,
} from '@tabler/icons-react'
import type { Icon } from '@tabler/icons-react'

// The backend stores icon *keys* (validated against the same list in
// LedgerDtos.ICONS); this is the only place they turn into drawings.
const CATEGORY_ICONS: Record<string, Icon> = {
  food: IconToolsKitchen2,
  transport: IconCar,
  bills: IconReceipt,
  fun: IconDice5,
  shopping: IconShoppingBag,
  health: IconHeartbeat,
  home: IconHome,
  education: IconSchool,
  gift: IconGift,
  salary: IconBriefcase,
  savings: IconPigMoney,
  travel: IconPlane,
  pet: IconPaw,
  phone: IconDeviceMobile,
  coffee: IconCoffee,
  coin: IconCoin,
}

const BADGE_ICONS: Record<string, Icon> = {
  coin: IconCoin,
  flame: IconFlame,
  shield: IconShieldCheck,
  scale: IconScale,
  trophy: IconTrophy,
  moon: IconMoon,
  coins: IconCoins,
  crown: IconCrown,
  bowl: IconBowl,
  barbell: IconBarbell,
  bulb: IconBulb,
  handshake: IconHeartHandshake,
}

export function CategoryIcon({ icon, color, size = 18 }: { icon: string; color: string; size?: number }) {
  const Glyph = CATEGORY_ICONS[icon] ?? IconCoin
  return (
    <span className="cat-icon" style={{ color }} aria-hidden="true">
      <Glyph size={size} stroke={1.8} />
    </span>
  )
}

export function BadgeIcon({ icon, size = 20 }: { icon: string; size?: number }) {
  const Glyph = BADGE_ICONS[icon] ?? IconTrophy
  return <Glyph size={size} stroke={1.8} aria-hidden="true" />
}

export function PlainIcon({ icon, size = 18 }: { icon: string; size?: number }) {
  const Glyph = CATEGORY_ICONS[icon] ?? IconCoin
  return <Glyph size={size} stroke={1.8} aria-hidden="true" />
}
