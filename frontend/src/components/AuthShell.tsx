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
  label, htmlFor, hint, children,
}: {
  label: string
  htmlFor: string
  hint?: string
  children: React.ReactNode
}) {
  return (
    <div className="field">
      <label htmlFor={htmlFor}>{label}</label>
      {children}
      {hint && <p className="field-hint">{hint}</p>}
    </div>
  )
}
