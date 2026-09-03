import { useEffect, useState } from 'react'
import { useNavigate, useParams } from 'react-router-dom'
import { api } from '../api/client'
import { Shell, Steps, TopBar } from '../components/Shell'
import { AiPending, Empty, Failed, Skeleton } from '../components/States'
import { useAiJob } from '../hooks/useAiJob'
import type { BagCheckOutput, ChecklistItem, Detection } from '../types/api'

const LEVEL: Record<string, { label: string; cls: string }> = {
  HIGH: { label: '신뢰도 높음', cls: 'badge-ok' },
  MEDIUM: { label: '신뢰도 보통', cls: '' },
  LOW: { label: '신뢰도 낮음', cls: 'badge-warn' },
}

/**
 * S-04 인식 결과 · 승인 — <b>이 서비스의 핵심 게이트다.</b>
 *
 * 명세 9.2: "사진 분석 결과는 사용자가 승인하기 전 최종 준비 상태에 반영되지
 * 않아야 한다." 승인을 거치지 않은 것은 무게·반입·완료율 어디에도 안 들어간다.
 *
 * 승인하면 내 목록에 없던 물품은 새로 만들고(PHOTO/PREPARED), 있으면 연결한다.
 */
export default function Detections() {
  const { tripId = '1' } = useParams()
  const nav = useNavigate()
  const [dets, setDets] = useState<Detection[] | null>(null)
  const [items, setItems] = useState<ChecklistItem[]>([])
  const [error, setError] = useState<string | null>(null)
  const job = useAiJob<BagCheckOutput>()

  const load = () => {
    setError(null)
    Promise.all([
      api.get<{ detections: Detection[] }>(`/trips/${tripId}/detections`),
      api.get<{ items: ChecklistItem[] }>(`/trips/${tripId}/items`),
    ])
      .then(([d, i]) => { setDets(d.detections); setItems(i.items) })
      .catch((e) => setError(e instanceof Error ? e.message : '알 수 없는 오류'))
  }
  useEffect(load, [tripId])

  const analyze = async () => {
    await job.start('BAG_CHECK', { photoIds: [1, 2] }, Number(tripId))
    load()
  }

  const approve = async (d: Detection, matchedItemIds: number[]) => {
    await api.patch(`/trips/${tripId}/detections/${d.detectionId}`, {
      approved: true, name: d.name, qty: d.qty, matchedItemIds,
    })
    load()
  }

  const pending = dets?.filter((d) => !d.approved) ?? []
  const done = dets?.filter((d) => d.approved) ?? []
  // 03-wireframe: "확인 필요" 묶음 = missingInfo 가 있거나 신뢰도가 낮은 것
  const needsCheck = pending.filter((d) => d.missingInfo || d.confidenceLevel === 'LOW')

  return (
    <Shell>
      <TopBar
        title="인식 결과 · 승인"
        sub="AI가 찾은 물품을 확인하고 승인해 주세요"
        right={
          <>
            <button type="button" className="btn btn-ghost" onClick={analyze} disabled={job.phase === 'running'}>
              다시 분석
            </button>
            <button type="button" className="btn" onClick={() => nav(`/trips/${tripId}/items`)}>
              체크리스트로
            </button>
          </>
        }
      />
      <Steps current={2} tripId={tripId} />

      <div className="content">
        {error && <Failed title="인식 결과를 불러오지 못했습니다" detail={error} onRetry={load} />}

        {job.phase === 'running' && (
          <div className="card">
            <AiPending label="사진을 분석하는 중" polls={job.polls} />
          </div>
        )}
        {job.phase === 'failed' && (
          <Failed title="분석하지 못했습니다" detail={job.error ?? ''} onRetry={analyze} />
        )}
        {job.phase === 'timeout' && (
          <Failed title="시간이 오래 걸립니다" detail="작업은 서버에 남아 있습니다" onRetry={analyze} />
        )}

        {!error && dets === null && <div className="card"><Skeleton rows={3} /></div>}

        {dets !== null && dets.length === 0 && job.phase !== 'running' && (
          <div className="card">
            <Empty
              title="인식된 물품이 없습니다"
              action={
                <div style={{ display: 'flex', gap: 8 }}>
                  <button type="button" className="btn" onClick={analyze}>사진 분석하기</button>
                  <button type="button" className="btn btn-ghost" onClick={() => nav(`/trips/${tripId}/photos`)}>
                    다시 촬영
                  </button>
                </div>
              }
            />
          </div>
        )}

        {pending.length > 0 && (
          <div className="card">
            <div className="card-head">
              <h2 className="card-title">승인 대기</h2>
              <span className="card-sub">{pending.length}개</span>
              <span className="spacer" />
              {needsCheck.length > 0 && (
                <span className="badge badge-warn">확인 필요 {needsCheck.length}</span>
              )}
            </div>
            <p className="card-sub" style={{ marginBottom: 12 }}>
              <b>승인 전에는 준비 상태·무게·반입 판정에 반영되지 않습니다.</b>
            </p>
            <ul>
              {pending.map((d) => (
                <DetectionRow key={d.detectionId} d={d} items={items} onApprove={approve} />
              ))}
            </ul>
          </div>
        )}

        {done.length > 0 && (
          <div className="card">
            <div className="card-head">
              <h2 className="card-title">승인함</h2>
              <span className="card-sub">{done.length}개</span>
            </div>
            <ul>
              {done.map((d) => (
                <li key={d.detectionId} className="row">
                  <div className="row-main">
                    <p className="row-name">{d.name} <span className="card-sub">× {d.qty}</span></p>
                  </div>
                  <div className="row-right"><span className="badge badge-ok">승인됨</span></div>
                </li>
              ))}
            </ul>
          </div>
        )}
      </div>
    </Shell>
  )
}

function DetectionRow({
  d, items, onApprove,
}: {
  d: Detection
  items: ChecklistItem[]
  onApprove: (d: Detection, ids: number[]) => void
}) {
  const [linked, setLinked] = useState<number[]>(d.linkedItems?.map((l) => l.itemId) ?? [])
  const lv = LEVEL[d.confidenceLevel] ?? LEVEL.MEDIUM

  return (
    <li className="row" style={{ alignItems: 'flex-start' }}>
      <div className="row-main">
        <p className="row-name">
          {d.name} <span className="card-sub">× {d.qty}</span>{' '}
          <span className={`badge ${lv.cls}`}>{lv.label}</span>
        </p>
        {d.missingInfo && (
          <p className="row-sub">확인 필요 — <b>{d.missingInfo}</b></p>
        )}
        <label className="link-select">
          <span>내 목록과 연결</span>
          <select
            value={linked[0] ?? ''}
            onChange={(e) => setLinked(e.target.value ? [Number(e.target.value)] : [])}
          >
            <option value="">연결 안 함 (추가 물품)</option>
            {items.map((i) => (
              <option key={i.itemId} value={i.itemId}>{i.name}</option>
            ))}
          </select>
        </label>
      </div>
      <div className="row-right">
        <button type="button" className="btn btn-sm" onClick={() => onApprove(d, linked)}>
          승인
        </button>
      </div>
    </li>
  )
}
