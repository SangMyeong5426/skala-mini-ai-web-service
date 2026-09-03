/**
 * 탐색 구조.
 *
 * docs/03-wireframe.md 의 화면 10개(S-01~S-10)는 <b>설계 사양</b>으로 그대로 두고,
 * 사용자가 오가는 길만 3단계 흐름으로 묶는다.
 *
 *   1 랜딩
 *   2 여행 등록 — 2-1 일자·여행지 / 2-2 사진·분석 / 2-3 체크리스트·추천·반입
 *   3 내 여행 — 과거 이력
 *
 * 2-2 는 S-03(사진)+S-04(인식·승인), 2-3 은 S-05(체크리스트·추천)+S-06(검수)이다.
 * 사용자에게는 한 단계지만 안에서 두 사양을 모두 지킨다.
 */
export interface NavItem {
  path: string
  name: string
}

/**
 * 상단 내비의 링크.
 *
 * 랜딩(`/`)은 브랜드 로고가 맡고 여행 등록은 버튼(`NAV_CTA`)으로 세우므로
 * 여기 남는 것은 하나뿐이다. 목적지가 셋밖에 없어서 사이드바를 두지 않는다.
 */
export const NAV: NavItem[] = [
  { path: '/trips', name: '내 여행' },
]

/** 상단 내비 오른쪽 버튼. 화면 전체에서 <b>누를 것은 이것 하나</b>다. */
export const NAV_CTA: NavItem = { path: '/trips/new', name: '여행 등록' }

/**
 * 화면 정본 — <b>03-wireframe 의 S-ID 와 실제 경로를 잇는 유일한 표.</b>
 *
 * 03 에는 경로 칸이 없어서 이 표를 지우면 어느 라우트가 어느 화면인지 알 방법이
 * 사라진다. 실제로 한 번 지웠다가 리뷰에서 잡혔다.
 *
 * <b>App.tsx 가 이 배열로 라우트를 만들지는 않는다.</b> 화면마다 감싸는 것이
 * 달라(Private 여부·Todo 여부) 배열 하나로 접기 어렵다. 대신 아래 대조표가
 * 정본이고, 라우트를 더하거나 옮기면 여기도 같이 고친다.
 *
 * `tier` 는 03:94-98 의 중복본이다 — 코드에서 "무엇이 남았나" 를 세려면 필요하다.
 */
export interface ScreenRoute {
  id: string
  name: string
  /** `:tripId` 를 채워 쓴다. S-09 는 모달이라 경로가 없다 */
  path: string | null
  /** 1차 = 데모 주 경로. 시간이 모자라면 3차부터 버린다 */
  tier: 1 | 2 | 3
  /** AI 확장 지점(★). 03 의 별표와 같다 */
  ai?: boolean
  /** 아직 화면이 없다 — Todo 자리표시자다 */
  todo?: boolean
}

export const SCREENS: ScreenRoute[] = [
  { id: 'S-00', name: '로그인·회원가입', path: '/login', tier: 1 },
  { id: 'S-01', name: '홈 · 내 여행', path: '/trips', tier: 1 },
  { id: 'S-02', name: '여행 등록', path: '/trips/new', tier: 1, todo: true },
  { id: 'S-03', name: '짐 사진 등록', path: '/trips/:tripId/photos', tier: 1 },
  { id: 'S-04', name: '인식 결과 · 사후 수정', path: '/trips/:tripId/detections', tier: 1, ai: true },
  { id: 'S-05', name: '내 체크리스트 · AI 추천', path: '/trips/:tripId/items', tier: 1, ai: true },
  { id: 'S-06', name: '검수 결과', path: '/trips/:tripId/inspection', tier: 1, ai: true, todo: true },
  { id: 'S-07', name: '무게 상세', path: '/trips/:tripId/weight', tier: 2, todo: true },
  { id: 'S-08', name: '반입 규정 상세', path: '/trips/:tripId/rules', tier: 2, todo: true },
  { id: 'S-09', name: '챗봇', path: null, tier: 3, ai: true },
  { id: 'S-10', name: '여행 기록 상세', path: '/trips/:tripId', tier: 3, todo: true },
  { id: 'S-11', name: '여행 일정 · 캘린더', path: '/trips/:tripId/itinerary', tier: 2, todo: true },
  { id: 'S-12', name: '3D 가방 정리', path: '/trips/:tripId/layout', tier: 3, todo: true },
]

/** 여행 준비 3단계. 단계 표시줄에 쓴다. */
export interface Step {
  no: string
  name: string
  /** `:tripId` 를 채워 쓴다 */
  path: string
}

export const STEPS: Step[] = [
  { no: '1', name: '여행 정보', path: '/trips/new' },
  { no: '2', name: '짐 사진 · 분석', path: '/trips/:tripId/photos' },
  { no: '3', name: '체크리스트 · 검수', path: '/trips/:tripId/items' },
]

export function stepPath(s: Step, tripId: number | string): string {
  return s.path.replace(':tripId', String(tripId))
}

