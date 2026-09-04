import { useEffect, useState } from 'react'
import { useNavigate, useParams } from 'react-router-dom'
import { api } from '../api/client'
import { Shell, Steps, TopBar } from '../components/Shell'
import { Disclaimer, Empty, Failed, Skeleton } from '../components/States'
import { TRANSPORT_LABEL, VERDICT_CLASS, VERDICT_LABEL } from '../lib/format'
import type { Inspection, TransportRule, TripDetail } from '../types/api'

/**
 * S-08 반입 규정 상세 — 판정 근거를 보고 부족한 정보를 채운다 (UC-07).
 *
 * 두 부분이다. 위는 <b>내 물품의 판정</b>(검수 결과의 `customs` 를 펼친 것),
 * 아래는 <b>규정 원문 검색</b>(`GET /api/rules`)이다.
 *
 * <b>판정을 여기서 만들지 않는다.</b> 최종 판정은 규칙 엔진이 공식 규정표로
 * 한다(원칙 ④). 이 화면은 그 결과와 출처를 보여줄 뿐이다.
 *
 * 부족한 정보(용량·Wh·날 길이)는 값을 채워 다시 판정받아야 하는데, 그 경로는
 * 챗봇(S-09)이다 — 오른쪽 아래 버튼으로 어느 화면에서나 열린다. 여기에 입력칸을
 * 또 두면 같은 일을 하는 곳이 둘이 된다.
 */
export default function Rules() {
  const { tripId } = useParams()
  const nav = useNavigate()
  const [trip, setTrip] = useState<TripDetail | null>(null)
  /*
   * <b>`null` 과 `[]` 는 다른 뜻이다.</b> 전자는 아직 못 받았다는 것이고 후자는
   * 판정이 없다는 <b>사실</b>이다. 예전에는 빈 상태를 `null` 에만 걸어 둬서,
   * 실서버가 `customs: []` 를 주면 제목의 `0개` 아래가 통째로 빈칸이 됐다 —
   * 검수로 가는 안내도 함께 사라졌다.
   *
   * 그래서 `loaded` 를 따로 둔다. 값 하나로 두 가지를 말하게 하지 않는다.
   */
  const [customs, setCustoms] = useState<Inspection['customs']>(null)
  const [loaded, setLoaded] = useState(false)
  const [error, setError] = useState<string | null>(null)

  const [keyword, setKeyword] = useState('')
  const [rules, setRules] = useState<TransportRule[] | null>(null)
  const [searching, setSearching] = useState(false)
  const [searchError, setSearchError] = useState<string | null>(null)

  const load = () => {
    setError(null); setTrip(null); setCustoms(null); setLoaded(false)
    Promise.all([
      api.get<TripDetail>(`/trips/${tripId}`),
      api.get<Inspection>(`/trips/${tripId}/inspection`),
    ])
      .then(([t, i]) => { setTrip(t); setCustoms(i.customs); setLoaded(true) })
      .catch((e) => setError(e instanceof Error ? e.message : '알 수 없는 오류'))
  }
  useEffect(load, [tripId])

  const search = (kw: string) => {
    if (!trip) return
    setSearching(true); setSearchError(null)
    // transport 는 필수다. 없으면 400 (06). 여행의 이동수단을 그대로 쓴다.
    const q = new URLSearchParams({ transport: trip.transport })
    if (kw.trim()) q.set('keyword', kw.trim())
    api.get<{ rules: TransportRule[] }>(`/rules?${q}`)
      .then((r) => setRules(r.rules))
      .catch((e) => setSearchError(e instanceof Error ? e.message : '알 수 없는 오류'))
      .finally(() => setSearching(false))
  }

  return (
    <Shell>
      <TopBar
        title="반입 규정 상세"
        sub={trip ? `${TRANSPORT_LABEL[trip.transport] ?? trip.transport} 기준` : undefined}
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
        {error && <Failed title="불러오지 못했습니다" detail={error} onRetry={load} />}
        {!error && !trip && <div className="card"><Skeleton rows={3} /></div>}

        {trip && (
          <>
            <section className="card">
              <div className="card-head" style={{ marginBottom: 12 }}>
                <h2 className="card-title">내 물품 판정</h2>
                <span className="spacer" />
                <span className="card-sub">{customs ? `${customs.length}개` : '아직 없음'}</span>
              </div>

              {!loaded && <Skeleton rows={2} />}

              {loaded && !customs?.length && (
                <Empty
                  title="아직 반입 판정을 받지 않았습니다"
                  action={
                    <button type="button" className="btn" onClick={() => nav(`/trips/${tripId}/inspection`)}>
                      검수 결과에서 확인하기
                    </button>
                  }
                />
              )}

              {customs && customs.length > 0 && (
                <ul className="list">
                  {customs.map((c) => (
                    <li key={c.itemId} className="list-col">
                      <div style={{ display: 'flex', alignItems: 'baseline', gap: 8 }}>
                        <span style={{ fontWeight: 600 }}>{c.name}</span>
                        <span className="spacer" />
                        <span className={`badge ${VERDICT_CLASS[c.verdict] ?? ''}`}>
                          {VERDICT_LABEL[c.verdict] ?? c.verdict}
                        </span>
                      </div>
                      <p className="card-sub" style={{ marginTop: 6 }}>{c.reason}</p>
                      {/*
                        * <b>"다시 판정합니다" 라고 말하지 않는다.</b> 챗봇의 RULE_CHECK 은
                        * `tripId`·`itemId` 를 일부러 안 보낸다 — 대화 이력을 남기지 않기로
                        * 했기 때문이다(02:154). 그래서 답은 대화창에만 나오고 이 여행의
                        * `item_rule_checks` 도 검수 결과도 <b>바뀌지 않는다.</b>
                        *
                        * 예전 문구는 사용자가 보완했다고 믿게 만들었다. 확인하러 온
                        * 화면에서 가장 나쁜 거짓말이다. 재판정 연결은 03 에 보류로 적었다.
                        */}
                      {c.missingInfo && (
                        <p className="card-sub" style={{ marginTop: 4 }}>
                          <strong>필요한 정보:</strong> {c.missingInfo} — 오른쪽 아래 챗봇에
                          물어볼 수 있습니다. 다만 <b>이 판정은 바뀌지 않습니다</b> —
                          답을 보고 직접 확인해 주세요.
                        </p>
                      )}
                      {c.sourceUrl && (
                        <p className="card-sub" style={{ marginTop: 4 }}>
                          <a href={c.sourceUrl} target="_blank" rel="noreferrer">출처</a>
                          {c.checkedAt && ` · ${c.checkedAt} 확인`}
                        </p>
                      )}
                    </li>
                  ))}
                </ul>
              )}
            </section>

            <section className="card" style={{ marginTop: 20 }}>
              <div className="card-head" style={{ marginBottom: 12 }}>
                <h2 className="card-title">규정 찾아보기</h2>
              </div>
              <form
                style={{ display: 'flex', gap: 8 }}
                onSubmit={(e) => { e.preventDefault(); search(keyword) }}
              >
                <input
                  className="input"
                  style={{ flex: 1 }}
                  placeholder="물품 이름 — 보조배터리 · 액체 · 가위 · 노트북"
                  value={keyword}
                  onChange={(e) => setKeyword(e.target.value)}
                />
                <button type="submit" className="btn" disabled={searching}>
                  {searching ? '찾는 중' : '검색'}
                </button>
              </form>

              {searchError && (
                <div style={{ marginTop: 12 }}>
                  <Failed title="규정을 찾지 못했습니다" detail={searchError} />
                </div>
              )}

              {rules !== null && rules.length === 0 && !searchError && (
                <div style={{ marginTop: 12 }}>
                  <Empty title="해당 규정을 찾지 못했습니다" />
                  <p className="card-sub">항공사에 직접 확인하세요.</p>
                </div>
              )}

              {rules && rules.length > 0 && (
                <ul className="list" style={{ marginTop: 12 }}>
                  {rules.map((r) => (
                    <li key={r.ruleId} className="list-col">
                      <div style={{ display: 'flex', alignItems: 'baseline', gap: 8 }}>
                        <span className={`badge ${VERDICT_CLASS[r.verdict] ?? ''}`}>
                          {VERDICT_LABEL[r.verdict] ?? r.verdict}
                        </span>
                        {r.conditionNote && <span style={{ fontWeight: 600 }}>{r.conditionNote}</span>}
                      </div>
                      <p className="card-sub" style={{ marginTop: 6 }}>{r.description}</p>
                      {r.sourceUrl && (
                        <p className="card-sub" style={{ marginTop: 4 }}>
                          <a href={r.sourceUrl} target="_blank" rel="noreferrer">출처</a>
                          {r.checkedAt && ` · ${r.checkedAt} 확인`}
                        </p>
                      )}
                    </li>
                  ))}
                </ul>
              )}
            </section>

            <Disclaimer>
              최종 반입 여부는 출발 당일 항공사와 보안검색기관의 판단을 따릅니다.
            </Disclaimer>
          </>
        )}
      </div>
    </Shell>
  )
}
