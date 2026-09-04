/**
 * 공항 목록 — `departureAirport` · `arrivalAirport` 드롭다운의 자료.
 *
 * <b>손으로 3자리를 치게 하지 않는다.</b> `CHAR(3)` IATA 코드라 오타가 나면
 * 서버는 400 을 주지 않는다(형식은 맞으니까). 대신 규칙 엔진이 그 노선을 못 찾아
 * 판정이 조용히 나빠진다. 고르게 하면 그 실패가 사라진다.
 *
 * <b>이번 범위는 항공 해외여행 하나다.</b> 그래서 출발은 국내 국제공항,
 * 도착은 해외 공항만 담는다. 국내선·기차·버스로 넓힐 때 이 파일에 줄을 더하고
 * 화면은 고치지 않는다 — 목록과 화면을 갈라 둔 이유다.
 *
 * 나라 코드는 ISO 3166-1 alpha-2 다. `trips.country_code` 가 그 형식이고
 * (`CHAR(2)`), `PACKING_LIST` 프롬프트의 `{{server:trip.countryCode}}` 로 간다.
 */
export interface Airport {
  /** IATA 3자리. `trips.departure_airport` · `arrival_airport` 에 그대로 들어간다 */
  code: string
  /** 화면에 보이는 이름. 도시가 앞, 공항이 뒤 */
  name: string
  /**
   * 이 공항이 있는 도시. `trips.origin` · `destination` 을 채우는 데 쓴다.
   *
   * <b>이름에서 잘라 쓰지 않는다.</b> `'도쿄 · 나리타'` 를 구분자로 자르면
   * `'인천'` 처럼 구분자가 없는 줄에서 깨지고, 나중에 표기를 바꾸면 조용히
   * 틀린 도시가 저장된다. 값으로 따로 갖는다.
   *
   * 날씨는 이 도시로 조회한다(Open-Meteo 지오코딩은 IATA 코드를 모른다).
   * 그래서 도시는 공항과 <b>별개 값</b>이고, 사용자가 고칠 수 있어야 한다 —
   * 나리타로 들어가 요코하마에 묵으면 날씨는 요코하마가 맞다.
   */
  city: string
}

export interface AirportGroup {
  /** `<optgroup>` 라벨 */
  country: string
  /** ISO 3166-1 alpha-2. 도착 공항을 고르면 `countryCode` 가 이 값으로 정해진다 */
  code: string
  airports: Airport[]
}

/**
 * 출발 — 국내 국제공항.
 *
 * 정기 국제선이 있는 곳만 넣는다. 여수·울산처럼 국내선 전용은 이번 범위(해외여행)
 * 에서 고를 이유가 없다.
 */
export const DEPARTURE_AIRPORTS: Airport[] = [
  { code: 'ICN', name: '인천', city: '인천' },
  { code: 'GMP', name: '서울 · 김포', city: '서울' },
  { code: 'PUS', name: '부산 · 김해', city: '부산' },
  { code: 'CJU', name: '제주', city: '제주' },
  { code: 'TAE', name: '대구', city: '대구' },
  { code: 'CJJ', name: '청주', city: '청주' },
  { code: 'MWX', name: '무안', city: '무안' },
  { code: 'YNY', name: '양양', city: '양양' },
]

/** 도착 — 해외 공항. 나라별로 묶어 `<optgroup>` 으로 보여준다 */
export const ARRIVAL_AIRPORT_GROUPS: AirportGroup[] = [
  {
    country: '일본', code: 'JP', airports: [
      { code: 'NRT', name: '도쿄 · 나리타', city: '도쿄' },
      { code: 'HND', name: '도쿄 · 하네다', city: '도쿄' },
      { code: 'KIX', name: '오사카 · 간사이', city: '오사카' },
      { code: 'FUK', name: '후쿠오카', city: '후쿠오카' },
      { code: 'CTS', name: '삿포로 · 신치토세', city: '삿포로' },
      { code: 'OKA', name: '오키나와 · 나하', city: '오키나와' },
    ],
  },
  {
    country: '중국', code: 'CN', airports: [
      { code: 'PVG', name: '상하이 · 푸둥', city: '상하이' },
      { code: 'PEK', name: '베이징 · 서우두', city: '베이징' },
      { code: 'CAN', name: '광저우', city: '광저우' },
      { code: 'TAO', name: '칭다오', city: '칭다오' },
    ],
  },
  { country: '홍콩', code: 'HK', airports: [{ code: 'HKG', name: '홍콩', city: '홍콩' }] },
  {
    country: '대만', code: 'TW', airports: [
      { code: 'TPE', name: '타이베이 · 타오위안', city: '타이베이' },
      { code: 'KHH', name: '가오슝', city: '가오슝' },
    ],
  },
  {
    country: '베트남', code: 'VN', airports: [
      { code: 'DAD', name: '다낭', city: '다낭' },
      { code: 'SGN', name: '호치민 · 떤선녓', city: '호치민' },
      { code: 'HAN', name: '하노이 · 노이바이', city: '하노이' },
      { code: 'CXR', name: '나트랑 · 깜라인', city: '나트랑' },
    ],
  },
  {
    country: '태국', code: 'TH', airports: [
      { code: 'BKK', name: '방콕 · 수완나품', city: '방콕' },
      { code: 'HKT', name: '푸껫', city: '푸껫' },
    ],
  },
  {
    country: '필리핀', code: 'PH', airports: [
      { code: 'MNL', name: '마닐라', city: '마닐라' },
      { code: 'CEB', name: '세부', city: '세부' },
    ],
  },
  { country: '싱가포르', code: 'SG', airports: [{ code: 'SIN', name: '싱가포르 · 창이', city: '싱가포르' }] },
  { country: '말레이시아', code: 'MY', airports: [{ code: 'KUL', name: '쿠알라룸푸르', city: '쿠알라룸푸르' }] },
  { country: '인도네시아', code: 'ID', airports: [{ code: 'DPS', name: '발리 · 덴파사르', city: '발리' }] },
  {
    country: '미국', code: 'US', airports: [
      { code: 'LAX', name: '로스앤젤레스', city: '로스앤젤레스' },
      { code: 'JFK', name: '뉴욕 · 케네디', city: '뉴욕' },
      { code: 'SFO', name: '샌프란시스코', city: '샌프란시스코' },
      { code: 'SEA', name: '시애틀', city: '시애틀' },
      { code: 'HNL', name: '호놀룰루', city: '호놀룰루' },
    ],
  },
  { country: '괌', code: 'GU', airports: [{ code: 'GUM', name: '괌', city: '괌' }] },
  {
    country: '프랑스', code: 'FR', airports: [{ code: 'CDG', name: '파리 · 샤를드골', city: '파리' }],
  },
  { country: '영국', code: 'GB', airports: [{ code: 'LHR', name: '런던 · 히스로', city: '런던' }] },
  {
    country: '독일', code: 'DE', airports: [
      { code: 'FRA', name: '프랑크푸르트', city: '프랑크푸르트' },
      { code: 'MUC', name: '뮌헨', city: '뮌헨' },
    ],
  },
  {
    country: '이탈리아', code: 'IT', airports: [
      { code: 'FCO', name: '로마 · 피우미치노', city: '로마' },
      { code: 'MXP', name: '밀라노 · 말펜사', city: '밀라노' },
    ],
  },
  { country: '네덜란드', code: 'NL', airports: [{ code: 'AMS', name: '암스테르담', city: '암스테르담' }] },
  { country: '스페인', code: 'ES', airports: [{ code: 'BCN', name: '바르셀로나', city: '바르셀로나' }] },
  { country: '체코', code: 'CZ', airports: [{ code: 'PRG', name: '프라하', city: '프라하' }] },
  {
    country: '호주', code: 'AU', airports: [
      { code: 'SYD', name: '시드니', city: '시드니' },
      { code: 'BNE', name: '브리즈번', city: '브리즈번' },
    ],
  },
  { country: '뉴질랜드', code: 'NZ', airports: [{ code: 'AKL', name: '오클랜드', city: '오클랜드' }] },
  { country: '아랍에미리트', code: 'AE', airports: [{ code: 'DXB', name: '두바이', city: '두바이' }] },
  { country: '튀르키예', code: 'TR', airports: [{ code: 'IST', name: '이스탄불', city: '이스탄불' }] },
]

/**
 * 도착 공항 코드로 나라 코드를 찾는다.
 *
 * 폼에 나라를 따로 묻지 않는 이유다 — 공항을 고르면 나라는 정해져 있는데
 * 또 물으면 두 값이 어긋날 수 있다. 목록에 없는 코드면 `null` 이고,
 * 그때는 `countryCode` 를 보내지 않는다(없는 값을 지어내지 않는다).
 */
export function countryOf(arrivalCode: string): string | null {
  if (!arrivalCode) return null
  const g = ARRIVAL_AIRPORT_GROUPS.find((x) => x.airports.some((a) => a.code === arrivalCode))
  return g ? g.code : null
}

/**
 * 공항 코드로 도시 이름을 찾는다. 출발·도착 목록을 모두 뒤진다.
 *
 * 공항을 고르면 도시 칸을 <b>채워 주되 잠그지 않는다.</b> 날씨는 도시로 조회하는데
 * (Open-Meteo 지오코딩), 나리타로 들어가 요코하마에 묵으면 날씨는 요코하마가 맞다.
 * 그래서 자동으로 채우는 것은 편의이고 정답은 사용자에게 있다.
 */
export function cityOf(code: string): string | null {
  if (!code) return null
  const dep = DEPARTURE_AIRPORTS.find((a) => a.code === code)
  if (dep) return dep.city
  for (const g of ARRIVAL_AIRPORT_GROUPS) {
    const a = g.airports.find((x) => x.code === code)
    if (a) return a.city
  }
  return null
}
