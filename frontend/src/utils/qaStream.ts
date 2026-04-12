export interface QaStreamEvent {
  type: 'context' | 'chunk' | 'done' | 'error'
  sessionId?: string
  questionMessageId?: string
  answerMessageId?: string
  content?: string
  answer?: string
  message?: string
  references?: Array<Record<string, unknown>>
}

export interface QaStreamParseResult {
  events: QaStreamEvent[]
  remainder: string
}

export function parseQaStreamChunk(chunk: string): QaStreamParseResult {
  const normalized = chunk.replace(/\r\n/g, '\n')
  const blocks = normalized.split('\n\n')
  const remainder = normalized.endsWith('\n\n') ? '' : (blocks.pop() ?? '')
  const events = blocks
    .map(parseQaStreamBlock)
    .filter((event): event is QaStreamEvent => event !== null)
  return {
    events,
    remainder,
  }
}

function parseQaStreamBlock(block: string): QaStreamEvent | null {
  const lines = block.split('\n')
  let eventName = ''
  const dataLines: string[] = []
  for (const line of lines) {
    if (line.startsWith('event:')) {
      eventName = line.slice('event:'.length).trim()
      continue
    }
    if (line.startsWith('data:')) {
      dataLines.push(line.slice('data:'.length).trim())
    }
  }
  if (!eventName || dataLines.length === 0) {
    return null
  }
  return JSON.parse(dataLines.join('\n')) as QaStreamEvent
}
