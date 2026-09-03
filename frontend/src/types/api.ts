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
  /** 보이지 않는 속성. `null` 이 아니면 S-04 「확인 필요」 묶음에 넣는다. */
  missingInfo?: string | null
  labelText?: string | null
  /** `PATCH /detections/{id}` 응답에만 온다. `GET /detections` 목록에는 없다. */
  linkedItems?: { itemId: number; name: string; confirmedByUser: boolean }[]
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
/** 06: `ROOM`(여유) · `NEAR`(근접) · `OVER_RISK`(초과 가능성) · `UNKNOWN`(정보 부족) */
export type WeightVerdict = 'ROOM' | 'NEAR' | 'OVER_RISK' | 'UNKNOWN'
export type RuleVerdict =
  | 'CABIN_OK' | 'CHECKED_OK' | 'CHECKED_FORBIDDEN'
  | 'RESTRICTED' | 'NEED_MORE_INFO' | 'ASK_AIRLINE'

export interface ReadyItem {
  itemId: number
  name: string
  qty: number
}

export interface NeedsCheckItem extends ReadyItem {
  /** 유사 후보. 사용자가 어느 것인지 고른다. */
  candidates: { detectionId: number; name: string; matchConfidence: number }[]
}

export interface NotInPhotoItem {
  itemId: number
  name: string
  priority: Priority
}

/** 사진에는 있는데 체크리스트에 없던 승인 물품. */
export interface ExtraItem {
  detectionId: number
  name: string
  confidence: number
  verdict?: RuleVerdict
  missingInfo?: string | null
}

export interface Inspection {
  tripId: number
  /** 아직 계산 전이면 `null`. 프런트는 그 영역만 로딩으로 그린다. */
  readiness: {
    prepared: ReadyItem[]
    needsCheck: NeedsCheckItem[]
    /** **`missing` 이 아니다.** 사진에서 못 찾았을 뿐 없다는 뜻이 아니다. */
    notInPhoto: NotInPhotoItem[]
    extra: ExtraItem[]
    completionRate: number
  } | null
  weight: {
    /** 단일 값이 아니라 **범위**다. 실측값처럼 표현하지 않는다. */
    minG: number
    typicalG: number
    maxG: number
    limitG: number
    verdict: WeightVerdict
    confidence: ConfidenceLevel
    confidenceReason: string
    /** 계산에서 뺀 항목 수를 숨기지 않는다. */
    excludedCount: number
    contributions: { name: string; typicalG: number; qty: number; subtotalG: number }[]
  } | null
  customs: {
    itemId: number
    name: string
    verdict: RuleVerdict
    /** 판정을 단정하지 않고 무엇이 부족한지 알려준다. */
    missingInfo?: string | null
    reason: string
    /** 규정 최신성 — 출처와 확인 날짜를 항상 함께 보여준다. */
    sourceUrl?: string
    checkedAt?: string
  }[] | null
  /** 책임 범위 고지. 화면에 반드시 넣는다. */
  notice?: string
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
