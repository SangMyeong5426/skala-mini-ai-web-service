import { useEffect, useState } from 'react'
import { useNavigate, useParams } from 'react-router-dom'
import { api } from '../api/client'
import { Shell, Steps, TopBar } from '../components/Shell'
import { AiPending, Empty, Failed, Skeleton } from '../components/States'
import { useAiJob } from '../hooks/useAiJob'
import { CATEGORY_LABEL, pct, PHOTO_STATUS_LABEL, SOURCE_LABEL } from '../lib/format'
import type { ItemsResponse, PackingListOutput, TripDetail } from '../types/api'

/**
 * S-05 내 체크리스트 · AI 추천.
 *
 * <b>두 목록은 다른 것이다</b> (Notion 개정안 1절).
 *   위 — 내 체크리스트: 챙긴 것 + 챙기기로 한 것. 완료율에 <b>들어간다</b>
 *   아래 — AI 추천: 검토할 후보. 채택 전에는 완료율에 <b>안 들어간다</b>
 *
 * "추천 승인" 은 챙길 목록에 넣겠다는 뜻이지 실제로 챙겼다는 뜻이 아니다.
 * 그래서 채택한 항목의 초기 상태는 미완료(UNCHECKED)다.
 */
export default function Items() {
  const { tripId = '1' } = useParams()
  const nav = useNavigate()
  const [data, setData] = useState<ItemsResponse | null>(null)
  const [trip, setTrip] = useState<TripDetail | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [cands, setCands] = useState<PackingListOutput | null>(null)
  const [picked, setPicked] = useState<Set<number>>(new Set())
  const job = useAiJob<PackingListOutput>()

  const load = () => {
    setError(null)
    // 07:513-521 의 PACKING_LIST required 를 채우려면 여행 정보가 필요하다.
    api.get<TripDetail>(`/trips/${tripId}`).then(setTrip).catch(() => setTrip(null))
    api
      .get<ItemsResponse>(`/trips/${tripId}/items`)
      .then(async (r) => {
        setData(r)
        // 추천 후보는 완료된 PACKING_LIST 작업에서 읽는다.
        // 여기서 실패해도 내 목록은 이미 받았으므로 화면 전체를 오류로 만들지 않는다.
        if (r.recommendationJobId) {
          try {
            const j = await api.get<{ output: PackingListOutput | null }>(`/ai-jobs/${r.recommendationJobId}`)
            setCands(j.output)
          } catch {
            setCands(null)
          }
        }
      })
      .catch((e) => setError(e instanceof Error ? e.message : '알 수 없는 오류'))
  }
  useEffect(load, [tripId])

  const recommend = async () => {
    // 07:470 — alreadyPacked 는 <b>PREPARED 만</b>이다. 전체를 보내면 아직 안 챙긴
    // 것까지 "이미 챙겼다" 로 넘어가 추천이 줄어든다.
    const alreadyPacked = (data?.items ?? [])
      .filter((i) => i.checkStatus === 'PREPARED')
      .map((i) => ({ name: i.name, category: i.category, qty: i.qty }))

    // 07:513-521 required 7개를 전부 채운다. 07:121 "없을 수 있는 값은 null 을
    // 허용하되 필드 자체는 반드시 낸다".
    await job.start('PACKING_LIST', {
      destination: trip?.destination ?? '',
      startDate: trip?.startDate ?? '',
      endDate: trip?.endDate ?? '',
      transport: trip?.transport ?? 'FLIGHT',
      purpose: trip?.purpose ?? null,
      note: trip?.note ?? null,
      alreadyPacked,
    }, Number(tripId))
    load()
  }

  const toggleDone = async (itemId: number, done: boolean) => {
    await api.patch(`/trips/${tripId}/items/${itemId}`, {
      checkStatus: done ? 'PREPARED' : 'UNCHECKED',
    })
    load()
  }

  const adopt = async () => {
    if (!cands || !data?.recommendationJobId) return
    for (const idx of picked) {
      const c = cands.items[idx]
      await api.post(`/trips/${tripId}/items`, {
        name: c.name, category: c.category, qty: c.qty, priority: c.priority,
        recommendation: { jobId: data.recommendationJobId, candidateIndex: idx },
      })
    }
    setPicked(new Set())
    load()
  }

  const items = data?.items ?? []
  // 비율만 보여주면 "몇 개 더 챙겨야 하나" 를 셈해야 안다. 개수를 함께 낸다.
  const doneCount = items.filter((i) => i.checkStatus === 'PREPARED').length
  const mine = new Set(items.map((i) => i.name.trim()))
  // 이미 내 목록에 있는 후보는 "추가됨" 으로 표시해 재선택을 막는다.
  // 채택 여부는 서버가 준 acceptedItemId 로 본다. 이름으로만 맞추면 사용자가
  // 이름을 고친 순간 "추가됨" 이 풀린다 — 05-erd 226행이 경고하는 그 문제다.
  // 이름 비교는 서버가 아직 연결을 못 준 후보를 위한 보조 수단으로만 남긴다.
  const open = (cands?.items ?? []).map((c, i) => ({
    c, i, added: c.acceptedItemId != null || mine.has(c.name.trim()),
  }))
  const warn = data?.unacceptedRequiredCount

  return (
    <Shell>
      <TopBar
        title="내 체크리스트"
        sub="챙긴 물품을 관리하고, 아래 추천에서 필요한 것만 고르세요"
        right={
          <>
            <button type="button" className="btn btn-ghost" onClick={recommend} disabled={job.phase === 'running'}>
              추천 받기
            </button>
            <button type="button" className="btn" onClick={() => nav(`/trips/${tripId}/inspection`)}>
              검수하기
            </button>
          </>
        }
      />
      <Steps current={3} tripId={tripId} />

      <div className="content">
        {error && <Failed title="체크리스트를 불러오지 못했습니다" detail={error} onRetry={load} />}
        {!error && data === null && <div className="card"><Skeleton rows={4} /></div>}

        {data && (
          <>
            {/* ── 위쪽: 내 체크리스트 ── */}
            <div className="card">
              <div className="card-head">
                <h2 className="card-title">내 체크리스트</h2>
                <span className="card-sub">{items.length}개</span>
                <span className="spacer" />
                <div style={{ textAlign: 'right' }}>
                  <span className="stat-label">준비 완료 </span>
                  <b style={{ fontSize: 16 }}>{doneCount} / {items.length}</b>
                  <span className="stat-label"> · {pct(data.completionRate)}</span>
                </div>
              </div>

              <div className="bar bar-lg" style={{ marginBottom: 14 }}>
                <span style={{ width: `${Math.round(data.completionRate * 100)}%` }} />
              </div>

              {warn !== 0 && (
                <div className="notice-warn">
                  <span>
                    {warn == null
                      ? '필수 추천 확인 전입니다'
                      : <>아직 채택하지 않은 <b>필수 후보 {warn}건</b>이 있습니다</>}
                  </span>
                  <a href="#recommend" className="btn btn-ghost btn-sm">확인하기</a>
                </div>
              )}

              {items.length === 0 ? (
                <Empty
                  title="아직 담은 물품이 없습니다"
                  action={
                    <div style={{ display: 'flex', gap: 8 }}>
                      <button type="button" className="btn" onClick={() => nav(`/trips/${tripId}/photos`)}>
                        사진 등록하기
                      </button>
                      <button type="button" className="btn btn-ghost" onClick={recommend}>추천 받기</button>
                    </div>
                  }
                />
              ) : (
                <ul>
                  {items.map((i) => (
                    <li key={i.itemId} className="row">
                      <input
                        type="checkbox"
                        checked={i.checkStatus === 'PREPARED'}
                        onChange={(e) => toggleDone(i.itemId, e.target.checked)}
                        aria-label={`${i.name} 챙김 완료`}
                      />
                      <div className="row-main">
                        <p className="row-name">
                          {i.name} <span className="card-sub">× {i.qty}</span>
                          {i.priority === 'REQUIRED' && <span className="badge badge-warn" style={{ marginLeft: 6 }}>필수</span>}
                        </p>
                        <p className="row-sub">
                          {CATEGORY_LABEL[i.category]} · {SOURCE_LABEL[i.source]}
                        </p>
                      </div>
                      <div className="row-right">
                        <span className={`badge${i.photoStatus === 'CONFIRMED' ? ' badge-ok' : i.photoStatus === 'NEEDS_CHECK' ? ' badge-warn' : ''}`}>
                          {PHOTO_STATUS_LABEL[i.photoStatus]}
                        </span>
                      </div>
                    </li>
                  ))}
                </ul>
              )}
            </div>

            {/* ── 아래쪽: AI 추천 ── */}
            <div className="card" id="recommend">
              <div className="card-head">
                <h2 className="card-title">AI 추천</h2>
                <span className="card-sub">채택해야 내 목록에 들어갑니다</span>
                <span className="spacer" />
                {picked.size > 0 && (
                  <button type="button" className="btn btn-sm" onClick={adopt}>
                    선택한 {picked.size}개 추가
                  </button>
                )}
              </div>

              {job.phase === 'running' && <AiPending label="추가 준비물을 추천하는 중" polls={job.polls} />}
              {job.phase === 'failed' && (
                <Failed title="추천을 만들지 못했습니다" detail={job.error ?? ''} onRetry={recommend} />
              )}
              {/* 06:537-538 — 60회를 넘기면 "시간이 오래 걸립니다" 와 재시도 버튼.
                  작업은 서버에 남으므로 사라졌다고 말하지 않는다. */}
              {job.phase === 'timeout' && (
                <Failed title="시간이 오래 걸립니다" detail="작업은 서버에 남아 있습니다" onRetry={recommend} />
              )}

              {job.phase !== 'running' && open.length === 0 && (
                <Empty
                  title="추가 추천이 없습니다"
                  action={<button type="button" className="btn btn-ghost" onClick={recommend}>추천 받기</button>}
                />
              )}

              {open.length > 0 && (
                <>
                  <ul>
                    {open.map(({ c, i, added }) => (
                      <li key={i} className="row">
                        <input
                          type="checkbox"
                          disabled={added}
                          checked={picked.has(i)}
                          onChange={(e) => {
                            const n = new Set(picked)
                            e.target.checked ? n.add(i) : n.delete(i)
                            setPicked(n)
                          }}
                          aria-label={`${c.name} 선택`}
                        />
                        <div className="row-main">
                          <p className="row-name">
                            {c.name} <span className="card-sub">× {c.qty}</span>
                            {c.priority === 'REQUIRED' && <span className="badge badge-warn" style={{ marginLeft: 6 }}>필수</span>}
                          </p>
                          {c.reason && <p className="row-sub">{c.reason}</p>}
                        </div>
                        <div className="row-right">
                          {added && <span className="badge badge-ok">추가됨</span>}
                        </div>
                      </li>
                    ))}
                  </ul>
                  {cands && (
                    <p className="disclaimer">
                      {cands.weatherSource === 'FORECAST'
                        ? '실시간 예보를 반영했습니다'
                        : '예보 범위를 넘어 계절 평균을 썼습니다'}
                      {cands.weatherAsOf && ` · 데이터 시점 ${cands.weatherAsOf}`}
                    </p>
                  )}
                </>
              )}
            </div>
          </>
        )}
      </div>
    </Shell>
  )
}
