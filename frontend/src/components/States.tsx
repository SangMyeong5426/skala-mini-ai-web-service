/**
 * 로딩·빈 상태·오류 세 가지를 한곳에 둔다.
 *
 * docs/03-wireframe.md 가 **모든 화면에 이 셋을 그리라고** 못박았다.
 * "이 칸을 채운 팀과 아닌 팀은 발표에서 바로 갈린다."
 * 화면마다 새로 만들면 문구와 모양이 흩어지므로 여기서 한 번만 만든다.
 */
import type { ReactNode } from 'react'

export function Skeleton({ rows = 3 }: { rows?: number }) {
  return (
    <div className="skeleton" aria-busy="true" aria-live="polite">
      {Array.from({ length: rows }, (_, i) => (
        <div key={i} className="skeleton-row" />
      ))}
      <span className="sr-only">불러오는 중</span>
    </div>
  )
}

/**
 * AI 처리 중 화면. **폴링 중임을 사용자가 알 수 있어야 한다** (03-wireframe).
 * Mock 이 즉시 답해도 이 상태를 거친다.
 */
export function AiPending({ label, polls }: { label: string; polls: number }) {
  return (
    <div className="state state-ai" role="status" aria-live="polite">
      <div className="spinner" aria-hidden="true" />
      <p className="state-title">{label}</p>
      {polls > 0 && <p className="state-sub">확인 {polls}회째</p>}
      <p className="state-sub">화면을 벗어나도 결과는 저장됩니다.</p>
    </div>
  )
}

export function Empty({ title, action }: { title: string; action?: ReactNode }) {
  return (
    <div className="state state-empty">
      <p className="state-title">{title}</p>
      {action}
    </div>
  )
}

export function Failed({
  title,
  detail,
  onRetry,
}: {
  title: string
  detail?: string
  onRetry?: () => void
}) {
  return (
    <div className="state state-error" role="alert">
      <p className="state-title">{title}</p>
      {detail && <p className="state-sub">{detail}</p>}
      {onRetry && (
        <button type="button" className="btn" onClick={onRetry}>
          다시 시도
        </button>
      )}
    </div>
  )
}

/** 책임 범위 고지. S-06·S-08 에 반드시 넣는다 (03-wireframe "지킬 것" 5번). */
export function Disclaimer({ children }: { children: ReactNode }) {
  return <p className="disclaimer">{children}</p>
}
