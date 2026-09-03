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
export type Transport = 'FLIGHT' | 'TRAIN' | 'BUS' | 'CAR'
export type Purpose = 'TOUR' | 'BUSINESS' | 'REST' | 'STUDY'
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

/**
 * 사진 확인 상태. **`checkStatus` 와 별개다.**
 * 실제로 챙겼는지(`checkStatus`)와 사진에서 확인됐는지(`photoStatus`)는 다른 축이다.
 */
export type PhotoStatus = 'CONFIRMED' | 'NEEDS_CHECK' | 'NOT_IN_PHOTO'

export interface ChecklistItem {
  itemId: number
  name: string
  category: Category
  qty: number
  priority: Priority
  /** 최초 등록 경로. PHOTO=사진 승인, AI·RULE=후보 채택, USER=직접 추가 */
  source: ItemSource
  /** `PREPARED` 만 실제 완료다. 나머지는 미완료. */
  checkStatus: CheckStatus
  photoStatus: PhotoStatus
}

/** `GET /api/trips/{tripId}/items` 응답. */
export interface ItemsResponse {
  items: ChecklistItem[]
  /** 내 목록의 준비율. 화면에는 백분율로 반올림해 보여준다(0.857 → 86%). */
  completionRate: number
  /** 이 여행의 최신 완료된 PACKING_LIST 작업. 추천 후보를 여기서 읽는다. */
  recommendationJobId: number | null
  /** 아직 채택하지 않은 필수 후보 수. `null` 이면 "필수 추천 확인 전". */
  unacceptedRequiredCount: number | null
}

/** `POST /api/trips/{tripId}/items` 요청. 추천 채택이면 `recommendation` 을 붙인다. */
export interface ItemCreate {
  name: string
  category: Category
  qty: number
  priority: Priority
  /** `candidateIndex` 는 완료된 추천 `output.items` 의 0부터 시작하는 위치다. */
  recommendation?: { jobId: number; candidateIndex: number }
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

/**
 * 검수 결과의 내 목록 항목.
 *
 * <b>06:1018 — `prepared` 와 `unprepared` 는 내 목록을 완료 여부로 나눈다.</b>
 * 예전에는 needsCheck·notInPhoto·extra 로 넷이었는데, 승인 게이트가 폐기되면서
 * 두 묶음으로 정리됐다. 백엔드(InspectionDtos.Readiness)도 이 모양이다.
 *
 * `photoStatus` 는 준비 완료와 <b>독립된 축</b>이다 — 신뢰도가 낮아
 * `NEEDS_CHECK` 여도 자동 등록된 PREPARED 면 완료로 센다(06:1019).
 */
export interface ReadyItem {
  itemId: number
  name: string
  qty: number
  photoStatus: PhotoStatus
}

export interface Inspection {
  tripId: number
  /** 아직 계산 전이면 `null`. 프런트는 그 영역만 로딩으로 그린다. */
  readiness: {
    prepared: ReadyItem[]
    /** **`missing` 이 아니다.** 아직 안 챙겼을 뿐이고 사진 상태는 따로 있다. */
    unprepared: ReadyItem[]
    completionRate: number
    /** 아직 채택하지 않은 필수 추천 수. 추천 전이면 `null` — "확인 전" 으로 쓴다. */
    unacceptedRequiredCount: number | null
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

// ── AI 출력 (docs/07-ai-ready.md) ─────────────────────────
/**
 * **06 의 REST 응답과 모양이 다르다.** 07 은 모델이 내는 원본이고 06 은 서버가
 * 저장·가공한 뒤다. 섞어 쓰면 백엔드 Mock 이 07 대로 응답하는 날 화면이 깨진다.
 *
 * 07 의 출력 Schema 는 `additionalProperties: false` 다. 필드를 더하지 않는다.
 */

/** AI-02 `PACKING_LIST` — 부족한 준비물만 돌려준다. */
export interface PackingListOutput {
  /** **후보다.** 사용자가 채택해야 내 목록에 들어간다. 생성만으로 목록이 바뀌지 않는다. */
  items: {
    name: string
    category: Category
    qty: number
    priority: Priority
    reason?: string
    source?: 'AI' | 'RULE'
    /** 채택되면 서버가 여기에 항목 id 를 넣는다. `null` 이면 아직 후보다. */
    acceptedItemId?: number | null
  }[]
  tips: string[]
  /** 예보 범위(16일) 안이면 FORECAST, 넘으면 계절 평균. */
  weatherSource: 'FORECAST' | 'SEASONAL'
  /** 날씨 데이터 시점. 대체 기준을 썼을 때 사용자에게 밝힌다. */
  weatherAsOf: string | null
}

/** AI-01 `BAG_CHECK` — 사진 속 물품 인식. */
export interface BagCheckOutput {
  /** `detectionId` 는 없다. 서버가 저장하면서 붙인다. */
  detections: {
    photoId: number
    name: string
    qty: number
    confidence: number
    confidenceLevel: ConfidenceLevel
    missingInfo: string | null
    labelText: string | null
  }[]
  /** 분석에 실패한 사진. 성공한 것만 보여주고 이건 재시도 대상이다. */
  failedPhotoIds: number[]
}

/** AI-03 `WEIGHT_ESTIMATE` — 무게 범위. 단일 값이 아니다. */
export interface WeightEstimateOutput {
  minG: number
  typicalG: number
  maxG: number
  limitG: number
  bagEmptyG: number
  verdict: WeightVerdict
  confidence: ConfidenceLevel
  confidenceReason: string
  excludedCount: number
  /** 계산에서 뺀 항목과 이유. 숨기지 않는다. */
  excluded: { name: string; reason: string }[]
  contributions: {
    name: string
    minG: number
    typicalG: number
    maxG: number
    qty: number
    subtotalG: number
  }[]
}

/** AI-04 `RULE_CHECK` — 물품 구조화와 판정 설명. 최종 판정은 규칙 엔진이 한다. */
export interface RuleCheckOutput {
  results: {
    /** 체크리스트에서 온 것이면 값, 챗봇 질문이면 null. */
    itemId: number | null
    /** 사진 인식에서 온 것이면 값. */
    detectionId: number | null
    name: string
    qty: number
    ruleKeyword: string | null
    /** 용량·Wh·날 길이 등 판정에 쓴 속성. 물품마다 다르다. */
    attributes: Record<string, unknown>
    verdict: RuleVerdict
    ruleId: number | null
    conditionNote: string | null
    reason: string
    /** 판정에 부족한 정보. 채우면 확정 판정이 가능해진다. */
    missingInfo: string | null
    /** 규정 최신성 — 출처와 확인 날짜를 항상 함께. */
    sourceUrl: string | null
    checkedAt: string | null
  }[]
  /** 챗봇(S-09)이 말풍선에 넣는 문장. S-06 검수 경로에서는 null 이다. */
  answer: string | null
  /** 정보가 부족할 때 되묻는 질문. 한 번에 하나씩. 없으면 null. */
  followUpQuestion: string | null
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

// ── 인증 ──────────────────────────────────────────────────
//
// 06-api-spec.md "회원가입·로그인 계약 (UC-01)" 을 그대로 옮긴 것이다.
//
// <b>토큰을 저장하지 않는다.</b> 인증은 서버 세션 + HttpOnly 쿠키다. JS 가
// 쿠키를 읽을 수 없으므로 localStorage 에 보관할 것도 없고, 요청마다
// `credentials: 'include'` 로 브라우저가 알아서 붙인다.
//
// <b>쿠키가 있다고 로그인한 것이 아니다.</b> CSRF 토큰을 주려고 로그인 전에도
// 익명 세션 쿠키가 생긴다. 반드시 `authenticated` 를 본다.

export interface User {
  userId: number
  loginId: string
  nickname: string
  email: string
}

/** 가입 입력. <b>이 넷만 받는다</b> — 비밀번호 확인 칸을 두지 않는다. */
export interface SignupRequest {
  nickname: string
  loginId: string
  password: string
  email: string
}

export interface LoginRequest {
  loginId: string
  password: string
}

/** 가입·로그인 응답. 가입은 `201`, 로그인은 `200` + 세션 쿠키다. */
export interface AuthUserResponse {
  user: User
}

/**
 * `GET /api/auth/session` — 앱 진입과 로그인·로그아웃 성공 후 부른다.
 * 미인증도 `200` 이고 상태만 돌려준다. `csrfToken` 은 항상 문자열이다.
 */
export interface SessionResponse {
  authenticated: boolean
  user: User | null
  csrfToken: string
}

/** 06 의 입력 규칙. 서버가 최종 판정하지만 화면에서 먼저 걸러 왕복을 줄인다. */
export const LOGIN_ID_RE = /^[a-z0-9_]{4,30}$/
export const PASSWORD_MIN = 8
/** BCrypt 가 잘리는 지점. 06:190 */
export const PASSWORD_MAX_BYTES = 72
