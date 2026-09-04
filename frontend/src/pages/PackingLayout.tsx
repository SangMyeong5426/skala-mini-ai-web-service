import { useEffect, useState } from 'react'
import { useNavigate, useParams } from 'react-router-dom'
import { api } from '../api/client'
import { Shell, TopBar } from '../components/Shell'
import { Empty, Failed, Skeleton } from '../components/States'
import { CATEGORY_LABEL } from '../lib/format'
import type {
  Compartment, ItemsResponse, PackingLayout, Placement, UnplacedItem,
} from '../types/api'

/** 가방 단면. 순서가 화면 배치 순서다. */
const ZONES: { id: Compartment; name: string; hint: string }[] = [
  { id: 'TOP', name: '윗칸', hint: '마지막에 넣고 먼저 꺼내는 것' },
  { id: 'MAIN_LEFT', name: '메인 왼쪽', hint: '무거운 것을 바퀴 쪽으로' },
  { id: 'MAIN_RIGHT', name: '메인 오른쪽', hint: '옷·부피 큰 것' },
  { id: 'FRONT_POCKET', name: '앞주머니', hint: '서류·여권처럼 자주 꺼내는 것' },
  { id: 'MESH', name: '메시망', hint: '속옷·양말처럼 눌러도 되는 것' },
]

/** 화면에 그릴 때 필요한 것 — 배치 + 이름. 서버 배치에는 itemId 밖에 없다. */
interface Placed extends Placement {
  name: string
  category?: string
  qty?: number
}

/**
 * S-12 3D 가방 정리 — 물품을 가방 구역에 배치한다.
 *
 * <b>저장은 드래그마다 하지 않는다.</b> 06 이 `PUT` 전체 교체로 정한 이유가
 * 그것이다 — 드래그마다 요청을 보내면 네트워크 순서가 뒤집혔을 때 물건이
 * 엉뚱한 자리에 남는다. 화면에서 다 옮긴 뒤 한 번 저장한다.
 *
 * 좌표(`posX/Y/Z`)는 구역 안에서 몇 번째인지로 자동 계산한다. 화면이
 * 자유 배치가 아니라 <b>구역 단위</b>라 사용자가 소수점을 만질 일이 없다.
 * 스키마는 그대로 지키므로 나중에 진짜 자유 배치로 바꿔도 계약은 안 바뀐다.
 */
export default function PackingLayoutPage() {
  const { tripId } = useParams()
  const nav = useNavigate()
  const [placed, setPlaced] = useState<Placed[] | null>(null)
  const [unplaced, setUnplaced] = useState<UnplacedItem[]>([])
  const [error, setError] = useState<string | null>(null)
  const [dirty, setDirty] = useState(false)
  const [busy, setBusy] = useState(false)
  const [saved, setSaved] = useState(false)

  const load = () => {
    setError(null); setPlaced(null); setDirty(false); setSaved(false)
    Promise.all([
      api.get<PackingLayout>(`/trips/${tripId}/packing-layout`),
      api.get<ItemsResponse>(`/trips/${tripId}/items`),
    ])
      .then(([layout, items]) => {
        const nameOf = new Map(items.items.map((i) => [i.itemId, i]))
        setPlaced(layout.placements.map((p) => {
          const it = nameOf.get(p.itemId)
          return { ...p, name: it?.name ?? `물품 ${p.itemId}`, category: it?.category, qty: it?.qty }
        }))
        setUnplaced(layout.unplaced)
      })
      .catch((e) => setError(e instanceof Error ? e.message : '알 수 없는 오류'))
  }
  useEffect(load, [tripId])

  /** 정리 대기 → 구역. 좌표는 구역 안 순번으로 만든다. */
  const drop = (zone: Compartment, itemId: number) => {
    const already = placed?.find((p) => p.itemId === itemId)
    const n = (placed ?? []).filter((p) => p.compartment === zone && p.itemId !== itemId).length
    const pos = { posX: Math.min(0.9, 0.1 + (n % 3) * 0.4), posY: Math.min(0.9, 0.1 + Math.floor(n / 3) * 0.3), posZ: 0.5 }

    if (already) {
      setPlaced((prev) => (prev ?? []).map((p) => (p.itemId === itemId ? { ...p, compartment: zone, ...pos } : p)))
    } else {
      const src = unplaced.find((u) => u.itemId === itemId)
      if (!src) return
      setPlaced((prev) => [...(prev ?? []), {
        itemId, compartment: zone, ...pos, rotated: false,
        name: src.name, category: src.category, qty: src.qty,
      }])
      setUnplaced((prev) => prev.filter((u) => u.itemId !== itemId))
    }
    setDirty(true); setSaved(false)
  }

  /** 구역 → 정리 대기. */
  const takeOut = (itemId: number) => {
    const p = placed?.find((x) => x.itemId === itemId)
    if (!p) return
    setPlaced((prev) => (prev ?? []).filter((x) => x.itemId !== itemId))
    setUnplaced((prev) => [...prev, {
      itemId, name: p.name,
      category: (p.category ?? 'ETC') as UnplacedItem['category'],
      qty: p.qty ?? 1,
    }])
    setDirty(true); setSaved(false)
  }

  const save = () => {
    setBusy(true); setError(null)
    api.put<PackingLayout>(`/trips/${tripId}/packing-layout`, {
      placements: (placed ?? []).map((p) => ({
        itemId: p.itemId, compartment: p.compartment,
        posX: p.posX, posY: p.posY, posZ: p.posZ, rotated: p.rotated ?? false,
      })),
    })
      .then(() => { setDirty(false); setSaved(true) })
      .catch((e) => setError(e instanceof Error ? e.message : '저장하지 못했습니다'))
      .finally(() => setBusy(false))
  }

  const reset = () => {
    setBusy(true); setError(null)
    // 배치만 지운다. 체크리스트 항목과 완료 상태는 그대로다 (06).
    api.del(`/trips/${tripId}/packing-layout`)
      .then(load)
      .catch((e) => setError(e instanceof Error ? e.message : '초기화하지 못했습니다'))
      .finally(() => setBusy(false))
  }

  return (
    <Shell>
      <TopBar
        title="가방 정리"
        sub="물품을 구역으로 끌어 놓고 한 번에 저장합니다"
        right={
          <>
            <button type="button" className="btn btn-ghost" disabled={busy} onClick={reset}>초기화</button>
            <button type="button" className="btn" disabled={busy || !dirty} onClick={save}>
              {busy ? '저장 중' : dirty ? '저장' : saved ? '저장됨' : '변경 없음'}
            </button>
          </>
        }
      />
      <div className="content">
        {error && <Failed title="가방 정리를 처리하지 못했습니다" detail={error} onRetry={load} />}
        {!error && placed === null && <div className="card"><Skeleton rows={4} /></div>}

        {placed !== null && (
          <div className="grid grid-2" style={{ alignItems: 'start', gap: 20 }}>
            <section>
              <div className="card-head" style={{ marginBottom: 10 }}>
                <h2 className="card-title">가방</h2>
                <span className="spacer" />
                <span className="card-sub">{placed.length}개 배치됨</span>
              </div>
              {ZONES.map((z) => (
                <div
                  key={z.id}
                  className="card"
                  style={{ marginBottom: 10 }}
                  onDragOver={(e) => e.preventDefault()}
                  onDrop={(e) => {
                    e.preventDefault()
                    const id = Number(e.dataTransfer.getData('text/plain'))
                    if (id) drop(z.id, id)
                  }}
                >
                  <div className="card-head" style={{ marginBottom: 8 }}>
                    <h3 className="card-title" style={{ fontSize: 15 }}>{z.name}</h3>
                    <span className="spacer" />
                    <span className="card-sub">{z.hint}</span>
                  </div>
                  <div style={{ display: 'flex', flexWrap: 'wrap', gap: 6, minHeight: 34 }}>
                    {placed.filter((p) => p.compartment === z.id).map((p) => (
                      <button
                        key={p.itemId}
                        type="button"
                        className="badge badge-ok"
                        draggable
                        onDragStart={(e) => e.dataTransfer.setData('text/plain', String(p.itemId))}
                        onClick={() => takeOut(p.itemId)}
                        title="누르면 정리 대기로 뺍니다"
                      >
                        {p.name}{p.qty && p.qty > 1 ? ` ×${p.qty}` : ''}
                      </button>
                    ))}
                    {placed.filter((p) => p.compartment === z.id).length === 0 && (
                      <span className="card-sub">여기로 끌어 놓으세요</span>
                    )}
                  </div>
                </div>
              ))}
            </section>

            <section className="card">
              <div className="card-head" style={{ marginBottom: 10 }}>
                <h2 className="card-title">정리 대기</h2>
                <span className="spacer" />
                <span className="card-sub">{unplaced.length}개</span>
              </div>
              {unplaced.length === 0 && <Empty title="모두 가방에 넣었습니다" />}
              <div style={{ display: 'flex', flexWrap: 'wrap', gap: 6 }}>
                {unplaced.map((u) => (
                  <span
                    key={u.itemId}
                    className="badge"
                    draggable
                    onDragStart={(e) => e.dataTransfer.setData('text/plain', String(u.itemId))}
                    style={{ cursor: 'grab' }}
                  >
                    {u.name}{u.qty > 1 ? ` ×${u.qty}` : ''}
                    <span className="card-sub" style={{ marginLeft: 6 }}>
                      {CATEGORY_LABEL[u.category] ?? u.category}
                    </span>
                  </span>
                ))}
              </div>
              <p className="card-sub" style={{ marginTop: 12 }}>
                구역에 놓은 물품을 누르면 다시 여기로 돌아옵니다. 저장하기 전까지는
                서버에 반영되지 않습니다.
              </p>
            </section>
          </div>
        )}

        <button
          type="button"
          className="btn btn-ghost"
          style={{ marginTop: 16 }}
          onClick={() => nav(`/trips/${tripId}/inspection`)}
        >
          검수 결과로
        </button>
      </div>
    </Shell>
  )
}
