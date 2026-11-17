export type FileDiff = {
  /** The file's path after the change (before it, for a deleted file). */
  path: string
  /** Unified-diff hunks of this file that belong to the group. */
  patch: string
  added: number
  removed: number
}

export type ChangeGroup = {
  title: string
  summary: string
  files: FileDiff[]
}

export type Review =
  | { status: 'idle' }
  | { status: 'loading'; source: string; step: string }
  | { status: 'error'; source: string; message: string }
  | {
      status: 'ready'
      source: string
      title: string
      url: string | undefined
      overview: string
      groups: ChangeGroup[]
    }

declare module 'claude-code' {
  interface PluginState {
    'guided-view': { review: Shaped<Review>; collapsed: Shaped<number[]> }
  }
}
