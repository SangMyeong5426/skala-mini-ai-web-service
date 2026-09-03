/**
 * Mock 응답. **docs/06-api-spec.md 의 예시를 그대로 옮긴 것이다.**
 *
 * <b>지어내지 않는다.</b> 손으로 만든 값을 쓰면 백엔드가 붙는 순간 필드명이
 * 어긋나 화면을 다시 고치게 된다. 06 을 고치면 여기도 같은 PR 에서 고친다.
 *
 * 백엔드가 붙으면 `VITE_USE_MOCK` 을 끄고, 데모 전에 이 파일을 지운다.
 *
 * 시드(`database/seed.sql`)의 도쿄 3박4일과 같은 시나리오다. 다만 06 예시가
 * `tripId: 12` 를 쓰므로 그 값도 함께 받아 준다.
 */
import type {
  AiJob, AiJobCreated, ChecklistItem, Detection, Inspection,
  JobType, TripDetail, TripPhoto, TripSummary,
} from '../types/api'

// ── 여행 (S-01) ───────────────────────────────────────────
export const TRIPS: TripSummary[] = [
  { tripId: 1, origin: '서울', destination: '도쿄', startDate: '2026-10-01',
    endDate: '2026-10-04', transport: 'FLIGHT', status: 'CONFIRMED', completionRate: 0.5 },
  { tripId: 2, origin: '서울', destination: '오사카', startDate: '2026-05-02',
    endDate: '2026-05-04', transport: 'FLIGHT', status: 'DONE', completionRate: 1.0 },
  { tripId: 3, origin: '서울', destination: '부산', startDate: '2026-03-14',
    endDate: '2026-03-15', transport: 'TRAIN', status: 'DONE', completionRate: 1.0 },
]

export const TRIP_DETAIL: TripDetail = {
  ...TRIPS[0],
  countryCode: 'JP',
  purpose: 'TOUR',
  airline: '대한항공',
  departureAirport: 'ICN',
  arrivalAirport: 'NRT',
  bagType: 'CARRY_ON',
  bagEmptyG: 3200,
  weightLimitG: 10000,
  note: '친구 2명, 디즈니랜드, 사진 많이 찍을 예정',
}

// ── 체크리스트 (S-05) — 06 예시 + 시드로 보강 ─────────────
export const ITEMS: ChecklistItem[] = [
  { itemId: 1, name: '여권', category: 'DOCUMENT', qty: 1, priority: 'REQUIRED', source: 'RULE', checkStatus: 'NOT_IN_PHOTO' },
  { itemId: 2, name: '상의', category: 'CLOTHING', qty: 4, priority: 'REQUIRED', source: 'PHOTO', checkStatus: 'PREPARED' },
  { itemId: 3, name: '하의', category: 'CLOTHING', qty: 2, priority: 'REQUIRED', source: 'PHOTO', checkStatus: 'PREPARED' },
  { itemId: 4, name: '속옷', category: 'CLOTHING', qty: 4, priority: 'REQUIRED', source: 'PHOTO', checkStatus: 'PREPARED' },
  { itemId: 5, name: '충전기', category: 'ELECTRONIC', qty: 1, priority: 'REQUIRED', source: 'PHOTO', checkStatus: 'PREPARED' },
  { itemId: 6, name: '보조배터리', category: 'ELECTRONIC', qty: 1, priority: 'REQUIRED', source: 'PHOTO', checkStatus: 'PREPARED' },
  { itemId: 7, name: '변환 플러그', category: 'ELECTRONIC', qty: 1, priority: 'REQUIRED', source: 'AI', checkStatus: 'NOT_IN_PHOTO' },
  { itemId: 8, name: '화장품', category: 'TOILETRY', qty: 1, priority: 'RECOMMENDED', source: 'AI', checkStatus: 'NEEDS_CHECK' },
  { itemId: 9, name: '상비약', category: 'MEDICINE', qty: 1, priority: 'RECOMMENDED', source: 'AI', checkStatus: 'NOT_IN_PHOTO' },
  { itemId: 10, name: '우산', category: 'ETC', qty: 1, priority: 'RECOMMENDED', source: 'AI', checkStatus: 'NOT_IN_PHOTO' },
]

// ── 사진 (S-03) ───────────────────────────────────────────
// 06 에 GET /photos 응답 예시가 아직 없다. schema.sql 과 seed.sql 로 만들었다.
// 06 이 채워지면 여기도 맞춘다.
export const PHOTOS: TripPhoto[] = [
  { photoId: 1, fileUrl: '/uploads/demo/bag-01.jpg', bagKind: 'CABIN' },
  { photoId: 2, fileUrl: '/uploads/demo/bag-02.jpg', bagKind: 'CABIN' },
]

// ── 인식 결과 (S-04) — 06 예시 그대로 ─────────────────────
export const DETECTIONS: Detection[] = [
  { detectionId: 2, photoId: 1, name: '보조배터리', qty: 1,
    confidence: 0.880, confidenceLevel: 'HIGH', approved: true,
    missingInfo: '배터리 정격(Wh)', labelText: null },
  { detectionId: 6, photoId: 2, name: '화장품 용기', qty: 1,
    confidence: 0.640, confidenceLevel: 'MEDIUM', approved: false,
    missingInfo: '용량(ml)', labelText: null },
  { detectionId: 8, photoId: 2, name: '검정 파우치', qty: 1,
    confidence: 0.430, confidenceLevel: 'LOW', approved: false,
    missingInfo: null, labelText: null },
]

// ── 검수 결과 (S-06) — 06 예시 그대로 ─────────────────────
export const INSPECTION: Inspection = {
  tripId: 1,
  readiness: {
    prepared: [{ itemId: 5, name: '충전기', qty: 1 }],
    needsCheck: [
      { itemId: 8, name: '화장품', qty: 1,
        candidates: [
          { detectionId: 6, name: '화장품 용기', matchConfidence: 0.71 },
          { detectionId: 8, name: '검정 파우치', matchConfidence: 0.31 },
        ] },
    ],
    notInPhoto: [{ itemId: 1, name: '여권', priority: 'REQUIRED' }],
    extra: [{ detectionId: 7, name: '가위', confidence: 0.91,
              verdict: 'NEED_MORE_INFO', missingInfo: '날 길이(cm)' }],
    completionRate: 0.5,
  },
  weight: {
    minG: 4570, typicalG: 5410, maxG: 6890,
    limitG: 10000,
    verdict: 'ROOM',
    confidence: 'MEDIUM',
    confidenceReason: '사진에서 미확인 4개, 승인 전 1개',
    excludedCount: 5,
    contributions: [
      { name: '상의', typicalG: 200, qty: 4, subtotalG: 800 },
      { name: '하의', typicalG: 400, qty: 2, subtotalG: 800 },
      { name: '보조배터리', typicalG: 280, qty: 1, subtotalG: 280 },
    ],
  },
  customs: [
    { itemId: 6, name: '보조배터리', verdict: 'NEED_MORE_INFO',
      missingInfo: '배터리 정격(Wh)',
      reason: '보조배터리는 위탁수하물로 부칠 수 없고, 기내 반입은 정격(Wh)에 따라 달라집니다. 라벨의 Wh 를 확인해 주세요.',
      sourceUrl: 'https://www.airport.kr/ap_ko/905/subview.do',
      checkedAt: '2026-09-02' },
    { itemId: 8, name: '화장품', verdict: 'NEED_MORE_INFO',
      missingInfo: '용량(ml)',
      reason: '액체류는 100ml 이하 용기에 담아 1L 지퍼백 하나에 넣어야 기내 반입됩니다.',
      sourceUrl: 'https://www.airport.kr/ap_ko/905/subview.do',
      checkedAt: '2026-09-02' },
  ],
  notice: '사진 분석 결과는 가방 전체를 확인한 것이 아닙니다. 사진에서 확인되지 않은 물건은 직접 확인해 주세요.',
}

// ── AI 작업 (S-04 · S-05 · S-06 · S-09) ───────────────────
/** 07-ai-ready.md 의 예시 output. jobType 별로 다르다. */
export const AI_OUTPUT: Record<JobType, unknown> = {
  PACKING_LIST: {
    items: [
      { name: '변환 플러그', category: 'ELECTRONIC', qty: 1, priority: 'REQUIRED',
        reason: '일본 콘센트는 A타입입니다.' },
      { name: '상비약', category: 'MEDICINE', qty: 1, priority: 'RECOMMENDED',
        reason: '해열제·소화제 정도는 챙기는 편이 좋습니다.' },
      { name: '우산', category: 'ETC', qty: 1, priority: 'RECOMMENDED',
        reason: '10월 초 도쿄는 비 예보가 있습니다.' },
    ],
    tips: ['일본 콘센트는 A타입입니다.', '10월 초 도쿄는 낮 24도, 얇은 겉옷을 권합니다.'],
    weatherSource: 'FORECAST',
  },
  BAG_CHECK: { detections: DETECTIONS },
  WEIGHT_ESTIMATE: INSPECTION.weight,
  RULE_CHECK: { customs: INSPECTION.customs },
}

export const AI_JOB_CREATED = (jobType: JobType, jobId: number): AiJobCreated => ({
  jobId, jobType, status: 'PENDING',
  createdAt: new Date().toISOString(),
  pollAfterMs: 400,
})

export const AI_JOB = (jobId: number, jobType: JobType, done: boolean): AiJob => ({
  jobId, jobType,
  status: done ? 'COMPLETED' : 'PENDING',
  output: done ? AI_OUTPUT[jobType] : null,
  modelName: done ? 'mock' : undefined,
  createdAt: new Date().toISOString(),
  completedAt: done ? new Date().toISOString() : null,
  pollAfterMs: 400,
})
