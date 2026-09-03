import { useEffect, useRef, useState } from 'react'
import { useAiJob } from '../hooks/useAiJob'
import { VERDICT_CLASS, VERDICT_LABEL } from '../lib/format'
import type { RuleCheckOutput } from '../types/api'

/**
 * S-09 수하물 확인 챗봇 — <b>모달로 연다.</b>
 *
 * 03-wireframe: "어느 화면에서든 열 수 있는 보조 흐름이다. 핵심 목표인 여행가방
 * 검수 경로를 가리지 않도록" 별도로 둔다. 여행을 등록하지 않아도 쓸 수 있다.
 *
 * 되묻는 질문은 <b>한 번에 하나씩</b>이다(F-08). 여러 개를 한꺼번에 물으면
 * 사용자가 답을 포기한다.
 */
interface Turn {
  role: 'user' | 'bot'
  text: string
  results?: RuleCheckOutput['results']
  followUp?: string | null
}

/**
 * 03:328 이 빈 상태에 노출하라고 정한 예시 질문 <b>3개 그대로</b>다.
 *
 * 문구를 마음대로 바꾸지 않는다. 서버(MockAiClient.ruleCheckFixture)가 이
 * 질문들의 <b>물품 이름과 수치를 함께</b> 보고 답할 규정을 고른다 —
 * 보조배터리+20000mAh · 화장품+120ml · 가위+7cm. 하나라도 빠지면
 * "규정을 찾지 못했습니다" 로 떨어진다.
 *
 * 실제로 그랬다. 예전 문구("화장품 100ml 넘으면…" · "손톱깎이 들고…")는
 * 수치가 없어 셋 중 둘이 그 답을 받았다.
 */
const SAMPLES = [
  '20000mAh 보조배터리 기내 되나요?',
  '120ml 화장품 기내 반입되나요?',
  '날 길이 7cm 가위 기내 반입되나요?',
]

/**
 * 되묻는 질문에 답할 때 <b>직전 판정 결과를 함께 들고 간다.</b>
 *
 * 06:409 — "직전 `results[]`의 입력 허용 필드 5개를 `items[]`로 함께 보내고 새
 * 작업을 만든다." 서버는 대화를 기억하지 않는다. 대화 전용 ID도, 대화를 저장하는
 * API도 만들지 않기로 했다(06:411). 문맥을 잇는 것은 <b>화면의 몫이다.</b>
 *
 * 빈 배열로 보내면 서버에는 "100Wh예요" 한 마디만 남는다. 무엇의 100Wh인지 알 수
 * 없으니 `ASK_AIRLINE` — "해당 물품의 규정을 찾지 못했습니다" 가 돌아온다.
 * 실서버에 직접 물어 확인했다. 들고 가면 `CABIN_OK` 다.
 *
 * `attributes` 는 통째로 넘기지 않고 <b>네 개만 골라 담는다.</b> 07:1455-1462 이
 * 그 4개를 required + 추가 필드 금지로 못박았고, 서버는 입력을 접수 <b>전에</b>
 * 검증해 어긋나면 400 VALIDATION_FAILED 를 낸다(06:415). 나중에 실제 모델이
 * 출력에 필드를 하나 더 얹어도 이쪽은 깨지지 않는다.
 */
function carryOver(prev: Turn | undefined) {
  if (!prev || prev.role !== 'bot' || !prev.followUp || !prev.results) return []
  return prev.results.map((r) => ({
    itemId: r.itemId,
    detectionId: r.detectionId,
    name: r.name,
    qty: r.qty,
    attributes: {
      capacityMl: r.attributes.capacityMl ?? null,
      batteryWh: r.attributes.batteryWh ?? null,
      batteryMah: r.attributes.batteryMah ?? null,
      bladeCm: r.attributes.bladeCm ?? null,
    },
  }))
}

export function ChatModal({ onClose }: { onClose: () => void }) {
  const [turns, setTurns] = useState<Turn[]>([])
  const [text, setText] = useState('')
  const job = useAiJob<RuleCheckOutput>()
  const endRef = useRef<HTMLDivElement>(null)
  const inputRef = useRef<HTMLInputElement>(null)

  useEffect(() => { inputRef.current?.focus() }, [])
  useEffect(() => { endRef.current?.scrollIntoView({ behavior: 'smooth' }) }, [turns, job.phase])

  // Esc 로 닫는다. 모달의 기본 예의다.
  useEffect(() => {
    const onKey = (e: KeyboardEvent) => { if (e.key === 'Escape') onClose() }
    window.addEventListener('keydown', onKey)
    return () => window.removeEventListener('keydown', onKey)
  }, [onClose])

  // 챗봇은 여행이 없어도 쓸 수 있다. tripId 를 보내지 않는다.
  // 07:1436-1441 required 4개. 여행 없는 챗봇은 transport=FLIGHT, airline=null 이다
  // (07:1467). 빠뜨리면 명세대로 검증하는 서버가 접수 전에 거절한다.
  const send = (question: string, items: ReturnType<typeof carryOver>) =>
    job.start('RULE_CHECK', { transport: 'FLIGHT', airline: null, question, items })

  const ask = async (q: string) => {
    if (!q.trim() || job.phase === 'running') return
    // <b>보내기 전에</b> 직전 턴을 읽는다. 아래 setTurns 로 사용자 말풍선을
    // 붙이고 나면 마지막 턴이 방금 쓴 질문으로 바뀐다.
    const items = carryOver(turns.at(-1))
    setTurns((t) => [...t, { role: 'user', text: q }])
    setText('')
    await send(q, items)
  }

  /**
   * 실패·시간초과 뒤의 "다시 시도".
   *
   * `ask` 를 다시 부르면 같은 질문 말풍선이 하나 더 붙는다. 화면에 이미 있는
   * 마지막 질문을 <b>그대로 다시 보낸다.</b> 되묻기에 답하던 중이었다면 그 앞의
   * 봇 답이 문맥이므로 한 칸 더 앞을 본다.
   */
  const retry = () => {
    const last = turns.at(-1)
    if (last?.role !== 'user' || job.phase === 'running') return
    void send(last.text, carryOver(turns.at(-2)))
  }

  // 작업이 끝나면 답을 대화에 넣는다.
  useEffect(() => {
    if (job.phase !== 'done' || !job.output) return
    const o = job.output
    setTurns((t) => [
      ...t,
      { role: 'bot', text: o.answer ?? '판정 결과를 정리했습니다.', results: o.results, followUp: o.followUpQuestion },
    ])
    job.reset()
  }, [job.phase])

  return (
    <div className="modal-back" role="presentation" onClick={onClose}>
      <div
        className="modal chat"
        role="dialog"
        aria-modal="true"
        aria-label="수하물 확인 챗봇"
        onClick={(e) => e.stopPropagation()}
      >
        <header className="modal-head">
          <div>
            <p className="modal-title">✦ 수하물 확인</p>
            <p className="card-sub">반입 규정을 물어보세요. 여행 등록 없이도 됩니다</p>
          </div>
          <button type="button" className="modal-x" onClick={onClose} aria-label="닫기">×</button>
        </header>

        <div className="chat-body">
          {turns.length === 0 && job.phase === 'idle' && (
            <div className="chat-empty">
              <p className="card-sub">이런 걸 물어보실 수 있어요</p>
              <div className="chat-samples">
                {SAMPLES.map((s) => (
                  <button key={s} type="button" className="chip" onClick={() => ask(s)}>{s}</button>
                ))}
              </div>
            </div>
          )}

          {turns.map((t, i) => (
            <div key={i} className={`bubble ${t.role}`}>
              <p>{t.text}</p>
              {t.results?.map((r, k) => (
                <div key={k} className="verdict">
                  <div className="verdict-head">
                    <b>{r.name}</b>
                    <span className={`badge ${VERDICT_CLASS[r.verdict] ?? 'badge-warn'}`}>
                      {VERDICT_LABEL[r.verdict] ?? r.verdict}
                    </span>
                  </div>
                  <p className="verdict-why">{r.reason}</p>
                  {r.sourceUrl && (
                    <p className="verdict-src">
                      <a href={r.sourceUrl} target="_blank" rel="noreferrer">출처</a>
                      {r.checkedAt && ` · ${r.checkedAt} 확인`}
                    </p>
                  )}
                </div>
              ))}
              {/* 되묻는 질문은 한 번에 하나씩 */}
              {t.followUp && <p className="follow">{t.followUp}</p>}
            </div>
          ))}

          {job.phase === 'running' && (
            <div className="bubble bot">
              <span className="dots"><i /><i /><i /></span>
            </div>
          )}
          {job.phase === 'failed' && (
            <div className="bubble bot">
              {/* 서버가 왜 거절했는지 알려 줬으면 그대로 보여준다.
                  "답변을 만들지 못했습니다" 만으로는 고칠 방법이 없다 */}
              <p>{job.error ?? '답변을 만들지 못했습니다.'}</p>
              <button type="button" className="btn btn-sm" onClick={retry}>
                다시 시도
              </button>
            </div>
          )}
          {/* 06:537-538 — 60회를 넘기면 "시간이 오래 걸립니다" 와 재시도 버튼 */}
          {job.phase === 'timeout' && (
            <div className="bubble bot">
              <p>시간이 오래 걸립니다. 작업은 서버에 남아 있습니다.</p>
              <button type="button" className="btn btn-sm" onClick={retry}>
                다시 시도
              </button>
            </div>
          )}
          <div ref={endRef} />
        </div>

        <form
          className="chat-input"
          onSubmit={(e) => { e.preventDefault(); ask(text) }}
        >
          {/*
            * 03 이 S-09 에 둔 자리이지만 <b>아직 붙일 데가 없다.</b> 06 에
            * 챗봇 사진 API 가 없고 07 도 그 흐름을 TBD 로 남겼다.
            * (백엔드에서 PR #49 로 만들고 있다 — 나오면 여기에 연결한다.)
            *
            * 핸들러 없는 버튼을 살려 두면 발표 중 눌렀을 때 <b>아무 일도 일어나지
            * 않는다.</b> 고장난 것처럼 보이느니 준비 중이라고 말하는 편이 낫다.
            */}
          <button
            type="button" className="attach" disabled
            aria-label="사진 첨부 (준비 중)" title="사진 첨부는 준비 중입니다"
          >▤</button>
          <input
            ref={inputRef}
            value={text}
            onChange={(e) => setText(e.target.value)}
            placeholder="예: 20000mAh 보조배터리 기내 되나요?"
            aria-label="질문 입력"
            /* 07:1381 question 은 1~500자다. 넘겨 보내면 서버가 접수 전에
               400 VALIDATION_FAILED 로 거절하고, 같은 글을 다시 보내는
               "다시 시도" 는 영원히 실패한다. 아예 못 넘기게 막는다 */
            maxLength={500}
          />
          <button type="submit" className="btn btn-sm" disabled={!text.trim() || job.phase === 'running'}>
            보내기
          </button>
        </form>

        <p className="chat-foot">
          최종 반입 여부는 출발 당일 항공사와 보안검색기관의 판단을 따릅니다.
        </p>
      </div>
    </div>
  )
}
