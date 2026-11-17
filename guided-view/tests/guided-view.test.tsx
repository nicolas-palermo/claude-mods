import { expect, test } from 'claude-code/testing'

import { buildGroups, parseDiff, parsePlan, parseRef } from '../hooks/diff'

const DIFF = `diff --git a/src/export.ts b/src/export.ts
index 1111111..2222222 100644
--- a/src/export.ts
+++ b/src/export.ts
@@ -1,3 +1,4 @@
 import { save } from './save'
+import { isSafari } from './browser'
 
 export const exportFile = () => {
@@ -10,2 +11,5 @@ export const exportFile = () => {
-  save()
+  if (isSafari()) {
+    return saveWithLink()
+  }
+  save()
diff --git a/src/export.test.ts b/src/export.test.ts
new file mode 100644
--- /dev/null
+++ b/src/export.test.ts
@@ -0,0 +1,2 @@
+test('exports on Safari', () => {})
+
diff --git a/logo.png b/logo.png
Binary files a/logo.png and b/logo.png differ
`

const ANSWER = JSON.stringify({
  title: 'Arreglar exportar en Safari',
  overview: 'Exportar usaba una API que Safari no tiene.',
  groups: [
    { title: 'Exportar con un link en Safari', summary: 'Usa `saveWithLink` en Safari.', hunks: ['h1', 'h2'] },
    { title: 'Test de Safari', summary: 'Agrega un test.', hunks: ['h3'] },
  ],
})

test('parseDiff splits files into hunks, a binary file included', () => {
  const hunks = parseDiff(DIFF)
  expect(hunks.map(h => `${h.id} ${h.path}`)).toEqual([
    'h1 src/export.ts',
    'h2 src/export.ts',
    'h3 src/export.test.ts',
    'h4 logo.png',
  ])
  expect(hunks[1]?.text.startsWith('@@ -10,2 +11,5 @@')).toBe(true)
  expect(hunks[3]?.text).toBe('')
})

test('every hunk lands in one group, leftovers in "Otros cambios"', () => {
  const plan = parsePlan('Here:\n' + ANSWER)
  expect(plan?.groups.length).toBe(2)
  const groups = buildGroups(parseDiff(DIFF), plan!)
  expect(groups.map(g => g.title)).toEqual([
    'Exportar con un link en Safari',
    'Test de Safari',
    'Otros cambios',
  ])
  expect(groups[0]?.files[0]?.added).toBe(5)
  expect(groups[0]?.files[0]?.removed).toBe(1)
})

test('parseRef reads a URL, owner/repo#n and a bare number', () => {
  expect(parseRef('https://github.com/acme/app/pull/42')).toEqual({ owner: 'acme', repo: 'app', number: 42 })
  expect(parseRef('acme/app#7')).toEqual({ owner: 'acme', repo: 'app', number: 7 })
  expect(parseRef('#12')).toEqual({ number: 12 })
  expect(parseRef('hola')).toBe(undefined)
})

const PANE = {
  plugin: 'guided-view',
  component: 'Pane',
  requestId: 'guided-view',
  props: {
    title: 'Guided view',
    isFocused: false,
    bodyColumns: 60,
    placement: 'dock',
    scroll: { offset: 0, bodyRows: 40 },
    view: {},
  },
} as const

test('the command draws the PR grouped by meaning, with its diffs', async ($, on) => {
  on('command.register', () => ({ value: { command: 'guided-view' } }))
  on('ui.open', () => ({ value: { isPlaced: true } }))
  on('ui.toast', () => ({ value: undefined }))
  on('session.start', (_, e) => ({ cwd: e.cwd }))
  on('process.run', (_, e) => {
    const argv = (e as { argv: string[] }).argv
    if (argv[1] === 'pr' && argv[2] === 'view') {
      return { value: { exitCode: 0, stdout: JSON.stringify({ title: 'Fix export', url: 'https://github.com/acme/app/pull/42', number: 42 }), stderr: '', isStdoutTruncated: false, isStderrTruncated: false } }
    }
    if (argv[1] === 'pr' && argv[2] === 'diff') {
      return { value: { exitCode: 0, stdout: DIFF, stderr: '', isStdoutTruncated: false, isStderrTruncated: false } }
    }
    return { value: { exitCode: 1, stdout: '', stderr: 'no', isStdoutTruncated: false, isStderrTruncated: false } }
  })
  on('model.complete', () => ({ value: { isAnswered: true, text: ANSWER, usage: { input_tokens: 0, output_tokens: 0, cache_read_input_tokens: 0, cache_creation_input_tokens: 0 } } }))

  await $.session.start({ cwd: '/', surface: 'desktop', isInteractive: true })
  await $.command.run({ command: 'guided-view', args: '42',
    origin: { kind: 'composer' },
    presentation: { isFullscreen: true, columns: 120 },
  })

  for (let i = 0; i < 50; i++) {
    const ui = await $.ui.mount({ ...PANE, surface: 'desktop' })
    const done = await ui.find({ type: 'Text', text: /Arreglar exportar en Safari/ })
    await ui.unmount()
    if (done !== undefined) break
    await Promise.resolve()
  }

  for (const surface of ['terminal', 'desktop', 'vscode', 'mobile'] as const) {
    const ui = await $.ui.mount({ ...PANE, surface })
    expect((await ui.find({ type: 'Text', text: /Arreglar exportar/ }))?.text).toBe('Arreglar exportar en Safari')
    expect(await ui.find({ type: 'Code' })).toBeDefined()
    expect(await ui.find({ type: 'Text', text: /src\/export\.test\.ts/ })).toBeDefined()
    await ui.unmount()
  }
})

test('before any review, the pane says how to start one', async ($, on) => {
  const ui = await $.ui.mount({ ...PANE, surface: 'desktop' })
  expect((await ui.find({ type: 'Text' }))?.text).toMatch(/guided-view/)
  await ui.unmount()
})
