/**
 * 백엔드가 붙기 전에 화면을 만들기 위한 가짜 서버.
 *
 * <b>라이브러리를 설치하지 않는다.</b> MSW·json-server 는 3일 일정에 맞지 않고
 * CLAUDE.md 가 기능을 늘리지 말라고 정해 뒀다. 이 파일 하나가 전부다.
 *
 * <b>여행별로 자원을 나눠 들고 있다.</b> 새 여행을 만들어 시험하다 시드 여행의
 * 상태를 바꾸면 안 된다.
 *
 * <b>상태가 화면 사이에 이어진다.</b> S-04 에서 승인하면 S-05 체크리스트와
 * S-06 검수 결과에 같은 물품이 같은 상태로 보인다. 안 이어지면 승인 흐름
 * 자체를 검증할 수 없다.
 *
 * <b>AI 작업은 즉시 답하지 않는다.</b> PENDING 두 번 뒤 COMPLETED 다.
 * 즉시 답하면 폴링 코드가 한 번도 안 돌아서 백엔드가 붙는 날 처음 실행하게 된다.
 * BAG_CHECK 은 완료 시 결과를 인식 목록에 넣는다 — 07 의 "작업이 끝나면 서버가
 * 쓰는 곳" 대로다. 그래야 S-04 가 승인할 ID 를 조회할 수 있다.
 *
 * <b>Location 헤더는 흉내 내지 않는다.</b> 이 함수는 본문만 돌려주므로
 * `201 + Location` 을 재현할 수 없다. 06 이 Location 을 약속한 세 곳은
 * <b>본문의 id 로 받는다.</b>
 *
 * 새로고침하면 초기화된다 — 페이지가 열린 동안만 유지한다.
 * 끄는 법: frontend/.env 의 VITE_USE_MOCK 를 false 로.
 */
import * as fx from './fixtures'
import type {
  BagCheckOutput, ChecklistItem, Detection, Inspection, JobType, PackingListOutput,
  TripDetail, TripPhoto,
} from '../types/api'

export const USE_MOCK = import.meta.env.VITE_USE_MOCK === 'true'

/** 실제 서버처럼 보이도록 약간 늦춘다. 스켈레톤이 한 번은 보여야 한다. */
const LATENCY_MS = 200

/**
 * 자원이 없다는 뜻. 경로를 아직 안 만들었다는 뜻(`undefined`)과 구분한다.
 *
 * 06 은 `GET /trips/{tripId}` 의 주요 오류로 404 를 적어 뒀다.
 * "없는 여행을 열면 어떻게 보이나" 는 화면이 다뤄야 하는 정상 흐름이지
 * Mock 이 덜 만들어졌다는 뜻이 아니다.
 */
export const NOT_FOUND = Symbol('NOT_FOUND')

// ── 여행별 상태 ───────────────────────────────────────────
interface TripState {
  detail: TripDetail
  items: ChecklistItem[]
  detections: Detection[]
  photos: TripPhoto[]
  /** detectionId → 연결된 itemId 목록. 06 의 matchedItemIds 규약 그대로. */
  links: Map<number, number[]>
}

function emptyTrip(detail: TripDetail): TripState {
  return { detail, items: [], detections: [], photos: [], links: new Map() }
}

const trips = new Map<number, TripState>()

// 1번은 시드(도쿄). 2·3번은 지난 여행이라 하위 자원을 두지 않는다 —
// S-10 여행 기록 상세는 3차라 데모에서 열지 않는다.
trips.set(1, {
  detail: { ...fx.TRIP_DETAIL },
  items: fx.ITEMS.map((i) => ({ ...i })),
  detections: fx.DETECTIONS.map((d) => ({ ...d })),
  photos: fx.PHOTOS.map((p) => ({ ...p })),
  links: new Map([[2, [6]], [6, [8]], [8, [8, 9]]]),
})
for (const t of fx.TRIPS.slice(1)) trips.set(t.tripId, emptyTrip({ ...t }))

let nextTripId = 100
let nextDetectionId = 100
let nextItemId = 100
let nextJobId = 1041

/** 작업 상태. tripId·input 을 들고 있어야 완료 시 어디에 쓸지 안다. */
const jobs = new Map<number, {
  left: number
  jobType: JobType
  tripId?: number
  /** BAG_CHECK 이 어느 사진을 분석했는지. 저장 범위를 여기에 맞춘다. */
  photoIds?: number[]
  /** 완료 결과를 도메인에 한 번만 반영한다. 반복 GET 으로 중복 삽입하지 않는다. */
  applied: boolean
}>()

/**
 * 실제 서버처럼 <b>복사본</b>을 돌려준다.
 *
 * 내부 배열을 그대로 주면 두 가지가 깨진다. 화면이 Mock 상태를 바꿀 수 있고,
 * 앞서 받아 둔 응답이 나중에 조용히 자란다(실제로 겪었다 — 검증 코드가
 * before 로 잡아 둔 배열이 BAG_CHECK 결과까지 품고 있었다).
 */
function delay<T>(value: T): Promise<T> {
  const copy = structuredClone(value)
  return new Promise((r) => setTimeout(() => r(copy), LATENCY_MS))
}

function idsIn(path: string): number[] {
  return (path.match(/\d+/g) ?? []).map(Number)
}

/**
 * 연결·승인이 바뀐 항목의 체크 상태를 <b>그 자리에서</b> 갱신한다.
 *
 * 조회할 때마다 계산하면 사용자가 직접 바꾼 값을 덮어쓴다 —
 * S-05 에서 완료 처리하거나 체크를 해제해도 되돌아간다.
 * 06 의 항목 PATCH 는 보낸 체크 상태를 그대로 바꾸는 계약이다.
 *
 * 그래서 상태는 <b>사건이 일어날 때만</b> 쓴다.
 *   승인된 인식이 연결됨 → PREPARED (사진에서 확인)
 *   연결은 있는데 승인 전 → NEEDS_CHECK
 *   연결이 하나도 없음    → NOT_IN_PHOTO
 * 그 뒤 사용자가 PATCH 로 바꾸면 그 값이 남는다.
 */
function syncStatus(t: TripState, itemIds: number[]) {
  for (const itemId of itemIds) {
    const item = t.items.find((i) => i.itemId === itemId)
    if (!item) continue
    const linked = [...t.links.entries()]
      .filter(([, ids]) => ids.includes(itemId))
      .map(([detectionId]) => detectionId)
    if (linked.length === 0) {
      item.checkStatus = 'NOT_IN_PHOTO'
      continue
    }
    const approved = linked.some(
      (id) => t.detections.find((d) => d.detectionId === id)?.approved,
    )
    item.checkStatus = approved ? 'PREPARED' : 'NEEDS_CHECK'
  }
}

function itemsOf(t: TripState): ChecklistItem[] {
  return t.items.map((i) => ({ ...i }))
}

function completionRate(t: TripState): number {
  if (t.items.length === 0) return 0
  const done = t.items.filter((i) => i.checkStatus === 'PREPARED').length
  return Math.round((done / t.items.length) * 100) / 100
}

function linkedItems(t: TripState, detectionId: number) {
  const approved = t.detections.find((d) => d.detectionId === detectionId)?.approved ?? false
  return (t.links.get(detectionId) ?? []).map((itemId) => ({
    itemId,
    name: t.items.find((i) => i.itemId === itemId)?.name ?? '',
    confirmedByUser: approved,
  }))
}

/**
 * BAG_CHECK 완료 결과를 인식 목록에 반영한다.
 *
 * <b>요청한 사진만</b> 다룬다. `input.photoIds` 에 없는 사진의 결과를 넣으면
 * 사용자가 고르지 않은 사진의 물품이 목록에 생긴다.
 *
 * 재분석이면 그 사진의 <b>미승인 행을 교체</b>한다. 승인 행은 보존한다 —
 * 사용자가 확인한 것을 AI 재실행이 지우면 안 된다.
 */
function applyBagCheck(t: TripState, out: BagCheckOutput, photoIds?: number[]) {
  const target = photoIds?.length ? out.detections.filter((d) => photoIds.includes(d.photoId))
                                  : out.detections
  const scope = new Set(target.map((d) => d.photoId))

  // 대상 사진의 미승인 행과 그 연결을 먼저 걷어낸다.
  const dropped = t.detections.filter((d) => scope.has(d.photoId) && !d.approved)
  const touched = new Set<number>()
  for (const d of dropped) {
    for (const itemId of t.links.get(d.detectionId) ?? []) touched.add(itemId)
    t.links.delete(d.detectionId)
  }
  t.detections = t.detections.filter((d) => !(scope.has(d.photoId) && !d.approved))

  for (const d of target) {
    // 같은 사진에 이미 승인된 같은 이름이 있으면 건드리지 않는다.
    if (t.detections.some((x) => x.photoId === d.photoId && x.name === d.name)) continue
    t.detections.push({
      detectionId: nextDetectionId++,
      photoId: d.photoId,
      name: d.name,
      qty: d.qty,
      confidence: d.confidence,
      confidenceLevel: d.confidenceLevel,
      missingInfo: d.missingInfo,
      labelText: d.labelText,
      approved: false,      // 승인 전이다. 명세 9.2.
    })
  }
  syncStatus(t, [...touched])
}

/**
 * PACKING_LIST 완료 결과를 체크리스트에 넣는다.
 *
 * 07 "작업이 끝나면 서버가 쓰는 곳" — 추천은 `source: AI` · `UNCHECKED` 로
 * 들어가고 S-05 가 GET 으로 다시 읽는다. 같은 이름은 넣지 않는다.
 */
function applyPackingList(t: TripState, out: PackingListOutput) {
  for (const rec of out.items) {
    if (t.items.some((i) => i.name === rec.name)) continue
    t.items.push({
      itemId: nextItemId++,
      name: rec.name,
      category: rec.category,
      qty: rec.qty,
      priority: rec.priority,
      source: 'AI',
      checkStatus: 'UNCHECKED',
    })
  }
}

/**
 * 경로를 보고 응답을 만든다.
 * 다루지 않는 경로는 `undefined` 를 반환하고 호출한 쪽이 404 로 처리한다 —
 * <b>안 만든 것을 조용히 성공시키지 않는다.</b>
 */
export function mockRequest(
  method: string,
  path: string,
  body?: unknown,
): Promise<unknown> | typeof NOT_FOUND | undefined {
  const p = path.split('?')[0]
  const b = (body ?? {}) as Record<string, unknown>

  // ── AI 작업 ────────────────────────────────────────────
  if (method === 'POST' && p === '/ai-jobs') {
    const jobType = b.jobType as JobType
    const jobId = nextJobId++
    const input = (b.input ?? {}) as Record<string, unknown>
    jobs.set(jobId, {
      left: 2,
      jobType,
      tripId: b.tripId as number | undefined,
      photoIds: Array.isArray(input.photoIds) ? (input.photoIds as number[]) : undefined,
      applied: false,
    })
    return delay(fx.AI_JOB_CREATED(jobType, jobId))
  }
  if (method === 'GET' && p.startsWith('/ai-jobs/')) {
    const [jobId] = idsIn(p)
    const job = jobs.get(jobId)
    if (!job) return NOT_FOUND
    if (job.left > 0) {
      job.left -= 1
      return delay(fx.AI_JOB(jobId, job.jobType, false))
    }
    // 07 "작업이 끝나면 서버가 쓰는 곳" — 완료 결과를 도메인에 반영한다.
    if (!job.applied) {
      job.applied = true
      const t = job.tripId ? trips.get(job.tripId) : undefined
      if (t && job.jobType === 'BAG_CHECK') {
        applyBagCheck(t, fx.AI_OUTPUT.BAG_CHECK, job.photoIds)
      }
      if (t && job.jobType === 'PACKING_LIST') {
        applyPackingList(t, fx.AI_OUTPUT.PACKING_LIST)
      }
    }
    return delay(fx.AI_JOB(jobId, job.jobType, true))
  }

  // ── 여행 ───────────────────────────────────────────────
  if (method === 'GET' && p === '/trips') {
    return delay({
      trips: [...trips.values()].map((t) => ({
        ...t.detail, completionRate: completionRate(t),
      })),
    })
  }

  if (method === 'POST' && p === '/trips') {
    const tripId = nextTripId++
    const created: TripDetail = {
      tripId,
      origin: String(b.origin ?? ''),
      destination: String(b.destination ?? ''),
      startDate: String(b.startDate ?? ''),
      endDate: String(b.endDate ?? ''),
      transport: (b.transport ?? 'FLIGHT') as TripDetail['transport'],
      status: 'DRAFT',            // 06: 생성 직후는 DRAFT
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
    // 새 여행은 자원이 비어 있다. 시드 여행 것을 물려주면 안 된다.
    trips.set(tripId, emptyTrip(created))
    return delay({ ...created, createdAt: new Date().toISOString() })
  }

  const tripOf = (): TripState | undefined => trips.get(idsIn(p)[0])

  if (method === 'GET' && /^\/trips\/\d+$/.test(p)) {
    const t = tripOf()
    return t ? delay({ ...t.detail, completionRate: completionRate(t) }) : NOT_FOUND
  }

  // ── 체크리스트 ─────────────────────────────────────────
  if (method === 'GET' && /^\/trips\/\d+\/items$/.test(p)) {
    const t = tripOf()
    if (!t) return NOT_FOUND
    return delay({ items: itemsOf(t), completionRate: completionRate(t) })
  }
  if (method === 'PATCH' && /^\/trips\/\d+\/items\/\d+$/.test(p)) {
    const t = tripOf()
    if (!t) return NOT_FOUND
    const itemId = idsIn(p)[1]
    const item = t.items.find((i) => i.itemId === itemId)   // 이 여행의 항목만
    if (!item) return NOT_FOUND
    // 06: 보낸 체크 상태를 그대로 바꾼다. 조회할 때 다시 덮어쓰지 않는다.
    Object.assign(item, b)
    return delay({ ...item })
  }

  // ── 사진 ───────────────────────────────────────────────
  if (method === 'GET' && /^\/trips\/\d+\/photos$/.test(p)) {
    const t = tripOf()
    return t ? delay({ photos: t.photos }) : NOT_FOUND
  }

  // ── 인식 결과 ──────────────────────────────────────────
  if (method === 'GET' && /^\/trips\/\d+\/detections$/.test(p)) {
    const t = tripOf()
    return t ? delay({ detections: t.detections }) : NOT_FOUND
  }
  if (method === 'PATCH' && /^\/trips\/\d+\/detections\/\d+$/.test(p)) {
    const t = tripOf()
    if (!t) return NOT_FOUND
    const detectionId = idsIn(p)[1]
    const d = t.detections.find((x) => x.detectionId === detectionId)
    if (!d) return NOT_FOUND

    if (b.approved !== undefined) d.approved = Boolean(b.approved)
    if (b.name !== undefined) d.name = String(b.name)
    if (b.qty !== undefined) d.qty = Number(b.qty)

    // 06 연결 수정 규약 — **전체 교체**다.
    //   [8]    → 연결을 [8] 하나로 교체
    //   []     → 연결을 모두 해제
    //   미전송 → 연결을 건드리지 않는다
    const before = t.links.get(detectionId) ?? []
    if (Array.isArray(b.matchedItemIds)) {
      t.links.set(detectionId, (b.matchedItemIds as number[]).slice())
    }
    // 연결이 끊긴 항목과 새로 붙은 항목 양쪽을 갱신한다.
    syncStatus(t, [...new Set([...before, ...(t.links.get(detectionId) ?? [])])])
    return delay({ ...d, linkedItems: linkedItems(t, detectionId) })
  }

  // ── 검수 결과 ──────────────────────────────────────────
  if (method === 'GET' && /^\/trips\/\d+\/inspection$/.test(p)) {
    const t = tripOf()
    if (!t) return NOT_FOUND
    const insp: Inspection = structuredClone(fx.INSPECTION)
    insp.tripId = t.detail.tripId          // 요청한 여행이어야 한다
    const items = itemsOf(t)

    if (insp.readiness) {
      const pick = (st: string) =>
        items.filter((i) => i.checkStatus === st)
          .map((i) => ({ itemId: i.itemId, name: i.name, qty: i.qty }))

      insp.readiness.prepared = pick('PREPARED')
      insp.readiness.needsCheck = items
        .filter((i) => i.checkStatus === 'NEEDS_CHECK')
        .map((i) => ({
          itemId: i.itemId, name: i.name, qty: i.qty,
          candidates: [...t.links.entries()]
            .filter(([, ids]) => ids.includes(i.itemId))
            .map(([detectionId]) => {
              const d = t.detections.find((x) => x.detectionId === detectionId)
              return { detectionId, name: d?.name ?? '', matchConfidence: d?.confidence ?? 0 }
            }),
        }))
      insp.readiness.notInPhoto = items
        .filter((i) => i.checkStatus === 'NOT_IN_PHOTO')
        .map((i) => ({ itemId: i.itemId, name: i.name, priority: i.priority }))
      // 승인됐는데 어느 항목에도 연결되지 않은 인식 물품 = 추가 물품.
      insp.readiness.extra = t.detections
        .filter((d) => d.approved && (t.links.get(d.detectionId) ?? []).length === 0)
        .map((d) => ({ detectionId: d.detectionId, name: d.name, confidence: d.confidence }))
      insp.readiness.completionRate = completionRate(t)
    }
    // weight·customs 는 AI 결과라 fixture 를 그대로 둔다.
    // 고정 무게 계산을 새로 만들라는 요구는 리뷰에도 없었다.
    return delay(insp)
  }

  return undefined
}
