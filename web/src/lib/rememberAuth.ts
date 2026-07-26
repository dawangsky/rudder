/**
 * 本机账号登录记录（localStorage）。
 * - 登录成功总会写入/更新账号条目（最多 6 条，FIFO 淘汰最旧）
 * - 「记住密码」勾选时保存密码；未勾选只保留邮箱（密码为空）
 * - 退出 / 切换账号不清理本列表
 */

const KEY = 'rudder_remember_accounts'
const LEGACY_KEY = 'rudder_remember_auth'
const MAX = 6

export type RememberedAccount = {
  email: string
  /** 未勾选「记住密码」时为空字符串 */
  password: string
  /** 是否记住了密码 */
  rememberPassword: boolean
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
        .filter((a) => a && typeof a.email === 'string')
        .map((a) => {
          const password = typeof a.password === 'string' ? a.password : ''
          const rememberPassword =
            typeof a.rememberPassword === 'boolean' ? a.rememberPassword : !!password
          return {
            email: a.email.trim(),
            password: rememberPassword ? password : '',
            rememberPassword,
            savedAt: typeof a.savedAt === 'number' ? a.savedAt : 0,
          }
        })
    }
    // 兼容旧版单账号结构
    const legacy = localStorage.getItem(LEGACY_KEY)
    if (legacy) {
      const data = JSON.parse(legacy) as { email?: string; password?: string }
      if (data?.email) {
        const password = typeof data.password === 'string' ? data.password : ''
        const list: RememberedAccount[] = [
          {
            email: data.email.trim(),
            password,
            rememberPassword: !!password,
            savedAt: Date.now(),
          },
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

/** 按写入时间升序（最旧在前）。 */
export function listRememberedAccounts(): RememberedAccount[] {
  return readRaw().slice().sort((a, b) => a.savedAt - b.savedAt)
}

/** 最近使用优先（下拉展示）。 */
export function listRememberedAccountsRecentFirst(): RememberedAccount[] {
  return listRememberedAccounts().slice().reverse()
}

export function findRememberedAccount(email: string): RememberedAccount | null {
  const key = normalizeEmail(email)
  return listRememberedAccounts().find((a) => normalizeEmail(a.email) === key) || null
}

/**
 * 登录成功后写入/更新记录。
 * 不论是否记住密码都会占一条名额；未记住则只存邮箱。
 * 超出 6 条时淘汰最旧记录（FIFO），新账号可顶掉旧的「已记住密码」账号。
 */
export function rememberAccount(
  email: string,
  password: string,
  rememberPassword: boolean,
): void {
  const trimmed = email.trim()
  if (!trimmed) return
  const key = normalizeEmail(trimmed)
  const now = Date.now()
  let list = readRaw().filter((a) => normalizeEmail(a.email) !== key)
  list.push({
    email: trimmed,
    password: rememberPassword ? password : '',
    rememberPassword,
    savedAt: now,
  })
  list.sort((a, b) => a.savedAt - b.savedAt)
  while (list.length > MAX) {
    list.shift()
  }
  writeRaw(list)
}

/** 退出/切换不清理；保留空实现以免误调用。 */
export function clearRememberedAuth(): void {
  /* no-op */
}

/** 最近一条（兼容旧回填）。 */
export function loadRememberedAuth(): { email: string; password: string; rememberPassword: boolean } | null {
  const first = listRememberedAccountsRecentFirst()[0]
  if (!first) return null
  return {
    email: first.email,
    password: first.rememberPassword ? first.password : '',
    rememberPassword: first.rememberPassword,
  }
}

/** @deprecated 请用 rememberAccount(email, password, rememberPassword) */
export function saveRememberedAuth(email: string, password: string): void {
  rememberAccount(email, password, true)
}
