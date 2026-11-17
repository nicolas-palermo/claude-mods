import { expect, test } from 'claude-code/testing'

const PANE = {
  plugin: 'guided-view',
  component: 'Pane',
  requestId: 'guided-view',
  props: {
    title: 'Guía',
    isFocused: false,
    bodyColumns: 40,
    placement: 'dock',
    scroll: { offset: 0, bodyRows: 30 },
    view: {},
  },
} as const

test('the pane shows the goal, the plan and the current step', async ($, on) => {
  on('command.register', () => ({ value: { command: 'guia' } }))
  on('ui.open', () => ({ value: { isPlaced: true } }))
  on('session.start', (_, e) => ({ cwd: e.cwd }))
  on('prompt.submit', (_, e) => ({ text: e.text }))
  on('tool.call', () => ({ result: {} }))

  await $.session.start({ cwd: '/', surface: 'desktop', isInteractive: true })
  await $.prompt.submit({
    text: 'Arreglá el botón de exportar',
    wait: false,
    origin: { kind: 'composer' },
  })
  await $.tool.call({
    tool: 'TodoWrite',
    todos: [
      { content: 'Reproducir el bug', status: 'completed', activeForm: 'Reproduciendo el bug' },
      { content: 'Corregir el handler', status: 'in_progress', activeForm: 'Corrigiendo el handler' },
      { content: 'Probar en Safari', status: 'pending', activeForm: 'Probando en Safari' },
    ],
  })

  for (const surface of ['terminal', 'desktop', 'vscode', 'mobile'] as const) {
    const ui = await $.ui.mount({ ...PANE, surface })
    expect((await ui.find({ type: 'Text', text: /exportar/ }))?.text).toBe(
      'Arreglá el botón de exportar',
    )
    expect((await ui.find({ type: 'Text', text: /Plan/ }))?.text).toBe('Plan · 1 de 3')
    expect((await ui.find({ type: 'Text', text: /handler/ }))?.text).toBe(
      '▸ Corrigiendo el handler',
    )
    expect((await ui.find({ type: 'Text', text: /Safari/ }))?.text).toBe('○ Probar en Safari')
    await ui.unmount()
  }
})

test('an empty session shows a hint', async ($, on) => {
  const ui = await $.ui.mount({ ...PANE, surface: 'desktop' })
  expect((await ui.find({ type: 'Text' }))?.text).toMatch(/Cuando le pidas algo/)
  await ui.unmount()
})
