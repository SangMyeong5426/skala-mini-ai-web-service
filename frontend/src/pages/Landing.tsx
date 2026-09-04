import { useEffect, useRef } from 'react'
import { Link } from 'react-router-dom'
import { JAPAN_PATH, KOREA_PATH, SEOUL, TOKYO } from '../lib/mapPaths'
import { useReveal } from '../components/Reveal'
import { Chat, SiteHeader } from '../components/Shell'

/**
 * 랜딩.
 *
 * 앱 화면과 <b>다른 세계로 짓는다.</b> 여기는 제품이 아니라 제품을 설명하는
 * 자리라서 여백을 크게 두고 한 화면에 한 이야기만 둔다.
 *
 * <b>문구는 팀이 확정한 원고를 그대로 쓴다.</b> 임의로 다듬지 않는다 —
 * 발표 대본과 같은 문장이어야 한다.
 *
 * 순서가 곧 주장이다. "사진 속 짐은 자동 등록하고, 더 필요한 짐은 추천받아
 * 선택한다" 는 흐름이 먼저 보이도록 배치했다.
 *
 *   1 메인            무엇을 해 주는 서비스인가
 *   2 화면 미리보기    결과 화면이 어떻게 생겼는가
 *   3 자동 등록·추천   이 서비스의 핵심 동작
 *   4 예상 무게 · 5 반입 규정   함께 확인하는 것
 *   6 사진 인식의 한계  못 찾아도 없는 것이 아니다
 *   7 시작 유도
 */
export default function Landing() {
  useReveal()

  return (
    <div className="site">
      <SiteHeader />

      <main>
        {/*
          * ── ① 메인 ──────────────────────────────────────
          * 트랙이 여분의 스크롤 길이를 만들고 히어로는 그 안에서 sticky 로
          * 붙는다. 비행기가 도쿄에 닿아야 트랙이 끝나고 다음 섹션이 올라온다.
          */}
        <div className="hero-track">
        <section className="sec sec-hero">
          <HeroArt />

          <div className="wrap wrap-center hero-copy">
            <h1 className="display">
              챙긴 짐을 찍으면,
              <br />체크리스트가 만들어집니다.
            </h1>
            <p className="lede">
              사진 속 물품은 자동으로 등록하고, 더 필요한 준비물은 추천해 드려요.
              <br className="br-wide" />
              반입 주의사항과 예상 무게까지 함께 확인하세요.
            </p>
            <Link to="/trips/new" className="btn-cta">여행 준비 시작하기</Link>
            <p className="fine">로그인 후 이용할 수 있어요.</p>
          </div>
        </section>
        </div>

        {/* ── ② 화면 미리보기 ───────────────────────────── */}
        <section className="sec">
          <div className="wrap wrap-center" data-reveal>
            <h2 className="statement">챙긴 짐부터 더 필요한 짐까지, 한눈에</h2>
            <p className="lede lede-sm">
              사진으로 등록한 물품과 아직 준비하지 않은 물품을 확인하세요.
              <br className="br-wide" />
              추가 추천은 따로 살펴보고, 필요한 것만 내 체크리스트에 담을 수 있어요.
            </p>
          </div>
          <div className="wrap" id="preview" data-reveal>
            <ProductShot />
          </div>
        </section>

        {/* ── ③ 사진 자동 등록 · 추천 선택 ──────────────── */}
        <section className="sec sec-alt">
          <div className="wrap wrap-center" data-reveal>
            <h2 className="statement">
              챙긴 짐은 바로 등록하고,
              <br />추천받은 짐은 골라 담으세요.
            </h2>
            <p className="lede lede-sm">
              사진에서 인식한 물품은 별도 승인 없이 체크리스트에 등록돼요.
              <br className="br-wide" />
              잘못 인식한 이름이나 수량은 수정하고, 필요 없는 항목은 삭제할 수 있어요.
            </p>
            <p className="lede lede-sm">
              AI는 현재 체크리스트에 없는 준비물을 추천해요.
              <br className="br-wide" />
              추천 물품은 직접 선택한 것만 추가됩니다.
            </p>
          </div>

          <div className="wrap wrap-narrow" data-reveal>
            <div className="panel">
              <p className="panel-label">사진에서 찾아 등록했어요</p>
              <div className="row">
                <span className="row-name">충전기 <em className="shot-qty">× 1</em></span>
                <span className="chip chip-ok">자동 등록됨</span>
                <span className="chip chip-line">수정</span>
                <span className="chip chip-line">삭제</span>
              </div>
              <div className="row">
                <span className="row-name">화장품 용기 <em className="shot-qty">× 1</em></span>
                <span className="chip chip-ok">자동 등록됨</span>
                <span className="chip chip-line">수정</span>
                <span className="chip chip-line">삭제</span>
              </div>

              <p className="panel-label panel-label-gap">이 준비물도 필요할까요?</p>
              <div className="row">
                <div className="row-stack">
                  <p className="row-name">여권 <em className="shot-qty">× 1</em> <em className="chip chip-req">필수</em></p>
                  <p className="row-why">해외 여행 출국 전 여권 준비 여부를 확인하세요.</p>
                </div>
                <span className="chip chip-add">체크리스트에 추가</span>
              </div>
              <div className="row">
                <div className="row-stack">
                  <p className="row-name">우산 <em className="shot-qty">× 1</em></p>
                  <p className="row-why">여행 중 강수에 대비할 휴대용 우산을 검토하세요.</p>
                </div>
                <span className="chip chip-add">체크리스트에 추가</span>
              </div>
              <div className="row">
                <div className="row-stack">
                  <p className="row-name">변환 플러그 <em className="shot-qty">× 1</em></p>
                  <p className="row-why">여행지에서 충전기를 연결할 어댑터를 확인하세요.</p>
                </div>
                <span className="chip chip-add">체크리스트에 추가</span>
              </div>
            </div>
          </div>
        </section>

        {/* ── ④ 예상 무게 · ⑤ 반입 규정 ────────────────── */}
        <section className="sec">
          <div className="wrap">
            <div className="duo">
              <article className="tile" data-reveal>
                <h3 className="tile-title">가방 무게, 출발 전에 가늠해 보세요</h3>
                <p className="tile-body">
                  준비 완료된 물품과 빈 가방 무게를 바탕으로 예상 범위를 보여드려요.
                  무게 정보가 없어 계산하지 못한 물품도 함께 확인할 수 있어요.
                </p>
                {/* 06-api-spec: minG 4610 · typicalG 5480 · maxG 7010 · limitG 10000 */}
                <p className="range">
                  <span>4.6</span>
                  <b>5.5</b>
                  <span>7.0</span>
                  <small>kg</small>
                </p>
                <div className="meter"><span style={{ width: '55%' }} /></div>
                <p className="tile-sub">
                  한도 10kg · 빈 가방 3.2kg 포함 · 무게 정보가 없어 뺀 물품 3개
                </p>
                <p className="tile-sub">
                  예상 무게는 참고용이에요. 출발 전 실제 무게를 확인해 주세요.
                </p>
              </article>

              <article className="tile" data-reveal data-reveal-delay="1">
                <h3 className="tile-title">가져가도 되는지, 근거와 함께 확인하세요</h3>
                <p className="tile-body">
                  이동수단과 물품 정보에 맞춰 반입 가능 여부와 주의사항을 안내해요.
                  공식 규정의 출처와 확인 날짜를 함께 보여드리고, 정보가 부족하면
                  무엇을 확인해야 하는지 알려드려요.
                </p>
                {/*
                  * <b>판정을 지어내지 않는다.</b> 06-api-spec 의 customs 예시가
                  * 같은 물품을 NEED_MORE_INFO 로 규정한다 — mAh 만으로는 Wh 를
                  * 알 수 없기 때문이다.
                  *
                  * 앞서는 여기에 "기내 가능" 을 박아 두었는데, 그것은 규정표의
                  * 한 줄(100Wh 이하 → CABIN_OK)을 <b>이 물품의 판정으로</b>
                  * 붙인 것이라 틀렸다. 규정 문장 자체는 맞다.
                  *
                  * 3단 계단을 함께 펴야 왜 못 정하는지가 납득된다. 문장은
                  * database/seed.sql 의 transport_rules 원문이다.
                  */}
                <div className="verdict-card">
                  <div className="row">
                    <span className="row-name">보조배터리 20,000mAh</span>
                    <span className="chip chip-warn">정보 부족</span>
                  </div>
                  <p className="verdict-why">
                    보조배터리는 위탁수하물로 부칠 수 없고, 기내 반입은 정격(Wh)에 따라
                    달라집니다. 라벨의 Wh 를 확인해 주세요.
                  </p>
                  <ul className="tier">
                    <li><b>100Wh 이하</b><span>기내 반입 가능</span></li>
                    <li><b>100 — 160Wh</b><span>항공사 사전 승인 필요</span></li>
                    <li><b>160Wh 초과</b><span>기내·위탁 모두 불가</span></li>
                  </ul>
                  <p className="verdict-src">인천국제공항 제한물품 안내 · 2026.09.02 확인</p>
                </div>
              </article>
            </div>
          </div>
        </section>

        {/* ── ⑥ 사진 인식의 한계 ────────────────────────── */}
        <section className="sec sec-alt">
          <div className="wrap wrap-center" data-reveal>
            <h2 className="statement">사진에 안 보인 짐도, 챙겨 두셨을 수 있어요</h2>
            <p className="lede lede-sm">
              사진에서 찾지 못했다고 ‘안 챙긴 물품’으로 단정하지 않아요.
              <br className="br-wide" />
              가방 안쪽이나 다른 곳에 넣어 두었다면, 직접 확인하고 준비 완료로 표시하세요.
            </p>
          </div>
        </section>

        {/* ── ⑦ 마지막 시작 유도 ────────────────────────── */}
        <section className="sec sec-close">
          <div className="wrap wrap-center" data-reveal>
            <h2 className="statement">
              챙긴 짐은 확인하고,
              <br />더 필요한 짐은 골라 담으세요.
            </h2>
            <p className="lede lede-sm">
              여행 정보를 입력하고 짐 사진을 올려보세요.
              <br className="br-wide" />
              사진 속 물품부터 체크리스트가 채워집니다.
            </p>
            <Link to="/trips/new" className="btn-cta">여행 준비 시작하기</Link>
            <p className="fine">로그인 후 나의 여행 준비를 시작하세요.</p>
          </div>
        </section>
      </main>

      <footer className="site-foot">
        <div className="wrap">
          <p className="foot-brand">짐싸조</p>
          <p className="fine">
            사진 분석·예상 무게·반입 안내는 준비를 돕는 참고 정보입니다.
            실제 무게는 저울로, 반입 여부는 출발 당일 항공사·보안검색기관 기준을 따릅니다.
          </p>
          <p className="fine">SKALA Full-Stack Engineering · 5조 Mini-project</p>
        </div>
      </footer>

      <Chat />
    </div>
  )
}

/**
 * 히어로 배경 일러스트 — 서울에서 도쿄로 가는 항로.
 *
 * 사진 대신 <b>손으로 그린 SVG</b>다. 저장소가 public 이라 사진 라이선스를
 * 신경 쓸 일이 없고, 라이브러리도 늘지 않는다(CLAUDE.md "기능을 늘리지 않는다").
 *
 * <b>스크롤이 비행이다.</b> 히어로가 화면에서 밀려 올라가는 만큼 비행기가
 * 항로를 따라 나아가고, 지나온 자리에 실선이 그려진다. 준비를 시작해서
 * 끝내는 흐름을 페이지를 내리는 동작 자체로 보여준다.
 *
 * 글과 항로는 <b>위아래로 자리를 나눈다.</b> 글은 히어로 위쪽에 붙고(app.css 의
 * .sec-hero padding-top), 지도·항로·도시는 아래 절반을 쓴다. 그래서 선을 가리는
 * 장치가 따로 필요 없다.
 *
 * <b>헤드라인이 세 줄로 길어지면 이 균형이 깨진다.</b> 그때는 글이 항로까지
 * 내려오므로 padding 이나 항로 높이를 다시 봐야 한다.
 */
/**
 * 지도를 좌우로 벌리는 양(px).
 *
 * 실제 지리보다 한반도와 일본을 떼어 놓는다. 정확한 투영대로 두면 두 땅이
 * 가운데에서 붙어 보여 항로가 짧고 답답하다. <b>모양은 건드리지 않고 통째로
 * 밀기만</b> 하므로 해안선은 그대로다.
 *
 * 도시 좌표도 같은 값만큼 함께 민다. 한쪽만 옮기면 서울이 바다에 찍힌다.
 *
 * <b>표시에 붙는 글자는 도시명이 아니라 공항명이다</b> — 인천 · 나리타. 바로
 * 아래 줄에 IATA 코드(ICN · NRT)가 오므로 도시명을 쓰면 "도쿄 / NRT" 처럼
 * 층위가 어긋난 짝이 된다. 좌표 상수 이름이 SEOUL · TOKYO 인 것은 그대로 두는데,
 * 그건 표시를 찍는 <b>지리 좌표</b>라 도시가 맞기 때문이다.
 *
 * 아래 목업(.shot-title)의 "서울 → 도쿄" 는 바꾸지 않는다. 그건 공항이 아니라
 * 시드의 여행 데이터(trips.origin · destination)를 그대로 비추는 자리다.
 */
const SPREAD = 70

const SEOUL_PT = { x: SEOUL.x - SPREAD, y: SEOUL.y }
const TOKYO_PT = { x: TOKYO.x + SPREAD, y: TOKYO.y }

/**
 * 스크롤 0 일 때 비행기가 서 있는 지점(항로 길이의 비율).
 *
 * 0 으로 두면 서울 표시와 겹쳐 한 덩어리로 뭉개진다. 표시 반경(19)과 기체
 * 길이(약 23)를 합친 만큼만 띄운 값이다 — "막 이륙했다" 로 읽히는 최소치다.
 */
const START = 0.06

/**
 * 서울에서 도쿄로.
 *
 * <b>위로 부푼 호다.</b> 실제 항공로가 대권 항로라 지도 위에서 곡선으로 보이는
 * 것과 같은 인상을 준다. 예전에는 제어점이 두 도시를 잇는 직선 위에 거의 얹혀
 * 있어서(부푼 정도 8.6) 자로 그은 선처럼 보였다.
 *
 * 중점이 현(弦)보다 55 단위 위에 오도록 잡았다 — 제어점 y 의 합이
 * `(475 + 591) + 3(y1 + y2) = 8 × 478` 을 만족한다. 뒤쪽(852)을 앞쪽(600)보다
 * 낮춰 <b>빠르게 올라 완만하게 내려오는</b> 모양으로 만든다. 이륙 직후 상승이
 * 가파른 실제 비행과 같고, 도착 지점에서 기수가 눕는다.
 *
 * 부푼 꼭대기는 y≈459(t≈0.28)로 출발점보다 16 위다. 지도 묶음이 아래로 내려가
 * 있고(app.css `.art-map`) viewBox 를 아래 기준으로 자르므로 글자에 닿지 않는다.
 */
const ROUTE = `M ${SEOUL_PT.x} ${SEOUL_PT.y} C 600 430 852 489 ${TOKYO_PT.x} ${TOKYO_PT.y}`

function HeroArt() {
  const trailRef = useRef<SVGPathElement>(null)
  const planeRef = useRef<SVGGElement>(null)
  const artRef = useRef<SVGSVGElement>(null)

  useEffect(() => {
    const trail = trailRef.current
    const plane = planeRef.current
    const art = artRef.current
    // .hero-art → .sec-hero → .hero-track. 진행률은 트랙을 기준으로 잰다
    const track = art?.parentElement?.parentElement
    if (!trail || !plane || !art || !track) return

    const total = trail.getTotalLength()
    trail.style.strokeDasharray = String(total)

    /** 항로 위 0~1 지점에 비행기를 놓고, 거기까지 실선을 채운다 */
    const place = (t: number) => {
      const d = total * (START + (1 - START) * t)
      const at = trail.getPointAtLength(d)
      // 진행 방향은 바로 앞 점과의 차이로 구한다 — 곡선 식을 따로 풀 필요가 없다
      const next = trail.getPointAtLength(Math.min(total, d + 2))
      const deg = (Math.atan2(next.y - at.y, next.x - at.x) * 180) / Math.PI
      plane.setAttribute('transform', `translate(${at.x} ${at.y}) rotate(${deg})`)
      trail.style.strokeDashoffset = String(total - d)
    }

    // 움직임을 줄여 달라는 설정이면 중간에 세워 두고 스크롤을 듣지 않는다
    if (window.matchMedia('(prefers-reduced-motion: reduce)').matches) {
      place(0.55)
      return
    }

    let raf = 0
    const draw = () => {
      raf = 0
      // 트랙이 화면에 붙잡혀 있는 구간을 0 → 1 로 편다.
      // 히어로가 화면 높이만큼 자리를 차지하므로 그만큼 빼야 끝에서 1 이 된다.
      const span = track.offsetHeight - window.innerHeight
      const gone = -track.getBoundingClientRect().top
      place(span > 0 ? Math.min(1, Math.max(0, gone / span)) : 1)
    }
    const onScroll = () => { if (!raf) raf = requestAnimationFrame(draw) }

    draw()
    window.addEventListener('scroll', onScroll, { passive: true })
    window.addEventListener('resize', onScroll)
    return () => {
      window.removeEventListener('scroll', onScroll)
      window.removeEventListener('resize', onScroll)
      if (raf) cancelAnimationFrame(raf)
    }
  }, [])

  return (
    <svg
      ref={artRef}
      className="hero-art"
      /*
       * <b>아래를 기준으로 자른다.</b> `slice` 는 넘치는 쪽을 잘라내는데,
       * 예전값 `xMidYMin` 은 위를 고정해서 <b>아래가 잘렸다</b> — 1920×950 창에서
       * 도쿄(y=701)가 잘림 경계(712)에 걸려 도시 표시와 비행기가 반쯤 사라졌다.
       *
       * 아래를 고정하면 잘리는 곳이 <b>빈 하늘</b>이 된다. 두 도시와 항로는 어떤
       * 창 비율에서도 남는다. 세로가 짧아질수록 구름부터 사라질 뿐이다.
       *
       * viewBox 높이를 900 → 800 으로 줄인 것도 같은 이유다. 실제 그림은 y≈741
       * 에서 끝나는데 900 까지 잡아 두면 아래에 빈 띠가 생기고, 그만큼 그림이
       * 위로 밀려 글자에 가까워진다.
       */
      viewBox="0 0 1440 800"
      preserveAspectRatio="xMidYMax slice"
      aria-hidden="true"
      focusable="false"
    >
      {/*
        * 구름 — 땅을 가리지 않게 바다 쪽에만 옅게 둔다.
        *
        * <b>흘러간다.</b> 비행기만 움직이면 배경이 사진처럼 굳어 보인다.
        *
        * 이어 붙이는 방법이 핵심이다. 한 벌만 밀면 오른쪽으로 나간 뒤 제자리로
        * <b>튀어 돌아온다.</b> 그래서 같은 구름을 한 벌 더 두되 이동거리만큼
        * (1760) 왼쪽에 놓는다. 앞의 것이 오른쪽으로 빠질 때 뒤의 것이 정확히
        * 그 자리에 들어와서 마디가 보이지 않는다.
        *
        * 1760 은 viewBox 너비(1440)보다 넓다. 그만큼 구름 사이에 빈 하늘이
        * 생기는데, 하늘이 늘 구름으로 차 있는 편보다 자연스럽다.
        *
        * 속도는 셋 다 다르다. 같으면 한 덩어리가 통째로 미끄러지는 것처럼 보인다.
        */}
      <g className="art-cloud">
        <defs>
          <g id="art-cloud-a">
            <ellipse cx="150" cy="176" rx="72" ry="22" />
            <ellipse cx="198" cy="162" rx="46" ry="28" />
          </g>
          <g id="art-cloud-b">
            <ellipse cx="1330" cy="700" rx="60" ry="18" />
            <ellipse cx="1366" cy="688" rx="36" ry="22" />
          </g>
          <g id="art-cloud-c">
            <ellipse cx="266" cy="812" rx="54" ry="16" />
          </g>
        </defs>
        <g className="art-drift" style={{ animationDuration: '164s' }}>
          <use href="#art-cloud-a" />
          <use href="#art-cloud-a" x="-1760" />
        </g>
        <g className="art-drift" style={{ animationDuration: '212s' }}>
          <use href="#art-cloud-b" />
          <use href="#art-cloud-b" x="-1760" />
        </g>
        <g className="art-drift" style={{ animationDuration: '132s' }}>
          <use href="#art-cloud-c" />
          <use href="#art-cloud-c" x="-1760" />
        </g>
      </g>

      <g className="art-map">
      {/*
        * 땅 — 한반도와 일본 열도. 실제 해안선을 그대로 옮긴 것이 아니라
        * 알아볼 수 있을 만큼만 단순화한 모양이다. 바탕(하늘색)이 바다다.
        * 옅게 깔아서 글자를 해치지 않는다 — 짙은 초록 글자와 9.6:1.
        */}
      <g className="art-land">
        <path d={KOREA_PATH} transform={`translate(${-SPREAD} 0)`} />
        <path d={JAPAN_PATH} transform={`translate(${SPREAD} 0)`} />
      </g>

      <g>
        {/* 남은 길 */}
        <path className="art-route" d={ROUTE} />
        {/* 지나온 길 — 길이를 JS 가 스크롤에 맞춰 늘린다 */}
        <path ref={trailRef} className="art-route-done" d={ROUTE} />
      </g>

      {/* 출발 */}
      <g className="art-node">
        <circle cx={SEOUL_PT.x} cy={SEOUL_PT.y} r="19" className="art-halo" />
        <circle cx={SEOUL_PT.x} cy={SEOUL_PT.y} r="7" />
        <text x={SEOUL_PT.x} y={SEOUL_PT.y + 48}>인천</text>
        <text x={SEOUL_PT.x} y={SEOUL_PT.y + 70} className="art-code">ICN</text>
      </g>

      {/* 도착 */}
      <g className="art-node">
        <circle cx={TOKYO_PT.x} cy={TOKYO_PT.y} r="19" className="art-halo" />
        <circle cx={TOKYO_PT.x} cy={TOKYO_PT.y} r="7" />
        <text x={TOKYO_PT.x} y={TOKYO_PT.y + 48}>나리타</text>
        <text x={TOKYO_PT.x} y={TOKYO_PT.y + 70} className="art-code">NRT</text>
      </g>

      {/*
        * 비행기 — 위에서 내려다본 여객기다. 코가 +X 를 보고 있어서 항로의
        * 접선 각도만 그대로 회전시키면 된다.
        * 도시 표시보다 <b>뒤에</b> 그린다. 먼저 그리면 도착할 때 도쿄 표시에
        * 가려진다. 출발할 때 서울 표시와 겹치는 문제는 START 로 띄워 해결한다.
        */}
      <g ref={planeRef} className="art-plane">
        <path
          transform="scale(0.82)"
          d="
            M 34 0
            C 34 -2.4 31.5 -4.2 28 -4.4
            L 6 -4.6
            L -4 -30 L -11 -30 L -12 -4.8
            L -23 -5
            L -28 -14 L -33 -14 L -32 -5.2
            L -35 -2.6 L -35 2.6
            L -32 5.2
            L -33 14 L -28 14 L -23 5
            L -12 4.8
            L -11 30 L -4 30 L 6 4.6
            L 28 4.4
            C 31.5 4.2 34 2.4 34 0
            Z
          "
        />
      </g>
      </g>
    </svg>
  )
}

/**
 * 히어로 아래 제품 화면.
 *
 * 데모 사진을 쓰지 않는다. 사진은 "가방"을 보여줄 뿐이고, 여기서 보여줘야 하는
 * 것은 <b>결과 화면이 어떻게 생겼는가</b>다.
 */
function ProductShot() {
  return (
    <div className="shot" aria-hidden="true">
      <div className="shot-chrome">
        <span className="shot-dot" /><span className="shot-dot" /><span className="shot-dot" />
        <span className="shot-url">짐싸조 · 서울 → 도쿄</span>
      </div>

      <div className="shot-body">
        <div className="shot-head">
          <div>
            <p className="shot-title">서울 → 도쿄</p>
            {/* 실측 예보가 아니다. 07 의 weatherSource 가 SEASONAL 이면 이렇게 쓴다 */}
            <p className="shot-sub">10.01 — 10.04 · 3박 4일 · 계절 평균 낮 24° 아침 16°</p>
          </div>
          <div className="shot-pct">
            <b>89</b><small>%</small>
          </div>
        </div>
        <div className="meter meter-slim"><span style={{ width: '89%' }} /></div>

        {/*
          * <b>두 덩어리로 나눈다.</b> 한 목록에 섞으면 ③ 의 약속과 어긋난다 —
          * "추천 물품은 직접 선택한 것만 추가됩니다".
          *
          * 가르는 신호는 <b>체크박스의 유무</b>다. 추천에는 체크박스가 없다.
          * 아직 내 목록이 아니니 체크할 대상도 아니기 때문이다.
          * 준비율(86%)도 위 목록만 센다.
          */}
        {/*
          * <b>체크리스트에 바로 들어오는 것은 사진에서 인식된 물품뿐이다.</b>
          *
          * 여행 필수품(schema.sql 의 `source = RULE`)도 사진에 없으면 목록에
          * 넣지 않고 <b>추천으로 내민다.</b> 06-api-spec 703행과 같은 규칙이다 —
          * "AI·RULE 은 해당 출처의 후보를 사용자가 채택".
          *
          * 그래서 가르는 기준이 하나로 정리된다.
          *   위(체크리스트)  = 사진에서 나온 것 + 내가 담은 것
          *   아래(추천)      = 아직 내 것이 아닌 것. 체크박스가 없다
          *
          * 필수품이라고 몰래 넣지 않는 것이 이 서비스의 태도다. 대신 `필수`
          * 표시를 달아 그냥 지나치지 않게 한다.
          */}
        <p className="shot-group">
          내 체크리스트
          {/* 비율만으로는 몇 개를 더 챙겨야 하는지 셈해야 안다 */}
          <span className="shot-count">8 / 9 준비 완료</span>
        </p>
        <ul className="shot-list">
          <li>
            <i className="box box-on">✓</i>
            <span>상의 <em className="shot-qty">× 4</em></span>
            <small className="shot-why">사진에서 자동 등록</small>
          </li>
          <li>
            <i className="box box-on">✓</i>
            <span>충전기 <em className="shot-qty">× 1</em></span>
            <small className="shot-why">사진에서 자동 등록</small>
          </li>
          <li>
            <i className="box box-on">✓</i>
            <span>화장품 용기 <em className="shot-qty">× 1</em></span>
            <small className="shot-why">사진에서 자동 등록</small>
          </li>
        </ul>

        {/*
          * <b>추천에는 이유를 함께 적는다.</b> 왜 권하는지 모르면 고를 수가 없다.
          * 문장은 07-ai-ready.md 의 `reason` 예시 그대로다 — 실제 모델이 채울 자리다.
          */}
        <p className="shot-group shot-group-gap">이 준비물도 필요할까요?</p>
        <ul className="shot-list shot-list-rec">
          <li>
            <div className="shot-rec">
              <p className="shot-rec-name">여권 <em className="shot-qty">× 1</em> <em className="chip chip-req">필수</em></p>
              <p className="shot-rec-why">해외 여행 출국 전 여권 준비 여부를 확인하세요.</p>
            </div>
            <em className="chip chip-add">체크리스트에 추가</em>
          </li>
          <li>
            <div className="shot-rec">
              <p className="shot-rec-name">변환 플러그 <em className="shot-qty">× 1</em></p>
              <p className="shot-rec-why">여행지에서 충전기를 연결할 어댑터를 확인하세요.</p>
            </div>
            <em className="chip chip-add">체크리스트에 추가</em>
          </li>
          <li>
            <div className="shot-rec">
              <p className="shot-rec-name">우산 <em className="shot-qty">× 1</em></p>
              <p className="shot-rec-why">여행 중 강수에 대비할 휴대용 우산을 검토하세요.</p>
            </div>
            <em className="chip chip-add">체크리스트에 추가</em>
          </li>
        </ul>
      </div>
    </div>
  )
}
