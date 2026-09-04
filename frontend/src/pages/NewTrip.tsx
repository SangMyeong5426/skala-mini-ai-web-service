import { useEffect, useRef, useState } from 'react'
import { useNavigate, useParams } from 'react-router-dom'
import { api, ApiFailure } from '../api/client'
import { Shell, Steps, TopBar } from '../components/Shell'
import { ARRIVAL_AIRPORT_GROUPS, DEPARTURE_AIRPORTS, cityOf, countryOf } from '../lib/airports'
import type { BagType, Purpose, Transport, TripCreated, TripDetail } from '../types/api'

/**
 * S-02 여행 등록.
 *
 * 추천과 반입 판단에 쓸 조건을 구조화해 받는다. 여기서 받은 값이
 * `PACKING_LIST` 의 input(07:513-521)과 `RULE_CHECK` 의 `transport`·`airline`
 * 로 그대로 흘러간다 — <b>이 화면이 비면 AI 입력이 빈다.</b>
 *
 * 필수는 일곱이다(03) — 출발일·귀국일·출발지·도착지·목적지 국가 코드·목적·이동수단.
 * 항공사·공항은 선택이지만 <b>비우면 정확도가 낮아진다고 미리 알려 준다.</b>
 * 저장하고 나서 알려 주면 늦다.
 */
/**
 * 이번 범위는 <b>항공 해외여행 하나</b>다. 나머지 셋은 <b>보이되 눌리지 않는다.</b>
 *
 * 목록에서 아예 빼지 않는 이유는 두 가지다. 하나는 `Codes.Transport` 가 넷을
 * 갖고 있고 DB 제약도 그대로라 — 지운 것이 아니라 아직 안 여는 것이고, 그
 * 사실이 화면에 드러나는 편이 정직하다. 다른 하나는 <b>다음에 무엇이 오는지</b>
 * 를 사용자가 알 수 있다는 것이다.
 *
 * 눌리지 않게 두는 이유는 `transport_rules` 규정 마스터가 항공 기준으로만
 * 채워져 있어서다. 고르게 하면 반입 판정이 빈 답을 낸다 — <b>고를 수 있는데
 * 답이 안 나오는 것이 아예 없는 것보다 나쁘다.</b>
 *
 * 확장할 때 `soon` 을 지우면 된다. 스키마·API·판정 구조는 그대로다.
 */
/**
 * 오늘 날짜(`YYYY-MM-DD`). `<input type="date">` 의 `min` 에 쓴다.
 *
 * <b>`toISOString()` 을 쓰지 않는다.</b> 그건 UTC 라 한국(UTC+9)에서는 자정부터
 * 오전 9시까지 <b>어제</b> 를 돌려준다. 그 시간대에 오늘 출발하는 여행을 만들려
 * 하면 달력이 막아 버린다.
 */
const TODAY = (() => {
  const d = new Date()
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`
})()

const TRANSPORTS: { v: Transport; label: string; soon?: boolean }[] = [
  { v: 'FLIGHT', label: '비행기' },
  { v: 'TRAIN', label: '기차', soon: true },
  { v: 'BUS', label: '버스', soon: true },
  { v: 'CAR', label: '자동차', soon: true },
]

const PURPOSES: { v: Purpose; label: string }[] = [
  { v: 'TOUR', label: '관광' },
  { v: 'BUSINESS', label: '출장' },
  { v: 'REST', label: '휴양' },
  { v: 'STUDY', label: '학업' },
]

/** 가방별 빈 무게·한도 기본값. 사용자가 재지 않아도 예상 무게를 낼 수 있게 한다 */
const BAGS: { v: BagType; label: string; emptyG: number; limitG: number }[] = [
  { v: 'CARRY_ON', label: '기내용 (약 20인치)', emptyG: 3200, limitG: 10000 },
  { v: 'MEDIUM', label: '중형 (약 24인치)', emptyG: 4200, limitG: 23000 },
  { v: 'LARGE', label: '대형 (약 28인치)', emptyG: 5200, limitG: 23000 },
]

export default function NewTrip() {
  const nav = useNavigate()
  /*
   * `tripId` 가 있으면 <b>수정</b>이다.
   *
   * 예전에는 이 화면이 새 여행만 만들었다. 그래서 짐 사진에서 단계 표시줄의
   * `여행 정보` 를 눌러 돌아오면 <b>빈 폼</b>이 떴다 — 방금 입력한 것이 전부
   * 사라진 것처럼 보이고, 저장하면 여행이 하나 더 생겼다.
   *
   * 서버에는 `PATCH /api/trips/{tripId}` 가 이미 있다. 화면만 그 길을 안 쓰고 있었다.
   */
  const { tripId } = useParams()
  const editing = Boolean(tripId)
  const [loading, setLoading] = useState(editing)

  /*
   * <b>도시도 국가도 상태가 아니라 공항에서 나온 값이다.</b>
   *
   * 공항이 필수가 되면서 따로 들고 있을 이유가 사라졌다. 상태로 두면 공항과
   * 어긋날 수 있는데(NRT 인데 도시는 오사카, 나라는 CN), 파생값이면 그런
   * 상태가 <b>존재할 수 없다.</b> 자동 채움 여부를 기억하던 플래그 두 개도
   * 같이 없앴다 — 덮어쓸지 말지를 판단할 일이 없다.
   *
   * #52 가 `목적지 국가 코드` 를 <b>손으로 치는 필수 칸</b>으로 넣었는데,
   * 여기서는 칸을 지우고 `countryOf(arrivalAirport)` 로 만든다. 요구사항은
   * 같다 — countryCode 를 반드시 보낸다. 다만 사람이 `JP` 를 치지 않아도 되고,
   * 공항과 나라가 어긋난 값이 애초에 만들어지지 않는다.
   *
   * 대신 "나리타로 들어가 요코하마에 묵는" 경우를 표현할 수 없다. 날씨가
   * 공항 도시 기준이 된다는 뜻이라, 07 의 알려진 한계에 적어 둔다.
   */
  const [startDate, setStartDate] = useState('')
  const [endDate, setEndDate] = useState('')
  const [purpose, setPurpose] = useState<Purpose>('TOUR')
  const [transport, setTransport] = useState<Transport>('FLIGHT')
  const [airline, setAirline] = useState('')
  const [departureAirport, setDepartureAirport] = useState('')
  const [arrivalAirport, setArrivalAirport] = useState('')
  const [bagType, setBagType] = useState<BagType>('CARRY_ON')
  const [note, setNote] = useState('')

  const [error, setError] = useState<string | null>(null)
  const [field, setField] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)

  /**
   * 저장된 상태의 지문. 이것과 지금 값이 같으면 손댄 것이 없다.
   *
   * 새 여행이면 빈 폼이 기준이고, 고치기면 서버에서 받은 값이 기준이 된다.
   * 저장에 성공하면 곧바로 다음 화면으로 가므로 여기서 다시 갱신하지 않는다.
   */
  const clean = useRef('')

  /* 수정이면 지금 값을 먼저 채운다. 못 불러오면 빈 폼으로 덮지 않고 오류를 말한다 */
  useEffect(() => {
    if (!editing) return
    api.get<TripDetail>(`/trips/${tripId}`)
      .then((t) => {
        setPurpose(t.purpose ?? 'TOUR')
        setTransport(t.transport ?? 'FLIGHT')
        setAirline(t.airline ?? '')
        setDepartureAirport(t.departureAirport ?? '')
        setArrivalAirport(t.arrivalAirport ?? '')
        setStartDate(t.startDate ?? '')
        setEndDate(t.endDate ?? '')
        setBagType(t.bagType ?? 'CARRY_ON')
        setNote(t.note ?? '')
        clean.current = JSON.stringify({
          startDate: t.startDate ?? '', endDate: t.endDate ?? '',
          purpose: t.purpose ?? 'TOUR', transport: t.transport ?? 'FLIGHT',
          airline: t.airline ?? '', departureAirport: t.departureAirport ?? '',
          arrivalAirport: t.arrivalAirport ?? '', bagType: t.bagType ?? 'CARRY_ON',
          note: t.note ?? '',
        })
      })
      .catch((e) => setError(e instanceof Error ? e.message : '여행을 불러오지 못했습니다.'))
      .finally(() => setLoading(false))
  }, [editing, tripId])

  /*
   * <b>손댄 것이 있나.</b> 불러온 값(또는 빈 폼)과 지금 값을 통째로 견준다.
   *
   * 칸마다 플래그를 두지 않는다 — 칸이 늘 때마다 빠뜨리기 쉽고, 되돌려 놓은
   * 경우(고쳤다가 원래대로)를 "안 고침" 으로 보지 못한다.
   */
  const snapshot = JSON.stringify({
    startDate, endDate, purpose, transport, airline,
    departureAirport, arrivalAirport, bagType, note,
  })
  const dirty = !loading && snapshot !== clean.current

  const isFlight = transport === 'FLIGHT'
  const origin = cityOf(departureAirport) ?? ''
  const destination = cityOf(arrivalAirport) ?? ''

  const bag = BAGS.find((b) => b.v === bagType)!
  // 03: "귀국일 < 출발일 시 날짜칸 강조". 저장을 눌러야 알려 주면 늦다
  const badRange = Boolean(startDate && endDate && endDate < startDate)
  // 03 "귀국일 < 출발일 시 날짜칸 강조" 와 같은 자리. 지난 출발일도 저장 전에 잡는다
  const pastStart = Boolean(startDate && startDate < TODAY)

  const submit = async (e: React.FormEvent) => {
    e.preventDefault()
    setError(null); setField(null)

    /*
     * 항공사·공항이 <b>필수</b>다.
     *
     * 셋 다 반입 판정에 직접 쓰인다 — 규칙 엔진이 노선과 항공사 기준으로
     * 규정을 고른다. 비우면 "일반 기준" 으로 떨어져 판정이 느슨해지는데,
     * 그 사실을 사용자는 결과를 볼 때까지 모른다. 처음에 받는 편이 낫다.
     *
     * 출발지·도착지는 검사하지 않는다 — 공항에서 나온 값이라 공항이 채워지면
     * 반드시 있다. 따로 검사하면 절대 걸리지 않는 조건이 남는다.
     */
    const required: [string, string, string][] = [
      ...(isFlight ? [
        ['airline', airline, '항공사를 입력해 주세요.'],
        ['departureAirport', departureAirport, '출발 공항을 골라 주세요.'],
        ['arrivalAirport', arrivalAirport, '도착 공항을 골라 주세요.'],
      ] as [string, string, string][] : []),
      ['startDate', startDate, '출발일을 골라 주세요.'],
      ['endDate', endDate, '귀국일을 골라 주세요.'],
    ]
    const miss = required.find(([, v]) => !v.trim())
    if (miss) { setField(miss[0]); setError(miss[2]); return }
    if (pastStart) { setField('startDate'); setError('출발일은 오늘 이후로 골라 주세요.'); return }
    /*
     * #52 의 `국가 코드는 영문 2자` 검사를 대신한다. 목록에서 고른 공항이면
     * 나라는 반드시 나오므로, 안 나왔다는 것은 <b>목록에 없는 코드</b>라는 뜻이다.
     * 그대로 보내면 서버가 countryCode 없이 받아 추천 프롬프트가 빈다.
     */
    if (isFlight && !countryOf(arrivalAirport)) {
      setField('arrivalAirport'); setError('도착 공항을 목록에서 다시 골라 주세요.'); return
    }
    if (badRange) { setField('endDate'); setError('귀국일이 출발일보다 빠릅니다.'); return }

    setBusy(true)
    try {
      // 06: POST /api/trips → 201 · PATCH /api/trips/{tripId} → 200. 본문의 tripId 로 이동한다.
      const body = {
        origin: origin.trim(),
        destination: destination.trim(),
        startDate,
        endDate,
        purpose,
        transport,
        // 항공이 아니면 항공 관련 값을 보내지 않는다
        airline: isFlight && airline.trim() ? airline.trim() : undefined,
        departureAirport: isFlight && departureAirport ? departureAirport : undefined,
        arrivalAirport: isFlight && arrivalAirport ? arrivalAirport : undefined,
        /*
         * <b>나라는 따로 묻지 않는다.</b> 도착 공항을 고르면 정해져 있는데 또 물으면
         * 두 값이 어긋날 수 있다. 목록에 없으면 null 이고 그때는 보내지 않는다 —
         * 없는 값을 지어내지 않는다.
         *
         * 이 값이 `PACKING_LIST` 프롬프트의 `{{server:trip.countryCode}}` 로 간다.
         * 예전에는 폼이 아예 안 보내서 새 여행은 늘 비어 있었다.
         */
        countryCode: (isFlight && countryOf(arrivalAirport)) || undefined,
        bagType,
        bagEmptyG: bag.emptyG,
        weightLimitG: bag.limitG,
        note: note.trim() || undefined,
      }
      // 등록하면 곧바로 사진으로. 03 의 주 경로다.
      /*
       * 수정이면 새로 만들지 않는다. 06 의 `PATCH /api/trips/{tripId}` 로 보내고
       * 하던 자리(짐 사진)로 되돌아간다 — 값을 확인하러 왔다가 흐름을 잃지 않게.
       */
      const id = editing
        // PATCH 는 `Detail` 을 준다 — `CreateResponse` 가 아니다
        ? (await api.patch<TripDetail>(`/trips/${tripId}`, body)).tripId ?? Number(tripId)
        : (await api.post<TripCreated>('/trips', body)).tripId
      nav(`/trips/${id}/photos`, { replace: true })
    } catch (err) {
      setError(err instanceof Error ? err.message : '여행을 등록하지 못했습니다.')
      if (err instanceof ApiFailure && err.field) setField(err.field)
    } finally {
      setBusy(false)
    }
  }

  /**
   * 서버가 알려준 그 칸의 오류. 06 의 오류 봉투는 `{ code, message, field? }` 다.
   *
   * <b>인라인 자리가 있는 칸의 목록을 함께 둔다.</b> 서버가 이 밖의 `field` 를
   * 주면(계약이 늘어나거나 우리가 칸을 빼먹으면) 오류가 어디에도 보이지 않고
   * 저장만 조용히 실패한다. 그때는 아래 일반 오류로 떨어뜨린다.
   */
  const INLINE = new Set([
    /*
     * `countryCode` 는 뺀다. 손입력 칸이 없어져서 그 이름의 칸이 화면에 없다 —
     * 인라인으로 돌리면 오류가 <b>아무 데도 안 뜬다.</b> 일반 오류로 떨어뜨린다.
     */
    'origin', 'destination', 'startDate', 'endDate', 'purpose', 'transport',
    'airline', 'departureAirport', 'arrivalAirport', 'note',
    'bagType', 'bagEmptyG', 'weightLimitG',
  ])
  const err = (name: string) => (field === name ? error : null)

  /**
   * 그 칸을 고치면 그 칸의 오류를 지운다.
   *
   * <b>없으면 경고가 남는다.</b> 오류는 제출할 때만 지워지도록 돼 있어서,
   * "출발지를 입력해 주세요" 를 보고 실제로 채워도 문구가 그대로 붙어 있었다.
   * 고쳤는데 아직 틀렸다고 하는 화면은 사용자를 멈춰 세운다.
   *
   * 다른 칸의 오류는 건드리지 않는다 — 도착지가 비어서 난 경고를 출발지를
   * 만졌다고 지우면 안 된다.
   */
  const clearErr = (name: string) => { if (field === name) { setField(null); setError(null) } }

  return (
    <Shell>
      <TopBar
        title="여행 정보"
        sub={editing ? '값을 고치면 그대로 저장됩니다' : '추천과 반입 판단에 쓸 조건을 알려 주세요'}
      />
      {/*
        * 고치던 중에 단계 표시줄을 누르면 입력한 것이 <b>말없이</b> 사라졌다.
        * 아래 `다음` 은 저장하고 가는데 위쪽 표시줄은 그냥 나갔다 — 같은 화면에서
        * 나가는 길 둘이 서로 다르게 굴었다.
        *
        * 여기서 자동 저장은 못 한다. 채우다 만 폼은 서버가 400 으로 거절하고,
        * 그러면 "왜 저장이 안 되지" 만 남는다. 대신 <b>사실을 알리고 고르게</b> 한다.
        */}
      <Steps
        current={1}
        tripId={tripId}
        beforeLeave={() =>
          !dirty || window.confirm('저장하지 않은 변경이 있습니다. 그대로 나가시겠습니까?')}
      />

      <div className="content">
        <form className="card form" onSubmit={submit} noValidate>
          <Cell
            label="이동수단" name="transport"
            hint="지금은 항공 해외여행만 다룹니다. 흐린 것은 다음 범위입니다"
            error={err('transport')}
          >
            <div className="chips">
              {TRANSPORTS.map((t) => (
                <button
                  key={t.v} type="button"
                  className={`pick${transport === t.v ? ' is-on' : ''}`}
                  aria-pressed={t.soon ? undefined : transport === t.v}
                  /* 왜 못 누르는지 마우스로도 알 수 있게 한다. disabled 는 이유를 말하지 않는다 */
                  title={t.soon ? '다음 범위입니다. 지금은 항공 해외여행만 다룹니다' : undefined}
                  disabled={t.soon}
                  onClick={() => setTransport(t.v)}
                >{t.label}{t.soon && <span className="pick-soon">준비 중</span>}</button>
              ))}
            </div>
          </Cell>

          {isFlight && (
            <>
              <div className="form-row">
                <Cell
                  label="항공사" name="airline"
                  hint="반입 규정을 이 항공사 기준으로 판정합니다"
                  error={err('airline')}
                >
                  <input
                    id="airline" value={airline} placeholder="대한항공"
                    onChange={(e) => { setAirline(e.target.value); clearErr('airline') }}
                  />
                </Cell>
                {/*
                  * <b>손으로 3자리를 치게 하지 않는다.</b> 오타가 나도 형식은 맞아서
                  * 서버가 400 을 주지 않고, 규칙 엔진이 그 노선을 못 찾아 판정이
                  * 조용히 나빠진다. 고르게 하면 그 실패가 사라진다.
                  */}
                <Cell label="출발 공항" name="departureAirport" error={err('departureAirport')}>
                  <select id="departureAirport" value={departureAirport}
                    /* 도시는 이 값에서 파생된다. 따로 채울 것이 없다 */
                    onChange={(e) => { setDepartureAirport(e.target.value); clearErr('departureAirport') }}>
                    <option value="">공항을 고르세요</option>
                    {DEPARTURE_AIRPORTS.map((a) => (
                      <option key={a.code} value={a.code}>{a.name} ({a.code})</option>
                    ))}
                  </select>
                </Cell>
                <Cell
                  label="도착 공항" name="arrivalAirport"
                  hint={countryOf(arrivalAirport) ? `나라 ${countryOf(arrivalAirport)} 로 저장됩니다` : undefined}
                  error={err('arrivalAirport')}
                >
                  <select id="arrivalAirport" value={arrivalAirport}
                    onChange={(e) => { setArrivalAirport(e.target.value); clearErr('arrivalAirport') }}>
                    <option value="">공항을 고르세요</option>
                    {ARRIVAL_AIRPORT_GROUPS.map((g) => (
                      <optgroup key={g.code} label={g.country}>
                        {g.airports.map((a) => (
                          <option key={a.code} value={a.code}>{a.name} ({a.code})</option>
                        ))}
                      </optgroup>
                    ))}
                  </select>
                </Cell>
              </div>
            </>
          )}

          {/*
            * <b>공항 다음에 둔다.</b> 도시가 위에 있으면 사용자가 먼저 채우게 되고,
            * 그러면 자동 채움이 아예 발동하지 않는다("손으로 쓴 값은 안 덮는다").
            * 기능을 만들어 놓고 순서 때문에 못 쓰던 자리다.
            *
            * 날씨는 이 도시로 조회한다 — 공항이 아니라. 그래서 채워 주되 잠그지
            * 않는다: 나리타로 들어가 요코하마에 묵으면 날씨는 요코하마가 맞다.
            */}
          {/*
            * <b>고칠 수 없다.</b> 위에서 고른 공항에서 나온 값이라, 여기서 따로
            * 고치면 공항과 도시가 어긋난 여행이 만들어진다(NRT 인데 도시는 오사카).
            *
            * 그래도 <b>보여는 준다.</b> 날씨와 준비물 추천이 이 도시로 돌아가므로,
            * 무엇을 기준으로 추천이 나오는지 저장 전에 알 수 있어야 한다.
            * 칸을 없애면 그 사실이 화면에서 사라진다.
            */}
          <div className="form-row">
            <Cell
              label="출발지" name="origin"
              hint={origin ? undefined : '출발 공항을 고르면 채워집니다'}
            >
              <input id="origin" value={origin} readOnly tabIndex={-1} placeholder="—" />
            </Cell>
            <Cell
              label="도착지" name="destination"
              hint={destination ? '날씨와 준비물 추천을 이 도시로 맞춥니다' : '도착 공항을 고르면 채워집니다'}
            >
              <input id="destination" value={destination} readOnly tabIndex={-1} placeholder="—" />
            </Cell>
          </div>

          <div className="form-row">
            <Cell
              label="출발일" name="startDate"
              error={err('startDate') ?? (pastStart ? '지난 날짜입니다.' : null)}
            >
              {/*
                * <b>지난 날짜를 고를 수 없게 막는다.</b> 어제 떠나는 여행이
                * 만들어지면 날씨 조회가 예보 범위를 벗어나 계절 평균으로 떨어지고,
                * 추천 품질이 조용히 나빠진다. 달력에서 아예 못 고르게 하는 편이
                * 저장한 뒤 알려 주는 것보다 낫다.
                *
                * `min` 만으로는 부족하다 — 키보드로 직접 칠 수 있어서 아래 검사도 함께 둔다.
                */}
              <input
                id="startDate" type="date" value={startDate} min={TODAY}
                onChange={(e) => { setStartDate(e.target.value); clearErr('startDate') }}
              />
            </Cell>
            <Cell
              label="귀국일" name="endDate"
              error={err('endDate') ?? (badRange ? '귀국일이 출발일보다 빠릅니다.' : null)}
            >
              <input
                id="endDate" type="date" value={endDate} min={startDate || TODAY}
                onChange={(e) => { setEndDate(e.target.value); clearErr('endDate') }}
              />
            </Cell>
          </div>

          <Cell label="목적" name="purpose" error={err('purpose')}>
            <div className="chips">
              {PURPOSES.map((p) => (
                <button
                  key={p.v} type="button"
                  className={`pick${purpose === p.v ? ' is-on' : ''}`}
                  aria-pressed={purpose === p.v}
                  onClick={() => setPurpose(p.v)}
                >{p.label}</button>
              ))}
            </div>
          </Cell>

          <Cell
            label="가방" name="bagType"
            hint={`빈 무게 ${(bag.emptyG / 1000).toFixed(1)}kg · 한도 ${bag.limitG / 1000}kg 로 계산합니다`}
            error={err('bagType') ?? err('bagEmptyG') ?? err('weightLimitG')}
          >
            <div className="chips">
              {BAGS.map((b) => (
                <button
                  key={b.v} type="button"
                  className={`pick${bagType === b.v ? ' is-on' : ''}`}
                  aria-pressed={bagType === b.v}
                  onClick={() => setBagType(b.v)}
                >{b.label}</button>
              ))}
            </div>
          </Cell>

          <Cell label="메모" name="note" hint="선택 · 동행인·특이사항을 적으면 추천에 반영됩니다" error={err('note')}>
            <input id="note" value={note} onChange={(e) => setNote(e.target.value)} placeholder="친구 2명, 디즈니랜드" />
          </Cell>

          {error && (!field || !INLINE.has(field)) && (
            <p className="auth-error" role="alert">{error}</p>
          )}

          <div className="form-foot">
            {/* 불러오는 동안 잠근다 — 빈 값이 그대로 저장되면 방금 입력한 것이 지워진다 */}
            <button type="submit" className="btn" disabled={busy || badRange || loading}>
              {/* 03:236 로딩 상태 — 저장 버튼 비활성 + 스피너 */}
              {busy && <span className="spinner" aria-hidden="true" />}
              {busy ? '저장하는 중…' : '다음 — 짐 사진 올리기'}
            </button>
          </div>
        </form>
      </div>
    </Shell>
  )
}

/** 입력 한 칸. 라벨·안내·오류를 한 자리에서 다룬다 */
function Cell({
  label, name, hint, error, children,
}: {
  label: string
  name: string
  hint?: string
  error?: string | null
  children: React.ReactNode
}) {
  return (
    <div className="cell">
      <label htmlFor={name}>{label}</label>
      {children}
      {error
        ? <p className="field-error" role="alert">{error}</p>
        : hint && <p className="field-hint">{hint}</p>}
    </div>
  )
}
