import { useEffect, useRef, useState } from 'react'
import { useNavigate, useParams } from 'react-router-dom'
import { api } from '../api/client'
import { Shell, Steps, TopBar } from '../components/Shell'
import { AiPending, Disclaimer, Empty, Failed, Skeleton } from '../components/States'
import { useAiJob } from '../hooks/useAiJob'
import { headroom, kg, WEIGHT_BAR_CLASS, WEIGHT_VERDICT_LABEL } from '../lib/format'
import type { Inspection, TripDetail, WeightEstimateOutput } from '../types/api'

/** 계산에서 뺀 이유. 코드로 오므로 화면 말로 바꾼다 (07 excluded.reason). */
const EXCLUDED_REASON: Record<string, string> = {
  UNCHECKED: '아직 안 챙김',
  NOT_IN_PHOTO: '사진에서 미확인',
  PENDING_APPROVAL: '승인 전',
  NO_WEIGHT_INFO: '무게 정보 없음',
}

const VERDICT_CLASS: Record<string, string> = {
  ROOM: 'badge-ok', NEAR: 'badge-warn', OVER_RISK: 'badge-danger', UNKNOWN: 'badge',
}

/**
 * S-07 무게 상세 — 무게가 왜 그렇게 나왔는지 본다 (UC-10).
 *
 * S-06 은 범위와 제외 <b>개수</b>만 보여준다. 여기는 <b>품목별 기여도와 제외
 * 이유</b>까지 펼친다. 그 둘은 `GET /inspection` 의 투영에 없고 작업 출력에만
 * 있어서(07 "inspection.weight 는 이 출력의 투영"), 이 화면이 작업을 직접 건다.
 *
 * <b>입력을 S-06 과 똑같이 만든다.</b> 서버가 같은 입력을 스스로 재구성해
 * 대조하고 한 글자라도 다르면 409 STALE_WEIGHT_INPUT 이다. 그래서 값을
 * 보정하지 않는다 — 가방 값에 기본값을 채우지 않고, excluded 의 reason 은
 * 언제나 `UNCHECKED` 다.
 */
export default function Weight() {
  const { tripId } = useParams()
  const nav = useNavigate()
  const job = useAiJob<WeightEstimateOutput>()
  const [error, setError] = useState<string | null>(null)
  const [empty, setEmpty] = useState(false)
  const started = useRef(false)

  useEffect(() => {
    if (started.current) return
    started.current = true
    setError(null); setEmpty(false)

    Promise.all([
      api.get<TripDetail>(`/trips/${tripId}`),
      api.get<Inspection>(`/trips/${tripId}/inspection`),
    ])
      .then(([trip, insp]) => {
        const prepared = insp.readiness?.prepared ?? []
        const unprepared = insp.readiness?.unprepared ?? []
        if (prepared.length === 0) { setEmpty(true); return }
        return job.start('WEIGHT_ESTIMATE', {
          bagType: trip.bagType ?? null,
          bagEmptyG: trip.bagEmptyG ?? null,
          weightLimitG: trip.weightLimitG ?? null,
          items: prepared.map((i) => ({ itemId: i.itemId, name: i.name, qty: i.qty })),
          excluded: unprepared.map((i) => ({ name: i.name, reason: 'UNCHECKED' })),
        }, Number(tripId)).then(() => undefined)
      })
      .catch((e) => setError(e instanceof Error ? e.message : '알 수 없는 오류'))
  }, [tripId, job])

  const retry = () => { started.current = false; job.reset(); setError(null); setEmpty(false) }
  const w = job.output
  /** 막대를 어디까지 채울지. 한도가 없으면 최대값을 100% 로 본다. */
  const scale = w ? (w.limitG ?? w.maxG) : 0

  return (
    <Shell>
      <TopBar
        title="무게 상세"
        sub="품목별 기여도와 계산에서 뺀 항목"
        right={
          <button type="button" className="btn btn-ghost" onClick={() => nav(`/trips/${tripId}/inspection`)}>
            검수 결과로
          </button>
        }
      />
      {/*
        * 단계 표시줄을 그대로 둔다. S-07·S-08 은 흐름을 벗어난 곳이 아니라
        * <b>3단계 검수의 상세</b>다(03:55). 여기서 표시줄이 사라지면 사용자는
        * 준비 흐름에서 튕겨 나온 것으로 읽고 돌아갈 길을 헤맨다.
        */}
      <Steps current={3} tripId={tripId} />

      <div className="content">
        {error && <Failed title="무게를 계산하지 못했습니다" detail={error} onRetry={retry} />}

        {empty && (
          <div className="card">
            <Empty
              title="계산할 물품이 없습니다"
              action={
                <button type="button" className="btn" onClick={() => nav(`/trips/${tripId}/items`)}>
                  체크리스트로
                </button>
              }
            />
          </div>
        )}

        {!error && !empty && job.phase === 'running' && (
          <div className="card"><AiPending label="예상 무게를 계산하는 중" polls={job.polls} /></div>
        )}
        {!error && !empty && job.phase === 'idle' && <div className="card"><Skeleton rows={4} /></div>}

        {job.phase === 'failed' && (
          <Failed title="무게를 계산하지 못했습니다" detail={job.error ?? ''} onRetry={retry} />
        )}
        {job.phase === 'timeout' && (
          <Failed title="시간이 오래 걸립니다" detail="작업은 서버에 남아 있습니다." onRetry={retry} />
        )}

        {w && (
          <>
            <section className="card">
              <div className="card-head" style={{ marginBottom: 12 }}>
                <h2 className="card-title">예상 무게</h2>
                <span className="spacer" />
                <span className={`badge ${VERDICT_CLASS[w.verdict] ?? ''}`}>
                  {WEIGHT_VERDICT_LABEL[w.verdict] ?? w.verdict}
                </span>
              </div>

              <p style={{ fontSize: 28, fontWeight: 700, margin: '0 0 4px' }}>
                {kg(w.typicalG)}
                <span className="card-sub" style={{ fontSize: 15, fontWeight: 500, marginLeft: 10 }}>
                  {kg(w.minG)} — {kg(w.maxG)}
                </span>
              </p>
              <p className="card-sub">
                신뢰도 {w.confidence === 'HIGH' ? '높음' : w.confidence === 'MEDIUM' ? '보통' : '낮음'}
                {w.confidenceReason && ` · ${w.confidenceReason}`}
              </p>

              {/* 한도 대비 막대. S-06 과 같은 규칙으로 칠한다 — 두 화면이 다른 색이면 안 된다 */}
              {w.limitG != null && (
                <div className="bar bar-lg" style={{ margin: '14px 0 10px' }}>
                  <span
                    className={WEIGHT_BAR_CLASS[w.verdict] ?? ''}
                    style={{ width: `${Math.min(100, Math.round((w.typicalG / w.limitG) * 100))}%` }}
                  />
                </div>
              )}

              <dl className="kv" style={{ marginTop: 14 }}>
                {w.bagEmptyG != null && (
                  <div className="kv-row"><dt className="stat-label">빈 가방</dt><dd>{kg(w.bagEmptyG)}</dd></div>
                )}
                <div className="kv-row">
                  <dt className="stat-label">항공사 한도</dt>
                  <dd>{w.limitG != null ? kg(w.limitG) : '입력하지 않음'}</dd>
                </div>
                {headroom(w.typicalG, w.limitG) && (
                  <div className="kv-row">
                    <dt className="stat-label">한도까지</dt>
                    <dd>{headroom(w.typicalG, w.limitG)}</dd>
                  </div>
                )}
              </dl>
            </section>

            <section className="card" style={{ marginTop: 20 }}>
              <div className="card-head" style={{ marginBottom: 12 }}>
                <h2 className="card-title">품목별 기여도</h2>
                <span className="spacer" />
                <span className="card-sub">{w.contributions.length}개 · 무거운 순</span>
              </div>
              {w.contributions.length === 0 && <Empty title="계산에 들어간 물품이 없습니다" />}
              <ul className="list">
                {w.contributions.map((c) => (
                  <li key={c.name} className="list-col">
                    <div style={{ display: 'flex', alignItems: 'baseline', gap: 8 }}>
                      <span style={{ fontWeight: 600 }}>{c.name}</span>
                      {c.qty > 1 && <span className="card-sub">×{c.qty}</span>}
                      <span className="spacer" />
                      <span style={{ fontWeight: 700 }}>{kg(c.subtotalG)}</span>
                    </div>
                    <div className="bar" style={{ marginTop: 6 }}>
                      <span style={{ width: `${scale ? Math.min(100, Math.round((c.subtotalG / scale) * 100)) : 0}%` }} />
                    </div>
                    <p className="card-sub" style={{ marginTop: 4 }}>
                      한 개 {c.minG}–{c.typicalG}–{c.maxG}g
                    </p>
                  </li>
                ))}
              </ul>
            </section>

            <section className="card" style={{ marginTop: 20 }}>
              <div className="card-head" style={{ marginBottom: 12 }}>
                <h2 className="card-title">계산에서 뺀 항목</h2>
                <span className="spacer" />
                <span className="card-sub">{w.excludedCount}개</span>
              </div>
              {w.excluded.length === 0 && <Empty title="뺀 항목이 없습니다" />}
              {w.excluded.length > 0 && (
                <ul className="list">
                  {w.excluded.map((x, i) => (
                    <li key={`${x.name}-${i}`} className="list-row">
                      <span style={{ fontWeight: 600 }}>{x.name}</span>
                      <span className="spacer" />
                      <span className="badge badge-warn">
                        {EXCLUDED_REASON[x.reason] ?? x.reason}
                      </span>
                    </li>
                  ))}
                </ul>
              )}
            </section>

            <Disclaimer>
              예상 무게는 참고용 추정치입니다. 탑승 전 실제 저울로 측정하세요.
            </Disclaimer>
          </>
        )}
      </div>
    </Shell>
  )
}
