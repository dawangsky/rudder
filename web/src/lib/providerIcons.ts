/** 自定义 Provider 图标（本机 localStorage，按 daemonId + provider 稳定关联）。 */

const STORE_KEY = 'rudder.providerIcons.v1'
const MAX_BYTES = 180_000 // data URL 约 180KB，避免撑爆 localStorage
export const ICONS_CHANGED_EVENT = 'rudder-provider-icons-changed'

type IconMap = Record<string, string>

function notifyChanged() {
  if (typeof window !== 'undefined') {
    window.dispatchEvent(new Event(ICONS_CHANGED_EVENT))
  }
}

function iconKey(daemonId: string | undefined, provider: string) {
  return `${daemonId || 'unknown'}::${provider}`
}

function readAll(): IconMap {
  try {
    const raw = localStorage.getItem(STORE_KEY)
    if (!raw) return {}
    const parsed = JSON.parse(raw) as IconMap
    return parsed && typeof parsed === 'object' ? parsed : {}
  } catch {
    return {}
  }
}

function writeAll(map: IconMap) {
  localStorage.setItem(STORE_KEY, JSON.stringify(map))
}

/** 读取自定义图标 data URL；无则返回空字符串。 */
export function getCustomProviderIcon(daemonId: string | undefined, provider: string): string {
  if (!provider) return ''
  return readAll()[iconKey(daemonId, provider)] || ''
}

/** 保存自定义图标（data URL）。 */
export function setCustomProviderIcon(
  daemonId: string | undefined,
  provider: string,
  dataUrl: string,
) {
  const map = readAll()
  map[iconKey(daemonId, provider)] = dataUrl
  writeAll(map)
  notifyChanged()
}

/** 清除自定义图标，恢复官方默认。 */
export function clearCustomProviderIcon(daemonId: string | undefined, provider: string) {
  const map = readAll()
  delete map[iconKey(daemonId, provider)]
  writeAll(map)
  notifyChanged()
}

/**
 * 将本地图片文件转为压缩后的 data URL（正方形裁切缩略）。
 */
export function fileToIconDataUrl(file: File): Promise<string> {
  return new Promise((resolve, reject) => {
    if (!file.type.startsWith('image/')) {
      reject(new Error('请选择图片文件（PNG / JPG / WebP / SVG）'))
      return
    }
    if (file.size > 2 * 1024 * 1024) {
      reject(new Error('图片请小于 2MB'))
      return
    }

    // SVG 直接读文本（限制体积）
    if (file.type === 'image/svg+xml') {
      if (file.size > 64_000) {
        reject(new Error('SVG 请小于 64KB'))
        return
      }
      const reader = new FileReader()
      reader.onload = () => {
        const text = String(reader.result || '')
        const url = `data:image/svg+xml;base64,${btoa(unescape(encodeURIComponent(text)))}`
        if (url.length > MAX_BYTES) {
          reject(new Error('图标过大，请换更小的文件'))
          return
        }
        resolve(url)
      }
      reader.onerror = () => reject(new Error('读取失败'))
      reader.readAsText(file)
      return
    }

    const reader = new FileReader()
    reader.onload = () => {
      const img = new Image()
      img.onload = () => {
        const size = 128
        const canvas = document.createElement('canvas')
        canvas.width = size
        canvas.height = size
        const ctx = canvas.getContext('2d')
        if (!ctx) {
          reject(new Error('无法处理图片'))
          return
        }
        const min = Math.min(img.width, img.height)
        const sx = (img.width - min) / 2
        const sy = (img.height - min) / 2
        ctx.clearRect(0, 0, size, size)
        ctx.drawImage(img, sx, sy, min, min, 0, 0, size, size)
        let quality = 0.88
        let url = canvas.toDataURL('image/webp', quality)
        if (!url.startsWith('data:image/webp')) {
          url = canvas.toDataURL('image/png')
        }
        while (url.length > MAX_BYTES && quality > 0.45) {
          quality -= 0.1
          url = canvas.toDataURL('image/webp', quality)
        }
        if (url.length > MAX_BYTES) {
          reject(new Error('图标过大，请换更小的图片'))
          return
        }
        resolve(url)
      }
      img.onerror = () => reject(new Error('图片无法解析'))
      img.src = String(reader.result || '')
    }
    reader.onerror = () => reject(new Error('读取失败'))
    reader.readAsDataURL(file)
  })
}
