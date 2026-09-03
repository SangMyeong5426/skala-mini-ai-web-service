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
  { no: '3', name: '체크리스트 · 검수', path: '/trips/:tripId/review' },
]

export function stepPath(s: Step, tripId: number | string): string {
  return s.path.replace(':tripId', String(tripId))
}

