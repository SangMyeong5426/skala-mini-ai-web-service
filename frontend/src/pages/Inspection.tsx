import { useEffect, useRef, useState } from 'react'
import { useNavigate, useParams } from 'react-router-dom'
import { api } from '../api/client'
import { AiPending, Failed, Skeleton } from '../components/States'
import { useAiJob } from '../hooks/useAiJob'
import { Shell, Steps, TopBar } from '../components/Shell'
import { headroom, kg, pct, VERDICT_CLASS, VERDICT_LABEL, WEIGHT_BAR_CLASS } from '../lib/format'
import type { Inspection, PhotoStatus, TripDetail, WeightVerdict } from '../types/api'

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

/** 07:1390 — RULE_CHECK `items` 의 maxItems. 서버는 51개부터 400 이다 */
const RULE_CHECK_MAX_ITEMS = 50

export default function InspectionPage() {
  const { tripId } = useParams()
  const nav = useNavigate()
  const [data, setData] = useState<Inspection | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [trip, setTrip] = useState<TripDetail | null>(null)
  const [saving, setSaving] = useState(false)
  const [saveError, setSaveError] = useState<string | null>(null)
  const weightJob = useAiJob()
  const ruleJob = useAiJob()
  const kicked = useRef(false)
  /** 화면을 떠났나. effect cleanup 과 비동기 함수가 같은 값을 본다 */
  const cancelled = useRef(false)

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
  /**
   * 없는 결과를 <b>여기서 만들어 온다.</b> 조회만 하고 "아직 계산하지
   * 않았습니다" 로 두면 사용자가 할 수 있는 일이 없다.
   *
   * 이름을 붙여 둔 것은 <b>재시도가 이 함수를 다시 부르기 위해서다.</b>
   * effect 안에 묻어 두면 409 STALE_WEIGHT_INPUT 을 받았을 때 다시 걸 방법이
   * 없어, "다시 시도" 가 조회만 하고 아무 일도 안 하는 버튼이 된다.
   */
  const startJobs = async () => {
    {
      const r = await load()
      if (cancelled.current || !r) return
      kicked.current = true

      // 07 의 두 입력 스키마는 서로 다르다. 여행 정보가 있어야 채울 수 있고,
      // 없으면 요청을 걸지 않는다 — 07 이 minLength·enum 을 요구한다.
      const trip = await api.get<TripDetail>(`/trips/${tripId}`).catch(() => null)
      if (cancelled.current || !trip) return
      setTrip(trip)

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
        }, Number(tripId)).then((r) => { if (r.done && !cancelled.current) void load() })
      }

      /*
       * 07:1436 RULE_CHECK required — transport · airline · question · items.
       * 항목마다 itemId · detectionId · name · qty · attributes 가 필요하다.
       *
       * <b>이동수단은 이 여행의 것</b>이다. FLIGHT 로 박아 두면 기차·버스
       * 여행도 항공 규정으로 판정한다 — 그건 여행 없는 챗봇의 기본값이다.
       */
      /*
       * <b>`!r.customs` 로 보면 안 된다.</b> 서버는 판정이 하나도 없을 때 null 이
       * 아니라 <b>빈 배열</b>을 준다(InspectionService.customs 의 `return List.of()`).
       * JS 에서 `![]` 는 false 라, 새로 만든 여행은 판정을 영영 걸지 않았다.
       * 데모에서 여행을 새로 만들어 여기까지 오면 반입 판정 칸이 계속 비어 있었다.
       */
      /*
       * 07:1390 `items` 는 <b>maxItems 50</b> 이고, 서버는 51개부터 접수 전에
       * 400 VALIDATION_FAILED 를 낸다(RuleCheckContract:36). 물품이 많은
       * 여행에서 판정이 통째로 안 나오는 것보다 앞 50개라도 나오는 편이 낫다.
       */
      const all = [...prepared, ...unprepared].slice(0, RULE_CHECK_MAX_ITEMS)
      if (!r.customs?.length && all.length > 0) {
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
        }, Number(tripId)).then((r) => { if (r.done && !cancelled.current) void load() })
      }
    }
  }

  useEffect(() => {
    /*
     * <b>취소 상태는 ref 로 공유한다.</b>
     *
     * 예전에는 effect 의 지역 `let alive` 를 `startJobs(alive)` 로 <b>값으로</b>
     * 넘겼다. 함수 안에는 `true` 사본이 남으므로 cleanup 이 바깥 변수를 false 로
     * 바꿔도 안쪽 검사는 계속 통과했다. 그래서 조회 응답이 늦게 오면 <b>이미
     * 떠난 화면에서 WEIGHT_ESTIMATE·RULE_CHECK 를 새로 접수했다.</b>
     *
     * useAiJob 의 이탈 검사는 첫 폴링 대기 <b>뒤에</b> 있어서 접수 자체를 막지
     * 못한다. 실제 모델을 부르는 설정에서는 모델 호출로도 이어진다.
     *
     * 재시도 경로도 같은 ref 를 본다 — 이탈 처리가 두 갈래가 되면 한쪽이 또
     * 빠진다.
     */
    cancelled.current = false
    if (!kicked.current) void startJobs()
    return () => { cancelled.current = true }
  }, [tripId])

  /**
   * 03:284 주요 요소의 <b>`최종 저장`</b>. 03:184 이 정한 흐름의 마지막 단계다 —
   * <i>"검수 결과 확인·수정 → 최종 저장"</i>.
   *
   * 새 여행은 `DRAFT`(작성 중)로 만들어진다(TripService:95 — "생성 직후는
   * DRAFT 다. 클라이언트가 status 를 정하지 않는다"). 준비가 끝났다는 뜻을
   * 남기는 것이 이 버튼이고, 그러면 내 여행 목록에서 <b>진행 중</b>으로 선다.
   *
   * 06 은 `PATCH /trips/{tripId}` 가 `status` 를 받는다고 적어 두었지만 S-06 의
   * 호출 API 목록에서 이 단계를 빠뜨렸다. 작성자 확인을 거쳐 06 에 함께 적었다.
   *
   * 06:289 — "경고는 완료율 계산·최종 저장을 막지 않는다". 미채택 필수 후보가
   * 있어도 저장을 막지 않는다.
   */
  const confirm = async () => {
    if (saving) return
    setSaving(true)
    try {
      await api.patch(`/trips/${tripId}`, { status: 'CONFIRMED' })
      setSaveError(null)
      nav('/trips')
    } catch (e) {
      setSaveError(e instanceof Error ? e.message : '저장하지 못했습니다.')
    } finally {
      setSaving(false)
    }
  }

  /**
   * <b>50개 상한에 걸려 판정에 못 보낸 물품.</b>
   *
   * 07:1390 이 `items` 를 maxItems 50 으로 정해서 51번째부터는 요청에 담지
   * 못한다. 조용히 자르면 물품 60개인 여행은 50개만 보이고 사용자는 60개 다
   * 봤다고 생각한다. 서버도 판정 기록이 없는 물품을 `customs` 에서 빼므로
   * 다음 방문에 저절로 채워지지 않는다.
   *
   * 이 저장소에는 같은 상황의 전례가 있다 — #46 의 `AI_VISION_MAX_PHOTOS` 는
   * 넘는 사진을 `failedPhotoIds` 로 남기며 <i>"조용히 버리면 사용자는 그 사진이
   * 분석된 줄 안다"</i> 고 적었다.
   *
   * <b>`customs` 에 없는 물품으로 세면 안 된다.</b> 규칙이 없어 판정이 나오지
   * 않은 물품도 서버가 `customs` 에서 빼기 때문에, 두 가지가 섞여 "판정 못 했다"
   * 고 잘못 말한다. 상한을 넘긴 것만 정확히 센다 — 목록 길이만 보면 되고
   * 재진입해도 같은 값이 나온다.
   */
  const unjudged = (() => {
    const r0 = data?.readiness
    if (!r0) return []
    // 요청에 담는 순서 그대로 세야 실제로 빠진 것이 나온다
    return [...r0.prepared, ...r0.unprepared].slice(RULE_CHECK_MAX_ITEMS).map((i) => i.name)
  })()

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

        {/*
          * <b>섹션 사이를 벌린다.</b> 카드에는 바깥 여백이 없어서, 여기처럼
          * 카드를 여럿 쌓는 화면에서는 서로 붙어 한 덩어리로 읽혔다. 준비
          * 상태·무게·판정은 <b>다른 이야기</b>인데 경계가 안 보였다.
          *
          * 각 카드에 `marginTop` 을 다는 대신 감싸는 쪽에서 한 번에 준다 —
          * 카드를 더 넣거나 순서를 바꿔도 여백이 따라온다.
          */}
        <div className="stack">
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
                      : <>미채택 <b>필수 후보 {r.unacceptedRequiredCount}건</b></>}
                  </span>
                  <button
                    type="button" className="btn btn-sm"
                    /* 03:289 · 06:1029 — "확인하기는 S-05 의 필수 추천 영역으로
                       이동한다". 목록 맨 위로 보내면 무엇을 확인하라는지 모른다 */
                    onClick={() => nav(`/trips/${tripId}/items#recommend`)}
                  >확인하기</button>
                </div>
              )}

              {/*
                * <b>두 묶음을 좌우로 나눈다.</b> 위아래로 쌓으면 챙긴 것이 길어질수록
                * <b>안 챙긴 것</b>이 화면 밖으로 밀려난다. 이 화면에서 정작 볼 것은
                * 뒤엣것인데, 그것을 보려면 매번 끝까지 스크롤해야 했다.
                *
                * 나란히 두면 완료율 막대 바로 아래에서 둘이 함께 보인다.
                */}
              <div className="split-cols">
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
                          onClick={() => nav(`/trips/${tripId}/detections?from=inspection`)}
                        >사진 확인</button>
                      )}
                    </div>
                  </li>
                ))}
              </Group>

              {/*
                * 03:288 빈 상태 — "내 목록이 비면 사진 등록·직접 추가 안내".
                * 목록이 통째로 비었는데 "모두 챙기셨습니다" 라고 하면 거짓말이다.
                * 챙긴 것도 챙길 것도 없는 상태다.
                */}
              <Group title="아직 안 챙김" count={r.unprepared.length} tone="warn"
                empty={r.prepared.length === 0
                  ? '내 목록이 비어 있습니다. 사진을 올리거나 체크리스트에서 직접 추가하세요'
                  : (
                    /*
                      * <b>다 챙긴 것은 이 화면에서 가장 좋은 소식이다.</b> 한 줄짜리
                      * 회색 글씨로 적어 두면 빈칸처럼 읽혀서, 정작 확인하러 온
                      * 사람이 "안 챙긴 게 없다" 는 사실을 못 보고 지나쳤다.
                      *
                      * 옆 기둥이 길어도 이 자리는 세로 가운데에 선다 — 두 기둥이
                      * 같은 키로 늘어나므로 남는 자리를 이 소식이 차지한다.
                      */
                    <div className="all-done">
                      <span className="all-done-mark" aria-hidden="true">✓</span>
                      <p className="all-done-title">다 챙기셨습니다</p>
                      <p className="all-done-sub">
                        빠뜨린 물품이 없습니다. 무게와 반입 규정만 확인하면 끝입니다.
                      </p>
                    </div>
                  )}>
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
                          onClick={() => nav(`/trips/${tripId}/detections?from=inspection`)}
                        >사진 확인</button>
                      )}
                    </div>
                  </li>
                ))}
              </Group>
              </div>
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
              /*
               * 07:1151 — 입력이 어긋나면 서버가 409 STALE_WEIGHT_INPUT 으로
               * <b>재조회를 요청</b>한다. 그 뜻대로 다시 조회하고 다시 건다.
               * 예전에는 실패 문구만 남아서 브라우저 새로고침 말고 길이 없었다.
               */
              <Failed
                title="무게를 계산하지 못했습니다"
                detail={weightJob.error ?? ''}
                onRetry={() => { kicked.current = false; void startJobs() }}
              />
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
                onRetry={() => { kicked.current = false; void startJobs() }}
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
                </p>
                {/*
                  * <b>한도가 없으면 막대를 그리지 않는다.</b> `limitG` 는 nullable 이다
                  * — 가방 정보를 안 넣은 여행이 있다(시드의 지난 여행이 그렇다).
                  *
                  * null 로 나누면 JS 에서 `Infinity` 가 되고 `Math.min(100, …)` 이
                  * 100 을 골라, 한도를 모르는데 <b>가득 찬 막대</b>가 그려졌다.
                  * 아래 문구도 "한도 0.0kg" 이 됐다. 둘 다 거짓말이다.
                  */}
                {w.limitG !== null && (
                  <div className="bar bar-lg" style={{ margin: '12px 0 10px' }}>
                    {/* 색은 서버가 정한 verdict 를 따른다. 화면에서 다시 계산하지 않는다 */}
                    <span
                      className={WEIGHT_BAR_CLASS[w.verdict] ?? ''}
                      style={{ width: `${Math.min(100, Math.round((w.typicalG / w.limitG) * 100))}%` }}
                    />
                  </div>
                )}
                {/*
                  * <b>한도까지 얼마 남았는지를 숫자로 준다.</b> 막대와 배지는 "괜찮다 /
                  * 아슬하다" 까지만 말하고, 무엇을 빼야 하는지는 알려 주지 않는다.
                  * 수하물 계산기들이 headroom 을 따로 적는 이유이고, 이 한 줄이
                  * S-07 무게 상세로 들어가지 않아도 행동할 수 있게 한다.
                  */}
                {headroom(w.typicalG, w.limitG) && (
                  <p className="stat-value" style={{ fontSize: 18, marginBottom: 2 }}>
                    한도까지 {headroom(w.typicalG, w.limitG)}
                  </p>
                )}
                <p className="card-sub">
                  {w.limitG === null ? '한도 정보 없음' : `한도 ${kg(w.limitG)}`}
                  {' · '}신뢰도 {w.confidence === 'HIGH' ? '높음' : w.confidence === 'MEDIUM' ? '보통' : '낮음'}
                  {w.excludedCount > 0 && ` · 계산 제외 ${w.excludedCount}개`}
                </p>
                <p className="row-sub" style={{ marginTop: 6 }}>{w.confidenceReason}</p>

                {w.contributions.length > 0 && (
                  <ul className="contrib">
                    {w.contributions.map((x) => (
                      <li key={x.name}>
                        <span>{x.name} <em className="card-sub">× {x.qty}</em></span>
                        <b>{kg(x.subtotalG)}</b>
                      </li>
                    ))}
                  </ul>
                )}
                <p className="disclaimer">
                  예상 무게는 참고용 추정치입니다. 탑승 전 실제 저울로 측정하세요.
                </p>
                {/*
                  * <b>S-07 로 가는 링크를 뺐다(사용자 지시).</b> 규정 검색과 같은
                  * 판단이다 — 여행 등록을 마치는 자리에서 곁길을 내지 않는다.
                  *
                  * 여기 요약으로 충분한지가 관건이다. 지금은 범위·신뢰도·한도까지
                  * 남은 무게와 기여도 상위 몇 개, 제외 개수와 그 이유 한 줄까지
                  * 보여준다. 어느 물품이 왜 빠졌는지 <b>이름까지</b> 보려면 S-07 이
                  * 필요한데, 그 화면은 지금 들어갈 문이 없다(03:55 참고).
                  */}
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
            {/* 조용히 빠뜨리지 않는다. 무엇이 안 봤는지 이름으로 말한다 */}
            {unjudged.length > 0 && ruleJob.phase !== 'running' && (
              <p className="notice-warn" style={{ display: 'block' }}>
                <b>{unjudged.length}개는 판정하지 못했습니다</b> — 한 번에{' '}
                {RULE_CHECK_MAX_ITEMS}개까지 확인합니다.{' '}
                항공사 규정을 직접 확인해 주세요 —{' '}
                {unjudged.slice(0, 5).join(' · ')}
                {unjudged.length > 5 && ` 외 ${unjudged.length - 5}개`}
              </p>
            )}
            {ruleJob.phase === 'timeout' && (
              <Failed
                title="시간이 오래 걸립니다"
                detail="작업은 서버에 남아 있습니다"
                onRetry={() => { void load() }}
              />
            )}
            {/* 빈 배열도 "없음" 이다. 작업이 도는 중에는 말하지 않는다 */}
            {data && !c?.length && ruleJob.phase === 'idle' && (
              <p className="card-sub">판정할 물품이 없습니다.</p>
            )}
            {c?.map((x) => (
              <div key={x.itemId} className="verdict">
                <div className="verdict-head">
                  <b>{x.name}</b>
                  <span className={`badge ${VERDICT_CLASS[x.verdict]}`}>{VERDICT_LABEL[x.verdict]}</span>
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
            {/*
              * <b>여기서 규정 검색으로 보내지 않는다.</b> 이 화면은 여행 등록의
              * 마지막 칸이다. 규정을 뒤지러 가는 것은 준비를 마치는 일과 결이
              * 다른 볼일이라, 흐름 한가운데에 문을 내면 마무리 직전에 사람을
              * 옆길로 뺀다.
              *
              * 규정 질문은 <b>챗봇</b>이 받는다 — 오른쪽 아래 버튼으로 어느
              * 화면에서나 열리고, 여행을 등록하지 않아도 답한다. 물어보는 것이
              * 검색어를 짜 넣는 것보다 빠르기도 하다.
              *
              * 판정 자체는 여기서 끝난다. 물품마다 근거 한 줄과 출처 링크가
              * 붙어 있어서, 더 볼 것이 있는 사람만 챗봇을 열면 된다.
              */}
          </div>
        </div>

        {data?.notice && <p className="disclaimer">{data.notice}</p>}

        {saveError && <Failed title="저장하지 못했습니다" detail={saveError} />}

        {/*
          * 03:284 — 검수 결과 아래 `최종 저장`. 흐름의 마지막 단추다.
          *
          * <b>카드 안쪽 아래 오른쪽.</b> S-02·S-03·S-04·S-05 와 같은 자리다.
          * 이 화면만 카드 밖 맨바닥에 버튼이 떠 있어서, 넷을 거쳐 온 사람의 눈이
          * 마지막에 와서 다시 헤맸다.
          */}
        {data && (
          <div className="card">
            <div className="form-foot">
              <button
                type="button" className="btn btn-ghost" style={{ marginRight: 'auto' }}
                onClick={() => nav(`/trips/${tripId}/items`)}
              >
                ← 이전: 체크리스트
              </button>
              <button type="button" className="btn" onClick={confirm} disabled={saving}>
                {saving ? '저장하는 중…' : trip?.status === 'DRAFT' ? '최종 저장' : '저장하고 목록으로'}
              </button>
            </div>
          </div>
        )}
        </div>
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
  /* 문자열 한 줄일 때도 있고, `다 챙기셨습니다` 처럼 한 덩어리일 때도 있다 */
  empty?: React.ReactNode
  children: React.ReactNode
}) {
  if (count === 0 && !empty) return null
  return (
    <div className="group">
      <p className="group-head">
        {title}
        <span className={`badge ${tone === 'ok' ? 'badge-ok' : tone === 'warn' ? 'badge-warn' : ''}`}>{count}</span>
      </p>
      {count === 0
        ? (typeof empty === 'string' ? <p className="card-sub">{empty}</p> : empty)
        : <ul>{children}</ul>}
    </div>
  )
}
