import { atom, read, update } from 'claude-code'
import type { Register } from 'claude-code'

import type { Guide, Step, StepStatus } from '../types'

const PANE = 'guided-view'
const TITLE = 'Guía'

const EMPTY: Guide = { goal: '', steps: [], now: undefined, isWorking: false }

// Change the tag when Guide's fields change: it declines what an earlier
// version stored, which outlives a reload.
const guide = atom({ plugin: 'guided-view', key: 'guide' } as const, EMPTY, {
  shape: 'guide-1',
})

const short = (text: string, max: number) => {
  const line = text.replace(/\s+/g, ' ').trim()

  return line.length > max ? `${line.slice(0, max - 1)}…` : line
}

const fileName = (path: unknown) =>
  typeof path === 'string' ? (path.split('/').pop() ?? path) : ''

// One plain line for what a tool call is doing right now.
const describe = (e: { tool: string } & Record<string, unknown>): string => {
  switch (e.tool) {
    case 'Bash':
      return typeof e.description === 'string' && e.description !== ''
        ? e.description
        : 'Corriendo un comando'
    case 'Read':
      return `Leyendo ${fileName(e.file_path)}`
    case 'Edit':
    case 'Write':
    case 'NotebookEdit':
      return `Editando ${fileName(e.file_path ?? e.notebook_path)}`
    case 'Grep':
    case 'Glob':
      return 'Buscando en los archivos'
    case 'WebSearch':
      return typeof e.query === 'string'
        ? `Buscando "${short(e.query, 40)}"`
        : 'Buscando en la web'
    case 'WebFetch':
      return 'Leyendo una página web'
    case 'Agent':
    case 'Task':
      return typeof e.description === 'string'
        ? `Delegando: ${e.description}`
        : 'Delegando una tarea'
    case 'TodoWrite':
    case 'TaskCreate':
    case 'TaskUpdate':
      return 'Actualizando el plan'
    default:
      return e.tool.startsWith('mcp__')
        ? `Usando ${e.tool.split('__').slice(1).join(' ')}`
        : `Usando ${e.tool}`
  }
}

const MARK: Record<StepStatus, string> = {
  completed: '✓',
  in_progress: '▸',
  pending: '○',
}

export const register: Register = on => {
  on('session.start', async ($, e, next) => {
    await $.command.register({
      name: 'guia',
      description: 'Abrir el panel Guía: el objetivo, el plan y el paso actual',
    })
    void $.ui.open({ id: PANE, title: TITLE })

    return next(e)
  })

  on('command.run', { command: 'guia' }, async $ => {
    await $.ui.open({ id: PANE, title: TITLE })

    return { text: 'Panel Guía abierto.' }
  })

  // A new request from the person is a new goal and a fresh plan.
  on('prompt.submit', async ($, e, next) => {
    const entered = await next(e)
    if (e.command === undefined && e.isMeta !== true && e.text.trim() !== '') {
      if (e.turnId === undefined) {
        await update($, guide, () => ({
          ...EMPTY,
          goal: short(e.text, 160),
          isWorking: true,
        }))
      }
    }

    return entered
  }).catch(($, e, next) => next(e))

  on('turn.start', async ($, e, next) => {
    if (e.agentId === undefined) {
      await update($, guide, g => ({ ...g, isWorking: true }))
    }

    return next(e)
  })

  on('turn.complete', async ($, e, next) => {
    if (e.agentId === undefined) {
      await update($, guide, g => ({ ...g, isWorking: false, now: undefined }))
    }

    return next(e)
  })

  on('tool.call', async ($, e, next) => {
    const isMain = e.agentId === undefined
    if (isMain) {
      await update($, guide, g => ({
        ...g,
        now: describe(e as { tool: string } & Record<string, unknown>),
      }))
    }

    const ran = await next(e)

    if (ran.deny === undefined && ran.isError !== true) {
      if (e.tool === 'TodoWrite') {
        const steps: Step[] = e.todos.map((todo, i) => ({
          id: `todo-${i}`,
          title: todo.content,
          active: todo.activeForm,
          status: todo.status,
        }))
        await update($, guide, g => ({ ...g, steps }))
      } else if (e.tool === 'TaskCreate') {
        const made = ran.result as { task?: { id?: string } } | undefined
        const id = made?.task?.id
        if (id !== undefined) {
          const step: Step = {
            id,
            title: e.subject,
            active: e.activeForm ?? e.subject,
            status: 'pending',
          }
          await update($, guide, g => ({
            ...g,
            steps: [...g.steps.filter(s => s.id !== id), step],
          }))
        }
      } else if (e.tool === 'TaskUpdate') {
        const { taskId, status, subject, activeForm } = e
        await update($, guide, g => ({
          ...g,
          steps:
            status === 'deleted'
              ? g.steps.filter(s => s.id !== taskId)
              : g.steps.map(s =>
                  s.id !== taskId
                    ? s
                    : {
                        ...s,
                        title: subject ?? s.title,
                        active: activeForm ?? s.active,
                        status: status ?? s.status,
                      },
                ),
        }))
      }
    }

    if (isMain) {
      await update($, guide, g => ({ ...g, now: undefined }))
    }

    return ran
  }).catch(($, e, next) => next(e))

  on('ui.render', { component: 'Pane', requestId: PANE }, async ($, e) => {
    const { Box, Text, Progress } = $.ui.resolve(e)
    const g = await read($, guide)
    const done = g.steps.filter(s => s.status === 'completed').length
    const current = g.steps.find(s => s.status === 'in_progress')
    const now = g.now ?? (g.isWorking ? (current?.active ?? 'Pensando…') : undefined)

    if (g.goal === '' && g.steps.length === 0) {
      return (
        <Box flexDirection="column" gap={1}>
          <Text dimColor>
            Cuando le pidas algo a Claude, acá vas a ver el objetivo, el plan y
            en qué paso va.
          </Text>
        </Box>
      )
    }

    return (
      <Box flexDirection="column" gap={1}>
        {g.goal !== '' && (
          <Box flexDirection="column">
            <Text dimColor>Objetivo</Text>
            <Text bold>{g.goal}</Text>
          </Box>
        )}

        {g.steps.length > 0 && (
          <Box flexDirection="column">
            <Text dimColor>
              Plan · {done} de {g.steps.length}
            </Text>
            <Progress
              label="Avance del plan"
              value={done}
              max={g.steps.length}
              valueText={`${done} de ${g.steps.length}`}
            />
            {g.steps.map(step => (
              <Text
                bold={step.status === 'in_progress'}
                dimColor={step.status === 'completed'}
              >
                {MARK[step.status]}{' '}
                {step.status === 'in_progress' ? step.active : step.title}
              </Text>
            ))}
          </Box>
        )}

        <Box flexDirection="column">
          <Text dimColor>Ahora</Text>
          <Text>{now ?? (done > 0 && done === g.steps.length ? 'Listo ✓' : 'Esperando tu próximo pedido')}</Text>
        </Box>
      </Box>
    )
  })
}
