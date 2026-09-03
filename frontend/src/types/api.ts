/**
 * docs/06-api-spec.md · docs/07-ai-ready.md 의 계약을 옮긴 것.
 *
 * **여기가 FE 와 BE 의 접점이다.** 06 을 고치면 여기도 고친다.
 * 값 목록은 database/schema.sql 의 CHECK 제약과 같아야 한다 — 어긋나면
 * 백엔드가 저장을 거부한다.
 */

// ── 공통 ──────────────────────────────────────────────────
/** 06 "오류 응답 형식" — 모든 오류가 같은 모양이다. */
export interface ApiError {
  error: { code: string; message: string; field?: string }
}

// ── 여행 (S-01 · S-02) ────────────────────────────────────
export type TripStatus = 'DRAFT' | 'CONFIRMED' | 'DONE'
export type Transport = 'FLIGHT' | 'TRAIN' | 'BUS' | 'CAR' | 'SHIP'
export type Purpose = 'TOUR' | 'BUSINESS' | 'STUDY' | 'ETC'
export type BagType = 'CARRY_ON' | 'MEDIUM' | 'LARGE'

export interface TripSummary {
  tripId: number
  origin: string
  destination: string
  startDate: string
  endDate: string
  transport: Transport
  status: TripStatus
  completionRate: number
}

export interface TripDetail extends TripSummary {
  countryCode?: string
  purpose?: Purpose
  airline?: string
  departureAirport?: string
  arrivalAirport?: string
  bagType?: BagType
  bagEmptyG?: number
  weightLimitG?: number
  note?: string
}

// ── 체크리스트 (S-05) ─────────────────────────────────────
export type Category =
  | 'DOCUMENT' | 'CLOTHING' | 'ELECTRONIC' | 'TOILETRY' | 'MEDICINE' | 'ETC'
export type Priority = 'REQUIRED' | 'RECOMMENDED'
/** PHOTO = 사진에서 확인 · AI = 추천 · RULE = 고정 필수 · USER = 직접 추가 */
export type ItemSource = 'RULE' | 'PHOTO' | 'AI' | 'USER'
/** `MISSING` 은 없다. 사진에서 못 찾은 것을 누락으로 단정하지 않는다. */
export type CheckStatus = 'UNCHECKED' | 'PREPARED' | 'NEEDS_CHECK' | 'NOT_IN_PHOTO'

export interface ChecklistItem {
  itemId: number
  name: string
  category: Category
  qty: number
  priority: Priority
  source: ItemSource
  checkStatus: CheckStatus
}

// ── 사진 · 인식 (S-03 · S-04) ─────────────────────────────
export type BagKind = 'CABIN' | 'CHECKED'
export type ConfidenceLevel = 'HIGH' | 'MEDIUM' | 'LOW'

export interface TripPhoto {
  photoId: number
  fileUrl: string
  bagKind: BagKind
}

export interface Detection {
  detectionId: number
  photoId: number
  name: string
  qty: number
  confidence: number
  confidenceLevel: ConfidenceLevel
  approved: boolean
  missingInfo?: string
  labelText?: string
  linkedItems: { itemId: number; name: string; confirmedByUser: boolean }[]
}

/** 06 PATCH /detections — `matchedItemIds` 는 **전체 교체**다. 증분이 아니다. */
export interface DetectionPatch {
  approved?: boolean
  name?: string
  qty?: number
  /** `[]` 는 연결 해제. 필드를 빼면 연결을 건드리지 않는다. */
  matchedItemIds?: number[]
}

// ── 검수 결과 (S-06) ──────────────────────────────────────
export type WeightVerdict = 'ROOM' | 'NEAR' | 'OVER'
export type RuleVerdict =
  | 'CABIN_OK' | 'CHECKED_OK' | 'CHECKED_FORBIDDEN'
  | 'RESTRICTED' | 'NEED_MORE_INFO' | 'ASK_AIRLINE'

export interface Inspection {
  tripId: number
  readiness: {
    prepared: ChecklistItem[]
    needsCheck: ChecklistItem[]
    notInPhoto: ChecklistItem[]
    extra: { detectionId: number; name: string; qty: number }[]
    completionRate: number
  } | null
  weight: {
    minG: number
    typicalG: number
    maxG: number
    limitG: number
    verdict: WeightVerdict
    confidence: ConfidenceLevel
    confidenceReason: string
    excludedCount: number
    contributions: { name: string; typicalG: number; qty: number; subtotalG: number }[]
  } | null
  customs: {
    itemId: number
    name: string
    verdict: RuleVerdict
    reason: string
    source?: string
    checkedAt?: string
  }[] | null
}

// ── AI 작업 (S-04 · S-05 · S-06 · S-09) ───────────────────
export type JobType = 'PACKING_LIST' | 'BAG_CHECK' | 'WEIGHT_ESTIMATE' | 'RULE_CHECK'
export type JobStatus = 'PENDING' | 'COMPLETED' | 'FAILED'

export interface AiJobCreated {
  jobId: number
  jobType: JobType
  status: JobStatus
  createdAt: string
  /** 다음 폴링까지 기다릴 밀리초. 실제 AI 를 붙이면 이 값만 늘어난다. */
  pollAfterMs: number
}

export interface AiJob<T = unknown> {
  jobId: number
  jobType: JobType
  status: JobStatus
  output: T | null
  modelName?: string
  errorMessage?: string
  createdAt: string
  completedAt: string | null
  pollAfterMs?: number
}

// ── 반입 규정 (S-08) ──────────────────────────────────────
export interface TransportRule {
  ruleId: number
  keyword: string
  verdict: RuleVerdict
  reason: string
  source: string
  checkedAt: string
}
