import { useEffect, useRef, useState } from 'react'
import { useAiJob } from '../hooks/useAiJob'
import { VERDICT_LABEL } from '../lib/format'
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

  const ask = async (q: string) => {
    if (!q.trim() || job.phase === 'running') return
    setTurns((t) => [...t, { role: 'user', text: q }])
    setText('')
    // 챗봇은 여행이 없어도 쓸 수 있다. tripId 를 보내지 않는다.
    // 07:1436-1441 required 4개. 여행 없는 챗봇은 transport=FLIGHT, airline=null 이다
    // (07:1467). 빠뜨리면 명세대로 검증하는 서버가 접수 전에 거절한다.
    await job.start('RULE_CHECK', {
      transport: 'FLIGHT',
      airline: null,
      question: q,
      items: [],
    })
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
                    <span className={`badge ${r.verdict === 'CABIN_OK' ? 'badge-ok' : r.verdict === 'CHECKED_FORBIDDEN' ? 'badge-danger' : 'badge-warn'}`}>
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
              <p>답변을 만들지 못했습니다.</p>
              <button type="button" className="btn btn-sm" onClick={() => ask(turns.at(-1)?.text ?? '')}>
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
          <button type="button" className="attach" aria-label="사진 첨부" title="사진 첨부">▤</button>
          <input
            ref={inputRef}
            value={text}
            onChange={(e) => setText(e.target.value)}
            placeholder="예: 20000mAh 보조배터리 기내 되나요?"
            aria-label="질문 입력"
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
