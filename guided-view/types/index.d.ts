export type StepStatus = 'pending' | 'in_progress' | 'completed'

export type Step = {
  id: string
  title: string
  active: string
  status: StepStatus
}

export type Guide = {
  goal: string
  steps: Step[]
  now: string | undefined
  isWorking: boolean
}

declare module 'claude-code' {
  interface PluginState {
    'guided-view': { guide: Shaped<Guide> }
  }
}
