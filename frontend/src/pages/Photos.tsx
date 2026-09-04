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
 * 사진은 <b>필수</b>다. 이 서비스의 약속이 "사진 한 장으로" 라서, 건너뛰면
 * 남는 것이 여행 조건만 보고 만든 일반 체크리스트다
 * (03-wireframe S-03 특이사항).
 */
/** 가방 종류 두 단계. 순서가 화면 순서다 — 들고 탈 것을 먼저 챙긴다 */
type BagSlot = 'CABIN' | 'CHECKED'
const SLOTS: { kind: BagSlot; title: string; sub: string }[] = [
  {
    kind: 'CABIN',
    title: '기내용 짐',
    /*
     * <b>어떻게 찍는지가 아니라 무엇이 그 가방에 드는지</b>를 적는다. 촬영 방법은
     * 오른쪽 가이드가 맡는다. 여기서 사용자가 헷갈리는 것은 "어떻게 찍나" 가
     * 아니라 <b>"이건 어느 쪽이지"</b> 다.
     *
     * 규정에 걸리기 쉬운 물건을 예로 든다. 보조배터리는 기내만 되고 100ml 넘는
     * 액체는 위탁만 되는데, 그 둘이 곧 반입 판정에서 가장 자주 걸리는 항목이다.
     */
    sub: '들고 타는 가방입니다. 보조배터리·노트북처럼 부칠 수 없는 것이 여기 들어갑니다',
  },
  {
    kind: 'CHECKED',
    title: '위탁용 짐',
    sub: '부치는 가방입니다. 100ml 넘는 액체·큰 가위처럼 들고 탈 수 없는 것이 여기 들어갑니다',
  },
]

export default function Photos() {
  const { tripId = '1' } = useParams()
  const nav = useNavigate()
  const [photos, setPhotos] = useState<TripPhoto[] | null>(null)
  const [error, setError] = useState<string | null>(null)
  // 끌어다 놓는 중인 칸. boolean 이면 두 칸이 동시에 켜진다
  const [drag, setDrag] = useState<BagSlot | null>(null)
  const [busy, setBusy] = useState(false)
  /** 목록 조회 실패와 구분한다. 올리다 실패한 것은 다른 사건이다 */
  const [uploadError, setUploadError] = useState<string | null>(null)
  /** 지금 올라가는 중인 파일 이름. 03:247 "파일별 업로드 진행" */
  const [pending, setPending] = useState<string[]>([])
  /*
   * 03 S-03 "기내용·위탁용 구분" 을 <b>칸으로</b> 푼다.
   *
   * 고르는 상태를 두지 않는다. 어느 칸에 놓았는지가 곧 종류라, 파일을 고를 때
   * 그 칸의 값을 함께 실어 보낸다. 상태로 들고 있으면 "지금 무슨 모드인지" 를
   * 화면이 또 말해 줘야 하고, 말해 주지 않으면 잘못 올려도 모른다.
   */
  /** 지금 보고 있는 단계. 이 값이 곧 올릴 사진의 종류다 */
  const [slot, setSlot] = useState<BagSlot>('CABIN')
  /** 지금 파일 선택창을 연 칸. 고른 파일을 어느 종류로 올릴지 여기서 안다 */
  const pickedKind = useRef<BagSlot>('CABIN')
  /** 올라가는 중인 파일이 어느 칸 것인지 — 자리표시자를 그 칸에만 그린다 */
  const [pendingKind, setPendingKind] = useState<BagSlot | null>(null)
  const fileRef = useRef<HTMLInputElement>(null)

  /** 그 칸의 파일 선택창을 연다 */
  const pick = (kind: BagSlot) => {
    pickedKind.current = kind
    fileRef.current?.click()
  }

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
  const upload = async (files: FileList | null, kind: BagSlot) => {
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
    setPendingKind(kind)
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
            files: [{ fileUrl: URL.createObjectURL(f), bagKind: kind }],
          })
        } else {
          const form = new FormData()
          form.append('files', f)
          form.append('bagKind', kind)
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

  const byKind = (k: BagSlot) => (photos ?? []).filter((p) => p.bagKind === k)
  const unknownPhotos = (photos ?? []).filter((p) => !p.bagKind)
  const current = SLOTS.find((x) => x.kind === slot)!
  const isLast = slot === SLOTS[SLOTS.length - 1].kind
  const empty = !photos?.length

  return (
    <Shell>
      {/*
        * <b>다음으로 가는 버튼을 위에 두지 않는다.</b> 여행 정보(S-02)와 검수
        * 결과(S-06)가 이미 아래 오른쪽에 두고 있어서, 이 화면만 위에 있으면
        * 3단계를 지나는 동안 눈이 위아래로 튄다. 흐름이 있는 화면은 같은 자리에.
        */}
      <TopBar title="짐 사진 등록" sub="싸 놓은 짐을 펼쳐서 찍어 주세요" />
      <Steps current={2} tripId={tripId} />

      {/*
        * 가이드는 오른쪽에 둔다. 사진을 올리는 <b>동안</b> 보는 글이라, 아래에
        * 두면 썸네일이 쌓일수록 화면 밖으로 밀려 정작 필요할 때 안 보인다.
        *
        * <b>바깥 폭은 그대로다.</b> `.content` 전체를 쓰고 그 안에서만 나누므로,
        * S-02 여행 정보와 같은 자리에서 시작해 같은 자리에서 끝난다.
        */}
      <div className="content">
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
            {/*
              * 예전에는 여기에 `spacer` 와 `marginLeft: auto` 가 <b>같이</b>
              * 있었다. 둘 다 오른쪽으로 미는 장치라 서로 밀어내면서 가운데
              * 버튼이 허공에 뜬 것처럼 보였다. 미는 장치는 하나만 둔다.
              *
              * `사진 추가` 버튼도 뺐다. 같은 일을 하는 자리가 아래 사진 칸에
              * 이미 있어서, 어느 쪽을 눌러야 하는지 고르게 만들 이유가 없다.
              */}
            <div className="card-head">
              <h2 className="card-title">올린 사진</h2>
              <span className="card-sub">
                {busy ? `올리는 중… ${pending.length}장 남음` : `${photos.length}장`}
              </span>
            </div>

            {/*
              * <b>가이드를 카드 안쪽 오른쪽 열로 넣는다.</b> 바깥에 `aside` 로
              * 두면 카드가 가이드 폭만큼 짧아져서, 카드 아래 오른쪽에 붙는
              * `다음` 버튼이 S-02 여행 정보의 것보다 300px 쯤 왼쪽에 선다.
              * 1→2 로 이어 걷는 자리라 그 어긋남이 바로 보인다.
              *
              * 안으로 넣으면 카드가 콘텐츠 폭을 그대로 쓰므로 버튼 자리가
              * 맞고, 사진 칸도 그만큼 넓어진다.
              */}
            <div className="bag-cols">
              <div>

              {/*
                * <b>한 번에 한 가방만 다룬다.</b> 두 칸을 나란히 두면 어느 쪽에
                * 놓아야 하는지 매번 고르게 되고, 칸이 좁아져 썸네일도 작아진다.
                *
                * 단계로 나누면 화면이 <b>지금 무엇을 받는지</b> 한 가지만 말한다.
                * 놓는 자리가 곧 종류라 고를 것이 없는 것은 그대로다.
                *
                * 기내용에 사진이 없어도 넘어갈 수 있다 — 위탁 수하물만 부치는
                * 여행이 있다. 다만 <b>둘 다 비면</b> 분석은 못 한다.
                */}
              {/*
                * <b>눌러서 오가지 않는다.</b> 예전에는 이 표시가 버튼이라
                * 아래 `다음` 과 같은 일을 하는 자리가 둘이 됐다. 어느 쪽을
                * 눌러야 하는지 고르게 만들 이유가 없다.
                *
                * 여기는 <b>지금 어디인지 읽는 곳</b>이고, 오가는 것은 아래
                * `이전`·`다음` 이 맡는다.
                */}
              <ol className="bag-steps" aria-label="가방 종류">
                {(SLOTS).map((sl, i) => (
                  <li
                    key={sl.kind}
                    className={`bag-step${slot === sl.kind ? ' is-current' : ''}`}
                    aria-current={slot === sl.kind ? 'step' : undefined}
                  >
                    <span className="bag-step-in">
                      <span className="bag-step-no" aria-hidden="true">
                        {byKind(sl.kind).length ? '✓' : i + 1}
                      </span>
                      <span>{sl.title}</span>
                      <span className="card-sub">{byKind(sl.kind).length}장</span>
                    </span>
                  </li>
                ))}
              </ol>

              <div className="bag-sec-head">
                <h3 className="bag-sec-title">{current.title}</h3>
                <span className="card-sub">{current.sub}</span>
              </div>

              <div
                className={`dropzone${drag ? ' is-over' : ''}`}
                onDragOver={(e) => { e.preventDefault(); setDrag(slot) }}
                onDragLeave={() => setDrag(null)}
                onDrop={(e) => { e.preventDefault(); setDrag(null); upload(e.dataTransfer.files, slot) }}
              >
                <div className="thumbs">
                  <button
                    type="button" className="thumb-add" disabled={busy}
                    onClick={() => pick(slot)}
                  >
                    <span aria-hidden="true">＋</span>
                    <span>{byKind(slot).length ? '사진 추가' : '사진 선택'}</span>
                    <span className="thumb-add-sub">끌어다 놓아도 됩니다</span>
                  </button>
                  {byKind(slot).map((p) => (
                    <figure key={p.photoId} className="thumb">
                      <img src={p.fileUrl} alt={`짐 사진 ${p.photoId}`} />
                      <figcaption>
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
                  {pendingKind === slot && pending.map((name) => (
                    <figure key={name} className="thumb thumb-pending">
                      <div className="thumb-ph"><span className="dots"><i /><i /><i /></span></div>
                      <figcaption><span className="badge">{name}</span></figcaption>
                    </figure>
                  ))}
                </div>
              </div>

              {/*
                * 종류를 고르지 않고 올린 사진. 이 화면으로는 더 만들 수 없고
                * 예전 데이터에만 남는다. <b>있을 때만</b> 보여준다 — 지우는 길이
                * 여기 말고 없기 때문이다.
                */}
              {unknownPhotos.length > 0 && (
                <div className="bag-unknown">
                  <span className="card-sub">종류 미상 {unknownPhotos.length}장</span>
                  <div className="thumbs">
                    {unknownPhotos.map((p) => (
                      <figure key={p.photoId} className="thumb">
                        <img src={p.fileUrl} alt={`짐 사진 ${p.photoId}`} />
                        <figcaption>
                          <button
                            type="button" className="thumb-x"
                            onClick={() => removePhoto(p.photoId)}
                            aria-label={`짐 사진 ${p.photoId} 삭제`}
                            title="삭제"
                          >×</button>
                        </figcaption>
                      </figure>
                    ))}
                  </div>
                </div>
              )}

              <input
                ref={fileRef}
                type="file"
                accept=".jpg,.jpeg,.png,.webp"
                multiple
                hidden
                onChange={(e) => {
                  // 어느 칸에서 열었는지는 pick() 이 기억해 둔다
                  upload(e.target.files, pickedKind.current)
                  // 같은 파일을 다시 골라도 onChange 가 뜨도록 비운다
                  e.target.value = ''
                }}
              />

              </div>

              <aside className="bag-guide">
                <h2 className="bag-guide-title">촬영 가이드</h2>
                <ul className="guide">
                  <li><b>물건을 펼쳐 놓고</b> 찍어 주세요. 가방을 닫은 채로는 알아볼 수 없습니다</li>
                  <li>겹치지 않게 놓으면 인식이 정확해집니다</li>
                  <li>밝은 곳에서, 흔들리지 않게</li>
                  <li>보조배터리·화장품은 <b>라벨이 보이게</b> 한 장 더 찍으면 좋습니다</li>
                </ul>
                {/*
                  * 사진이 어디로 나가는지 밝힌다(#52). `AI_PROVIDER=openai` 면
                  * 바이트가 바깥으로 나간다 — 올리기 <b>전에</b> 알아야 하는 사실이라
                  * 가이드 맨 아래에 둔다.
                  */}
                <p className="disclaimer">
                  사진은 짐 물품 분석에만 사용하며, 실제 AI 모드에서는 OpenAI로 전송됩니다.
                  여권·항공권이 함께 찍히지 않게 해 주세요.
                </p>
              </aside>
            </div>

            {/*
              * <b>버튼을 카드 안에 둔다.</b> S-02 여행 정보가 폼 카드 안쪽
              * 아래에 저장 버튼을 두는 것과 같은 구조다. 카드 밖에 두면
              * 어느 내용에 대한 동작인지 묶이지 않는다. 두 열 <b>바깥</b>에
              * 두는 것도 같은 이유다 — 카드 폭을 다 써야 버튼 오른쪽 끝이
              * S-02 의 것과 같은 자리에 온다.
              *
              * <b>`사진 없이 시작` 은 없다.</b> 이 서비스의 약속이 "사진 한
              * 장으로" 라서, 건너뛰면 여행 조건만 보고 만든 일반 체크리스트가
              * 남는다. 대신 왜 못 넘어가는지 버튼 옆에 적는다 — 비활성
              * 버튼만 두면 사용자는 고장으로 읽는다.
              */}
            <div className="form-foot" style={{ marginTop: 20, paddingTop: 20, borderTop: '1px solid var(--line)' }}>
              {isLast && empty && (
                <p className="card-sub" style={{ marginRight: 'auto', alignSelf: 'center' }}>
                  분석할 사진이 필요합니다. 한 장만 올려도 시작할 수 있어요.
                </p>
              )}
              {isLast ? (
                <>
                  {/* 왼쪽 끝에 붙인다 — 되돌아가는 길과 나아가는 길을 양끝으로 나눈다 */}
                  <button
                    type="button" className="btn btn-ghost"
                    style={{ marginRight: 'auto' }}
                    onClick={() => setSlot('CABIN')}
                  >
                    ← 이전: 기내용 짐
                  </button>
                  <button
                    type="button"
                    className="btn"
                    disabled={empty || busy}
                    /*
                     * <b>분석해 달라는 뜻</b>을 함께 넘긴다. S-04 는 인식 결과가
                     * 이미 있으면 분석을 건너뛰는데(사후 수정하러 다시 들어온
                     * 경우다), 그러면 사진을 새로 올리고 눌러도 아무 일이 없었다.
                     */
                    onClick={() => nav(`/trips/${tripId}/detections?analyze=1`)}
                  >
                    분석 시작
                  </button>
                </>
              ) : (
                <>
                  {/*
                    * <b>첫 단계의 `이전` 은 화면 밖으로 나간다.</b> 앞 단계가
                    * 이 화면 안에 없으니 흐름상 앞은 S-02 여행 정보다. 여기서만
                    * `이전` 이 사라지면 두 단계의 왼쪽 아래가 서로 달라 보이고,
                    * 사용자는 되돌아갈 길을 위쪽 표시줄에서 다시 찾아야 한다.
                    *
                    * `/edit` 으로 보낸다 — `/trips/new` 는 빈 폼이라 입력한
                    * 값이 사라진 것처럼 보이고, 저장하면 여행이 하나 더 생긴다.
                    */}
                  <button
                    type="button" className="btn btn-ghost"
                    style={{ marginRight: 'auto' }}
                    onClick={() => nav(`/trips/${tripId}/edit`)}
                  >
                    ← 이전: 여행 정보
                  </button>
                  {/* 기내용이 비어도 넘어갈 수 있다 — 위탁만 부치는 여행이 있다 */}
                  <button type="button" className="btn" disabled={busy} onClick={() => setSlot('CHECKED')}>
                    다음: 위탁용 짐 →
                  </button>
                </>
              )}
            </div>
          </div>
        )}
      </div>
    </Shell>
  )
}
