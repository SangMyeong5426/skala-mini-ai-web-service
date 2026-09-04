import { useEffect, useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { api } from '../api/client'
import { useAuth } from '../auth/context'
import { Shell, TopBar } from '../components/Shell'
import { Empty, Failed, Skeleton } from '../components/States'
import { pct, period, TRANSPORT_LABEL } from '../lib/format'
import type { TripSummary } from '../types/api'

/**
 * 내 여행 — 진행 중과 지난 여행을 함께 본다.
 *
 * 지난 여행은 다음 여행에 재사용하기 위한 것이다(UC-09).
 * 과거 반입 판정은 복사하지 않는다 — 규정은 바뀐다.
 */
export default function MyTrips() {
  const { user } = useAuth()
  const [trips, setTrips] = useState<TripSummary[] | null>(null)
  const [error, setError] = useState<string | null>(null)
  const nav = useNavigate()

  const load = () => {
    setError(null); setTrips(null)
    api.get<{ trips: TripSummary[] }>('/trips')
      .then((r) => setTrips(r.trips))
      .catch((e) => setError(e instanceof Error ? e.message : '알 수 없는 오류'))
  }
  useEffect(load, [])

  const ongoing = trips?.filter((t) => t.status !== 'DONE') ?? []
  const past = trips?.filter((t) => t.status === 'DONE') ?? []

  return (
    <Shell>
      <TopBar
        title="내 여행"
        sub="진행 중인 준비를 이어가거나 지난 여행을 다시 씁니다"
        right={
          <button type="button" className="btn" onClick={() => nav('/trips/new')}>＋ 새 여행</button>
        }
      />
      <div className="content">
        <section className="trip-banner" aria-label="여행 준비 안내">
          <div className="trip-banner-copy">
            <p className="trip-banner-title">
              {user?.nickname}님, 여행 가시기 전에<br />놓치신 것은 없으신가요?
            </p>
          </div>
          <TravelBannerArt />
        </section>

        {error && <Failed title="목록을 불러오지 못했습니다" detail={error} onRetry={load} />}
        {!error && trips === null && <div className="card"><Skeleton rows={3} /></div>}

        {trips !== null && trips.length === 0 && (
          <div className="card">
            <Empty
              title="아직 등록한 여행이 없습니다"
              action={<button type="button" className="btn" onClick={() => nav('/trips/new')}>첫 여행 만들기</button>}
            />
          </div>
        )}

        {ongoing.length > 0 && (
          <section>
            <div className="card-head">
              <h2 className="card-title">진행 중</h2>
              <span className="card-sub">{ongoing.length}건</span>
            </div>
            <div className="trip-list">
              {ongoing.map((t) => <TripCard key={t.tripId} trip={t} />)}
            </div>
          </section>
        )}

        {past.length > 0 && (
          <section style={{ marginTop: 28 }}>
            <div className="card-head">
              <h2 className="card-title">지난 여행</h2>
              <span className="card-sub">{past.length}건</span>
            </div>
            <div className="grid grid-3">
              {past.map((t) => <TripCard key={t.tripId} trip={t} past />)}
            </div>
          </section>
        )}
      </div>
    </Shell>
  )
}

function TripCard({ trip, past }: { trip: TripSummary; past?: boolean }) {
  /*
   * 03:176 — "S-01 에서 <b>마지막 저장 단계</b>로 복귀한다".
   *
   * 예전에는 진행 중이면 무조건 사진(S-03)으로 보냈다. 검수까지 마치고
   * `최종 저장` 을 누른 여행을 다시 눌러도 사진 업로드 화면이 나왔다.
   *
   * 목록 응답에는 어느 단계까지 갔는지가 없다. 있는 것은 `status` 뿐이고,
   * 그것이 정확히 이 뜻이다 — `DRAFT` 는 준비 중, `CONFIRMED` 는 최종 저장을
   * 거친 것(S-06 의 `최종 저장` 이 그 값을 만든다).
   */
  const to = past
    ? `/trips/${trip.tripId}`
    : trip.status === 'CONFIRMED'
      ? `/trips/${trip.tripId}/inspection`
      : `/trips/${trip.tripId}/photos`

  const summary = (
    <>
      <div className="card-head" style={{ marginBottom: 10 }}>
        <h3 className="card-title">{trip.origin} → {trip.destination}</h3>
        <span className="spacer" />
        <span className={`badge${past ? '' : ' badge-ok'}`}>
          {past ? '완료' : trip.status === 'DRAFT' ? '작성 중' : '진행 중'}
        </span>
      </div>
      <p className="card-sub">
        {period(trip.startDate, trip.endDate)} · {TRANSPORT_LABEL[trip.transport] ?? trip.transport}
      </p>
      <div style={{ marginTop: 14 }}>
        <div style={{ display: 'flex', marginBottom: 6 }}>
          <span className="stat-label">준비 완료율</span>
          <span style={{ marginLeft: 'auto', fontWeight: 700 }}>{pct(trip.completionRate)}</span>
        </div>
        <div className="bar"><span style={{ width: `${Math.round((trip.completionRate ?? 0) * 100)}%` }} /></div>
      </div>
    </>
  )

  if (past) {
    return (
      <Link to={to} className="card" style={{ display: 'block', color: 'inherit', textDecoration: 'none' }}>
        {summary}
      </Link>
    )
  }

  return (
    <article className="card trip-card">
      <Link to={to} className="trip-card-main">{summary}</Link>
      <nav className="trip-actions" aria-label={`${trip.destination} 여행 바로가기`}>
        <TripAction to={`/trips/${trip.tripId}/items`} icon="checklist">체크리스트 보기</TripAction>
        <TripAction to={`/trips/${trip.tripId}/photos`} icon="camera">짐 사진으로 등록</TripAction>
        <TripAction to={`/trips/${trip.tripId}/rules`} icon="shield">반입 규정 확인</TripAction>
      </nav>
    </article>
  )
}

function TripAction({
  to, icon, children,
}: {
  to: string
  icon: 'checklist' | 'camera' | 'shield'
  children: React.ReactNode
}) {
  return (
    <Link to={to} className="trip-action">
      <svg viewBox="0 0 24 24" aria-hidden="true">
        {icon === 'checklist' && <><rect x="6" y="4" width="13" height="17" rx="2" /><path d="M9 2h7v4H9zM9 11l1.5 1.5L13 9.5M9 16l1.5 1.5L13 15.5M15 12h2M15 17h2" /></>}
        {icon === 'camera' && <><path d="M4 8h3l1.5-2h7L17 8h3v11H4z" /><circle cx="12" cy="13" r="3.5" /></>}
        {icon === 'shield' && <><path d="M12 3l7 3v5c0 4.7-2.7 8-7 10-4.3-2-7-5.3-7-10V6z" /><path d="M9 12l2 2 4-4" /></>}
      </svg>
      <span>{children}</span>
      <span className="trip-action-arrow" aria-hidden="true">›</span>
    </Link>
  )
}

/** 데모 배너의 여행 소품을 외부 이미지 없이 그린 장식용 SVG. */
function TravelBannerArt() {
  return (
    <svg className="trip-banner-art" viewBox="0 0 660 280" aria-hidden="true" focusable="false">
      <g className="trip-banner-clouds" fill="#fff">
        <g transform="translate(36 70)"><ellipse cx="28" cy="13" rx="28" ry="10" /><ellipse cx="49" cy="9" rx="20" ry="14" /></g>
        <g transform="translate(470 42)"><ellipse cx="30" cy="14" rx="31" ry="11" /><ellipse cx="53" cy="9" rx="21" ry="15" /></g>
      </g>
      <path className="trip-banner-route" d="M54 155 C78 80 164 50 266 64 S442 96 506 72" />
      <g transform="translate(254 53) rotate(8)">
        <path className="trip-banner-plane" d="M0 9L42 1c5-1 8 2 4 5L30 14l8 9-7 2-13-8-10 4-6-3 8-7z" />
      </g>

      <g className="trip-banner-city" opacity=".72">
        <path d="M385 202h23v-45h19v45h29v-68h20v68h25v-53h23v53h25v-79h18v79h30v23H385z" fill="#a8c0c7" />
        <path d="M329 202l36-75 38 75z" fill="#dbe3db" />
        <path d="M347 164l18-37 18 37z" fill="#fff" />
        <path d="M430 205l21-112 21 112M440 158h22M435 181h32M445 124h12" fill="none" stroke="#c65d3d" strokeWidth="5" />
        <path d="M552 203h35M569 203V79M560 113h18M563 96h12" fill="none" stroke="#86a7b3" strokeWidth="4" />
      </g>

      <g className="trip-banner-passport">
        <rect x="168" y="169" width="54" height="78" rx="4" />
        <circle cx="195" cy="207" r="13" />
        <path d="M182 207h26M195 194c8 8 8 18 0 26M195 194c-8 8-8 18 0 26" />
      </g>
      <g className="trip-banner-suitcase">
        <path d="M244 157v-14c0-7 5-11 11-11h24c6 0 11 4 11 11v14" />
        <rect x="228" y="153" width="83" height="99" rx="13" />
        <path d="M247 166v73M270 166v73M292 166v73" />
        <circle cx="246" cy="258" r="7" /><circle cx="294" cy="258" r="7" />
      </g>
      <g className="trip-banner-map">
        <path d="M357 194l54-20 54 15 54-21v67l-54 20-54-15-54 21z" />
        <path d="M411 174v66M465 189v66" />
        <path d="M447 213c0 10-12 23-12 23s-12-13-12-23a12 12 0 1 1 24 0z" />
        <circle cx="435" cy="213" r="4" />
      </g>
    </svg>
  )
}
