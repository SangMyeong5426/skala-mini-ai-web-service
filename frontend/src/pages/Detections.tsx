import { useEffect, useRef, useState } from 'react'
import { useNavigate, useParams } from 'react-router-dom'
import { api } from '../api/client'
import { Shell, Steps, TopBar } from '../components/Shell'
import { AiPending, Empty, Failed, Skeleton } from '../components/States'
import { useAiJob } from '../hooks/useAiJob'
import type { BagCheckOutput, ChecklistItem, Detection, TripDetail } from '../types/api'

const LEVEL: Record<string, { label: string; cls: string }> = {
  HIGH: { label: '신뢰도 높음', cls: 'badge-ok' },
  MEDIUM: { label: '신뢰도 보통', cls: '' },
  LOW: { label: '신뢰도 낮음', cls: 'badge-warn' },
}

/**
 * S-04 인식 결과 · 사후 수정.
 *
 * <b>승인 게이트는 폐기됐다.</b> 06:686-737 이 BAG_CHECK 완료 시 이름 있는 인식
 * 물품을 즉시 PHOTO/PREPARED 로 등록하도록 바꿨고, `approved` 전송은 400 이다.
 * 그래서 이 화면에 승인 버튼이 없다.
 *
 * 남는 일은 <b>고치는 것</b>이다 — 잘못 인식한 이름·수량 수정.
 */
export default function Detections() {
  const { tripId = '1' } = useParams()
  const nav = useNavigate()
  const [dets, setDets] = useState<Detection[] | null>(null)
  const [items, setItems] = useState<ChecklistItem[]>([])
  const [photoIds, setPhotoIds] = useState<number[]>([])
  const [error, setError] = useState<string | null>(null)
  /** 조회 실패(error)와 구분한다. 수정·삭제가 거절당한 것은 다른 사건이다 */
  const [actionError, setActionError] = useState<string | null>(null)
  const job = useAiJob<BagCheckOutput>()
  /** 자동 추천. 폴링해야 recommendationJobId 가 생긴다 */
  const rec = useAiJob()

  /**
   * 재조회. 갱신된 목록·인식·사진을 <b>함께</b> 돌려준다.
   *
   * 추천 요청이 내 목록을 써야 하고, 자동 분석이 사진 ID 를 써야 한다.
   * state 를 읽으면 방금 setState 한 값이 아직 반영되지 않아 빈 배열을 보낸다.
   */
  const load = (): Promise<{ items: ChecklistItem[]; dets: Detection[]; photoIds: number[] }> => {
    setError(null)
    return Promise.all([
      api.get<{ detections: Detection[] }>(`/trips/${tripId}/detections`),
      api.get<{ items: ChecklistItem[] }>(`/trips/${tripId}/items`),
      api.get<{ photos: { photoId: number }[] }>(`/trips/${tripId}/photos`),
    ])
      .then(([d, i, ph]) => {
        const ids = ph.photos.map((x) => x.photoId)
        setDets(d.detections); setItems(i.items); setPhotoIds(ids)
        return { items: i.items, dets: d.detections, photoIds: ids }
      })
      .catch((e) => {
        setError(e instanceof Error ? e.message : '알 수 없는 오류')
        return { items: [] as ChecklistItem[], dets: [] as Detection[], photoIds: [] as number[] }
      })
  }

  const analyze = () => analyzeWith(photoIds)

  const analyzeWith = async (ids: number[]) => {
    // 사진 ID 를 박아 두면 다른 여행에서 남의 사진을 분석하려 든다.
    // 06 의 소유권 검증에서 거절되는 요청이다.
    // 완료를 확인하고 넘어간다. FAILED·timeout 이면 후속 추천을 걸지 않는다.
    const ok = await job.start('BAG_CHECK', { photoIds: ids }, Number(tripId))
    const fresh = (await load()).items
    if (!ok) return

    /*
     * 06:705 · 03:262 — 자동 등록 뒤 <b>곧바로 추가 추천을 요청</b>한다.
     * "S-04 에서 사용자 입력을 기다리지 않는다."
     *
     * 07:470 대로 alreadyPacked 는 PREPARED 만 보낸다. 실패해도 이 화면은
     * 이미 제 일을 했으므로 오류로 덮지 않는다 — S-05 에서 다시 시도할 수 있다.
     */
    try {
      const t = await api.get<TripDetail>(`/trips/${tripId}`)
      // 07 은 destination·startDate 에 minLength 1 을 요구한다. 폴백을 만들지 않고
      // 값이 없으면 요청 자체를 걸지 않는다 — "FE 가 모르는 값을 요구하지 않는다".
      if (!t.destination || !t.startDate || !t.endDate) return

      /*
       * <b>폴링까지 해야 완료된다.</b> POST 만 하고 작업 ID 를 버리면
       * GET items 의 recommendationJobId 가 계속 null 이라 S-05 가 후보를
       * 찾지 못한다. 기존 폴링 경로를 그대로 쓴다.
       */
      await rec.start('PACKING_LIST', {
        destination: t.destination,
        startDate: t.startDate,
        endDate: t.endDate,
        transport: t.transport,
        purpose: t.purpose ?? 'TOUR',
        note: t.note ?? null,
        alreadyPacked: fresh
          .filter((i) => i.checkStatus === 'PREPARED')
          .map((i) => ({ name: i.name, category: i.category, qty: i.qty })),
      }, Number(tripId))
    } catch {
      /* 추천 실패는 S-05 에서 다시 시도한다 */
    }
  }

  /*
   * <b>들어오자마자 분석을 건다.</b> 03:262 — "S-04 에서 사용자 입력을 기다리지
   * 않는다". 03:259 가 BAG_CHECK 접수를 이 화면의 호출 API 로 적어 두었다.
   *
   * 예전에는 S-03 의 `분석 시작` 이 이 화면으로 넘기기만 했다. 그래서 사진을
   * 올리고 넘어오면 "인식된 물품이 없습니다" 라는 빈 상태가 먼저 보였고,
   * 사용자가 `다시 분석` 을 눌러야 그제서야 분석이 시작됐다. 아직 한 번도
   * 분석한 적이 없는데 "다시" 를 누르라는 화면이었다.
   *
   * 이미 인식 결과가 있으면 걸지 않는다 — 사후 수정하러 다시 들어온 것이다.
   */
  const kicked = useRef(false)
  useEffect(() => {
    kicked.current = false
    void load().then((r) => {
      if (kicked.current || r.dets.length > 0 || r.photoIds.length === 0) return
      kicked.current = true
      void analyzeWith(r.photoIds)
    })
  }, [tripId])


  /**
   * 잘못 인식한 이름·수량을 고친다. 승인이 아니라 <b>사후 수정</b>이다.
   *
   * <b>실패를 삼키지 않는다.</b> 06:113 이 이 PATCH 의 오류로 400·404·409 를
   * 적어 뒀다 — 이름이 비었거나 100자를 넘거나, 수량이 1~99 밖이거나, 연결
   * 항목이 여러 개라 무엇을 고칠지 모호할 때(409 AMBIGUOUS_LINK)다.
   *
   * 예전에는 catch 가 없어서 거절당하면 행이 옛 이름으로 되돌아가기만 했다.
   * 사용자는 저장이 왜 안 됐는지 알 수 없었다.
   */
  const edit = async (d: Detection, patch: { name?: string; qty?: number }) => {
    try {
      await api.patch(`/trips/${tripId}/detections/${d.detectionId}`, patch)
      setActionError(null)
    } catch (e) {
      setActionError(e instanceof Error ? e.message : '수정하지 못했습니다.')
    }
    void load()
  }

  /**
   * 오인식은 <b>내 목록에서 지운다.</b> 03:257 의 `목록에서 삭제` 이고,
   * 06:755 가 "오인식 삭제는 기존 item DELETE 를 사용한다" 로 정했다.
   * 새 엔드포인트를 만들지 않는다.
   *
   * 인식 행 자체는 남는다 — 사진에 그렇게 찍혀 있었다는 사실은 사실이다.
   * 지워지는 것은 그것 때문에 만들어진 내 목록 항목이고, 연결은 FK 로 함께
   * 사라진다(schema.sql `ON DELETE CASCADE`).
   */
  const removeItem = async (itemId: number) => {
    try {
      await api.del(`/trips/${tripId}/items/${itemId}`)
      setActionError(null)
    } catch (e) {
      setActionError(e instanceof Error ? e.message : '삭제하지 못했습니다.')
    }
    void load()
  }

  const list = dets ?? []
  // 03-wireframe: "확인 필요" 묶음 = missingInfo 가 있거나 신뢰도가 낮은 것
  const needsCheck = list.filter((d) => d.missingInfo || d.confidenceLevel === 'LOW')

  return (
    <Shell>
      <TopBar
        title="인식 결과"
        sub="사진에서 찾아 자동 등록했어요. 틀린 것만 고치시면 됩니다"
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
        {/* 수정·삭제가 거절당한 것은 조회 실패와 다른 사건이다 */}
        {actionError && (
          <Failed title="저장하지 못했습니다" detail={actionError} onRetry={() => { void load() }} />
        )}

        {rec.phase === 'running' && (
          <AiPending label="부족한 준비물을 추천하는 중" polls={rec.polls} />
        )}

        {job.phase === 'running' && (
          <div className="card">
            <AiPending label="사진을 분석하고 목록에 등록하는 중" polls={job.polls} />
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

        {list.length > 0 && (
          <div className="card">
            <div className="card-head">
              <h2 className="card-title">사진에서 찾아 등록했어요</h2>
              <span className="card-sub">{list.length}개</span>
              <span className="spacer" />
              {needsCheck.length > 0 && (
                <span className="badge badge-warn">확인 필요 {needsCheck.length}</span>
              )}
            </div>
            <p className="card-sub" style={{ marginBottom: 12 }}>
              인식한 물품은 <b>승인 없이 체크리스트에 등록</b>됩니다.
              잘못 인식한 이름이나 수량은 여기서 고치세요.
            </p>
            <ul>
              {list.map((d) => (
                <DetectionRow key={d.detectionId} d={d} items={items} onEdit={edit} onRemove={removeItem} />
              ))}
            </ul>
          </div>
        )}
      </div>
    </Shell>
  )
}

/**
 * 인식 결과 한 줄.
 *
 * 승인 버튼이 없다. 06:732 가 <i>"approved 전송 → 400. FE 에 승인 버튼·요청을
 * 두지 않는다"</i> 로 못박았다. 이미 등록된 것이라 남는 일은 <b>고치는 것</b>뿐이다.
 */
function DetectionRow({
  d, items, onEdit, onRemove,
}: {
  d: Detection
  items: ChecklistItem[]
  onEdit: (d: Detection, patch: { name?: string; qty?: number }) => void
  onRemove: (itemId: number) => void
}) {
  const [editing, setEditing] = useState(false)
  const [name, setName] = useState(d.name)
  const [qty, setQty] = useState(d.qty)
  const lv = LEVEL[d.confidenceLevel] ?? LEVEL.MEDIUM
  const linked = d.linkedItems?.[0]
  /*
   * 06:667 의 linkedItems 에는 itemId 만 있다. 이름은 <b>내 목록에서 찾는다.</b>
   * 실서버에 붙였을 때 "내 목록의  로 등록됨" 처럼 이름 자리가 비어 있었다.
   * 아직 목록을 못 받았거나 그 사이 지워졌으면 이름 없는 문장으로 낮춘다.
   */
  const linkedName = linked
    ? items.find((i) => i.itemId === linked.itemId)?.name ?? null
    : null

  /**
   * 06:746 — "이름 1~100자 · qty 1~99 등 item 검증 적용".
   * 서버가 거절할 값은 보내지 않는다. 이름을 통째로 지우고 저장하면
   * 예전에는 `name: ''` 이 그대로 나가 400 을 받았다.
   */
  const save = () => {
    const trimmed = name.trim()
    if (!trimmed || trimmed.length > 100) { setName(d.name); setEditing(false); return }
    setEditing(false)
    if (trimmed !== d.name || qty !== d.qty) onEdit(d, { name: trimmed, qty })
  }

  return (
    <li className="row" style={{ alignItems: 'flex-start' }}>
      <div className="row-main">
        {editing ? (
          <div className="edit-row">
            <input value={name} onChange={(e) => setName(e.target.value)} aria-label="물품 이름" />
            <input
              type="number" min={1} max={99} value={qty}
              onChange={(e) => setQty(Math.min(99, Math.max(1, Number(e.target.value))))}
              aria-label="수량"
            />
          </div>
        ) : (
          <p className="row-name">
            {d.name} <span className="card-sub">× {d.qty}</span>{' '}
            <span className={`badge ${lv.cls}`}>{lv.label}</span>
          </p>
        )}
        {d.missingInfo && (
          <p className="row-sub">확인 필요 — <b>{d.missingInfo}</b></p>
        )}
        {linked ? (
          <p className="row-sub">
            {linkedName ? <>내 목록의 <b>{linkedName}</b> 로 등록됨</> : '내 목록에 등록됨'}
          </p>
        ) : (
          /*
           * <b>연결이 없으면 내 목록에 없다.</b> 06 에서 linkedItems 가 곧
           * 등록됐다는 증거다. 빈 배열은 연결을 끊었거나(matchedItemIds: [])
           * 목록에서 지운 뒤의 상태다.
           *
           * 예전에는 이 자리에 "내 목록에 새로 추가됨" 을 띄우고 오른쪽에는
           * "자동 등록됨" 배지를 달았다. 뜻이 정반대였다. 실제로 시드의
           * 인식 7번(가위)이 linkedItems: [] 인데 등록된 것처럼 보였다.
           */
          <p className="row-sub">내 목록에 없습니다</p>
        )}
      </div>
      <div className="row-right">
        {linked && <span className="badge badge-ok">자동 등록됨</span>}
        {editing ? (
          <button type="button" className="btn btn-sm" onClick={save}>저장</button>
        ) : (
          <button type="button" className="btn btn-ghost btn-sm" onClick={() => setEditing(true)}>
            수정
          </button>
        )}
        {/* 03:257 — 오인식은 목록에서 지운다. 인식 행 자체는 남는다 */}
        {linked && !editing && (
          <button
            type="button" className="btn btn-ghost btn-sm"
            onClick={() => onRemove(linked.itemId)}
          >
            목록에서 삭제
          </button>
        )}
      </div>
    </li>
  )
}
