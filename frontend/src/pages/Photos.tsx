import { useEffect, useRef, useState } from 'react'
import { useNavigate, useParams } from 'react-router-dom'
import { api } from '../api/client'
import { Shell, Steps, TopBar } from '../components/Shell'
import { Failed, Skeleton } from '../components/States'
import type { TripPhoto } from '../types/api'

/**
 * S-03 짐 사진 등록 — <b>이 서비스의 시작점이다.</b>
 *
 * 사진 없이도 시작할 수 있다. 그때는 S-05 의 빈 내 목록과 추천으로 간다
 * (03-wireframe S-03 특이사항).
 */
export default function Photos() {
  const { tripId = '1' } = useParams()
  const nav = useNavigate()
  const [photos, setPhotos] = useState<TripPhoto[] | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [drag, setDrag] = useState(false)
  const [busy, setBusy] = useState(false)
  const fileRef = useRef<HTMLInputElement>(null)

  const load = () => {
    setError(null)
    api
      .get<{ photos: TripPhoto[] }>(`/trips/${tripId}/photos`)
      .then((r) => setPhotos(r.photos))
      .catch((e) => setError(e instanceof Error ? e.message : '알 수 없는 오류'))
  }
  useEffect(load, [tripId])

  /**
   * 사진 등록 — 06:104 `POST /trips/{tripId}/photos` → `201`.
   *
   * Mock 은 파일 본문을 받지 못하므로 <b>브라우저가 만든 미리보기 URL</b>을 보낸다.
   * 실제 서버로 바꿀 때 이 함수의 body 만 FormData 로 바꾸면 된다.
   */
  const upload = async (files: FileList | null) => {
    const picked = Array.from(files ?? []).filter((f) => f.type.startsWith('image/'))
    if (!picked.length) return
    setBusy(true)
    setError(null)
    try {
      await api.post(`/trips/${tripId}/photos`, {
        files: picked.map((f) => ({ fileUrl: URL.createObjectURL(f), bagKind: 'CABIN' })),
      })
      load()
    } catch (e) {
      setError(e instanceof Error ? e.message : '사진을 올리지 못했습니다')
    } finally {
      setBusy(false)
    }
  }

  const empty = photos !== null && photos.length === 0

  return (
    <Shell>
      <TopBar
        title="짐 사진 등록"
        sub="싸 놓은 짐을 펼쳐서 찍어 주세요"
        right={
          <>
            <button type="button" className="btn btn-ghost" onClick={() => nav(`/trips/${tripId}/items`)}>
              사진 없이 시작
            </button>
            <button
              type="button"
              className="btn"
              disabled={empty}
              onClick={() => nav(`/trips/${tripId}/detections`)}
            >
              분석 시작
            </button>
          </>
        }
      />
      <Steps current={2} tripId={tripId} />

      <div className="content">
        <div className="grid" style={{ gridTemplateColumns: 'minmax(0,1fr) 300px' }}>
          <div>
            {error && <Failed title="사진을 불러오지 못했습니다" detail={error} onRetry={load} />}

            {!error && photos === null && (
              <div className="card"><Skeleton rows={2} /></div>
            )}

            {photos !== null && (
              <div className="card">
                <div className="card-head">
                  <h2 className="card-title">올린 사진</h2>
                  <span className="card-sub">{photos.length}장</span>
                  <span className="spacer" />
                  <button
                    type="button"
                    className="btn btn-ghost btn-sm"
                    onClick={() => fileRef.current?.click()}
                    disabled={busy}
                  >
                    {busy ? '올리는 중…' : '사진 추가'}
                  </button>
                </div>

                <div
                  className={`dropzone${drag ? ' is-over' : ''}`}
                  onDragOver={(e) => { e.preventDefault(); setDrag(true) }}
                  onDragLeave={() => setDrag(false)}
                  onDrop={(e) => { e.preventDefault(); setDrag(false); upload(e.dataTransfer.files) }}
                >
                  {empty ? (
                    <div className="state">
                      <p className="state-title">싸 놓은 짐을 찍어 올려 주세요</p>
                      <p className="state-sub">여기로 끌어다 놓거나 아래 버튼을 누르세요</p>
                      <button
                        type="button" className="btn" disabled={busy}
                        onClick={() => fileRef.current?.click()}
                      >
                        {busy ? '올리는 중…' : '사진 선택'}
                      </button>
                    </div>
                  ) : (
                    <div className="thumbs">
                      {photos.map((p) => (
                        <figure key={p.photoId} className="thumb">
                          <img src={p.fileUrl} alt={`짐 사진 ${p.photoId}`} />
                          <figcaption>
                            <span className="badge">{p.bagKind === 'CABIN' ? '기내용' : '위탁용'}</span>
                          </figcaption>
                        </figure>
                      ))}
                    </div>
                  )}
                </div>

                <input
                  ref={fileRef}
                  type="file"
                  accept="image/*"
                  multiple
                  hidden
                  onChange={(e) => {
                    upload(e.target.files)
                    // 같은 파일을 다시 골라도 onChange 가 뜨도록 비운다
                    e.target.value = ''
                  }}
                />
              </div>
            )}
          </div>

          <aside>
            <div className="card">
              <div className="card-head">
                <h2 className="card-title">촬영 가이드</h2>
              </div>
              <ul className="guide">
                <li><b>물건을 펼쳐 놓고</b> 찍어 주세요. 가방을 닫은 채로는 알아볼 수 없습니다</li>
                <li>겹치지 않게 놓으면 인식이 정확해집니다</li>
                <li>밝은 곳에서, 흔들리지 않게</li>
                <li>보조배터리·화장품은 <b>라벨이 보이게</b> 한 장 더 찍으면 좋습니다</li>
              </ul>
              <p className="disclaimer">
                사진은 이 서비스 안에서만 씁니다. 여권·항공권이 함께 찍히지 않게 해 주세요.
              </p>
            </div>
          </aside>
        </div>
      </div>
    </Shell>
  )
}
