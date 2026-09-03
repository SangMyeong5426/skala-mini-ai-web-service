/**
 * Mock 점검 화면. 백엔드 없이 계약이 도는지 눈으로 확인한다.
 *
 * 실제 화면을 만들기 전에 이것으로 확인한다 —
 * fetch 래퍼 · 오류 봉투 · **폴링**이 도는지.
 * 화면이 다 붙으면 이 파일과 라우트를 지운다.
 */
import { useEffect, useState } from 'react'
import { api, ApiFailure } from '../api/client'
import { SCREENS } from '../routes'
import { USE_MOCK } from '../api/mock'
import { useAiJob } from '../hooks/useAiJob'
import { AiPending, Failed, Skeleton } from '../components/States'
import type { Detection, Inspection, TripSummary } from '../types/api'

export function MockCheck() {
  const [trips, setTrips] = useState<TripSummary[] | null>(null)
  const [detections, setDetections] = useState<Detection[] | null>(null)
  const [inspection, setInspection] = useState<Inspection | null>(null)
  const [err, setErr] = useState<string | null>(null)
  const [missErr, setMissErr] = useState<string | null>(null)

  const job = useAiJob()

  useEffect(() => {
    Promise.all([
      api.get<{ trips: TripSummary[] }>('/trips'),
      api.get<{ detections: Detection[] }>('/trips/1/detections'),
      api.get<Inspection>('/trips/1/inspection'),
    ])
      .then(([t, d, i]) => {
        setTrips(t.trips)
        setDetections(d.detections)
        setInspection(i)
      })
      .catch((e) => setErr(e instanceof Error ? e.message : '알 수 없는 오류'))

    // 오류 경로도 확인한다 — Mock 에 없는 경로는 404 여야 한다.
    api.get('/nope').catch((e) => {
      if (e instanceof ApiFailure) setMissErr(`${e.status} ${e.code}`)
    })
  }, [])

  // `/__mock?auto=1` 로 열면 폴링까지 자동으로 돈다. 스크린샷·확인용.
  useEffect(() => {
    if (new URLSearchParams(window.location.search).get('auto') === '1') {
      void job.start('PACKING_LIST', {}, 1)
    }
    // job 은 매 렌더 새 객체라 의존성에 넣으면 무한 루프가 된다. 한 번만 돈다.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  // routes.tsx 의 SCREENS 가 03-wireframe 과 코드를 잇는 표다.
  // 여기서 실제로 세어야 죽은 표가 되지 않는다.
  const left = SCREENS.filter((x) => x.todo)

  return (
    <section className="page">
      <h1 className="page-title">Mock 계약 확인</h1>
      <p className="page-note">
        VITE_USE_MOCK = <strong>{String(USE_MOCK)}</strong>
      </p>

      {/* 03-wireframe 의 화면 13개 중 무엇이 남았는지. SCREENS 가 정본이다 */}
      <p className="page-note">
        화면 <strong>{SCREENS.length - left.length} / {SCREENS.length}</strong> 구현
        {left.length > 0 && <> — 남은 것: {left.map((x) => x.id).join(' · ')}</>}
      </p>

      {err && <Failed title="조회 실패" detail={err} />}

      <h2 className="page-title" style={{ fontSize: 16 }}>조회</h2>
      {!trips ? (
        <Skeleton rows={2} />
      ) : (
        <ul>
          <li>여행 {trips.length}건 — {trips.map((t) => t.destination).join(' · ')}</li>
          <li>인식 물품 {detections?.length}건 — 전부 자동 등록됨(승인 게이트 폐기)</li>
          <li>
            검수 — 준비완료 {inspection?.readiness?.prepared.length} · 미완료{' '}
            {inspection?.readiness?.unprepared.length} · 미채택 필수{' '}
            {String(inspection?.readiness?.unacceptedRequiredCount)}
          </li>
          <li>무게 {inspection?.weight ? `${inspection.weight.typicalG}g / 한도 ${inspection.weight.limitG}g (${inspection.weight.verdict})` : '미계산 — 화면이 작업을 시작한다'}</li>
          <li>없는 경로 → {missErr ?? '확인 중'}</li>
        </ul>
      )}

      <h2 className="page-title" style={{ fontSize: 16 }}>폴링</h2>
      {job.phase === 'idle' && (
        <button type="button" className="btn" onClick={() => job.start('PACKING_LIST', {}, 1)}>
          AI 작업 시작
        </button>
      )}
      {job.phase === 'running' && <AiPending label="빠뜨린 물건을 찾는 중" polls={job.polls} />}
      {job.phase === 'failed' && <Failed title="실패" detail={job.error ?? ''} onRetry={job.reset} />}
      {job.phase === 'timeout' && <Failed title="시간이 오래 걸립니다" onRetry={job.reset} />}
      {job.phase === 'done' && (
        <div>
          <p>
            ✅ 완료 — <strong>{job.polls}회 폴링</strong> 후 COMPLETED
          </p>
          <pre style={{ fontSize: 12, overflow: 'auto', background: '#f0f2f1', padding: 10, borderRadius: 8 }}>
            {JSON.stringify(job.output, null, 2).slice(0, 400)}
          </pre>
          <button type="button" className="btn" onClick={job.reset}>다시</button>
        </div>
      )}
    </section>
  )
}
