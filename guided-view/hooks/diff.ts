import type { ChangeGroup, FileDiff } from '../types'

/** One hunk of one file, with the id the model groups it by. */
export type Hunk = { id: string; path: string; text: string }

export type Plan = {
  title?: string
  overview?: string
  groups: { title: string; summary: string; hunks: string[] }[]
}

const FILE_HEAD = /^diff --git a\/(.+?) b\/(.+)$/

/** Splits a unified diff (git or gh output) into hunks, file by file. */
export const parseDiff = (diff: string): Hunk[] => {
  const hunks: Hunk[] = []
  let path: string | undefined
  let current: string[] | undefined
  let hasHunk = false

  const flush = () => {
    if (path !== undefined && current !== undefined) {
      hunks.push({ id: `h${hunks.length + 1}`, path, text: current.join('\n') })
    }
    current = undefined
  }
  const closeFile = () => {
    flush()
    // A binary file, a pure rename or a mode change carries no hunk.
    if (path !== undefined && !hasHunk) {
      hunks.push({ id: `h${hunks.length + 1}`, path, text: '' })
    }
  }

  for (const line of diff.split('\n')) {
    const head = FILE_HEAD.exec(line)
    if (head !== null) {
      closeFile()
      path = head[2]
      hasHunk = false
      continue
    }
    if (path === undefined) continue
    if (line.startsWith('+++ ') && current === undefined) {
      const after = line.slice(4)
      if (after.startsWith('b/')) path = after.slice(2)
      continue
    }
    if (line.startsWith('@@')) {
      flush()
      current = [line]
      hasHunk = true
      continue
    }
    if (current !== undefined) {
      if (/^[ +\-\\]/.test(line) || line === '') current.push(line)
      else flush()
    }
  }
  closeFile()

  // Trailing empty lines from the diff's end are not part of the last hunk.
  return hunks.map(h => ({ ...h, text: h.text.replace(/\n+$/, '') }))
}

const count = (text: string, mark: '+' | '-') =>
  text.split('\n').filter(l => l.startsWith(mark)).length

const MAX_PATCH = 40_000

/** Joins hunks into one FileDiff per file, in the order they came. */
const toFiles = (hunks: Hunk[]): FileDiff[] => {
  const files: FileDiff[] = []
  for (const hunk of hunks) {
    let file = files.find(f => f.path === hunk.path)
    if (file === undefined) {
      file = { path: hunk.path, patch: '', added: 0, removed: 0 }
      files.push(file)
    }
    file.added += count(hunk.text, '+')
    file.removed += count(hunk.text, '-')
    if (hunk.text === '' || file.patch.length + hunk.text.length > MAX_PATCH) {
      continue
    }
    file.patch = file.patch === '' ? hunk.text : `${file.patch}\n${hunk.text}`
  }

  return files
}

/** The groups the model planned, every hunk in exactly one of them. */
export const buildGroups = (hunks: Hunk[], plan: Plan): ChangeGroup[] => {
  const byId = new Map(hunks.map(h => [h.id, h]))
  const used = new Set<string>()
  const groups: ChangeGroup[] = []

  for (const g of plan.groups) {
    const mine = g.hunks
      .filter(id => byId.has(id) && !used.has(id))
      .map(id => {
        used.add(id)
        return byId.get(id) as Hunk
      })
    if (mine.length === 0) continue
    // Keep the diff's own order inside a group.
    mine.sort((a, b) => hunks.indexOf(a) - hunks.indexOf(b))
    groups.push({ title: g.title, summary: g.summary, files: toFiles(mine) })
  }

  const left = hunks.filter(h => !used.has(h.id))
  if (left.length > 0) {
    groups.push({
      title: 'Otros cambios',
      summary: 'Cambios que no entraron en ningún grupo.',
      files: toFiles(left),
    })
  }

  return groups
}

/** Without the model: one group per file. */
export const groupsByFile = (hunks: Hunk[]): ChangeGroup[] =>
  toFiles(hunks).map(file => ({
    title: file.path,
    summary: `+${file.added} −${file.removed}`,
    files: [file],
  }))

const MAX_PROMPT = 150_000

/** The hunks as the model reads them, each cut short when the diff is big. */
export const describeHunks = (hunks: Hunk[]): string => {
  const total = hunks.reduce((n, h) => n + h.text.length, 0)
  const perHunk =
    total <= MAX_PROMPT ? Infinity : Math.max(600, MAX_PROMPT / hunks.length)

  return hunks
    .map(h => {
      const body = h.text === '' ? '(binary, rename or mode change)' : h.text
      const cut =
        body.length > perHunk ? `${body.slice(0, perHunk)}\n… (cut)` : body
      return `[${h.id}] ${h.path}\n${cut}`
    })
    .join('\n\n')
}

export const PLAN_PROMPT = `You help a person review a pull request. Below are its diff hunks, each with an id in brackets and its file.

Group the hunks by MEANING: what each change is for, not which file it is in. One group is one idea a reviewer can check on its own (a feature, a fix, a refactor, tests for something, config). Hunks of one file may land in different groups. Order the groups so a reviewer reads the most important first, and tests and config last. Use between 1 and 12 groups. Every hunk id goes in exactly one group.

Write in Spanish (rioplatense), plain and short:
- "title": the PR as a whole, in under 10 words.
- "overview": two or three sentences on what the PR does.
- each group's "title": under 8 words, what the change does.
- each group's "summary": two to four sentences on what was done and why, and anything a reviewer should look at closely. Markdown is allowed; name files or functions in backticks.

Answer with JSON only, no fence, in this shape:
{"title": "...", "overview": "...", "groups": [{"title": "...", "summary": "...", "hunks": ["h1", "h4"]}]}

HUNKS:

`

/** Reads the model's answer, or undefined when it holds no usable plan. */
export const parsePlan = (text: string): Plan | undefined => {
  const start = text.indexOf('{')
  const end = text.lastIndexOf('}')
  if (start < 0 || end <= start) return undefined
  try {
    const raw = JSON.parse(text.slice(start, end + 1)) as Record<string, unknown>
    if (!Array.isArray(raw.groups)) return undefined
    const groups = raw.groups.flatMap(g => {
      const one = g as Record<string, unknown>
      if (typeof one.title !== 'string' || !Array.isArray(one.hunks)) return []
      return [
        {
          title: one.title,
          summary: typeof one.summary === 'string' ? one.summary : '',
          hunks: one.hunks.filter((id): id is string => typeof id === 'string'),
        },
      ]
    })
    return {
      title: typeof raw.title === 'string' ? raw.title : undefined,
      overview: typeof raw.overview === 'string' ? raw.overview : undefined,
      groups,
    }
  } catch {
    return undefined
  }
}

/** A PR named by URL, `owner/repo#12`, `#12` or `12`. */
export type PrRef = { owner?: string; repo?: string; number: number }

export const parseRef = (arg: string): PrRef | undefined => {
  const text = arg.trim()
  const url = /github\.com\/([^/\s]+)\/([^/\s]+)\/pull\/(\d+)/.exec(text)
  if (url !== null) {
    return { owner: url[1], repo: url[2], number: Number(url[3]) }
  }
  const short = /^([\w.-]+)\/([\w.-]+)#(\d+)$/.exec(text)
  if (short !== null) {
    return { owner: short[1], repo: short[2], number: Number(short[3]) }
  }
  const bare = /^#?(\d+)$/.exec(text)
  if (bare !== null) return { number: Number(bare[1]) }

  return undefined
}
