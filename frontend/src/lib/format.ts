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

/** ISO 시각 → `14:30`. 서버는 UTC 로 주므로 브라우저 시간대로 옮겨 보여준다 */
export function hhmm(iso: string): string {
  const d = new Date(iso)
  return `${String(d.getHours()).padStart(2, '0')}:${String(d.getMinutes()).padStart(2, '0')}`
}

/** ISO 시각 → `2026-10-01`. 날짜별로 묶을 때 쓰는 키다 */
export function dayKey(iso: string): string {
  const d = new Date(iso)
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`
}

/** `2026-10-01` → `10.01 (목)` */
export function dayLabel(key: string): string {
  const d = new Date(`${key}T00:00:00`)
  return `${md(key)} (${'일월화수목금토'[d.getDay()]})`
}

/** 여행 기간의 날짜를 모두 만든다. 일정이 없는 날도 자리를 보여주려고 */
export function daysBetween(start: string, end: string): string[] {
  const out: string[] = []
  const from = new Date(`${start}T00:00:00`)
  const to = new Date(`${end}T00:00:00`)
  for (let d = from; d <= to; d.setDate(d.getDate() + 1)) out.push(dayKey(d.toISOString()))
  return out
}

/** 일정 종류 라벨. 색으로만 구분하지 않는다 */
export const ITINERARY_KIND_LABEL: Record<string, string> = {
  FLIGHT: '항공', LODGING: '숙소', ACTIVITY: '일정', TRANSPORT: '이동', OTHER: '기타',
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
/**
 * 반입 판정 라벨. <b>화면마다 다르게 부르지 않는다.</b>
 *
 * S-06 검수와 S-09 챗봇이 각자 표를 들고 있어서 같은 판정이 다른 말로 나왔다.
 * 그냥 어색한 정도가 아니라 <b>뜻이 뒤집히는 것</b>이 있었다 —
 * `CHECKED_FORBIDDEN` 을 챗봇이 "위탁 금지" 로 불렀는데, 그러면 기내는 되는
 * 것처럼 읽힌다. 200Wh 보조배터리처럼 <b>기내·위탁 모두 금지</b>인 물건에
 * 그렇게 말하면 위험하다(06:416).
 *
 * 배지 색도 여기서 함께 정한다. 라벨만 합치고 색을 각자 두면 같은 판정이
 * 화면마다 다른 색으로 보인다.
 */
/*
 * <b>가능한 것은 두 축을 다 말한다.</b> TSA 의 "What Can I Bring" 은 물품마다
 * `기내 / 위탁` 두 칸을 각각 채운다 — 한 칸만 보여주면 나머지 칸을 읽는 사람이
 * 짐작하게 되고, 짐작이 틀리면 공항에서 짐을 버린다.
 *
 * 우리 enum 은 두 축을 담고 있지 않아 표를 그대로 옮길 수는 없다. 같은
 * `CABIN_OK` 라도 보조배터리는 위탁이 금지고 노트북은 허용이다(06:413·422) —
 * 위탁 여부는 enum 이 아니라 규정 문구에 있다. 그래서 <b>enum 이 실제로
 * 결정하는 둘만</b> 두 축으로 적는다.
 *
 *   CHECKED_OK        위탁 안내 = 기내는 안 된다 → "위탁만 가능"
 *   CHECKED_FORBIDDEN 06:415 "기내·위탁 모두 금지" → "기내·위탁 불가"
 *
 * 나머지 넷은 한 축만 확정되거나(CABIN_OK) 판정 자체가 보류라, 없는 사실을
 * 지어내지 않도록 그대로 둔다. 위탁 여부는 옆의 근거 문장이 말한다.
 */
export const VERDICT_LABEL: Record<string, string> = {
  CABIN_OK: '기내 가능', CHECKED_OK: '위탁만 가능', CHECKED_FORBIDDEN: '기내·위탁 불가',
  RESTRICTED: '조건부', NEED_MORE_INFO: '정보 부족', ASK_AIRLINE: '항공사 확인',
}

export const VERDICT_CLASS: Record<string, string> = {
  CABIN_OK: 'badge-ok', CHECKED_OK: 'badge-ok', CHECKED_FORBIDDEN: 'badge-danger',
  RESTRICTED: 'badge-warn', NEED_MORE_INFO: 'badge-warn', ASK_AIRLINE: 'badge-warn',
}

/**
 * 한도 대비 막대에 입힐 상태 클래스. 서버가 정한 verdict 를 그대로 쓴다.
 *
 * `ROOM` 과 `UNKNOWN` 은 기본색(초록)이다 — 정보가 부족한 것을 위험으로
 * 칠하면 없는 사실을 말하게 된다. 07 이 "판정하지 않는다(UNKNOWN)" 로 둔 뜻과 같다.
 */
export const WEIGHT_BAR_CLASS: Record<string, string> = {
  ROOM: '', NEAR: 'is-near', OVER_RISK: 'is-over', UNKNOWN: '',
}

/**
 * 한도까지 남은 무게. 수하물 계산기들이 쓰는 "headroom" 표기다.
 *
 * <b>부호를 말로 바꾼다.</b> `-1.1kg` 은 읽는 사람이 한 번 더 해석해야 하지만
 * "1.1kg 초과" 는 그대로 행동으로 이어진다. 한도를 모르면 null 을 돌려주고
 * 화면은 이 줄을 아예 그리지 않는다 — 0 으로 채우면 거짓이 된다.
 */
export function headroom(typicalG: number, limitG: number | null): string | null {
  if (limitG == null) return null
  const d = limitG - typicalG
  return d >= 0 ? `${kg(d)} 남음` : `${kg(-d)} 초과`
}

export const WEIGHT_VERDICT_LABEL: Record<string, string> = {
  ROOM: '여유', NEAR: '한도 근접', OVER_RISK: '초과 가능성', UNKNOWN: '정보 부족',
}
