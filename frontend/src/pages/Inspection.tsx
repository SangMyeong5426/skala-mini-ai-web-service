import { useEffect, useRef, useState } from 'react'
import { useNavigate, useParams } from 'react-router-dom'
import { api } from '../api/client'
import { AiPending, Failed, Skeleton } from '../components/States'
import { useAiJob } from '../hooks/useAiJob'
import { Shell, Steps, TopBar } from '../components/Shell'
import { pct } from '../lib/format'
import type { Inspection, PhotoStatus, RuleVerdict, TripDetail, WeightVerdict } from '../types/api'

/**
 * S-06 검수 결과 ★AI — 준비 상태 · 예상 무게 · 반입 판정을 한 화면에서 본다.
 *
 * <b>세 영역이 따로 로딩된다.</b> 무게가 아직이어도 준비 상태는 먼저 보여야
 * 한다(03). 그래서 `readiness` · `weight` · `customs` 가 각각 null 일 수 있고
 * 영역마다 따로 그린다.
 *
 * <b>무게를 확정값처럼 말하지 않는다.</b> 최소–대표–최대 범위와 신뢰도, 계산에서
 * 뺀 개수를 함께 낸다(F-10).
 *
 * <b>판정은 AI 가 아니라 규칙 엔진이 한다.</b> 출처와 확인 날짜를 항상 붙인다.
 */
const WEIGHT: Record<WeightVerdict, { label: string; cls: string }> = {
  ROOM: { label: '여유', cls: 'badge-ok' },
  NEAR: { label: '한도 근접', cls: 'badge-warn' },
  OVER_RISK: { label: '초과 위험', cls: 'badge-danger' },
  UNKNOWN: { label: '판단 보류', cls: '' },
}

const RULE: Record<RuleVerdict, { label: string; cls: string }> = {
  CABIN_OK: { label: '기내 가능', cls: 'badge-ok' },
  CHECKED_OK: { label: '위탁 가능', cls: 'badge-ok' },
  CHECKED_FORBIDDEN: { label: '반입 불가', cls: 'badge-danger' },
  RESTRICTED: { label: '조건부', cls: 'badge-warn' },
  NEED_MORE_INFO: { label: '정보 부족', cls: 'badge-warn' },
  ASK_AIRLINE: { label: '항공사 확인', cls: 'badge-warn' },
}

const kg = (g: number) => (g / 1000).toFixed(1)

export default function InspectionPage() {
  const { tripId } = useParams()
  const nav = useNavigate()
  const [data, setData] = useState<Inspection | null>(null)
  const [error, setError] = useState<string | null>(null)
  const weightJob = useAiJob()
  const ruleJob = useAiJob()
  const kicked = useRef(false)

  const load = () =>
    api.get<Inspection>(`/trips/${tripId}/inspection`)
      .then((r) => { setData(r); setError(null); return r })
      .catch((e) => {
        setError(e instanceof Error ? e.message : '알 수 없는 오류')
        return null
      })

  /*
   * 06:1029 · 03 S-06 — 무게·판정이 없으면 <b>여기서 작업을 시작하고 폴링</b>한다.
   * 조회만 하고 "아직 계산하지 않았습니다" 로 두면 사용자가 할 수 있는 일이 없다.
   *
   * 둘을 따로 돌린다. 03 이 "세 영역이 각각 따로 로딩된다" 로 정했고,
   * 무게가 실패해도 반입 판정은 보여야 한다.
   */
  useEffect(() => {
    let alive = true
    void (async () => {
      const r = await load()
      if (!alive || !r || kicked.current) return
      kicked.current = true

      // 07 의 두 입력 스키마는 서로 다르다. 여행 정보가 있어야 채울 수 있고,
      // 없으면 요청을 걸지 않는다 — 07 이 minLength·enum 을 요구한다.
      const trip = await api.get<TripDetail>(`/trips/${tripId}`).catch(() => null)
      if (!alive || !trip) return

      const prepared = r.readiness?.prepared ?? []
      const unprepared = r.readiness?.unprepared ?? []

      /*
       * 07:927 WEIGHT_ESTIMATE required — bagType · bagEmptyG · weightLimitG ·
       * items · excluded. items 는 <b>PREPARED 만</b>이고 미완료는 excluded 로
       * 분리한다(07:939 "내 목록의 미완료 항목만 excluded 에 UNCHECKED 로").
       *
       * <b>서버가 같은 입력을 스스로 만들어 놓고 우리 것과 대조한다.</b> 한 글자라도
       * 다르면 409 STALE_WEIGHT_INPUT 이고 무게가 통째로 안 나온다. 그래서
       * 여기서는 값을 <b>보정하지 않는다.</b>
       *
       * - reason 은 <b>언제나 UNCHECKED</b> 다. photoStatus 로 NOT_IN_PHOTO 를
       *   보내면 서버(UNCHECKED)와 어긋난다. enum 에 NOT_IN_PHOTO 가 있는 것은
       *   구 데이터를 읽기 위한 것이지 우리가 만들어 보낼 값이 아니다(07:941).
       * - 가방 값에 ?? 0 · ?? 'CARRY_ON' 같은 기본값을 넣지 않는다. 서버는
       *   비어 있으면 null 을 그대로 보내므로 기본값을 채우는 순간 어긋난다.
       */
      if (!r.weight && prepared.length > 0) {
        void weightJob.start('WEIGHT_ESTIMATE', {
          bagType: trip.bagType ?? null,
          bagEmptyG: trip.bagEmptyG ?? null,
          weightLimitG: trip.weightLimitG ?? null,
          items: prepared.map((i) => ({ itemId: i.itemId, name: i.name, qty: i.qty })),
          excluded: unprepared.map((i) => ({ name: i.name, reason: 'UNCHECKED' })),
        }, Number(tripId)).then((done) => { if (done && alive) void load() })
      }

      /*
       * 07:1436 RULE_CHECK required — transport · airline · question · items.
       * 항목마다 itemId · detectionId · name · qty · attributes 가 필요하다.
       *
       * <b>이동수단은 이 여행의 것</b>이다. FLIGHT 로 박아 두면 기차·버스
       * 여행도 항공 규정으로 판정한다 — 그건 여행 없는 챗봇의 기본값이다.
       */
      const all = [...prepared, ...unprepared]
      if (!r.customs && all.length > 0) {
        void ruleJob.start('RULE_CHECK', {
          transport: trip.transport,
          airline: trip.airline ?? null,
          question: null,
          items: all.map((i) => ({
            itemId: i.itemId,
            detectionId: null,
            name: i.name,
            qty: i.qty,
            // 속성은 서버가 채운다. FE 는 모르는 값을 지어내지 않는다.
            attributes: { capacityMl: null, batteryWh: null, batteryMah: null, bladeCm: null },
          })),
        }, Number(tripId)).then((done) => { if (done && alive) void load() })
      }
    })()
    return () => { alive = false }
  }, [tripId])

  const r = data?.readiness
  const w = data?.weight
  const c = data?.customs

  return (
    <Shell>
      <TopBar
        title="검수 결과"
        sub="준비 상태·예상 무게·반입 여부를 한 번에 확인하세요"
        right={
          <button type="button" className="btn btn-ghost" onClick={() => nav(`/trips/${tripId}/items`)}>
            체크리스트로
          </button>
        }
      />
      <Steps current={3} tripId={tripId} />

      <div className="content">
        {error && <Failed title="검수 결과를 불러오지 못했습니다" detail={error} onRetry={() => { void load() }} />}

        {/* ── ① 준비 상태 ── */}
        <div className="card">
          <div className="card-head">
            <h2 className="card-title">준비 상태</h2>
            <span className="spacer" />
            {r && <b style={{ fontSize: 16 }}>{pct(r.completionRate)}</b>}
          </div>

          {!data && !error && <Skeleton rows={3} />}
          {r && (
            <>
              <div className="bar bar-lg" style={{ marginBottom: 16 }}>
                <span style={{ width: `${Math.round(r.completionRate * 100)}%` }} />
              </div>

              {r.unacceptedRequiredCount !== 0 && (
                <div className="notice-warn">
                  <span>
                    {r.unacceptedRequiredCount === null
                      ? '필수 추천 확인 전입니다'
                      : <>아직 채택하지 않은 <b>필수 후보 {r.unacceptedRequiredCount}건</b>이 있습니다</>}
                  </span>
                  <button
                    type="button" className="btn btn-sm"
                    onClick={() => nav(`/trips/${tripId}/items`)}
                  >확인하기</button>
                </div>
              )}

              <Group title="챙김 완료" count={r.prepared.length} tone="ok"
                empty="현재 챙김 완료된 물품이 없습니다">
                {r.prepared.map((i) => (
                  <li key={i.itemId} className="row">
                    <div className="row-main">
                      <p className="row-name">{i.name} <span className="card-sub">× {i.qty}</span></p>
                    </div>
                    <div className="row-right">
                      <PhotoBadge status={i.photoStatus} />
                      {i.photoStatus === 'NEEDS_CHECK' && (
                        <button
                          type="button" className="btn btn-ghost btn-sm"
                          onClick={() => nav(`/trips/${tripId}/detections`)}
                        >사진 확인</button>
                      )}
                    </div>
                  </li>
                ))}
              </Group>

              <Group title="아직 안 챙김" count={r.unprepared.length} tone="warn"
                empty="모두 챙기셨습니다">
                {r.unprepared.map((i) => (
                  <li key={i.itemId} className="row">
                    <div className="row-main">
                      <p className="row-name">{i.name} <span className="card-sub">× {i.qty}</span></p>
                    </div>
                    <div className="row-right">
                      <PhotoBadge status={i.photoStatus} />
                      {i.photoStatus === 'NEEDS_CHECK' && (
                        <button
                          type="button" className="btn btn-ghost btn-sm"
                          onClick={() => nav(`/trips/${tripId}/detections`)}
                        >사진 확인</button>
                      )}
                    </div>
                  </li>
                ))}
              </Group>
            </>
          )}
        </div>

        <div className="grid grid-2">
          {/* ── ② 예상 무게 ── */}
          <div className="card">
            <div className="card-head">
              <h2 className="card-title">예상 무게</h2>
              <span className="spacer" />
              {w && <span className={`badge ${WEIGHT[w.verdict].cls}`}>{WEIGHT[w.verdict].label}</span>}
            </div>

            {!data && !error && <Skeleton rows={3} />}
            {weightJob.phase === 'running' && <AiPending label="예상 무게를 계산하는 중" polls={weightJob.polls} />}
            {weightJob.phase === 'failed' && (
              <Failed title="무게를 계산하지 못했습니다" detail={weightJob.error ?? ''} />
            )}
            {/*
              * 06:537-538 — 60회를 넘기면 "시간이 오래 걸립니다" 와 재시도 버튼.
              * 여기서 다시 하는 일은 <b>작업 재시작이 아니라 조회</b>다. 작업은 서버에
              * 남아 돌고 있고, 끝나면 결과가 검수 응답에 실려 온다.
              */}
            {weightJob.phase === 'timeout' && (
              <Failed
                title="시간이 오래 걸립니다"
                detail="작업은 서버에 남아 있습니다"
                onRetry={() => { void load() }}
              />
            )}
            {data && !w && weightJob.phase === 'idle' && (
              <p className="card-sub">계산할 물품이 없습니다.</p>
            )}
            {w && (
              <>
                <p className="range">
                  <span>{kg(w.minG)}</span>
                  <b>{kg(w.typicalG)}</b>
                  <span>{kg(w.maxG)}</span>
                  <small>kg</small>
                </p>
                <div className="bar bar-lg" style={{ margin: '12px 0 10px' }}>
                  <span style={{ width: `${Math.min(100, Math.round((w.typicalG / w.limitG) * 100))}%` }} />
                </div>
                <p className="card-sub">
                  한도 {kg(w.limitG)}kg · 신뢰도 {w.confidence === 'HIGH' ? '높음' : w.confidence === 'MEDIUM' ? '보통' : '낮음'}
                  {w.excludedCount > 0 && ` · 계산 제외 ${w.excludedCount}개`}
                </p>
                <p className="row-sub" style={{ marginTop: 6 }}>{w.confidenceReason}</p>

                {w.contributions.length > 0 && (
                  <ul className="contrib">
                    {w.contributions.map((x) => (
                      <li key={x.name}>
                        <span>{x.name} <em className="card-sub">× {x.qty}</em></span>
                        <b>{x.subtotalG}g</b>
                      </li>
                    ))}
                  </ul>
                )}
                <p className="disclaimer">
                  예상 무게는 참고용 추정치입니다. 탑승 전 실제 저울로 측정하세요.
                </p>
              </>
            )}
          </div>

          {/* ── ③ 반입 판정 ── */}
          <div className="card">
            <div className="card-head">
              <h2 className="card-title">반입 판정</h2>
              <span className="card-sub">규칙 엔진이 공식 규정과 대조합니다</span>
            </div>

            {!data && !error && <Skeleton rows={3} />}
            {ruleJob.phase === 'running' && <AiPending label="반입 규정을 확인하는 중" polls={ruleJob.polls} />}
            {ruleJob.phase === 'failed' && (
              <Failed title="판정하지 못했습니다" detail={ruleJob.error ?? ''} />
            )}
            {ruleJob.phase === 'timeout' && (
              <Failed
                title="시간이 오래 걸립니다"
                detail="작업은 서버에 남아 있습니다"
                onRetry={() => { void load() }}
              />
            )}
            {data && !c && ruleJob.phase === 'idle' && (
              <p className="card-sub">판정할 물품이 없습니다.</p>
            )}
            {c?.length === 0 && <p className="card-sub">확인할 물품이 없습니다.</p>}
            {c?.map((x) => (
              <div key={x.itemId} className="verdict">
                <div className="verdict-head">
                  <b>{x.name}</b>
                  <span className={`badge ${RULE[x.verdict].cls}`}>{RULE[x.verdict].label}</span>
                </div>
                <p className="verdict-why">{x.reason}</p>
                {x.missingInfo && (
                  <p className="verdict-why"><b>확인 필요 — {x.missingInfo}</b></p>
                )}
                {x.sourceUrl && (
                  <p className="verdict-src">
                    <a href={x.sourceUrl} target="_blank" rel="noreferrer">출처</a>
                    {x.checkedAt && ` · ${x.checkedAt} 확인`}
                  </p>
                )}
              </div>
            ))}
          </div>
        </div>

        {data?.notice && <p className="disclaimer">{data.notice}</p>}
      </div>
    </Shell>
  )
}

/**
 * 사진에서의 상태. <b>준비 완료와 다른 축이다.</b>
 * `NOT_IN_PHOTO` 는 "없다" 가 아니라 "사진에서 못 찾았다" 다.
 */
function PhotoBadge({ status }: { status: PhotoStatus }) {
  const m = {
    CONFIRMED: { label: '사진에서 확인', cls: 'badge-ok' },
    NEEDS_CHECK: { label: '확인 필요', cls: 'badge-warn' },
    NOT_IN_PHOTO: { label: '사진에서 미확인', cls: '' },
  }[status]
  return <span className={`badge ${m.cls}`}>{m.label}</span>
}

/** 준비 상태의 두 묶음. 비어도 자리를 남겨 "없다" 를 말해 준다 */
function Group({
  title, count, tone, empty, children,
}: {
  title: string
  count: number
  tone: 'ok' | 'warn' | ''
  empty?: string
  children: React.ReactNode
}) {
  if (count === 0 && !empty) return null
  return (
    <div className="group">
      <p className="group-head">
        {title}
        <span className={`badge ${tone === 'ok' ? 'badge-ok' : tone === 'warn' ? 'badge-warn' : ''}`}>{count}</span>
      </p>
      {count === 0 ? <p className="card-sub">{empty}</p> : <ul>{children}</ul>}
    </div>
  )
}
