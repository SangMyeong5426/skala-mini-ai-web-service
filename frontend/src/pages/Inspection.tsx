import { useEffect, useState } from 'react'
import { useNavigate, useParams } from 'react-router-dom'
import { api } from '../api/client'
import { Failed, Skeleton } from '../components/States'
import { Shell, Steps, TopBar } from '../components/Shell'
import { pct } from '../lib/format'
import type { Inspection, RuleVerdict, WeightVerdict } from '../types/api'

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

  const load = () => {
    setError(null)
    api.get<Inspection>(`/trips/${tripId}/inspection`)
      .then(setData)
      .catch((e) => setError(e instanceof Error ? e.message : '알 수 없는 오류'))
  }
  useEffect(load, [tripId])

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
        {error && <Failed title="검수 결과를 불러오지 못했습니다" detail={error} onRetry={load} />}

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

              <Group title="챙김 완료" count={r.prepared.length} tone="ok"
                empty="현재 챙김 완료된 물품이 없습니다">
                {r.prepared.map((i) => (
                  <li key={i.itemId} className="row">
                    <span className="row-name">{i.name} <span className="card-sub">× {i.qty}</span></span>
                    <span className="row-right"><span className="badge badge-ok">확인됨</span></span>
                  </li>
                ))}
              </Group>

              <Group title="확인 필요" count={r.needsCheck.length} tone="warn">
                {r.needsCheck.map((i) => (
                  <li key={i.itemId} className="row">
                    <div className="row-main">
                      <p className="row-name">{i.name} <span className="card-sub">× {i.qty}</span></p>
                      {i.candidates.length > 0 && (
                        <p className="row-sub">
                          사진 후보 — {i.candidates.map((x) => `${x.name} ${Math.round(x.matchConfidence * 100)}%`).join(' · ')}
                        </p>
                      )}
                    </div>
                    <div className="row-right">
                      <button
                        type="button" className="btn btn-ghost btn-sm"
                        onClick={() => nav(`/trips/${tripId}/detections`)}
                      >사진 확인</button>
                    </div>
                  </li>
                ))}
              </Group>

              <Group title="사진에서 미확인" count={r.notInPhoto.length} tone="">
                {r.notInPhoto.map((i) => (
                  <li key={i.itemId} className="row">
                    <span className="row-name">
                      {i.name}
                      {i.priority === 'REQUIRED' && <span className="badge badge-warn" style={{ marginLeft: 6 }}>필수</span>}
                    </span>
                    <span className="row-right"><span className="badge">직접 확인</span></span>
                  </li>
                ))}
              </Group>

              <Group title="목록에 없던 물품" count={r.extra.length} tone="">
                {r.extra.map((i) => (
                  <li key={i.detectionId} className="row">
                    <span className="row-name">
                      {i.name} <span className="card-sub">신뢰도 {i.confidence.toFixed(2)}</span>
                    </span>
                    <span className="row-right">
                      {i.verdict && <span className={`badge ${RULE[i.verdict].cls}`}>{RULE[i.verdict].label}</span>}
                    </span>
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
            {data && !w && <p className="card-sub">아직 계산하지 않았습니다.</p>}
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
            {data && !c && <p className="card-sub">아직 판정하지 않았습니다.</p>}
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

/** 준비 상태의 네 묶음. 비면 접어 둔다 — 없는 것을 자리로 알리지 않는다 */
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
