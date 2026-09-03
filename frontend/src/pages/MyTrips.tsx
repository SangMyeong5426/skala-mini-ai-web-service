import { useEffect, useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { api } from '../api/client'
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
            <div className="grid grid-2">
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
  const to = past ? `/trips/${trip.tripId}` : `/trips/${trip.tripId}/photos`
  return (
    <Link to={to} className="card" style={{ display: 'block', color: 'inherit', textDecoration: 'none' }}>
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
    </Link>
  )
}
