import { useEffect, useState } from 'react'
import { useNavigate, useParams } from 'react-router-dom'
import { api } from '../api/client'
import { Shell, TopBar } from '../components/Shell'
import { Empty, Failed, Skeleton } from '../components/States'
import {
  CATEGORY_LABEL, PHOTO_STATUS_LABEL, SOURCE_LABEL,
  TRANSPORT_LABEL, kg, pct, period,
} from '../lib/format'
import type { ChecklistItem, ItemsResponse, TripDetail } from '../types/api'

/**
 * S-10 여행 기록 상세 — 지난 여행을 다시 본다 (UC-09).
 *
 * <b>읽기 전용이다.</b> 지난 여행의 체크리스트를 여기서 고칠 수 있으면 "기록"이
 * 아니게 된다. 바꾸고 싶으면 새 여행을 만들라는 것이 UC-09 의 재사용이다.
 *
 * <b>반입 판정은 보여주지 않는다.</b> 규정은 바뀐다 — 03-wireframe 이
 * "과거 규정 판정은 재사용하지 않고 새 여행 시 최신 규정으로 재판정" 이라고
 * 못박았다. 여기서 옛 판정을 보여주면 그것을 그대로 믿게 된다.
 *
 * `내 여행`(S-01)의 지난 여행 카드가 이리로 온다.
 */
export default function TripRecord() {
  const { tripId } = useParams()
  const nav = useNavigate()
  const [trip, setTrip] = useState<TripDetail | null>(null)
  const [items, setItems] = useState<ItemsResponse | null>(null)
  const [error, setError] = useState<string | null>(null)

  const load = () => {
    setError(null); setTrip(null); setItems(null)
    Promise.all([
      api.get<TripDetail>(`/trips/${tripId}`),
      api.get<ItemsResponse>(`/trips/${tripId}/items`),
    ])
      .then(([t, i]) => { setTrip(t); setItems(i) })
      .catch((e) => setError(e instanceof Error ? e.message : '알 수 없는 오류'))
  }
  useEffect(load, [tripId])

  const done = trip?.status === 'DONE'

  return (
    <Shell>
      <TopBar
        title={trip ? `${trip.origin} → ${trip.destination}` : '여행 기록'}
        sub={trip ? period(trip.startDate, trip.endDate) : undefined}
        right={
          /*
           * <b>`이 여행처럼` 이라고 말하지 않는다.</b> 눌러 봐야 빈 폼이 열린다 —
           * 여행 정보도 체크리스트도 옮겨 가지 않는다.
           *
           * 옮기지 않는 것이 <b>맞는 설계</b>다(팀 결정). 지난 여행을 베끼면 날짜도
           * 목적지도 다른 여행에 옛 목록이 통째로 딸려 온다. 대신 <b>추천이</b> 지난
           * 이력을 보고 그때 챙겼던 것을 다시 권하는 쪽으로 간다 — 사용자가 지우는
           * 일이 아니라 서비스가 고르는 일이 된다.
           *
           * 그 입력은 `07-ai-ready.md` 의 PACKING_LIST 에 아직 없다. 02·03 에
           * 보류로 적어 두었고, 여기서는 <b>하는 일 그대로</b> 이름을 단다.
           */
          <button type="button" className="btn" onClick={() => nav('/trips/new')}>
            새 여행 만들기
          </button>
        }
      />
      <div className="content">
        {error && <Failed title="여행을 불러오지 못했습니다" detail={error} onRetry={load} />}
        {!error && !trip && <div className="card"><Skeleton rows={4} /></div>}

        {trip && (
          <>
            <section className="card">
              <div className="card-head" style={{ marginBottom: 12 }}>
                <h2 className="card-title">여행 정보</h2>
                <span className="spacer" />
                <span className={`badge${done ? '' : ' badge-ok'}`}>
                  {done ? '완료' : trip.status === 'DRAFT' ? '작성 중' : '진행 중'}
                </span>
              </div>
              <dl className="kv">
                <Row label="구간" value={`${trip.origin} → ${trip.destination}`} />
                <Row label="기간" value={period(trip.startDate, trip.endDate)} />
                <Row label="이동수단" value={TRANSPORT_LABEL[trip.transport] ?? trip.transport} />
                {trip.airline && <Row label="항공사" value={trip.airline} />}
                {(trip.departureAirport || trip.arrivalAirport) && (
                  <Row label="공항" value={`${trip.departureAirport ?? '—'} → ${trip.arrivalAirport ?? '—'}`} />
                )}
                {trip.bagEmptyG != null && <Row label="빈 가방" value={kg(trip.bagEmptyG)} />}
                {trip.weightLimitG != null && <Row label="무게 한도" value={kg(trip.weightLimitG)} />}
                {trip.note && <Row label="메모" value={trip.note} />}
              </dl>
            </section>

            <section className="card" style={{ marginTop: 20 }}>
              <div className="card-head" style={{ marginBottom: 12 }}>
                <h2 className="card-title">그때의 체크리스트</h2>
                <span className="spacer" />
                <span className="card-sub">
                  {items ? `${items.items.length}개 · 준비 ${pct(items.completionRate)}` : ''}
                </span>
              </div>

              {!items && <Skeleton rows={3} />}
              {items && items.items.length === 0 && (
                <Empty title="체크리스트가 비어 있습니다" />
              )}
              {items && items.items.length > 0 && (
                <ul className="list">
                  {items.items.map((it) => <RecordRow key={it.itemId} item={it} />)}
                </ul>
              )}
            </section>

            <p className="disclaimer" style={{ marginTop: 16 }}>
              지난 여행의 기록입니다. 반입 규정은 바뀔 수 있어 그때의 판정은 함께 보여주지
              않습니다. 새 여행에서 최신 규정으로 다시 확인하세요.
            </p>
          </>
        )}
      </div>
    </Shell>
  )
}

function Row({ label, value }: { label: string; value: string }) {
  return (
    <div className="kv-row">
      <dt className="stat-label">{label}</dt>
      <dd>{value}</dd>
    </div>
  )
}

/** 읽기 전용 한 줄. 체크박스를 두지 않는다 — 지난 여행은 고치지 않는다. */
function RecordRow({ item }: { item: ChecklistItem }) {
  const prepared = item.checkStatus === 'PREPARED'
  return (
    <li className="list-row">
      <span className={`badge${prepared ? ' badge-ok' : ''}`} style={{ minWidth: 52 }}>
        {prepared ? '준비함' : '미준비'}
      </span>
      <span style={{ fontWeight: 600 }}>{item.name}</span>
      {item.qty > 1 && <span className="card-sub">×{item.qty}</span>}
      <span className="spacer" />
      <span className="card-sub">{CATEGORY_LABEL[item.category] ?? item.category}</span>
      <span className="badge">{SOURCE_LABEL[item.source] ?? item.source}</span>
      {item.photoStatus && (
        <span className="card-sub">{PHOTO_STATUS_LABEL[item.photoStatus] ?? item.photoStatus}</span>
      )}
    </li>
  )
}
