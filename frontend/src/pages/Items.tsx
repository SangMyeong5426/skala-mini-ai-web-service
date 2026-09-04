import { useEffect, useState } from 'react'
import { useNavigate, useParams } from 'react-router-dom'
import { api } from '../api/client'
import { Shell, Steps, TopBar } from '../components/Shell'
import { AiPending, Empty, Failed, Skeleton } from '../components/States'
import { useAiJob } from '../hooks/useAiJob'
import { CATEGORY_LABEL, pct, PHOTO_STATUS_LABEL, SOURCE_LABEL } from '../lib/format'
import type { ChecklistItem, ItemsResponse, PackingListOutput, TripDetail } from '../types/api'

/**
 * S-05 내 체크리스트 · AI 추천.
 *
 * <b>두 목록은 다른 것이다</b> (Notion 개정안 1절).
 *   왼쪽 — 내 체크리스트: 챙긴 것 + 챙기기로 한 것. 완료율에 <b>들어간다</b>
 *   오른쪽 — AI 추천: 검토할 후보. 채택 전에는 완료율에 <b>안 들어간다</b>
 *
 * <b>위아래가 아니라 좌우다.</b> 쌓아 두면 추천이 화면 밖으로 밀려서, 내 목록을
 * 보며 무엇이 빠졌는지 견주는 일이 스크롤 왕복이 된다. 두 목록은 <b>견주라고</b>
 * 있는 것이라 나란히 놓는다.
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
  /** 조회 실패(error)와 구분한다. 수정·삭제·추가가 거절당한 것은 다른 사건이다 */
  const [actionError, setActionError] = useState<string | null>(null)
  /** 직접 추가 폼 (03:271) */
  const [newName, setNewName] = useState('')
  const [newQty, setNewQty] = useState(1)
  const [adding, setAdding] = useState(false)
  /**
   * 지금 담고 있는 후보의 위치. 연타로 같은 것이 두 번 들어가는 것을 막고,
   * 누른 버튼에만 `담는 중` 을 띄운다. `'all'` 은 전체 추가다.
   */
  const [adopting, setAdopting] = useState<number | 'all' | null>(null)
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

  /*
   * S-06 의 `확인하기` 가 `#recommend` 로 보낸다(03:289). 해시만 붙이고 끝내면
   * 목록 맨 위가 보여서 무엇을 확인하라는 것인지 알 수 없다.
   */
  useEffect(() => {
    if (!data || window.location.hash !== '#recommend') return
    document.getElementById('recommend')?.scrollIntoView({ behavior: 'smooth' })
  }, [data])

  const recommend = async () => {
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
   * 내 목록의 <b>이름·수량을 고친다.</b> 06:97 의 PATCH 다.
   *
   * 예전에는 이 화면에 수정이 아예 없었다. 사진에서 "생수 1" 로 잡힌 것을
   * 2 로 고치려면 지우고 다시 넣어야 했고, 그러면 인식과의 연결과 출처가
   * 함께 사라졌다. 수량 하나 고치자고 잃을 것이 아니다.
   */
  const editItem = async (itemId: number, patch: { name?: string; qty?: number }) => {
    try {
      await api.patch(`/trips/${tripId}/items/${itemId}`, patch)
      setActionError(null)
    } catch (e) {
      setActionError(e instanceof Error ? e.message : '수정하지 못했습니다.')
    }
    void load()
  }

  /**
   * 후보 하나를 채택한다. 06:991 의 <b>단건 POST</b> 가 원래 규약이고,
   * 전체 추가도 이것을 돌려 쓴다.
   *
   * <b>고르기 단계를 없앴다.</b> 예전에는 왼쪽 체크박스로 표시해 두고 위쪽
   * `선택한 n개 추가` 를 눌러야 들어갔다 — 한 개를 담는 데도 두 번을 눌러야
   * 했고, 담는 버튼은 고른 것이 하나라도 있어야 나타나서 처음 온 사용자에게는
   * 채택하는 길이 아예 안 보였다.
   *
   * 지금은 각 줄 오른쪽의 `추가` 가 곧 채택이다. 왼쪽 체크박스는 내 목록의
   * <b>챙김 완료</b> 하나만 쓴다 — 03:353 이 두 종류의 체크를 구분하라고 한
   * 것이 이 뜻이다.
   */
  const adoptOne = async (idx: number): Promise<boolean> => {
    if (!cands || !data?.recommendationJobId) return false
    const c = cands.items[idx]
    try {
      await api.post(`/trips/${tripId}/items`, {
        name: c.name, category: c.category, qty: c.qty, priority: c.priority,
        recommendation: { jobId: data.recommendationJobId, candidateIndex: idx },
      })
      return true
    } catch {
      return false
    }
  }

  const adoptSingle = async (idx: number) => {
    if (adopting !== null) return
    setAdopting(idx)
    const ok = await adoptOne(idx)
    setActionError(ok ? null : '담지 못했습니다. 다시 눌러 주세요.')
    setAdopting(null)
    void load()
  }

  /**
   * 06:991 — "여러 후보를 선택하면 기존 단건 POST 를 후보별로 호출한다.
   * <b>일부 실패 시 성공한 항목은 유지하고 실패 후보만 재시도한다.</b>"
   *
   * 그래서 실패해도 중간에 끊지 않고 끝까지 돌린 뒤, 몇 개가 남았는지만 알린다.
   * 남은 것은 그 줄의 `추가` 로 다시 담으면 된다.
   */
  const adoptAll = async () => {
    if (!cands || !data?.recommendationJobId || adopting !== null) return
    const targets = openCands.filter((o) => !o.added).map((o) => o.i)
    if (targets.length === 0) return
    setAdopting('all')
    let failed = 0
    for (const idx of targets) {
      if (!(await adoptOne(idx))) failed += 1
    }
    setActionError(failed === 0 ? null : `${failed}개를 담지 못했습니다. 다시 눌러 주세요.`)
    setAdopting(null)
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
  const openCands = (cands?.items ?? []).map((c, i) => ({
    c, i, added: c.acceptedItemId != null || mine.has(c.name.trim()),
  }))
  /** 아직 안 담은 후보 수. `전체 추가` 가 몇 개를 담는지 버튼에 적는다 */
  const remaining = openCands.filter((o) => !o.added).length
  const warn = data?.unacceptedRequiredCount

  return (
    <Shell>
      <TopBar
        title="내 체크리스트"
        sub="왼쪽에서 내 목록을 손보고, 오른쪽 추천에서 필요한 것만 담으세요"
        /* 추천 받기는 이 화면에 머무는 동작이라 위에 남긴다 */
        right={
          <button type="button" className="btn btn-ghost" onClick={recommend} disabled={job.phase === 'running'}>
            추천 받기
          </button>
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
          <div className="stack">
            {/*
              * <b>완료율은 두 기둥 위로 뺀다.</b>
              *
              * 왼쪽 카드 안에 두었더니 왼쪽만 머리 아래에 막대가 한 줄 더 붙어서,
              * 두 목록의 첫 줄이 다른 높이에서 시작했다. 경고 줄까지 뜨는 날에는
              * 더 벌어졌다 — 나란히 놓은 뜻이 무색해진다.
              *
              * 자리를 옮기고 보니 원래 여기가 맞다. 완료율은 왼쪽 목록만의 값이
              * 아니라 <b>이 여행의 준비 상태</b>다. S-06 도 같은 자리에서 같은
              * 막대로 보여준다.
              */}
            <div className="card">
              <div className="card-head" style={{ marginBottom: 12 }}>
                <h2 className="card-title">준비 상태</h2>
                <span className="spacer" />
                <span className="stat-label">준비 완료 </span>
                <b style={{ fontSize: 16 }}>{doneCount} / {items.length}</b>
                <span className="stat-label">· {pct(data.completionRate)}</span>
              </div>

              <div className="bar bar-lg">
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
            </div>

            <div className="items-cols">
            {/* ── 왼쪽: 내 체크리스트 ── */}
            <div className="card">
              <div className="card-head">
                <h2 className="card-title">내 체크리스트</h2>
                <span className="card-sub">{items.length}개</span>
              </div>

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
                    <ItemRow
                      key={i.itemId}
                      item={i}
                      onToggle={toggleDone}
                      onEdit={editItem}
                      onRemove={removeItem}
                    />
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

            {/* ── 오른쪽: AI 추천 ── */}
            <div className="card" id="recommend">
              <div className="card-head">
                <h2 className="card-title">AI 추천</h2>
                <span className="card-sub">{openCands.length}개</span>
                <span className="spacer" />
                {/*
                  * <b>전체 추가는 여기 하나뿐이다.</b> 줄마다 있는 `추가` 와 같은
                  * 쪽(오른쪽)에 두어 "담는 일은 오른쪽" 이라는 규칙을 지킨다.
                  *
                  * 담을 것이 없으면 감춘다 — 눌러도 아무 일이 없는 버튼을
                  * 남겨 두면 사용자는 고장으로 읽는다.
                  */}
                {remaining > 0 && (
                  <button
                    type="button" className="btn btn-sm"
                    onClick={adoptAll} disabled={adopting !== null}
                  >
                    {adopting === 'all' ? '담는 중…' : `전체 추가 ${remaining}개`}
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

              {job.phase !== 'running' && openCands.length === 0 && (
                <Empty
                  title="추가 추천이 없습니다"
                  action={<button type="button" className="btn btn-ghost" onClick={recommend}>추천 받기</button>}
                />
              )}

              {openCands.length > 0 && (
                <>
                  <ul>
                    {openCands.map(({ c, i, added }) => (
                      <li key={i} className="row">
                        <div className="row-main">
                          <p className="row-name">
                            {c.name} <span className="card-sub">× {c.qty}</span>
                            {c.priority === 'REQUIRED' && <span className="badge badge-warn" style={{ marginLeft: 6 }}>필수</span>}
                          </p>
                          {c.reason && <p className="row-sub">{c.reason}</p>}
                        </div>
                        {/* 담는 것도 오른쪽이다. 왼쪽 체크박스는 내 목록의 챙김 완료 하나만 쓴다 */}
                        <div className="row-right">
                          {added ? (
                            <span className="badge badge-ok">추가됨</span>
                          ) : (
                            <button
                              type="button" className="btn btn-sm"
                              onClick={() => adoptSingle(i)}
                              disabled={adopting !== null}
                              aria-label={`${c.name} 내 목록에 추가`}
                            >
                              {adopting === i ? '담는 중…' : '추가'}
                            </button>
                          )}
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
            </div>

            {/*
              * 다음으로 가는 버튼은 S-02·S-03·S-04 와 같은 <b>아래 오른쪽</b>이다.
              * 다만 여기서는 두 기둥 <b>바깥</b>에 둔다 — 검수는 왼쪽 목록만의 일도,
              * 오른쪽 추천만의 일도 아니라 둘을 다 마쳤다는 뜻이기 때문이다.
              */}
          <div className="card">
            <div className="form-foot">
              <button
                type="button" className="btn btn-ghost" style={{ marginRight: 'auto' }}
                onClick={() => nav(`/trips/${tripId}/detections`)}
              >
                ← 이전: 인식 결과
              </button>
              <button type="button" className="btn" onClick={() => nav(`/trips/${tripId}/inspection`)}>
                다음 — 검수하기 →
              </button>
            </div>
          </div>
          </div>
        )}
      </div>
    </Shell>
  )
}
/**
 * 내 체크리스트 한 줄.
 *
 * <b>체크박스와 수정은 다른 일이다.</b> 왼쪽 체크는 "실제로 챙겼다"(03:353),
 * 오른쪽 `수정` 은 이름·수량을 고치는 것이다. 03 이 두 종류의 체크를 구분하라고
 * 한 자리라 한쪽이 다른 쪽을 겸하게 두지 않는다.
 *
 * 고치는 동안에는 체크박스를 잠근다 — 저장하기 전에 완료로 바꾸면 어느 값이
 * 저장된 것인지 사용자가 알 수 없다.
 */
function ItemRow({
  item, onToggle, onEdit, onRemove,
}: {
  item: ChecklistItem
  onToggle: (itemId: number, done: boolean) => void
  onEdit: (itemId: number, patch: { name?: string; qty?: number }) => void
  onRemove: (itemId: number) => void
}) {
  const [editing, setEditing] = useState(false)
  const [name, setName] = useState(item.name)
  const [qty, setQty] = useState(item.qty)

  const start = () => { setName(item.name); setQty(item.qty); setEditing(true) }

  /**
   * 06 의 item 검증 그대로다 — 이름 1~100자 · 수량 1~99.
   * <b>서버가 거절할 값은 보내지 않는다.</b> 이름을 통째로 지우고 저장하면
   * `name: ''` 이 그대로 나가 400 을 받는다. 그때는 원래 값으로 되돌린다.
   */
  const save = () => {
    const trimmed = name.trim()
    if (!trimmed || trimmed.length > 100) { setName(item.name); setEditing(false); return }
    setEditing(false)
    if (trimmed !== item.name || qty !== item.qty) onEdit(item.itemId, { name: trimmed, qty })
  }

  return (
    <li className="row">
      <input
        type="checkbox"
        checked={item.checkStatus === 'PREPARED'}
        disabled={editing}
        onChange={(e) => onToggle(item.itemId, e.target.checked)}
        aria-label={`${item.name} 챙김 완료`}
      />
      <div className="row-main">
        {editing ? (
          <div className="edit-row">
            <input
              value={name}
              onChange={(e) => setName(e.target.value)}
              maxLength={100}
              aria-label="물품 이름"
              /* 엔터로 저장하고 Esc 로 물린다 — 마우스로 버튼을 찾아가지 않아도 된다 */
              onKeyDown={(e) => {
                if (e.key === 'Enter') { e.preventDefault(); save() }
                if (e.key === 'Escape') { setName(item.name); setQty(item.qty); setEditing(false) }
              }}
            />
            <input
              type="number" min={1} max={99} value={qty}
              onChange={(e) => setQty(Math.min(99, Math.max(1, Number(e.target.value))))}
              aria-label="수량"
            />
          </div>
        ) : (
          <>
            <p className="row-name">
              {item.name} <span className="card-sub">× {item.qty}</span>
              {item.priority === 'REQUIRED' && <span className="badge badge-warn" style={{ marginLeft: 6 }}>필수</span>}
            </p>
            <p className="row-sub">
              {CATEGORY_LABEL[item.category]} · {SOURCE_LABEL[item.source]}
            </p>
          </>
        )}
      </div>
      <div className="row-right">
        {editing ? (
          <>
            <button type="button" className="btn btn-sm" onClick={save}>저장</button>
            <button
              type="button" className="btn btn-ghost btn-sm"
              onClick={() => { setName(item.name); setQty(item.qty); setEditing(false) }}
            >
              취소
            </button>
          </>
        ) : (
          <>
            <span className={`badge${item.photoStatus === 'CONFIRMED' ? ' badge-ok' : item.photoStatus === 'NEEDS_CHECK' ? ' badge-warn' : ''}`}>
              {PHOTO_STATUS_LABEL[item.photoStatus]}
            </span>
            {/*
              * <b>수정이 없어서 지우고 다시 넣어야 했다.</b> 그러면 인식과의
              * 연결과 출처(PHOTO)가 함께 사라진다. 수량 하나 고치자고 잃을
              * 것이 아니다. 06:97 의 PATCH 가 이미 이름·수량을 받는다.
              */}
            <button type="button" className="btn btn-ghost btn-sm" onClick={start} aria-label={`${item.name} 수정`}>
              수정
            </button>
            {/* 03:272 가 이 화면의 호출 API 로 DELETE 를 적어 뒀다 */}
            <button
              type="button" className="btn btn-ghost btn-sm"
              onClick={() => onRemove(item.itemId)}
              aria-label={`${item.name} 삭제`}
            >
              삭제
            </button>
          </>
        )}
      </div>
    </li>
  )
}
