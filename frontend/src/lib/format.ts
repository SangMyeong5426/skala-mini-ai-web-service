/** 화면 공통 표기 규약. 06 이 정한 것을 한곳에 모은다. */

/** 완료율 — 서버의 `0.857` 은 어디서나 `86%` 다 (03-wireframe). */
export function pct(rate: number | null | undefined): string {
  if (rate == null) return '—'
  return `${Math.round(rate * 100)}%`
}

/** `2026-10-01` → `10.01` */
export function md(iso: string): string {
  const [, m, d] = iso.split('-')
  return `${m}.${d}`
}

/** 기간 표기 — `10.01 — 10.04 (3박 4일)` */
export function period(start: string, end: string): string {
  const nights = Math.max(0, Math.round((Date.parse(end) - Date.parse(start)) / 86_400_000))
  return `${md(start)} — ${md(end)} (${nights}박 ${nights + 1}일)`
}

/** 그램 → `5.4kg` */
export function kg(g: number): string {
  return `${(g / 1000).toFixed(1)}kg`
}

export const CATEGORY_LABEL: Record<string, string> = {
  DOCUMENT: '서류', CLOTHING: '의류', ELECTRONIC: '전자기기',
  TOILETRY: '세면용품', MEDICINE: '의약품', ETC: '기타',
}

export const TRANSPORT_LABEL: Record<string, string> = {
  FLIGHT: '항공', TRAIN: '기차', BUS: '버스', CAR: '자동차', SHIP: '선박',
}

export const SOURCE_LABEL: Record<string, string> = {
  PHOTO: '사진에서 확인', AI: 'AI 추천', RULE: '필수 규칙', USER: '직접 추가',
}

/** 사진 확인 상태 — checkStatus 와 별개 축이다. */
export const PHOTO_STATUS_LABEL: Record<string, string> = {
  CONFIRMED: '확인됨', NEEDS_CHECK: '확인 필요', NOT_IN_PHOTO: '사진에서 미확인',
}

/** 반입 판정 6종. 색으로만 구분하지 않으려고 라벨을 함께 둔다. */
export const VERDICT_LABEL: Record<string, string> = {
  CABIN_OK: '기내 가능', CHECKED_OK: '위탁 가능', CHECKED_FORBIDDEN: '위탁 금지',
  RESTRICTED: '반입 제한', NEED_MORE_INFO: '추가정보 필요', ASK_AIRLINE: '항공사 확인',
}

export const WEIGHT_VERDICT_LABEL: Record<string, string> = {
  ROOM: '여유', NEAR: '한도 근접', OVER_RISK: '초과 가능성', UNKNOWN: '정보 부족',
}
