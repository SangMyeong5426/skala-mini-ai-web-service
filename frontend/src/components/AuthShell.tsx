import { Link } from 'react-router-dom'
import { SiteHeader } from './Shell'

/**
 * 로그인·회원가입이 함께 쓰는 껍데기.
 *
 * 랜딩과 같은 상단 헤더를 쓴다. 인증 화면만 다른 세계로 만들면 갑자기 다른
 * 서비스로 넘어온 것처럼 보인다.
 *
 * 가운데 한 장짜리 카드다. 옆에 아무것도 두지 않는다 — 여기서 할 일은 하나뿐이다.
 */
export function AuthShell({
  title, sub, children, foot,
}: {
  title: string
  sub: string
  children: React.ReactNode
  foot: React.ReactNode
}) {
  return (
    <div className="site site-app">
      <SiteHeader />
      <main className="auth-wrap">
        <div className="auth-card">
          <h1 className="auth-title">{title}</h1>
          <p className="auth-sub">{sub}</p>
          {children}
          <p className="auth-foot">{foot}</p>
        </div>
        <p className="auth-back"><Link to="/">← 서비스 소개로 돌아가기</Link></p>
      </main>
    </div>
  )
}

/**
 * 입력 한 줄.
 *
 * 라벨을 placeholder 로 대신하지 않는다. 입력을 시작하면 사라져서 무슨 칸인지
 * 알 수 없게 된다.
 */
export function Field({
  label, htmlFor, hint, error, children,
}: {
  label: string
  htmlFor: string
  hint?: string
  /** 서버가 알려준 이 칸의 오류. 있으면 안내문 대신 <b>오류 모양</b>으로 보인다 */
  error?: string | null
  children: React.ReactNode
}) {
  const msgId = `${htmlFor}-msg`
  return (
    <div className="field">
      <label htmlFor={htmlFor}>{label}</label>
      {/*
        * 오류를 회색 안내문으로 바꿔치기하지 않는다. 그러면 평소 문구와 똑같이
        * 생겨서 무엇이 잘못됐는지 눈에도 스크린리더에도 남지 않는다.
        */}
      <div
        className={error ? 'field-in is-bad' : 'field-in'}
        aria-invalid={error ? true : undefined}
      >
        {children}
      </div>
      {error
        ? <p id={msgId} className="field-error" role="alert">{error}</p>
        : hint && <p id={msgId} className="field-hint">{hint}</p>}
    </div>
  )
}
