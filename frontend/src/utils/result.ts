import type { Result } from '@/types/api'

const codeMessages: Record<number, string> = {
  40000: '请求参数不正确，请检查输入内容。',
  40003: '文件类型暂不支持，请上传 PDF、PPT、Word、TXT、Markdown、音频或视频资料。',
  40006: '资料的问答索引还没有准备完成，请稍后再试。',
  40100: '登录状态已失效，请重新登录。',
  40102: '登录凭证已过期，请重新登录。',
  40401: '没有找到对应资料，请刷新列表后重试。',
  40403: '资料解析文本还没有生成完成，请稍后再试。',
  40405: 'AI 摘要还没有生成完成，请稍后再试。',
  40406: '问答会话不存在，请重新创建会话。',
  40900: '当前操作正在处理中，请不要重复提交。',
  50000: '服务内部异常，请稍后再试。',
  50001: 'AI 服务繁忙或超时，请稍后再试。',
}

export class StudyFlowError extends Error {
  constructor(
    public readonly code: number,
    message: string,
    public readonly rawMessage?: string,
  ) {
    super(message)
    this.name = 'StudyFlowError'
  }
}

export function mapResultMessage(code: number, rawMessage?: string): string {
  return codeMessages[code] ?? rawMessage ?? '请求失败，请稍后再试。'
}

export function unwrapResult<T>(result: Result<T>): T {
  if (result.code === 0) {
    return result.data
  }
  throw new StudyFlowError(result.code, mapResultMessage(result.code, result.message), result.message)
}

export function getDisplayError(error: unknown): string {
  if (error instanceof StudyFlowError) {
    return error.message
  }
  if (error instanceof Error && error.message) {
    return error.message
  }
  return '请求失败，请稍后再试。'
}
