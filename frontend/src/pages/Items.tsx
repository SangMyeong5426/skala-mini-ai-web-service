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
  /** 조회 실패(error)와 구분한다. 수정·삭제·추가가 거절당한 것은 다른 사건이다 */
  const [actionError, setActionError] = useState<string | null>(null)
  /** 직접 추가 폼 (03:271) */
  const [newName, setNewName] = useState('')
  const [newQty, setNewQty] = useState(1)
  const [adding, setAdding] = useState(false)
  /** 채택 연타 방지 — 같은 후보가 두 번 들어간다 */
  const [adopting, setAdopting] = useState(false)
  const job = useAiJob<PackingListOutput>()

  const load = () => {
    setError(null)
    // 07:513-521 의 PACKING_LIST required 를 채우려면 여행 정보가 필요하다.
    api.get<TripDetail>(`/trips/${tripId}`).then(setTrip).catch(() => setTrip(null))
    api
      .get<ItemsResponse>(`/trips/${tripId}/items`)
      .then(async (r) => {
        // 추천 작업이 바뀌었으면 옛 배열에서 고른 위치는 의미가 없다
        setData((prev) => {
          if (prev && prev.recommendationJobId !== r.recommendationJobId) setPicked(new Set())
          return r
        })
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

  /*
   * S-06 의 `확인하기` 가 `#recommend` 로 보낸다(03:289). 해시만 붙이고 끝내면
   * 목록 맨 위가 보여서 무엇을 확인하라는 것인지 알 수 없다.
   */
  useEffect(() => {
    if (!data || window.location.hash !== '#recommend') return
    document.getElementById('recommend')?.scrollIntoView({ behavior: 'smooth' })
  }, [data])

  const recommend = async () => {
    /*
     * <b>고른 것을 먼저 비운다.</b> picked 는 후보 배열의 <b>위치</b>다
     * (06 의 candidateIndex). 새 추천이 오면 같은 위치에 다른 물건이 온다.
     * 비우지 않으면 사용자가 고르지 않은 후보가 새 jobId 와 옛 위치로 채택된다.
     */
    setPicked(new Set())
    // 07:470 — alreadyPacked 는 <b>PREPARED 만</b>이다. 전체를 보내면 아직 안 챙긴
    // 것까지 "이미 챙겼다" 로 넘어가 추천이 줄어든다.
    const alreadyPacked = (data?.items ?? [])
      .filter((i) => i.checkStatus === 'PREPARED')
      .map((i) => ({ name: i.name, category: i.category, qty: i.qty }))

    /*
     * <b>여행 정보가 없으면 요청을 걸지 않는다.</b>
     *
     * 예전에는 `?? ''` · `?? 'FLIGHT'` 로 빈칸을 메웠다. 07:466 은 destination 에
     * `minLength: 1` 과 `pattern: \S` 를, startDate·endDate 에 `format: date` 를
     * 요구하고, `purpose` enum 에는 null 이 없다. 즉 <b>스키마 위반을 만들어
     * 보내는 코드</b>였다. transport 를 FLIGHT 로 박는 것은 더 나쁘다 — 기차
     * 여행에 항공 기준으로 추천이 나온다.
     *
     * 07:121 의 "없을 수 있는 값은 null 을 허용하되 필드는 반드시 낸다" 는
     * <b>스키마가 null 을 허용한 필드</b>에만 해당한다. note 가 그렇다.
     *
     * S-04 의 자동 추천이 같은 판단을 이미 하고 있다 — 없으면 걸지 않는다.
     */
    if (!trip?.destination || !trip.startDate || !trip.endDate) {
      setActionError('여행 정보를 불러오는 중입니다. 잠시 후 다시 눌러 주세요.')
      return
    }

    // 07:513-521 required 7개를 전부 채운다.
    await job.start('PACKING_LIST', {
      destination: trip.destination,
      startDate: trip.startDate,
      endDate: trip.endDate,
      transport: trip.transport,
      purpose: trip.purpose ?? 'TOUR',
      note: trip.note ?? null,
      alreadyPacked,
    }, Number(tripId))
    load()
  }

  /** 06:97 — 이 PATCH 의 주요 오류는 400·404 다. 조용히 되돌리지 않는다. */
  const toggleDone = async (itemId: number, done: boolean) => {
    try {
      await api.patch(`/trips/${tripId}/items/${itemId}`, {
        checkStatus: done ? 'PREPARED' : 'UNCHECKED',
      })
      setActionError(null)
    } catch (e) {
      setActionError(e instanceof Error ? e.message : '변경하지 못했습니다.')
    }
    // 성공이든 실패든 서버 상태와 다시 맞춘다
    void load()
  }

  /** 03:272 · 06:98 — 내 목록에서 지운다. 추천 채택도 함께 풀린다(06:1010). */
  const removeItem = async (itemId: number) => {
    try {
      await api.del(`/trips/${tripId}/items/${itemId}`)
      setActionError(null)
    } catch (e) {
      setActionError(e instanceof Error ? e.message : '삭제하지 못했습니다.')
    }
    void load()
  }

  /**
   * 03:271 이 S-05 주요 요소로 정한 <b>직접 추가</b>.
   *
   * 사진 없이 시작하거나 추천이 실패해도 목록을 만들 수 있어야 한다. 서버의
   * 실패 안내도 "다시 시도하거나 직접 추가해 주세요" 인데 정작 그 경로가
   * 화면에 없었다.
   *
   * `recommendation` 을 빼고 보내면 서버가 `source: USER` 로 넣는다.
   * 실서버에 직접 확인했다 — 201 · source USER · checkStatus UNCHECKED.
   */
  const addDirect = async () => {
    const name = newName.trim()
    if (!name || adding) return
    setAdding(true)
    try {
      await api.post(`/trips/${tripId}/items`, {
        name, category: 'ETC', qty: newQty, priority: 'RECOMMENDED',
      })
      setNewName(''); setNewQty(1); setActionError(null)
    } catch (e) {
      setActionError(e instanceof Error ? e.message : '추가하지 못했습니다.')
    } finally {
      setAdding(false)
    }
    void load()
  }

  /**
   * 06:991 — "여러 후보를 선택하면 기존 단건 POST 를 후보별로 호출한다.
   * <b>일부 실패 시 성공한 항목은 유지하고 실패 후보만 재시도한다.</b>"
   *
   * 예전에는 try/catch 가 없어서 중간에 하나가 거절당하면 루프가 그 자리에서
   * 끊겼다. 남은 후보는 보내지지도 않고, 이미 등록된 것도 재조회를 안 해
   * "추가됨" 으로 바뀌지 않았다. 오류는 콘솔에만 남았다.
   */
  const adopt = async () => {
    if (!cands || !data?.recommendationJobId || adopting) return
    setAdopting(true)
    const failed = new Set<number>()
    for (const idx of picked) {
      const c = cands.items[idx]
      try {
        await api.post(`/trips/${tripId}/items`, {
          name: c.name, category: c.category, qty: c.qty, priority: c.priority,
          recommendation: { jobId: data.recommendationJobId, candidateIndex: idx },
        })
      } catch {
        failed.add(idx)
      }
    }
    // 실패한 후보만 선택으로 남긴다 — 그대로 다시 누르면 재시도가 된다
    setPicked(failed)
    setActionError(
      failed.size === 0 ? null : `${failed.size}개를 담지 못했습니다. 다시 눌러 주세요.`,
    )
    setAdopting(false)
    void load()
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
        {/* 조회 실패와 구분한다 — 목록은 이미 보이고 있고, 방금 한 동작만 거절당했다 */}
        {actionError && (
          <Failed title="반영하지 못했습니다" detail={actionError} onRetry={load} />
        )}
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
                      : <>미채택 <b>필수 후보 {warn}건</b></>}
                  </span>
                  <a href="#recommend" className="btn btn-ghost btn-sm">확인하기</a>
                </div>
              )}

              {items.length === 0 ? (
                <Empty
                  title="아직 담은 물품이 없습니다"
                  /* 03:274 — "내 목록이 비면 사진 등록·직접 추가 안내". 직접 추가
                     줄은 이 아래에 늘 열려 있으므로 여기서는 사진·추천만 권한다 */
                  action={
                    <div style={{ display: 'flex', gap: 8 }}>
                      <button type="button" className="btn" onClick={() => nav(`/trips/${tripId}/photos`)}>
                        사진 등록하기
                      </button>
                      <button
                        type="button" className="btn btn-ghost" onClick={recommend}
                        disabled={job.phase === 'running'}
                      >추천 받기</button>
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
                        {/* 03:272 가 이 화면의 호출 API 로 DELETE 를 적어 뒀다 */}
                        <button
                          type="button" className="btn btn-ghost btn-sm"
                          onClick={() => removeItem(i.itemId)}
                          aria-label={`${i.name} 삭제`}
                        >
                          삭제
                        </button>
                      </div>
                    </li>
                  ))}
                </ul>
              )}

              {/*
                * 03:271 주요 요소 — <b>직접 추가.</b> 목록이 비었을 때만 열지
                * 않는다. 사진에 안 찍힌 물건은 언제든 생긴다.
                *
                * 카테고리·우선순위는 묻지 않는다. 한 줄로 끝나야 실제로 쓴다 —
                * 서버 기본값(ETC · 권장)으로 넣고 필요하면 나중에 고친다.
                */}
              <form
                className="add-row"
                onSubmit={(e) => { e.preventDefault(); void addDirect() }}
              >
                <input
                  value={newName}
                  onChange={(e) => setNewName(e.target.value)}
                  placeholder="직접 추가할 물품 (예: 우산)"
                  maxLength={100}
                  aria-label="직접 추가할 물품 이름"
                />
                <input
                  type="number" min={1} max={99} value={newQty}
                  onChange={(e) => setNewQty(Math.min(99, Math.max(1, Number(e.target.value))))}
                  aria-label="수량"
                  style={{ width: 72 }}
                />
                <button type="submit" className="btn btn-sm" disabled={!newName.trim() || adding}>
                  {adding ? '추가하는 중…' : '추가'}
                </button>
              </form>
            </div>

            {/* ── 아래쪽: AI 추천 ── */}
            <div className="card" id="recommend">
              <div className="card-head">
                <h2 className="card-title">AI 추천</h2>
                <span className="card-sub">채택해야 내 목록에 들어갑니다</span>
                <span className="spacer" />
                {picked.size > 0 && (
                  <button type="button" className="btn btn-sm" onClick={adopt} disabled={adopting}>
                    {adopting ? '담는 중…' : `선택한 ${picked.size}개 추가`}
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
                    /*
                     * 07:655 문구 그대로다. SEASONAL 은 <b>두 경우</b>다 —
                     * 출발일이 16일을 넘거나, 날씨 조회가 실패했거나.
                     * "예보 범위를 넘어" 라고 단정하면 조회가 실패했을 때
                     * 틀린 이유를 말한다. 기준일이 없으면 그 사실만 알린다.
                     */
                    <p className="disclaimer">
                      {cands.weatherSource === 'FORECAST'
                        ? '실시간 예보를 반영했습니다'
                        : '실시간 예보가 아닌 계절 평균 기준입니다'}
                      {cands.weatherAsOf
                        ? ` · 데이터 시점 ${cands.weatherAsOf}`
                        : cands.weatherSource === 'SEASONAL' && ' · 날씨 자료를 받지 못했습니다'}
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
