/**
 * 生成 100 张原创卡通 SVG 头像到 web/public/avatars/
 * 用法：npx tsx web/scripts/generate-agent-avatars.ts
 */
import fs from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'

const outDir = path.join(path.dirname(fileURLToPath(import.meta.url)), '../public/avatars')

const skins = ['#FFE0BD', '#FFD5A8', '#F5C6A0', '#E8B898', '#D4A574', '#C68642', '#FFCCBC', '#F8D5C2']
const hairs = [
  '#1a1a1a', '#2c1810', '#4a3728', '#6b4423', '#8B4513',
  '#c0392b', '#e74c3c', '#f39c12', '#f1c40f', '#2ecc71',
  '#3498db', '#9b59b6', '#e91e63', '#00bcd4', '#ff5722',
  '#607d8b', '#795548', '#212121', '#5d4037', '#ff8a65',
]
const eyes = ['#2c3e50', '#1a237e', '#4a148c', '#00695c', '#bf360c', '#37474f', '#0d47a1', '#880e4f']
const accents = ['#fff', '#ffe082', '#ffcdd2', '#c8e6c9', '#bbdefb', '#f8bbd0', '#d1c4e9', '#b2dfdb']
const bgs = [
  '#E3F2FD', '#FCE4EC', '#F3E5F5', '#E8F5E9', '#FFF3E0',
  '#E0F7FA', '#FFF8E1', '#F1F8E9', '#EDE7F6', '#EFEBE9',
  '#FFEBEE', '#E8EAF6', '#E0F2F1', '#FFFDE7', '#FBE9E7',
]

function mulberry32(a: number) {
  return function () {
    let t = (a += 0x6d2b79f5)
    t = Math.imul(t ^ (t >>> 15), t | 1)
    t ^= t + Math.imul(t ^ (t >>> 7), t | 61)
    return ((t ^ (t >>> 14)) >>> 0) / 4294967296
  }
}
const pick = <T,>(rng: () => number, arr: T[]): T => arr[Math.floor(rng() * arr.length)]!

function hairPath(style: number, color: string): string {
  const paths = [
    `<ellipse cx="64" cy="52" rx="38" ry="36" fill="${color}"/><path d="M28 58 Q32 28 64 26 Q96 28 100 58 L96 70 Q64 40 32 70 Z" fill="${color}"/>`,
    `<path d="M26 50 Q30 22 64 20 Q98 22 102 50 L104 118 Q90 128 64 126 Q38 128 24 118 Z" fill="${color}"/><ellipse cx="64" cy="48" rx="36" ry="32" fill="${color}"/>`,
    `<ellipse cx="64" cy="50" rx="34" ry="30" fill="${color}"/><path d="M30 55 Q18 70 16 110 Q28 118 36 100 Q40 70 38 58 Z" fill="${color}"/><path d="M98 55 Q110 70 112 110 Q100 118 92 100 Q88 70 90 58 Z" fill="${color}"/>`,
    `<path d="M30 70 L34 28 L48 48 L56 18 L64 44 L72 16 L80 46 L94 22 L98 70 Q64 36 30 70 Z" fill="${color}"/><ellipse cx="64" cy="58" rx="32" ry="26" fill="${color}"/>`,
    `<circle cx="40" cy="40" r="16" fill="${color}"/><circle cx="58" cy="30" r="18" fill="${color}"/><circle cx="78" cy="34" r="17" fill="${color}"/><circle cx="92" cy="48" r="14" fill="${color}"/><circle cx="36" cy="56" r="13" fill="${color}"/><ellipse cx="64" cy="56" rx="36" ry="28" fill="${color}"/>`,
    `<ellipse cx="64" cy="50" rx="36" ry="34" fill="${color}"/><path d="M40 40 Q50 70 44 96 Q36 90 34 60 Z" fill="${color}"/><path d="M70 28 Q90 36 96 70 L88 72 Q78 40 64 36 Z" fill="${color}"/>`,
    `<ellipse cx="64" cy="52" rx="34" ry="30" fill="${color}"/><path d="M72 40 Q110 30 108 90 Q96 100 84 70 Q78 48 72 42 Z" fill="${color}"/>`,
    `<path d="M28 68 Q26 30 50 24 Q56 10 64 22 Q72 8 78 24 Q102 28 100 68 Q64 42 28 68 Z" fill="${color}"/><ellipse cx="64" cy="58" rx="34" ry="28" fill="${color}"/>`,
  ]
  return paths[style % paths.length]
}

function makeSvg(i: number): string {
  const rng = mulberry32(1000 + i * 97)
  const skin = pick(rng, skins)
  const hair = pick(rng, hairs)
  const eye = pick(rng, eyes)
  const accent = pick(rng, accents)
  const bg = pick(rng, bgs)
  const hairStyle = Math.floor(rng() * 8)
  const blush = rng() > 0.35
  const glasses = rng() > 0.82
  const freckles = rng() > 0.75
  const mouth = Math.floor(rng() * 4)
  const eyeStyle = Math.floor(rng() * 3)
  let eyeSvg = ''
  if (eyeStyle === 0) {
    eyeSvg = `<ellipse cx="50" cy="72" rx="7" ry="9" fill="#fff"/><ellipse cx="78" cy="72" rx="7" ry="9" fill="#fff"/><circle cx="51" cy="73" r="4.2" fill="${eye}"/><circle cx="79" cy="73" r="4.2" fill="${eye}"/><circle cx="52.5" cy="71.5" r="1.4" fill="#fff"/><circle cx="80.5" cy="71.5" r="1.4" fill="#fff"/>`
  } else if (eyeStyle === 1) {
    eyeSvg = `<ellipse cx="50" cy="72" rx="8" ry="10" fill="#fff"/><ellipse cx="78" cy="72" rx="8" ry="10" fill="#fff"/><ellipse cx="51" cy="73" rx="3.5" ry="5" fill="${eye}"/><ellipse cx="79" cy="73" rx="3.5" ry="5" fill="${eye}"/><circle cx="52" cy="71" r="1.2" fill="#fff"/><circle cx="80" cy="71" r="1.2" fill="#fff"/>`
  } else {
    eyeSvg = `<path d="M42 72 Q50 62 58 72 Q50 78 42 72 Z" fill="#fff"/><path d="M70 72 Q78 62 86 72 Q78 78 70 72 Z" fill="#fff"/><circle cx="50" cy="72" r="3.8" fill="${eye}"/><circle cx="78" cy="72" r="3.8" fill="${eye}"/><circle cx="51.2" cy="70.8" r="1.1" fill="#fff"/><circle cx="79.2" cy="70.8" r="1.1" fill="#fff"/>`
  }
  const mouths = [
    `<path d="M56 92 Q64 98 72 92" fill="none" stroke="#c47a6a" stroke-width="2.2" stroke-linecap="round"/>`,
    `<ellipse cx="64" cy="94" rx="6" ry="3.5" fill="#e8917a"/>`,
    `<path d="M58 91 Q64 96 70 91" fill="none" stroke="#c47a6a" stroke-width="2" stroke-linecap="round"/><path d="M60 94 Q64 99 68 94" fill="#e8917a"/>`,
    `<path d="M57 93 L71 93" stroke="#c47a6a" stroke-width="2" stroke-linecap="round"/>`,
  ]
  const blushSvg = blush ? `<ellipse cx="42" cy="84" rx="7" ry="4" fill="#ff8a80" opacity="0.45"/><ellipse cx="86" cy="84" rx="7" ry="4" fill="#ff8a80" opacity="0.45"/>` : ''
  const freckleSvg = freckles ? `<circle cx="44" cy="80" r="1" fill="#c47a6a" opacity="0.5"/><circle cx="48" cy="83" r="0.9" fill="#c47a6a" opacity="0.45"/><circle cx="84" cy="80" r="1" fill="#c47a6a" opacity="0.5"/><circle cx="80" cy="83" r="0.9" fill="#c47a6a" opacity="0.45"/>` : ''
  const glassesSvg = glasses ? `<circle cx="50" cy="72" r="10" fill="none" stroke="#455a64" stroke-width="1.8"/><circle cx="78" cy="72" r="10" fill="none" stroke="#455a64" stroke-width="1.8"/><path d="M60 72 H68" stroke="#455a64" stroke-width="1.8"/>` : ''
  const shirt = pick(rng, hairs)
  return `<?xml version="1.0" encoding="UTF-8"?>
<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 128 128" width="128" height="128" role="img" aria-label="agent avatar ${i}">
  <rect width="128" height="128" rx="28" fill="${bg}"/>
  <ellipse cx="64" cy="118" rx="34" ry="14" fill="${shirt}"/>
  <circle cx="64" cy="78" r="36" fill="${skin}"/>
  ${hairPath(hairStyle, hair)}
  <path d="M34 58 Q48 42 64 44 Q80 42 94 58 Q78 50 64 52 Q50 50 34 58 Z" fill="${hair}" opacity="0.95"/>
  ${eyeSvg}${glassesSvg}${blushSvg}${freckleSvg}${mouths[mouth]}
  <ellipse cx="64" cy="82" rx="1.6" ry="1.1" fill="#e0a090" opacity="0.55"/>
  <ellipse cx="48" cy="58" rx="8" ry="5" fill="${accent}" opacity="0.25"/>
</svg>
`
}

fs.mkdirSync(outDir, { recursive: true })
const manifest: string[] = []
for (let i = 1; i <= 100; i++) {
  const id = String(i).padStart(3, '0')
  const file = `chibi-${id}.svg`
  fs.writeFileSync(path.join(outDir, file), makeSvg(i))
  manifest.push(`/avatars/${file}`)
}
fs.writeFileSync(path.join(outDir, 'manifest.json'), JSON.stringify({ count: manifest.length, avatars: manifest }, null, 2))
console.log('generated', manifest.length, 'svgs ->', outDir)
