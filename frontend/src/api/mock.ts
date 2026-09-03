/**
 * 백엔드가 붙기 전에 화면을 만들기 위한 가짜 서버.
 *
 * <b>라이브러리를 설치하지 않는다.</b> MSW·json-server 는 3일 일정에 맞지 않고
 * CLAUDE.md 가 기능을 늘리지 말라고 정해 뒀다. 이 파일 하나가 전부다.
 *
 * <b>상태를 들고 있는다.</b> PATCH 한 결과가 다음 GET 에 보여야 한다.
 * 안 그러면 S-04 에서 승인하고 S-05 로 갔을 때 승인이 사라져서
 * 승인 → 체크리스트 흐름 자체를 검증할 수 없다.
 * 새로고침하면 초기화된다 — 페이지가 열린 동안만 유지한다.
 *
 * <b>AI 작업은 즉시 답하지 않는다.</b> PENDING 을 두 번 돌려준 뒤 COMPLETED 가 된다.
 * 즉시 답하면 폴링 코드가 한 번도 안 돌아서, 백엔드가 붙는 날 처음 실행하게 된다.
 *
 * <b>Location 헤더는 흉내 내지 않는다.</b> 이 함수는 본문만 돌려주므로
 * `201 + Location` 을 재현할 수 없다. 06 이 Location 을 약속한 세 곳
 * (POST /trips · POST /items · POST /photos)은 <b>본문의 id 로 받는다.</b>
 * 백엔드가 붙으면 헤더를 써도 되지만, 본문에도 id 가 있으므로 화면을 고칠 필요는 없다.
 *
 * 끄는 법: frontend/.env 의 VITE_USE_MOCK 를 false 로.
 */
import * as fx from './fixtures'
import type { ChecklistItem, Detection, JobType, TripDetail, TripSummary } from '../types/api'

export const USE_MOCK = import.meta.env.VITE_USE_MOCK === 'true'

/** 실제 서버처럼 보이도록 약간 늦춘다. 스켈레톤이 한 번은 보여야 한다. */
const LATENCY_MS = 200

// ── 메모리 상태 ───────────────────────────────────────────
// fixtures 는 건드리지 않는다. 여기 복사본만 바뀐다.
const db = {
  trips: fx.TRIPS.map((t) => ({ ...t })) as TripSummary[],
  details: new Map<number, TripDetail>([[1, { ...fx.TRIP_DETAIL }]]),
  items: fx.ITEMS.map((i) => ({ ...i })) as ChecklistItem[],
  detections: fx.DETECTIONS.map((d) => ({ ...d })) as Detection[],
  /** detectionId → 연결된 itemId 목록. 06 의 matchedItemIds 규약을 그대로 따른다. */
  links: new Map<number, number[]>([[2, [6]], [6, [8]], [8, [8, 9]]]),
  nextTripId: 100,
}

const pending = new Map<number, { left: number; jobType: JobType }>()
let nextJobId = 1041

function delay<T>(value: T): Promise<T> {
  return new Promise((r) => setTimeout(() => r(value), LATENCY_MS))
}

function idsIn(path: string): number[] {
  return (path.match(/\d+/g) ?? []).map(Number)
}

/** 06 의 completionRate — PREPARED 비율. 상태가 바뀌면 같이 바뀌어야 한다. */
function completionRate(): number {
  if (db.items.length === 0) return 0
  const done = db.items.filter((i) => i.checkStatus === 'PREPARED').length
  return Math.round((done / db.items.length) * 100) / 100
}

/** detectionId 에 걸린 연결을 06 의 linkedItems 모양으로. */
function linkedItems(detectionId: number) {
  return (db.links.get(detectionId) ?? []).map((itemId) => ({
    itemId,
    name: db.items.find((i) => i.itemId === itemId)?.name ?? '',
    confirmedByUser: db.detections.find((d) => d.detectionId === detectionId)?.approved ?? false,
  }))
}

/**
 * 경로를 보고 응답을 만든다.
 * 다루지 않는 경로는 `undefined` 를 반환하고 호출한 쪽이 404 로 처리한다 —
 * <b>안 만든 것을 조용히 성공시키지 않는다.</b>
 */
export function mockRequest(method: string, path: string, body?: unknown): Promise<unknown> | undefined {
  const p = path.split('?')[0]
  const b = (body ?? {}) as Record<string, unknown>

  // ── AI 작업 ────────────────────────────────────────────
  if (method === 'POST' && p === '/ai-jobs') {
    const jobType = b.jobType as JobType
    const jobId = nextJobId++
    pending.set(jobId, { left: 2, jobType })
    return delay(fx.AI_JOB_CREATED(jobType, jobId))
  }
  if (method === 'GET' && p.startsWith('/ai-jobs/')) {
    const [jobId] = idsIn(p)
    const state = pending.get(jobId)
    if (!state) return delay(fx.AI_JOB(jobId, 'PACKING_LIST', true))
    if (state.left > 0) {
      state.left -= 1
      return delay(fx.AI_JOB(jobId, state.jobType, false))
    }
    return delay(fx.AI_JOB(jobId, state.jobType, true))
  }

  // ── 여행 ───────────────────────────────────────────────
  if (method === 'GET' && p === '/trips') return delay({ trips: db.trips })

  if (method === 'POST' && p === '/trips') {
    // 요청 본문을 그대로 반영한다. 무시하면 S-02 저장 후 이동이 엉뚱한 여행으로 간다.
    const tripId = db.nextTripId++
    const created: TripDetail = {
      tripId,
      origin: String(b.origin ?? ''),
      destination: String(b.destination ?? ''),
      startDate: String(b.startDate ?? ''),
      endDate: String(b.endDate ?? ''),
      transport: (b.transport ?? 'FLIGHT') as TripDetail['transport'],
      // 06: 생성 직후는 DRAFT 다.
      status: 'DRAFT',
      completionRate: 0,
      countryCode: b.countryCode as string | undefined,
      purpose: b.purpose as TripDetail['purpose'],
      airline: b.airline as string | undefined,
      departureAirport: b.departureAirport as string | undefined,
      arrivalAirport: b.arrivalAirport as string | undefined,
      bagType: b.bagType as TripDetail['bagType'],
      bagEmptyG: b.bagEmptyG as number | undefined,
      weightLimitG: b.weightLimitG as number | undefined,
      note: b.note as string | undefined,
    }
    db.details.set(tripId, created)
    db.trips.unshift({ ...created })
    return delay({ ...created, createdAt: new Date().toISOString() })
  }

  if (method === 'GET' && /^\/trips\/\d+$/.test(p)) {
    const [tripId] = idsIn(p)
    const found = db.details.get(tripId)
    if (!found) return undefined   // 없는 여행은 404 다
    return delay(found)
  }

  // ── 체크리스트 ─────────────────────────────────────────
  if (method === 'GET' && /^\/trips\/\d+\/items$/.test(p)) {
    return delay({ items: db.items, completionRate: completionRate() })
  }
  if (method === 'PATCH' && /^\/trips\/\d+\/items\/\d+$/.test(p)) {
    const itemId = idsIn(p)[1]
    const item = db.items.find((i) => i.itemId === itemId)
    if (!item) return undefined
    Object.assign(item, b)          // 상태에 반영한다
    return delay({ ...item })
  }

  // ── 사진 ───────────────────────────────────────────────
  if (method === 'GET' && /^\/trips\/\d+\/photos$/.test(p)) return delay({ photos: fx.PHOTOS })

  // ── 인식 결과 ──────────────────────────────────────────
  if (method === 'GET' && /^\/trips\/\d+\/detections$/.test(p)) {
    return delay({ detections: db.detections })
  }
  if (method === 'PATCH' && /^\/trips\/\d+\/detections\/\d+$/.test(p)) {
    const detectionId = idsIn(p)[1]
    const d = db.detections.find((x) => x.detectionId === detectionId)
    if (!d) return undefined

    if (b.approved !== undefined) d.approved = Boolean(b.approved)
    if (b.name !== undefined) d.name = String(b.name)
    if (b.qty !== undefined) d.qty = Number(b.qty)

    // 06 연결 수정 규약 — **전체 교체**다.
    //   [8]    → 연결을 [8] 하나로 교체
    //   []     → 연결을 모두 해제
    //   미전송 → 연결을 건드리지 않는다
    if (Array.isArray(b.matchedItemIds)) {
      db.links.set(detectionId, (b.matchedItemIds as number[]).slice())
    }
    return delay({ ...d, linkedItems: linkedItems(detectionId) })
  }

  // ── 검수 결과 ──────────────────────────────────────────
  if (method === 'GET' && /^\/trips\/\d+\/inspection$/.test(p)) {
    // readiness 는 현재 상태에서 다시 계산한다. 승인·완료 처리가 반영돼야 한다.
    const insp = structuredClone(fx.INSPECTION)
    if (insp.readiness) insp.readiness.completionRate = completionRate()
    return delay(insp)
  }

  return undefined
}
