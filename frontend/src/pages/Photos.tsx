import { useEffect, useRef, useState } from 'react'
import { useNavigate, useParams } from 'react-router-dom'
import { api } from '../api/client'
import { USE_MOCK } from '../api/mock'
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
  /** 목록 조회 실패와 구분한다. 올리다 실패한 것은 다른 사건이다 */
  const [uploadError, setUploadError] = useState<string | null>(null)
  /** 지금 올라가는 중인 파일 이름. 03:247 "파일별 업로드 진행" */
  const [pending, setPending] = useState<string[]>([])
  // 03 S-03: "기내용·위탁용 구분". 값을 정할 수단이 없으면 배지가 거짓말이 된다
  const [bagKind, setBagKind] = useState<'CABIN' | 'CHECKED'>('CABIN')
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
   * 서버가 실제로 받는 형식·용량. <b>추측이 아니라 서버 설정 그대로다.</b>
   *   PhotoService.java:33  `ALLOWED = Set.of("jpg", "jpeg", "png", "webp")`
   *   application.properties:36  `max-file-size=10MB`
   *
   * 예전에는 `image/*` 로만 걸렀다. HEIC·GIF·SVG 가 통과해 서버까지 갔고,
   * 서버는 검증을 <b>전부 먼저</b> 돌려 하나라도 틀리면 400 을 내며 아무것도
   * 저장하지 않는다(PhotoService.java:62-76). 아이폰 사진 한 장 때문에 나머지
   * 정상 사진까지 통째로 올라가지 않았다.
   */
  const ALLOWED_EXT = /\.(jpe?g|png|webp)$/i
  const MAX_BYTES = 10 * 1024 * 1024

  /**
   * 사진 등록 — 06:104 `POST /trips/{tripId}/photos` → `201`.
   *
   * <b>파일마다 따로 보낸다.</b> 03:249 가 "형식·용량 초과 시 해당 썸네일에
   * 표시하고 나머지는 그대로 진행" 으로 정했는데, 한 요청에 묶으면 서버가
   * 전부를 한 번에 거절하므로 그렇게 할 수 없다.
   *
   * Mock 은 파일 본문을 받지 못하므로 <b>브라우저가 만든 미리보기 URL</b>을 보낸다.
   */
  const upload = async (files: FileList | null) => {
    const all = Array.from(files ?? [])
    if (!all.length) return

    const tooBig = all.filter((f) => f.size > MAX_BYTES)
    const badType = all.filter((f) => !ALLOWED_EXT.test(f.name) && f.size <= MAX_BYTES)
    const picked = all.filter((f) => ALLOWED_EXT.test(f.name) && f.size <= MAX_BYTES)

    const notes: string[] = []
    if (badType.length) notes.push(`${badType.map((f) => f.name).join(', ')} — jpg·png·webp 만 됩니다`)
    if (tooBig.length) notes.push(`${tooBig.map((f) => f.name).join(', ')} — 10MB 를 넘습니다`)

    if (!picked.length) {
      // 조용히 버리면 사용자는 "느린 건가" 와 "거부됐다" 를 구분할 수 없다
      setUploadError(notes.join(' · ') || '올릴 수 있는 사진이 없습니다.')
      return
    }

    setUploadError(null)
    // 03:247 로딩 상태 — 파일별 진행. 지금 무엇이 올라가는지 이름으로 보여준다
    setPending(picked.map((f) => f.name))
    setBusy(true)

    const failed: string[] = []
    for (const f of picked) {
      try {
        /*
         * 06:1032-1040 — 실제 계약은 <b>multipart</b>다. `files` 파트에 파일
         * 바이트를, `bagKind` 를 파라미터로 보낸다. `blob:` URL 은 이 브라우저
         * 안에서만 유효한 미리보기 주소라 서버가 읽을 수 없다.
         *
         * Mock 은 파일 본문을 다루지 못하므로 <b>API 경계에서만</b> 갈라
         * 미리보기 URL 을 넘긴다. 화면 코드는 한 갈래다.
         */
        if (USE_MOCK) {
          await api.post(`/trips/${tripId}/photos`, {
            files: [{ fileUrl: URL.createObjectURL(f), bagKind }],
          })
        } else {
          const form = new FormData()
          form.append('files', f)
          form.append('bagKind', bagKind)
          await api.post(`/trips/${tripId}/photos`, form)
        }
      } catch (e) {
        failed.push(`${f.name} — ${e instanceof Error ? e.message : '올리지 못했습니다'}`)
      } finally {
        setPending((q) => q.filter((n) => n !== f.name))
      }
    }

    setBusy(false)
    setUploadError([...notes, ...failed].join(' · ') || null)
    load()
  }

  /**
   * 06:106 #12 — `DELETE /trips/{tripId}/photos/{photoId}` → 204.
   * 03:245 주요 요소의 "미리보기 썸네일·<b>삭제</b>" 다.
   *
   * 서버의 BAG_CHECK 은 그 여행의 사진을 <b>전부</b> 분석하므로
   * (AiJobService.java:141), 잘못 올린 사진을 빼는 길이 여기 말고 없다.
   */
  const removePhoto = async (photoId: number) => {
    try {
      await api.del(`/trips/${tripId}/photos/${photoId}`)
      setUploadError(null)
    } catch (e) {
      setUploadError(e instanceof Error ? e.message : '삭제하지 못했습니다.')
    }
    load()
  }

  const empty = !photos?.length

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
            {/* 03:249 — 올리다 난 오류는 목록 조회 실패와 다르다. 제목이 원인을
                거꾸로 말하면 사용자가 엉뚱한 곳을 고친다 */}
            {uploadError && (
              <Failed
                title="사진을 올리지 못했습니다"
                detail={uploadError}
                onRetry={() => fileRef.current?.click()}
              />
            )}

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
                    {busy ? `올리는 중… ${pending.length}장 남음` : '사진 추가'}
                  </button>
                  <div className="chips" style={{ marginLeft: 'auto' }}>
                    {(['CABIN', 'CHECKED'] as const).map((k) => (
                      <button
                        key={k} type="button"
                        className={`pick${bagKind === k ? ' is-on' : ''}`}
                        aria-pressed={bagKind === k}
                        onClick={() => setBagKind(k)}
                      >{k === 'CABIN' ? '기내용' : '위탁용'}</button>
                    ))}
                  </div>
                </div>

                <div
                  className={`dropzone${drag ? ' is-over' : ''}`}
                  onDragOver={(e) => { e.preventDefault(); setDrag(true) }}
                  onDragLeave={() => setDrag(false)}
                  onDrop={(e) => { e.preventDefault(); setDrag(false); upload(e.dataTransfer.files) }}
                >
                  {empty && !pending.length ? (
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
                            {/* 03:245 — 미리보기 썸네일·삭제 */}
                            <button
                              type="button" className="thumb-x"
                              onClick={() => removePhoto(p.photoId)}
                              aria-label={`짐 사진 ${p.photoId} 삭제`}
                              title="삭제"
                            >×</button>
                          </figcaption>
                        </figure>
                      ))}
                      {/* 올라가는 중인 파일을 자리로 보여준다 — 몇 장 중 몇 장인지 */}
                      {pending.map((name) => (
                        <figure key={name} className="thumb thumb-pending">
                          <div className="thumb-ph"><span className="dots"><i /><i /><i /></span></div>
                          <figcaption><span className="badge">{name}</span></figcaption>
                        </figure>
                      ))}
                    </div>
                  )}
                </div>

                <input
                  ref={fileRef}
                  type="file"
                  accept=".jpg,.jpeg,.png,.webp"
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
