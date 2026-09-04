import { useEffect, useState } from 'react'
import { useNavigate, useParams } from 'react-router-dom'
import { api } from '../api/client'
import { Shell, TopBar } from '../components/Shell'
import { Empty, Failed, Skeleton } from '../components/States'
import { ITINERARY_KIND_LABEL, dayKey, dayLabel, daysBetween, hhmm, period } from '../lib/format'
import type { Itinerary, ItineraryKind, TripDetail } from '../types/api'

const KINDS: ItineraryKind[] = ['FLIGHT', 'LODGING', 'ACTIVITY', 'TRANSPORT', 'OTHER']

/**
 * S-11 여행 일정 — 날짜별로 항공편·숙소·일정을 본다.
 *
 * 달력 격자 대신 <b>여행 기간을 날짜로 펼친다.</b> 3~4일짜리 여행에서 한 달을
 * 그리면 대부분이 빈칸이고, 정작 필요한 "그날 몇 시에 무엇" 이 안 보인다.
 * 일정이 없는 날도 줄을 남겨 비어 있음을 드러낸다.
 *
 * 여행 기간 밖의 일정도 버리지 않는다 — 시각을 잘못 넣었을 때 사라지면
 * 고칠 수가 없다. 맨 아래 "기간 밖" 으로 모아 보여준다.
 */
export default function ItineraryPage() {
  const { tripId } = useParams()
  const nav = useNavigate()
  const [trip, setTrip] = useState<TripDetail | null>(null)
  const [items, setItems] = useState<Itinerary[] | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [adding, setAdding] = useState(false)
  const [busy, setBusy] = useState(false)
  const [formError, setFormError] = useState<string | null>(null)

  const load = () => {
    setError(null)
    Promise.all([
      api.get<TripDetail>(`/trips/${tripId}`),
      api.get<{ itineraries: Itinerary[] }>(`/trips/${tripId}/itineraries`),
    ])
      .then(([t, r]) => { setTrip(t); setItems(r.itineraries) })
      .catch((e) => setError(e instanceof Error ? e.message : '알 수 없는 오류'))
  }
  useEffect(load, [tripId])

  const add = (e: React.FormEvent<HTMLFormElement>) => {
    e.preventDefault()
    const f = new FormData(e.currentTarget)
    const startAt = String(f.get('startAt') ?? '')
    if (!startAt) { setFormError('시작 시각은 필수입니다.'); return }
    const endAt = String(f.get('endAt') ?? '')
    setBusy(true); setFormError(null)
    api.post(`/trips/${tripId}/itineraries`, {
      kind: f.get('kind'),
      title: String(f.get('title') ?? '').trim(),
      place: String(f.get('place') ?? '').trim() || null,
      code: String(f.get('code') ?? '').trim() || null,
      // datetime-local 은 시간대가 없다. 브라우저 시간대로 해석해 ISO 로 보낸다.
      startAt: new Date(startAt).toISOString(),
      endAt: endAt ? new Date(endAt).toISOString() : null,
      note: String(f.get('note') ?? '').trim() || null,
    })
      .then(() => { setAdding(false); load() })
      .catch((err) => setFormError(err instanceof Error ? err.message : '추가하지 못했습니다'))
      .finally(() => setBusy(false))
  }

  const remove = (id: number) => {
    setBusy(true)
    api.del(`/trips/${tripId}/itineraries/${id}`)
      .then(load)
      .catch((e) => setError(e instanceof Error ? e.message : '삭제하지 못했습니다'))
      .finally(() => setBusy(false))
  }

  const days = trip ? daysBetween(trip.startDate, trip.endDate) : []
  const byDay = new Map<string, Itinerary[]>()
  for (const it of items ?? []) {
    const k = dayKey(it.startAt)
    byDay.set(k, [...(byDay.get(k) ?? []), it])
  }
  for (const list of byDay.values()) list.sort((a, b) => a.startAt.localeCompare(b.startAt))
  const outside = (items ?? []).filter((it) => !days.includes(dayKey(it.startAt)))

  return (
    <Shell>
      <TopBar
        title="여행 일정"
        sub={trip ? period(trip.startDate, trip.endDate) : undefined}
        right={
          <button type="button" className="btn" onClick={() => setAdding((v) => !v)}>
            {adding ? '취소' : '＋ 일정 추가'}
          </button>
        }
      />
      <div className="content">
        {error && <Failed title="일정을 불러오지 못했습니다" detail={error} onRetry={load} />}
        {!error && !trip && <div className="card"><Skeleton rows={4} /></div>}

        {adding && (
          <form className="card" onSubmit={add} style={{ marginBottom: 20 }}>
            <h2 className="card-title" style={{ marginBottom: 12 }}>새 일정</h2>
            <div className="grid grid-2" style={{ gap: 12 }}>
              <label className="field">
                <span className="stat-label">종류</span>
                <select name="kind" className="input" defaultValue="ACTIVITY">
                  {KINDS.map((k) => <option key={k} value={k}>{ITINERARY_KIND_LABEL[k]}</option>)}
                </select>
              </label>
              <label className="field">
                <span className="stat-label">제목 *</span>
                <input name="title" className="input" required maxLength={100} placeholder="인천 → 나리타" />
              </label>
              <label className="field">
                <span className="stat-label">장소</span>
                <input name="place" className="input" maxLength={100} placeholder="ICN" />
              </label>
              <label className="field">
                <span className="stat-label">편명·예약번호</span>
                <input name="code" className="input" maxLength={50} placeholder="KE703" />
              </label>
              <label className="field">
                <span className="stat-label">시작 *</span>
                <input name="startAt" type="datetime-local" className="input" required />
              </label>
              <label className="field">
                <span className="stat-label">종료</span>
                <input name="endAt" type="datetime-local" className="input" />
              </label>
            </div>
            <label className="field" style={{ marginTop: 12 }}>
              <span className="stat-label">메모</span>
              <input name="note" className="input" placeholder="2시간 30분" />
            </label>
            {formError && <p className="card-sub" role="alert" style={{ marginTop: 8 }}>{formError}</p>}
            <div style={{ marginTop: 14 }}>
              <button type="submit" className="btn" disabled={busy}>{busy ? '저장 중' : '추가'}</button>
            </div>
          </form>
        )}

        {trip && items === null && <div className="card"><Skeleton rows={3} /></div>}

        {trip && items !== null && items.length === 0 && !adding && (
          <div className="card">
            <Empty
              title="등록한 일정이 없습니다"
              action={<button type="button" className="btn" onClick={() => setAdding(true)}>첫 일정 추가</button>}
            />
          </div>
        )}

        {trip && items !== null && items.length > 0 && (
          <section>
            {days.map((d) => (
              <div className="card" key={d} style={{ marginBottom: 14 }}>
                <div className="card-head" style={{ marginBottom: 10 }}>
                  <h2 className="card-title">{dayLabel(d)}</h2>
                  <span className="spacer" />
                  <span className="card-sub">{byDay.get(d)?.length ?? 0}건</span>
                </div>
                {(byDay.get(d) ?? []).length === 0 && <p className="card-sub">일정 없음</p>}
                <ul className="list">
                  {(byDay.get(d) ?? []).map((it) => (
                    <Row key={it.itineraryId} it={it} busy={busy} onDelete={remove} />
                  ))}
                </ul>
              </div>
            ))}

            {outside.length > 0 && (
              <div className="card">
                <div className="card-head" style={{ marginBottom: 10 }}>
                  <h2 className="card-title">기간 밖</h2>
                  <span className="spacer" />
                  <span className="card-sub">시각을 확인하세요</span>
                </div>
                <ul className="list">
                  {outside.map((it) => (
                    <Row key={it.itineraryId} it={it} busy={busy} onDelete={remove} showDate />
                  ))}
                </ul>
              </div>
            )}
          </section>
        )}

        <button
          type="button"
          className="btn btn-ghost"
          style={{ marginTop: 8 }}
          onClick={() => nav(`/trips/${tripId}/photos`)}
        >
          짐 준비로 돌아가기
        </button>
      </div>
    </Shell>
  )
}

function Row({
  it, busy, onDelete, showDate,
}: {
  it: Itinerary; busy: boolean; onDelete: (id: number) => void; showDate?: boolean
}) {
  return (
    <li className="list-row">
      <span className="badge">{ITINERARY_KIND_LABEL[it.kind] ?? it.kind}</span>
      <span style={{ fontVariantNumeric: 'tabular-nums', minWidth: showDate ? 96 : 44 }}>
        {showDate ? `${dayLabel(dayKey(it.startAt))} ` : ''}{hhmm(it.startAt)}
        {it.endAt && `–${hhmm(it.endAt)}`}
      </span>
      <span style={{ fontWeight: 600 }}>{it.title}</span>
      {it.place && <span className="card-sub">{it.place}</span>}
      {it.code && <span className="badge">{it.code}</span>}
      {it.note && <span className="card-sub">{it.note}</span>}
      <span className="spacer" />
      <button
        type="button"
        className="btn btn-ghost btn-sm"
        disabled={busy}
        onClick={() => onDelete(it.itineraryId)}
      >
        삭제
      </button>
    </li>
  )
}
