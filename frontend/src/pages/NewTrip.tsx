import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { api, ApiFailure } from '../api/client'
import { Shell, Steps, TopBar } from '../components/Shell'
import type { BagType, Purpose, Transport, TripCreated } from '../types/api'

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
const TRANSPORTS: { v: Transport; label: string }[] = [
  { v: 'FLIGHT', label: '비행기' },
  { v: 'TRAIN', label: '기차' },
  { v: 'BUS', label: '버스' },
  { v: 'CAR', label: '자동차' },
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

  const [origin, setOrigin] = useState('')
  const [destination, setDestination] = useState('')
  const [countryCode, setCountryCode] = useState('')
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

  const isFlight = transport === 'FLIGHT'
  const bag = BAGS.find((b) => b.v === bagType)!
  // 03: "귀국일 < 출발일 시 날짜칸 강조". 저장을 눌러야 알려 주면 늦다
  const badRange = Boolean(startDate && endDate && endDate < startDate)

  const submit = async (e: React.FormEvent) => {
    e.preventDefault()
    setError(null); setField(null)

    const required: [string, string, string][] = [
      ['origin', origin, '출발지를 입력해 주세요.'],
      ['destination', destination, '도착지를 입력해 주세요.'],
      ['countryCode', countryCode, '목적지 국가 코드를 입력해 주세요.'],
      ['startDate', startDate, '출발일을 골라 주세요.'],
      ['endDate', endDate, '귀국일을 골라 주세요.'],
    ]
    const miss = required.find(([, v]) => !v.trim())
    if (miss) { setField(miss[0]); setError(miss[2]); return }
    if (!/^[A-Za-z]{2}$/.test(countryCode.trim())) {
      setField('countryCode'); setError('국가 코드는 영문 2자로 입력해 주세요.'); return
    }
    if (badRange) { setField('endDate'); setError('귀국일이 출발일보다 빠릅니다.'); return }

    setBusy(true)
    try {
      // 06: POST /api/trips → 201 + Location. 본문의 tripId 로 이동한다.
      const created = await api.post<TripCreated>('/trips', {
        origin: origin.trim(),
        destination: destination.trim(),
        countryCode: countryCode.trim().toUpperCase(),
        startDate,
        endDate,
        purpose,
        transport,
        // 항공이 아니면 항공 관련 값을 보내지 않는다
        airline: isFlight && airline.trim() ? airline.trim() : undefined,
        departureAirport: isFlight && departureAirport.trim() ? departureAirport.trim().toUpperCase() : undefined,
        arrivalAirport: isFlight && arrivalAirport.trim() ? arrivalAirport.trim().toUpperCase() : undefined,
        bagType,
        bagEmptyG: bag.emptyG,
        weightLimitG: bag.limitG,
        note: note.trim() || undefined,
      })
      // 등록하면 곧바로 사진으로. 03 의 주 경로다.
      nav(`/trips/${created.tripId}/photos`, { replace: true })
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
    'origin', 'destination', 'countryCode', 'startDate', 'endDate', 'purpose', 'transport',
    'airline', 'departureAirport', 'arrivalAirport', 'note',
    'bagType', 'bagEmptyG', 'weightLimitG',
  ])
  const err = (name: string) => (field === name ? error : null)

  return (
    <Shell>
      <TopBar title="여행 정보" sub="추천과 반입 판단에 쓸 조건을 알려 주세요" />
      <Steps current={1} />

      <div className="content">
        <form className="card form" onSubmit={submit} noValidate>
          <div className="form-row">
            <Cell label="출발지" name="origin" error={err('origin')}>
              <input id="origin" value={origin} onChange={(e) => setOrigin(e.target.value)} placeholder="서울" />
            </Cell>
            <Cell label="도착지" name="destination" error={err('destination')}>
              <input id="destination" value={destination} onChange={(e) => setDestination(e.target.value)} placeholder="도쿄" />
            </Cell>
          </div>

          <Cell label="목적지 국가 코드" name="countryCode" hint="ISO 영문 2자리 · 예: 일본 JP, 한국 KR" error={err('countryCode')}>
            <input id="countryCode" value={countryCode} maxLength={2}
              onChange={(e) => setCountryCode(e.target.value.toUpperCase())} placeholder="JP" />
          </Cell>

          <div className="form-row">
            <Cell label="출발일" name="startDate" error={err('startDate')}>
              <input id="startDate" type="date" value={startDate} onChange={(e) => setStartDate(e.target.value)} />
            </Cell>
            <Cell
              label="귀국일" name="endDate"
              error={err('endDate') ?? (badRange ? '귀국일이 출발일보다 빠릅니다.' : null)}
            >
              <input id="endDate" type="date" value={endDate} onChange={(e) => setEndDate(e.target.value)} />
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

          <Cell label="이동수단" name="transport" error={err('transport')}>
            <div className="chips">
              {TRANSPORTS.map((t) => (
                <button
                  key={t.v} type="button"
                  className={`pick${transport === t.v ? ' is-on' : ''}`}
                  aria-pressed={transport === t.v}
                  onClick={() => setTransport(t.v)}
                >{t.label}</button>
              ))}
            </div>
          </Cell>

          {isFlight && (
            <>
              <div className="form-row">
                <Cell label="항공사" name="airline" hint="선택" error={err('airline')}>
                  <input id="airline" value={airline} onChange={(e) => setAirline(e.target.value)} placeholder="대한항공" />
                </Cell>
                <Cell label="출발 공항" name="departureAirport" hint="선택 · 3자리 코드" error={err('departureAirport')}>
                  <input id="departureAirport" value={departureAirport} maxLength={3}
                    onChange={(e) => setDepartureAirport(e.target.value)} placeholder="ICN" />
                </Cell>
                <Cell label="도착 공항" name="arrivalAirport" hint="선택 · 3자리 코드" error={err('arrivalAirport')}>
                  <input id="arrivalAirport" value={arrivalAirport} maxLength={3}
                    onChange={(e) => setArrivalAirport(e.target.value)} placeholder="NRT" />
                </Cell>
              </div>
              {/* 03 오류 상태 — 저장 전에 미리 알려 준다 */}
              {!airline.trim() && (
                <p className="notice-warn">
                  항공사를 비우면 <b>일반 기준만 적용</b>되어 반입 판정의 정확도가 낮아집니다.
                </p>
              )}
            </>
          )}

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
            <button type="submit" className="btn" disabled={busy || badRange}>
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
