/** 智能体卡通头像：预置图库 + 随机选取（SVG 位于 public/avatars）。 */

export const AGENT_AVATAR_COUNT = 100

export const AGENT_AVATARS: string[] = Array.from({ length: AGENT_AVATAR_COUNT }, (_, i) => {
  const id = String(i + 1).padStart(3, '0')
  return `/avatars/chibi-${id}.svg`
})

export function pickRandomAvatar(exclude?: string): string {
  const pool = exclude ? AGENT_AVATARS.filter((a) => a !== exclude) : AGENT_AVATARS
  if (!pool.length) return AGENT_AVATARS[0] || ''
  return pool[Math.floor(Math.random() * pool.length)]
}

export function isPresetAvatar(src?: string | null): boolean {
  if (!src) return false
  return AGENT_AVATARS.includes(src)
}
