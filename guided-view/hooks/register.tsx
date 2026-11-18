import { atom, read, update } from 'claude-code'
import type { EngineInterface, Register } from 'claude-code'

import type { Review } from '../types'
import {
  PLAN_PROMPT,
  buildGroups,
  describeHunks,
  groupsByFile,
  parseDiff,
  parsePlan,
  parseRef,
} from './diff'
import type { PrRef } from './diff'

const PANE = 'guided-view'
const TITLE = 'Guided view'
const COMMAND = 'guided-view'

// Change a tag when its value's fields change: it declines what an earlier
// version stored, which outlives a reload.
const review = atom(
  { plugin: 'guided-view', key: 'review' } as const,
  { status: 'idle' } as Review,
  { shape: 'review-1' },
)
const collapsed = atom(
  { plugin: 'guided-view', key: 'collapsed' } as const,
  [] as number[],
  { shape: 'collapsed-1' },
)

type $ = EngineInterface

// The desktop app may start without Homebrew on its PATH.
const GH = ['gh', '/opt/homebrew/bin/gh', '/usr/local/bin/gh']

const run = async ($: $, argv: string[]) => {
  try {
    return await $.process.run(argv, { timeoutMs: 60_000 })
  } catch {
    return undefined
  }
}

const gh = async ($: $, args: string[]) => {
  for (const bin of GH) {
    const ran = await run($, [bin, ...args])
    if (ran === undefined) continue
    return ran
  }
  return undefined
}

type Source = { title: string; url: string | undefined; diff: string }

const repoArgs = (ref: PrRef) =>
  ref.owner !== undefined && ref.repo !== undefined
    ? ['-R', `${ref.owner}/${ref.repo}`]
    : []

/** A PR through gh, or, for a public one gh can't reach, GitHub's API. */
const fromPr = async ($: $, ref: PrRef | undefined): Promise<Source> => {
  const target = ref === undefined ? [] : [String(ref.number), ...repoArgs(ref)]
  const view = await gh($, ['pr', 'view', ...target, '--json', 'title,url,number'])
  if (view?.exitCode === 0) {
    const diff = await gh($, ['pr', 'diff', ...target])
    if (diff?.exitCode === 0) {
      const meta = JSON.parse(view.stdout) as { title: string; url: string; number: number }
      return { title: `#${meta.number} ${meta.title}`, url: meta.url, diff: diff.stdout }
    }
  }

  if (ref?.owner !== undefined && ref.repo !== undefined) {
    const api = `https://api.github.com/repos/${ref.owner}/${ref.repo}/pulls/${ref.number}`
    const [meta, diff] = await Promise.all([
      $.http.fetch(api, { headers: { Accept: 'application/vnd.github+json' } }),
      $.http.fetch(api, { headers: { Accept: 'application/vnd.github.diff' } }),
    ])
    if (meta.ok && diff.ok) {
      const pr = JSON.parse(meta.text) as { title: string; html_url: string }
      return { title: `#${ref.number} ${pr.title}`, url: pr.html_url, diff: diff.text }
    }
    throw new Error(
      `No pude traer el PR: gh no respondió y GitHub contestó ${meta.status}. Si el repo es privado, instalá gh y corré \`gh auth login\`.`,
    )
  }

  if (ref !== undefined) {
    throw new Error(
      'No pude traer ese PR con gh. Pasá la URL completa del PR, o instalá gh y corré `gh auth login`.',
    )
  }
  throw new Error('no-pr')
}

/** The current branch against the default branch, uncommitted work included. */
const fromBranch = async ($: $): Promise<Source> => {
  const head = await run($, ['git', 'rev-parse', '--abbrev-ref', 'HEAD'])
  if (head?.exitCode !== 0) {
    throw new Error(
      'Esta sesión no está en un repo de git. Abrí la sesión en el repo, o pasá un PR: `/guided-view <url del PR>`.',
    )
  }
  const remoteHead = await run($, ['git', 'rev-parse', '--abbrev-ref', 'origin/HEAD'])
  const base = remoteHead?.exitCode === 0 ? remoteHead.stdout.trim() : 'origin/main'
  const mergeBase = await run($, ['git', 'merge-base', 'HEAD', base])
  if (mergeBase?.exitCode !== 0) {
    throw new Error(`No encontré la base ${base} para comparar la rama.`)
  }
  const diff = await run($, ['git', 'diff', '--no-color', '--no-ext-diff', mergeBase.stdout.trim()])
  if (diff?.exitCode !== 0) throw new Error('git diff falló.')

  return {
    title: `${head.stdout.trim()} contra ${base.replace(/^origin\//, '')}`,
    url: undefined,
    diff: diff.stdout,
  }
}

const load = async ($: $, arg: string) => {
  const source = arg.trim() === '' ? 'el PR de esta rama' : arg.trim()
  const step = (text: string) =>
    update($, review, () => ({ status: 'loading' as const, source, step: text }))

  try {
    await step('Trayendo el diff…')
    const ref = parseRef(arg)
    if (arg.trim() !== '' && ref === undefined) {
      throw new Error(
        'No entendí ese PR. Usá la URL del PR, `dueño/repo#12` o el número.',
      )
    }
    let got: Source
    try {
      got = await fromPr($, ref)
    } catch (err) {
      if (!(err instanceof Error) || err.message !== 'no-pr') throw err
      got = await fromBranch($)
    }

    const hunks = parseDiff(got.diff)
    if (hunks.length === 0) {
      throw new Error(`No hay cambios en ${got.title}.`)
    }

    await step(`Agrupando ${hunks.length} cambios por significado…`)
    const answer = await $.model.complete({
      model: 'sonnet',
      prompt: PLAN_PROMPT + describeHunks(hunks),
      maxTokens: 8000,
      effort: 'low',
      timeoutMs: 240_000,
    })
    const plan = answer.isAnswered ? parsePlan(answer.text) : undefined

    await update($, collapsed, () => [])
    await update($, review, () => ({
      status: 'ready' as const,
      source,
      title: plan?.title ?? got.title,
      url: got.url,
      overview:
        plan === undefined
          ? 'No pude agrupar los cambios con el modelo, así que van archivo por archivo.'
          : (plan.overview ?? ''),
      groups: plan === undefined ? groupsByFile(hunks) : buildGroups(hunks, plan),
    }))
    $.ui.toast(`Guided view lista: ${got.title}`)
  } catch (err) {
    const message = err instanceof Error ? err.message : String(err)
    await update($, review, () => ({ status: 'error' as const, source, message }))
  }
}

let lastArg = ''

export const register: Register = on => {
  on('session.start', async ($, e, next) => {
    await $.command.register({
      name: COMMAND,
      description:
        'Revisar un PR agrupado por significado: /guided-view [url o número del PR]. Sin nada, usa el PR o la rama actual.',
    })
    return next(e)
  })

  on('command.run', { command: COMMAND }, async ($, e) => {
    lastArg = e.args
    await $.ui.open({ id: PANE, title: TITLE })
    void load($, e.args)

    return { text: 'Armando la guided view en el panel…' }
  }).catch(($, e, next) =>
    next.called ? next(e) : { text: 'La guided view no pudo arrancar.' },
  )

  on('ui.render', { component: 'Pane', requestId: PANE }, async ($, e) => {
    const { Box, Text, Button, Code, Markdown, Progress, Link } = $.ui.resolve(e)
    const r = await read($, review)

    if (r.status === 'idle') {
      return (
        <Box flexDirection="column" gap={1}>
          <Text>Escribí `/guided-view` para revisar el PR de esta rama.</Text>
          <Text dimColor>
            También acepta la URL de un PR, `dueño/repo#12` o el número.
          </Text>
        </Box>
      )
    }

    if (r.status === 'loading') {
      return (
        <Box flexDirection="column" gap={1}>
          <Text bold>{r.source}</Text>
          <Progress label={r.step} />
          <Text dimColor>{r.step}</Text>
        </Box>
      )
    }

    if (r.status === 'error') {
      return (
        <Box flexDirection="column" gap={1}>
          <Text bold color="red">
            No pude armar la vista
          </Text>
          <Markdown text={r.message} />
          <Button key="retry" onPress={() => void load($, lastArg)}>
            Reintentar
          </Button>
        </Box>
      )
    }

    const closed = await read($, collapsed)
    const width = e.props.bodyColumns
    const toggle = (i: number) =>
      void update($, collapsed, list =>
        list.includes(i) ? list.filter(n => n !== i) : [...list, i],
      )

    return (
      <Box flexDirection="column" gap={1}>
        <Box flexDirection="column">
          <Text bold>{r.title}</Text>
          {r.url !== undefined && <Link href={r.url} label="Abrir en GitHub" />}
        </Box>
        {r.overview !== '' && <Markdown text={r.overview} />}
        <Box flexDirection="row" gap={1}>
          <Text dimColor>
            {r.groups.length} {r.groups.length === 1 ? 'grupo' : 'grupos'}
          </Text>
          <Button key="reload" onPress={() => void load($, lastArg)}>
            Recargar
          </Button>
        </Box>

        {r.groups.map((group, i) => {
          const isOpen = !closed.includes(i)
          const added = group.files.reduce((n, f) => n + f.added, 0)
          const removed = group.files.reduce((n, f) => n + f.removed, 0)

          return (
            <Box
              flexDirection="column"
              gap={1}
              borderStyle="round"
              paddingX={1}
              width={width}
            >
              <Button key={`group-${i}`} plain onPress={() => toggle(i)}>
                {isOpen ? '▾' : '▸'} {i + 1}. {group.title}{' '}
                <Text dimColor>
                  +{added} −{removed}
                </Text>
              </Button>
              {isOpen && group.summary !== '' && <Markdown text={group.summary} />}
              {isOpen &&
                group.files.map(file => (
                  <Box flexDirection="column">
                    <Text dimColor>
                      {file.path} · +{file.added} −{file.removed}
                    </Text>
                    {file.patch === '' ? (
                      <Text dimColor>(binario, renombre o cambio de modo)</Text>
                    ) : (
                      <Code source={file.patch} path={file.path} format="diff" />
                    )}
                  </Box>
                ))}
            </Box>
          )
        })}
      </Box>
    )
  })
}
