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
  AiJob, AiJobCreated, BagCheckOutput, ChecklistItem, Detection, Inspection,
  JobType, PackingListOutput, RuleCheckOutput, TripDetail, TripPhoto, TripSummary,
  WeightEstimateOutput,
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
/** 06 의 `GET /items` 예시 그대로. photoStatus 는 checkStatus 와 별개 축이다. */
export const ITEMS: ChecklistItem[] = [
    {
      itemId: 2,
      name: "상의",
      category: "CLOTHING",
      qty: 4,
      priority: "RECOMMENDED",
      source: "PHOTO",
      checkStatus: "PREPARED",
      photoStatus: "CONFIRMED"
    },
    {
      itemId: 3,
      name: "하의",
      category: "CLOTHING",
      qty: 2,
      priority: "RECOMMENDED",
      source: "PHOTO",
      checkStatus: "PREPARED",
      photoStatus: "CONFIRMED"
    },
    {
      itemId: 4,
      name: "속옷",
      category: "CLOTHING",
      qty: 4,
      priority: "RECOMMENDED",
      source: "PHOTO",
      checkStatus: "PREPARED",
      photoStatus: "CONFIRMED"
    },
    {
      itemId: 5,
      name: "충전기",
      category: "ELECTRONIC",
      qty: 1,
      priority: "RECOMMENDED",
      source: "PHOTO",
      checkStatus: "PREPARED",
      photoStatus: "CONFIRMED"
    },
    {
      itemId: 6,
      name: "보조배터리",
      category: "ELECTRONIC",
      qty: 1,
      priority: "RECOMMENDED",
      source: "PHOTO",
      checkStatus: "PREPARED",
      photoStatus: "CONFIRMED"
    },
    {
      itemId: 11,
      name: "가위",
      category: "ETC",
      qty: 1,
      priority: "RECOMMENDED",
      source: "PHOTO",
      checkStatus: "PREPARED",
      photoStatus: "CONFIRMED"
    },
    {
      itemId: 8,
      name: "화장품 용기",
      category: "TOILETRY",
      qty: 1,
      photoStatus: "CONFIRMED",
      priority: "RECOMMENDED",
      source: "PHOTO",
      checkStatus: "PREPARED"
    },
    {
      itemId: 9,
      name: "검정 파우치",
      category: "ETC",
      qty: 1,
      photoStatus: "NEEDS_CHECK",
      priority: "RECOMMENDED",
      source: "PHOTO",
      checkStatus: "PREPARED"
    },
    {
      itemId: 7,
      name: "변환 플러그",
      category: "ELECTRONIC",
      qty: 1,
      priority: "REQUIRED",
      source: "AI",
      checkStatus: "UNCHECKED",
      photoStatus: "NOT_IN_PHOTO"
    }
  ]

/** 06 이 items 응답에 함께 주는 값들. */
export const ITEMS_META = {
  completionRate: 0.889,
  recommendationJobId: 1041,
  unacceptedRequiredCount: 1,
}

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
    // 06:541·591 — 내 목록을 완료 여부로만 나눈다. Mock 이 여행 상태로 덮어쓰므로
    // 여기는 모양을 보여주는 예시다.
    prepared: [
      { itemId: 5, name: '충전기', qty: 1, photoStatus: 'CONFIRMED' },
      { itemId: 8, name: '화장품 용기', qty: 1, photoStatus: 'CONFIRMED' },
      { itemId: 9, name: '검정 파우치', qty: 1, photoStatus: 'NEEDS_CHECK' },
    ],
    unprepared: [
      { itemId: 7, name: '변환 플러그', qty: 1, photoStatus: 'NOT_IN_PHOTO' },
    ],
    completionRate: 0.889,
    unacceptedRequiredCount: 1,
  },
  weight: {
    // 06:603-610 예시 그대로. 랜딩(4.6—5.5—7.0)과 같은 값이어야 한다.
    minG: 4610, typicalG: 5480, maxG: 7010,
    limitG: 10000,
    verdict: 'ROOM',
    confidence: 'MEDIUM',
    // 승인 게이트가 폐기됐으므로 "승인 전" 이라는 사유가 남아 있으면 안 된다
    confidenceReason: '자동 등록 8개 중 6개의 무게를 계산했습니다. 미완료 1개와 무게 정보가 없는 2개는 제외했습니다.',
    excludedCount: 3,
    contributions: [
      { name: '상의', typicalG: 200, qty: 4, subtotalG: 800 },
      { name: '하의', typicalG: 400, qty: 2, subtotalG: 800 },
      { name: '보조배터리', typicalG: 280, qty: 1, subtotalG: 280 },
      { name: '속옷', typicalG: 60, qty: 4, subtotalG: 240 },
      { name: '충전기', typicalG: 90, qty: 1, subtotalG: 90 },
      { name: '가위', typicalG: 70, qty: 1, subtotalG: 70 },
    ],
  },
  customs: [
    { itemId: 6, name: '보조배터리', verdict: 'NEED_MORE_INFO',
      missingInfo: '배터리 정격(Wh)',
      reason: '보조배터리는 위탁수하물로 부칠 수 없고, 기내 반입은 정격(Wh)에 따라 달라집니다. 라벨의 Wh 를 확인해 주세요.',
      sourceUrl: 'https://www.airport.kr/ap_ko/905/subview.do',
      checkedAt: '2026-09-02' },
    { itemId: 8, name: '화장품 용기', verdict: 'NEED_MORE_INFO',
      missingInfo: '용량(ml)',
      reason: '액체류는 100ml 이하 용기에 담아 1L 지퍼백 하나에 넣어야 기내 반입됩니다.',
      sourceUrl: 'https://www.airport.kr/ap_ko/905/subview.do',
      checkedAt: '2026-09-02' },
  ],
  notice: '사진 분석 결과는 가방 전체를 확인한 것이 아닙니다. 사진에서 확인되지 않은 물건은 직접 확인해 주세요.',
}

// ── AI 작업 (S-04 · S-05 · S-06 · S-09) ───────────────────
/**
 * <b>docs/07-ai-ready.md 의 「예시」 절 output 을 그대로 옮긴 것이다.</b>
 *
 * 06 의 REST 응답 조각을 재사용하면 안 된다. 07 은 <b>모델이 내는 원본</b>이고
 * 06 은 <b>서버가 저장·가공한 뒤</b>라 모양이 일부러 다르다. 예를 들어 07 의
 * `RULE_CHECK.results[]` 는 `ruleId` · `ruleKeyword` · `attributes` 까지 담는데,
 * 06 의 `inspection.customs[]` 에는 그 셋이 없다 — 서버가 흡수한 뒤이기 때문이다.
 *
 * 07 의 출력 Schema 는 `additionalProperties: false` 다. 필드를 하나라도 더하면
 * 백엔드 Mock 이 07 대로 응답하는 날 화면이 깨진다.
 */
export const AI_OUTPUT: {
  PACKING_LIST: PackingListOutput
  BAG_CHECK: BagCheckOutput
  WEIGHT_ESTIMATE: WeightEstimateOutput
  RULE_CHECK: RuleCheckOutput
} = {
  PACKING_LIST: {
    items: [
      {
        name: "변환 플러그",
        category: "ELECTRONIC",
        qty: 1,
        priority: "REQUIRED",
        reason: "여행지에서 충전기를 연결할 어댑터를 확인하세요.",
        source: "AI",
        acceptedItemId: null
      },
      {
        name: "상비약",
        category: "MEDICINE",
        qty: 1,
        priority: "RECOMMENDED",
        reason: "평소 사용하는 약이 있다면 여행 기간에 맞게 준비하세요.",
        source: "AI",
        acceptedItemId: null
      },
      {
        name: "화장품",
        category: "TOILETRY",
        qty: 1,
        priority: "RECOMMENDED",
        reason: "숙소 제공 여부에 따라 개인 세면용품을 검토하세요.",
        source: "AI",
        acceptedItemId: null
      },
      {
        name: "우산",
        category: "ETC",
        qty: 1,
        priority: "RECOMMENDED",
        reason: "여행 중 강수에 대비할 휴대용 우산을 검토하세요.",
        source: "AI",
        acceptedItemId: null
      },
      {
        name: "여권",
        category: "DOCUMENT",
        qty: 1,
        priority: "REQUIRED",
        reason: "해외 여행 출국 전 여권 준비 여부를 확인하세요.",
        source: "RULE",
        acceptedItemId: null
      }
    ],
    tips: [
      "일본 콘센트는 A타입, 100V입니다.",
      "10월 초 도쿄 계절 평균은 낮 24도, 아침 16도입니다. 실시간 예보가 아닙니다.",
      "디즈니랜드는 하루 2만 보 이상 걷습니다."
    ],
    weatherSource: "SEASONAL",
    weatherAsOf: "2026-09-03"
  },

  BAG_CHECK: {
    detections: [
      {
        photoId: 1,
        name: "충전기",
        qty: 1,
        confidence: 0.93,
        confidenceLevel: "HIGH",
        missingInfo: null,
        labelText: null
      },
      {
        photoId: 1,
        name: "보조배터리",
        qty: 1,
        confidence: 0.88,
        confidenceLevel: "HIGH",
        missingInfo: "배터리 정격(Wh)",
        labelText: null
      },
      {
        photoId: 1,
        name: "상의",
        qty: 4,
        confidence: 0.81,
        confidenceLevel: "HIGH",
        missingInfo: null,
        labelText: null
      },
      {
        photoId: 1,
        name: "하의",
        qty: 2,
        confidence: 0.79,
        confidenceLevel: "MEDIUM",
        missingInfo: null,
        labelText: null
      },
      {
        photoId: 1,
        name: "속옷",
        qty: 4,
        confidence: 0.72,
        confidenceLevel: "MEDIUM",
        missingInfo: null,
        labelText: null
      },
      {
        photoId: 2,
        name: "화장품 용기",
        qty: 1,
        confidence: 0.64,
        confidenceLevel: "MEDIUM",
        missingInfo: "용량(ml)",
        labelText: null
      },
      {
        photoId: 2,
        name: "가위",
        qty: 1,
        confidence: 0.91,
        confidenceLevel: "HIGH",
        missingInfo: "날 길이(cm)",
        labelText: null
      },
      {
        photoId: 2,
        name: "검정 파우치",
        qty: 1,
        confidence: 0.43,
        confidenceLevel: "LOW",
        missingInfo: null,
        labelText: null
      }
    ],
    failedPhotoIds: []
  },

  WEIGHT_ESTIMATE: {
    minG: 4610,
    typicalG: 5480,
    maxG: 7010,
    limitG: 10000,
    bagEmptyG: 3200,
    verdict: "ROOM",
    confidence: "MEDIUM",
    confidenceReason: "준비 완료 6개를 계산했습니다. 미완료 1개와 미승인 인식 후보 2개는 제외했습니다.",
    excludedCount: 3,
    excluded: [
      {
        name: "변환 플러그",
        reason: "UNCHECKED"
      },
      {
        name: "화장품 용기",
        reason: "NO_WEIGHT_INFO"
      },
      {
        name: "검정 파우치",
        reason: "NO_WEIGHT_INFO"
      }
    ],
    contributions: [
      {
        name: "상의",
        minG: 120,
        typicalG: 200,
        maxG: 350,
        qty: 4,
        subtotalG: 800
      },
      {
        name: "하의",
        minG: 250,
        typicalG: 400,
        maxG: 650,
        qty: 2,
        subtotalG: 800
      },
      {
        name: "보조배터리",
        minG: 180,
        typicalG: 280,
        maxG: 450,
        qty: 1,
        subtotalG: 280
      },
      {
        name: "속옷",
        minG: 40,
        typicalG: 60,
        maxG: 90,
        qty: 4,
        subtotalG: 240
      },
      {
        name: "충전기",
        minG: 50,
        typicalG: 90,
        maxG: 180,
        qty: 1,
        subtotalG: 90
      },
      {
        name: "가위",
        minG: 40,
        typicalG: 70,
        maxG: 120,
        qty: 1,
        subtotalG: 70
      }
    ]
  },

  RULE_CHECK: {
    results: [
      {
        itemId: 6,
        detectionId: null,
        name: "보조배터리",
        qty: 1,
        ruleKeyword: "보조배터리",
        attributes: {
          capacityMl: null,
          batteryWh: null,
          batteryMah: null,
          bladeCm: null
        },
        verdict: "NEED_MORE_INFO",
        ruleId: 1,
        conditionNote: "100Wh 이하",
        reason: "보조배터리는 위탁수하물로 부칠 수 없고, 기내 반입은 정격(Wh)에 따라 달라집니다. 라벨의 Wh 를 확인해 주세요.",
        missingInfo: "배터리 정격(Wh)",
        sourceUrl: "https://www.airport.kr/ap_ko/905/subview.do",
        checkedAt: "2026-09-02"
      },
      {
        itemId: 11,
        detectionId: 7,
        name: "가위",
        qty: 1,
        ruleKeyword: "가위",
        attributes: {
          capacityMl: null,
          batteryWh: null,
          batteryMah: null,
          bladeCm: null
        },
        verdict: "NEED_MORE_INFO",
        ruleId: 6,
        conditionNote: "날 길이 6cm 초과",
        reason: "날 길이를 확인해야 반입 조건을 비교할 수 있습니다. 라벨이나 실측 길이를 확인해 주세요.",
        missingInfo: "날 길이(cm)",
        sourceUrl: "https://www.airport.kr/ap_ko/907/subview.do",
        checkedAt: "2026-09-02"
      }
    ],
    answer: null,
    followUpQuestion: null
  },
}

/**
 * 챗봇 호출일 때의 <b>RULE_CHECK 출력</b>.
 *
 * `AI_OUTPUT.RULE_CHECK` 은 07 의 <b>예시 1(물품 목록 호출)</b>이라 `answer` 와
 * `followUpQuestion` 이 null 이다. 07:1733 이 <i>"question 이 있으면 answer 는
 * string"</i> 이라고 못박았으므로 챗봇에 그것을 돌려주면 계약 위반이다.
 *
 * 이 값은 07 의 <b>예시 2 — S-09 챗봇 · 여행 없이 질문</b> 출력 그대로다.
 * 질문에서 뽑은 물품이라 `itemId`·`detectionId` 가 null 이다(07:1888).
 */
export const RULE_CHECK_CHAT: RuleCheckOutput = {
  results: [
    {
      itemId: null,
      detectionId: null,
      name: "보조배터리",
      qty: 1,
      ruleKeyword: "보조배터리",
      attributes: {
        capacityMl: null,
        batteryWh: null,
        batteryMah: 20000,
        bladeCm: null
      },
      verdict: "NEED_MORE_INFO",
      ruleId: 1,
      conditionNote: "100Wh 이하",
      reason: "mAh만으로 정격 Wh를 확정하지 않습니다. 라벨의 Wh를 확인해 주세요.",
      missingInfo: "배터리 정격(Wh)",
      sourceUrl: "https://www.airport.kr/ap_ko/905/subview.do",
      checkedAt: "2026-09-02"
    }
  ],
  answer: "배터리 정격 Wh가 없어 반입 조건을 아직 판단할 수 없습니다. 라벨을 확인해 주세요. 최종 반입 여부는 출발 당일 항공사와 보안검색기관의 판단을 따릅니다.",
  followUpQuestion: "배터리 라벨에 표시된 정격 Wh는 얼마인가요?"
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
