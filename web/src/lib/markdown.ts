/** Markdown 渲染（预览用）：marked + DOMPurify。 */
import { marked } from 'marked'
import DOMPurify from 'dompurify'

marked.setOptions({
  gfm: true,
  breaks: false,
})

/** 去掉 YAML frontmatter，预览只展示正文。 */
export function stripYamlFrontmatter(md: string): string {
  const text = md.replace(/^\uFEFF/, '')
  if (!text.startsWith('---')) return text
  const end = text.indexOf('\n---', 3)
  if (end === -1) return text
  let body = text.slice(end + 4)
  if (body.startsWith('\r\n')) body = body.slice(2)
  else if (body.startsWith('\n')) body = body.slice(1)
  return body
}

export function renderMarkdown(md: string, opts?: { stripFrontmatter?: boolean }): string {
  const src = opts?.stripFrontmatter === false ? md : stripYamlFrontmatter(md || '')
  const raw = marked.parse(src || '', { async: false }) as string
  return DOMPurify.sanitize(raw, {
    USE_PROFILES: { html: true },
  })
}
