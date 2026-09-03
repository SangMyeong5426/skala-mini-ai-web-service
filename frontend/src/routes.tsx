import { Placeholder } from './pages/Placeholder'

/**
 * docs/03-wireframe.md 의 화면 10개.
 *
 * 경로는 06-api-spec 의 자원 이름을 따른다 — 화면과 API 가 같은 말을 쓰면
 * FE·BE 가 서로 물어볼 일이 줄어든다.
 *
 * 로그인은 화면 번호를 받지 않는다. 손현아의 User Flow 개정에서
 * `S-01`~`S-10` 앞의 **공통 진입 단계**로 정했다. 화면 ID·인증 방식은 TBD 라
 * 여기서는 경로만 잡아 둔다.
 */
export interface ScreenRoute {
  path: string
  id: string
  name: string
  /** 1차 = 데모 주 경로. 시간이 모자라면 3차부터 버린다 */
  tier: 1 | 2 | 3
  ai?: boolean
  note?: string
}

export const SCREENS: ScreenRoute[] = [
  { path: '/', id: 'S-01', name: '홈', tier: 1, note: '진행 중 여행 + 과거 여행 카드' },
  { path: '/trips/new', id: 'S-02', name: '여행 등록', tier: 1 },
  { path: '/trips/:tripId/photos', id: 'S-03', name: '짐 사진 등록', tier: 1, note: '이 서비스의 시작점' },
  { path: '/trips/:tripId/detections', id: 'S-04', name: '인식 결과 · 승인', tier: 1, ai: true, note: '승인 전에는 다음 단계에 반영되지 않는다' },
  { path: '/trips/:tripId/items', id: 'S-05', name: '체크리스트', tier: 1, ai: true, note: '승인 물품 + AI 추천 부족분' },
  { path: '/trips/:tripId/inspection', id: 'S-06', name: '검수 결과', tier: 1, ai: true, note: '준비 상태 + 무게 + 반입 판정' },
  { path: '/trips/:tripId/weight', id: 'S-07', name: '무게 상세', tier: 2 },
  { path: '/trips/:tripId/rules', id: 'S-08', name: '반입 규정 상세', tier: 2 },
  { path: '/chat', id: 'S-09', name: '챗봇', tier: 3, ai: true },
  { path: '/trips/:tripId', id: 'S-10', name: '여행 기록 상세', tier: 3 },
]

export const LOGIN_PATH = '/login'

export function screenElement(s: ScreenRoute) {
  return <Placeholder id={s.id} name={s.name} note={s.note} />
}
