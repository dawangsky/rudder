/**
 * 本机账号登录记录（localStorage）。
 * 默认记录账号与密码；最多 6 条，超出按先进先出淘汰最旧记录。
 * 退出 / 切换账号不清理本列表。
 */

const KEY = 'rudder_remember_accounts'
const LEGACY_KEY = 'rudder_remember_auth'
const MAX = 6

export type RememberedAccount = {
  email: string
  password: string
  /** 写入/更新时间戳，用于 FIFO 淘汰 */
  savedAt: number
}

function normalizeEmail(email: string) {
  return email.trim().toLowerCase()
}

function readRaw(): RememberedAccount[] {
  try {
    const raw = localStorage.getItem(KEY)
    if (raw) {
      const data = JSON.parse(raw) as RememberedAccount[]
      if (!Array.isArray(data)) return []
      return data
        .filter((a) => a && typeof a.email === 'string' && typeof a.password === 'string')
        .map((a) => ({
          email: a.email.trim(),
          password: a.password,
          savedAt: typeof a.savedAt === 'number' ? a.savedAt : 0,
        }))
    }
    // 兼容旧版单账号结构
    const legacy = localStorage.getItem(LEGACY_KEY)
    if (legacy) {
      const data = JSON.parse(legacy) as { email?: string; password?: string }
      if (data?.email && typeof data.password === 'string') {
        const list: RememberedAccount[] = [
          { email: data.email.trim(), password: data.password, savedAt: Date.now() },
        ]
        writeRaw(list)
        localStorage.removeItem(LEGACY_KEY)
        return list
      }
    }
  } catch {
    /* ignore */
  }
  return []
}

function writeRaw(list: RememberedAccount[]) {
  localStorage.setItem(KEY, JSON.stringify(list.slice(0, MAX)))
}

/** 按写入时间升序（最旧在前）；展示时通常 reverse 成最近优先。 */
export function listRememberedAccounts(): RememberedAccount[] {
  return readRaw().slice().sort((a, b) => a.savedAt - b.savedAt)
}

/** 最近使用优先的列表（下拉展示用）。 */
export function listRememberedAccountsRecentFirst(): RememberedAccount[] {
  return listRememberedAccounts().slice().reverse()
}

export function findRememberedAccount(email: string): RememberedAccount | null {
  const key = normalizeEmail(email)
  return listRememberedAccounts().find((a) => normalizeEmail(a.email) === key) || null
}

/**
 * 登录成功后写入/更新记录。
 * 同邮箱则更新密码并刷新时间；新邮箱超出上限时淘汰最旧一条（FIFO）。
 */
export function rememberAccount(email: string, password: string): void {
  const trimmed = email.trim()
  if (!trimmed) return
  const key = normalizeEmail(trimmed)
  const now = Date.now()
  let list = readRaw().filter((a) => normalizeEmail(a.email) !== key)
  list.push({ email: trimmed, password, savedAt: now })
  // FIFO：按 savedAt 升序，超出 MAX 丢掉最旧
  list.sort((a, b) => a.savedAt - b.savedAt)
  while (list.length > MAX) {
    list.shift()
  }
  writeRaw(list)
}

/** @deprecated 仅兼容旧调用；现默认始终记录，勿在退出时调用。 */
export function clearRememberedAuth(): void {
  // 按产品约定：退出/切换不清理账号记录
}

/** 兼容旧 Login 回填：返回最近一条。 */
export function loadRememberedAuth(): { email: string; password: string } | null {
  const list = listRememberedAccountsRecentFirst()
  const first = list[0]
  if (!first) return null
  return { email: first.email, password: first.password }
}

/** 兼容旧调用名。 */
export function saveRememberedAuth(email: string, password: string): void {
  rememberAccount(email, password)
}
