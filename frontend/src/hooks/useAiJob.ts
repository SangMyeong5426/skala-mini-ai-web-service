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
 * 06 폴링 규약: 예산을 넘기면 "시간이 오래 걸립니다" 와 재시도 버튼을
 * 보여주고, **작업은 서버에 남는다.**
 *
 * <b>예산은 횟수가 아니라 시간이다.</b> 예전에는 60회로 셌는데, 간격을 서버가
 * `pollAfterMs` 로 정하므로 실제 예산은 서버 손에 있었다 — 500ms 를 보내면
 * 30초다. 서버의 AI 타임아웃은 `app.ai.timeout-ms=60000` 이고 재시도가 한 번
 * 더 있다. 즉 <b>서버가 아직 일하는 중인데 화면이 먼저 포기했다.</b>
 * 실제로 새 백엔드에서 BAG_CHECK 이 10초가 걸리기 시작했고, 실제 모델을
 * 붙이면 그 위로 더 간다.
 *
 * 90초는 서버 타임아웃 60초에 재시도·네트워크 여유를 더한 값이다.
 * `MAX_POLLS` 는 서버가 `pollAfterMs: 0` 같은 값을 보낼 때를 대비한
 * <b>무한 루프 방지용</b>이지 예산이 아니다.
 */
const MAX_WAIT_MS = 90_000
const FALLBACK_DELAY_MS = 500
/**
 * 폴링 간격의 <b>하한</b>. 서버가 `pollAfterMs: 0` 을 보내면 마감 검사
 * (`now + 0 > deadline`)는 90초가 지나야 참이 되므로, 그동안 네트워크가
 * 허용하는 속도로 요청을 몰아친다. 횟수 상한은 그 <b>속도</b>를 막지 못한다.
 */
const MIN_DELAY_MS = 250

/** 테스트에서 시계를 갈아 끼울 수 있게 한 겹 둔다 */
const nowMs = () => Date.now()

/** `start` 가 돌려주는 것. state 와 달리 await 직후 바로 읽을 수 있다 */
export interface AiJobResult<T> {
  done: boolean
  output: T | null
}

export type AiJobPhase = 'idle' | 'running' | 'done' | 'failed' | 'timeout'

export interface UseAiJob<T> {
  phase: AiJobPhase
  /** 폴링 횟수. 화면에 진행 표시를 그릴 때 쓴다. */
  polls: number
  output: T | null
  error: string | null
  jobId: number | null
  /**
   * 작업을 걸고 끝날 때까지 기다린다.
   *
   * <b>완료 결과를 함께 돌려준다.</b> `output` state 는 다음 렌더에나 보이므로,
   * `await start(...)` 직후에 `job.output` 을 읽으면 <b>시작 전 렌더의 값</b>이
   * 잡힌다 — 첫 성공에서는 아무것도 못 읽고, 두 번째에는 지난 결과를 읽는다.
   * 실제로 그 버그를 냈다. 그래서 결과를 반환값에 싣는다.
   *
   * `done` 은 COMPLETED 만 true 다. 실패·시간초과는 false —
   * 후속 작업을 걸기 전에 확인한다.
   */
  start: (jobType: JobType, input: unknown, tripId?: number) => Promise<AiJobResult<T>>
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
  /** 접수 중인 작업이 있나. 같은 훅으로 두 건을 걸지 않는다 */
  const busy = useRef(false)
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
    async (jobType: JobType, input: unknown, tripId?: number): Promise<AiJobResult<T>> => {
      /*
       * <b>한 훅에 한 작업이다.</b> 호출부의 `disabled` 에만 기대면 버튼을 하나
       * 빠뜨렸을 때 같은 작업이 두 건 접수되고, 두 폴링 루프가 하나의
       * phase·output·polls 를 서로 덮어쓴다. state 가 아니라 ref 로 막는다 —
       * setState 는 다음 렌더에나 반영돼서 연타를 못 잡는다.
       */
      if (busy.current) return { done: false, output: null }
      busy.current = true
      const startedAt = nowMs()
      setPhase('running')
      setPolls(0)
      setOutput(null)
      setError(null)

      try {
        const created = await api.post<AiJobCreated>('/ai-jobs', { jobType, tripId, input })
        setJobId(created.jobId)

        let wait = Math.max(created.pollAfterMs ?? FALLBACK_DELAY_MS, MIN_DELAY_MS)
        const deadline = startedAt + MAX_WAIT_MS
        // 하한이 있으므로 횟수 상한은 필요 없다 — 예산이 곧 종료 조건이다
        for (let n = 1; ; n++) {
          await new Promise((r) => setTimeout(r, wait))
          if (!alive.current) return { done: false, output: null }

          const job = await api.get<AiJob<T>>(`/ai-jobs/${created.jobId}`)
          setPolls(n)

          if (job.status === 'COMPLETED') {
            const output = job.output ?? null
            setOutput(output)
            setPhase('done')
            return { done: true, output }
          }
          if (job.status === 'FAILED') {
            // 06: FAILED 도 200 이다. 조회 자체는 성공했기 때문이다.
            // 네트워크 오류와 AI 실패를 구분해야 기본 체크리스트로 넘어갈 수 있다.
            setError(job.errorMessage ?? 'AI 작업이 실패했습니다.')
            setPhase('failed')
            return { done: false, output: null }
          }
          wait = Math.max(job.pollAfterMs ?? wait, MIN_DELAY_MS)
          // 다음 한 번을 더 기다릴 여유가 없으면 여기서 접는다
          if (nowMs() + wait > deadline) break
        }
        if (alive.current) setPhase('timeout')
        return { done: false, output: null }
      } catch (e) {
        if (!alive.current) return { done: false, output: null }
        setError(e instanceof Error ? e.message : '알 수 없는 오류입니다.')
        setPhase('failed')
        return { done: false, output: null }
      } finally {
        busy.current = false
      }
    },
    [],
  )

  return { phase, polls, output, error, jobId, start, reset }
}
