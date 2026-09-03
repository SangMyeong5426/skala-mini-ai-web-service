import { useCallback, useEffect, useRef, useState } from 'react'
import { api } from '../api/client'
import type { AiJob, AiJobCreated, JobType } from '../types/api'

/**
 * AI 작업을 만들고 끝날 때까지 폴링한다.
 *
 * **Mock 이 즉시 답해도 폴링으로 구현한다.** CLAUDE.md 의 규칙이다.
 * 실제 LLM 을 붙이면 `pollAfterMs` 만 늘어나고 이 코드는 그대로다 —
 * 그것이 AI-Ready 원칙 3(Asynchronous Pipeline)의 증명이다.
 *
 * 06 폴링 규약: 최대 60회. 초과하면 "시간이 오래 걸립니다" 와 재시도 버튼을
 * 보여주고, **작업은 서버에 남는다.**
 */
const MAX_POLLS = 60
const FALLBACK_DELAY_MS = 500

export type AiJobPhase = 'idle' | 'running' | 'done' | 'failed' | 'timeout'

export interface UseAiJob<T> {
  phase: AiJobPhase
  /** 폴링 횟수. 화면에 진행 표시를 그릴 때 쓴다. */
  polls: number
  output: T | null
  error: string | null
  jobId: number | null
  /** 완료(COMPLETED)면 true. 실패·시간초과는 false — 후속 작업을 걸기 전에 확인한다 */
  start: (jobType: JobType, input: unknown, tripId?: number) => Promise<boolean>
  reset: () => void
}

export function useAiJob<T = unknown>(): UseAiJob<T> {
  const [phase, setPhase] = useState<AiJobPhase>('idle')
  const [polls, setPolls] = useState(0)
  const [output, setOutput] = useState<T | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [jobId, setJobId] = useState<number | null>(null)

  // 화면을 벗어나면 폴링을 멈춘다. 작업 자체는 서버에 남는다.
  const alive = useRef(true)
  useEffect(() => {
    alive.current = true
    return () => {
      alive.current = false
    }
  }, [])

  const reset = useCallback(() => {
    setPhase('idle')
    setPolls(0)
    setOutput(null)
    setError(null)
    setJobId(null)
  }, [])

  const start = useCallback(
    async (jobType: JobType, input: unknown, tripId?: number): Promise<boolean> => {
      setPhase('running')
      setPolls(0)
      setOutput(null)
      setError(null)

      try {
        const created = await api.post<AiJobCreated>('/ai-jobs', { jobType, tripId, input })
        setJobId(created.jobId)

        let wait = created.pollAfterMs ?? FALLBACK_DELAY_MS
        for (let n = 1; n <= MAX_POLLS; n++) {
          await new Promise((r) => setTimeout(r, wait))
          if (!alive.current) return false

          const job = await api.get<AiJob<T>>(`/ai-jobs/${created.jobId}`)
          setPolls(n)

          if (job.status === 'COMPLETED') {
            setOutput(job.output)
            setPhase('done')
            return true
          }
          if (job.status === 'FAILED') {
            // 06: FAILED 도 200 이다. 조회 자체는 성공했기 때문이다.
            // 네트워크 오류와 AI 실패를 구분해야 기본 체크리스트로 넘어갈 수 있다.
            setError(job.errorMessage ?? 'AI 작업이 실패했습니다.')
            setPhase('failed')
            return false
          }
          wait = job.pollAfterMs ?? wait
        }
        if (alive.current) setPhase('timeout')
        return false
      } catch (e) {
        if (!alive.current) return false
        setError(e instanceof Error ? e.message : '알 수 없는 오류입니다.')
        setPhase('failed')
        return false
      }
    },
    [],
  )

  return { phase, polls, output, error, jobId, start, reset }
}
