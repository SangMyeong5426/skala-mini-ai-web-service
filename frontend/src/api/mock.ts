/**
 * 백엔드가 붙기 전에 화면을 만들기 위한 가짜 서버.
 *
 * <b>라이브러리를 설치하지 않는다.</b> MSW·json-server 는 3일 일정에 맞지 않고
 * CLAUDE.md 가 기능을 늘리지 말라고 정해 뒀다. 여기 함수 하나가 전부다.
 *
 * <b>AI 작업은 즉시 답하지 않는다.</b> PENDING 을 두 번 돌려준 뒤 COMPLETED 가 된다.
 * 즉시 답하면 폴링 코드가 한 번도 안 돌아서, 백엔드가 붙는 날 처음 실행하게 된다.
 * 이렇게 두면 "AI 가 처리 중" 화면과 진행 표시를 오늘 확인할 수 있다.
 *
 * 끄는 법: frontend/.env 의 VITE_USE_MOCK 를 지우거나 false 로 둔다.
 */
import * as fx from './fixtures'
import type { JobType } from '../types/api'

export const USE_MOCK = import.meta.env.VITE_USE_MOCK === 'true'

/** 실제 서버처럼 보이도록 약간 늦춘다. 스켈레톤이 한 번은 보여야 한다. */
const LATENCY_MS = 200

/** 작업별 남은 PENDING 횟수. 0 이 되면 COMPLETED. */
const pending = new Map<number, { left: number; jobType: JobType }>()
let nextJobId = 1041

function delay<T>(value: T): Promise<T> {
  return new Promise((r) => setTimeout(() => r(value), LATENCY_MS))
}

/** 경로에서 숫자를 뽑는다. `/trips/1/items` → 1 */
function firstId(path: string): number {
  const m = path.match(/\/(\d+)/)
  return m ? Number(m[1]) : 1
}

/**
 * 경로를 보고 fixtures 를 돌려준다.
 * 다루지 않는 경로는 `undefined` 를 반환하고, 호출한 쪽이 404 로 처리한다 —
 * **아직 안 만든 것을 조용히 성공시키지 않는다.**
 */
export function mockRequest(method: string, path: string, body?: unknown): Promise<unknown> | undefined {
  const p = path.split('?')[0]

  // ── AI 작업 ────────────────────────────────────────────
  if (method === 'POST' && p === '/ai-jobs') {
    const jobType = (body as { jobType: JobType }).jobType
    const jobId = nextJobId++
    // PENDING 2회 뒤 COMPLETED. 폴링 UI 가 실제로 돌아간다.
    pending.set(jobId, { left: 2, jobType })
    return delay(fx.AI_JOB_CREATED(jobType, jobId))
  }
  if (method === 'GET' && p.startsWith('/ai-jobs/')) {
    const jobId = firstId(p)
    const state = pending.get(jobId)
    if (!state) return delay(fx.AI_JOB(jobId, 'PACKING_LIST', true))
    if (state.left > 0) {
      state.left -= 1
      return delay(fx.AI_JOB(jobId, state.jobType, false))
    }
    return delay(fx.AI_JOB(jobId, state.jobType, true))
  }

  // ── 여행 ───────────────────────────────────────────────
  if (method === 'GET' && p === '/trips') return delay({ trips: fx.TRIPS })
  if (method === 'POST' && p === '/trips') return delay({ ...fx.TRIP_DETAIL, tripId: 99 })
  if (method === 'GET' && /^\/trips\/\d+$/.test(p)) return delay(fx.TRIP_DETAIL)

  // ── 체크리스트 ─────────────────────────────────────────
  if (method === 'GET' && /^\/trips\/\d+\/items$/.test(p)) {
    return delay({ items: fx.ITEMS, completionRate: 0.5 })
  }
  if (method === 'PATCH' && /^\/trips\/\d+\/items\/\d+$/.test(p)) {
    const itemId = Number(p.split('/').pop())
    const item = fx.ITEMS.find((i) => i.itemId === itemId) ?? fx.ITEMS[0]
    return delay({ ...item, ...(body as object) })
  }

  // ── 사진 ───────────────────────────────────────────────
  if (method === 'GET' && /^\/trips\/\d+\/photos$/.test(p)) return delay({ photos: fx.PHOTOS })

  // ── 인식 결과 ──────────────────────────────────────────
  if (method === 'GET' && /^\/trips\/\d+\/detections$/.test(p)) {
    return delay({ detections: fx.DETECTIONS })
  }
  if (method === 'PATCH' && /^\/trips\/\d+\/detections\/\d+$/.test(p)) {
    const detectionId = Number(p.split('/').pop())
    const d = fx.DETECTIONS.find((x) => x.detectionId === detectionId) ?? fx.DETECTIONS[0]
    return delay({ ...d, ...(body as object), linkedItems: [] })
  }

  // ── 검수 결과 ──────────────────────────────────────────
  if (method === 'GET' && /^\/trips\/\d+\/inspection$/.test(p)) return delay(fx.INSPECTION)

  return undefined
}
