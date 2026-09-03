import { useState } from 'react'
import { Link, NavLink, useNavigate } from 'react-router-dom'
import { useAuth } from '../auth/context'
import { NAV, NAV_CTA, STEPS, stepPath } from '../routes'
import { ChatModal } from './ChatModal'

/**
 * 공통 셸 — <b>상단 내비 + 가운데 정렬 본문.</b>
 *
 * 랜딩과 앱 화면이 같은 헤더(<code>SiteHeader</code>)와 같은 폭(<code>.page</code>)을
 * 쓴다. 예전에는 랜딩만 상단 헤더고 나머지는 왼쪽 사이드바여서, 같은 서비스인데
 * 두 개처럼 보였다.
 *
 * 사이드바를 버린 이유는 <b>목적지가 셋뿐</b>이기 때문이다. 216px 기둥을 세워
 * 두면 대부분이 빈칸이라 미완성으로 읽힌다.
 */
export function Shell({ children }: { children: React.ReactNode }) {
  return (
    <div className="site site-app">
      <SiteHeader />
      <div className="page">{children}</div>
      <Chat />
    </div>
  )
}

/**
 * 상단 내비 — 랜딩과 앱 화면이 함께 쓴다.
 *
 * 목적지 셋을 각각 다른 무게로 둔다. 브랜드가 랜딩, 가운데 링크가 내 여행,
 * 오른쪽 버튼이 여행 등록이다. <b>버튼은 하나뿐</b>이라 어디를 눌러야 하는지
 * 헷갈리지 않는다.
 */
export function SiteHeader() {
  const { user, loading, logout } = useAuth()
  const nav = useNavigate()

  const [signingOut, setSigningOut] = useState(false)
  const [signOutError, setSignOutError] = useState(false)

  // 06:263 — "서버 실패 시 로그아웃 완료로 표시하지 않고 재시도한다".
  // 예외를 삼키면 아무 일도 안 일어난 것처럼 보인다.
  const signOut = async () => {
    setSigningOut(true)
    setSignOutError(false)
    try {
      await logout()
      nav('/', { replace: true })
    } catch {
      setSignOutError(true)
    } finally {
      setSigningOut(false)
    }
  }

  return (
    <header className="site-head">
      <div className="site-head-in">
        <NavLink to="/" className="site-brand">짐싸조</NavLink>

        {/*
          * 확인이 끝나기 전에는 아무것도 그리지 않는다. 로그인한 사람에게
          * "로그인" 이 잠깐 보였다 바뀌면 잘못 들어온 줄 안다.
          */}
        {loading ? (
          <span className="site-nav" aria-hidden="true" />
        ) : user ? (
          <>
            <nav className="site-nav" aria-label="주요 메뉴">
              <span className="site-who">{user.nickname}</span>
              {NAV.map((n) => (
                <NavLink
                  key={n.path}
                  to={n.path}
                  end
                  className={({ isActive }) => (isActive ? 'is-active' : undefined)}
                >
                  {n.name}
                </NavLink>
              ))}
              <button
                type="button" className="site-nav-btn" onClick={signOut} disabled={signingOut}
              >
                {signingOut ? '로그아웃 중…' : signOutError ? '다시 시도' : '로그아웃'}
              </button>
            </nav>
            <Link to={NAV_CTA.path} className="btn-head">＋ {NAV_CTA.name}</Link>
          </>
        ) : (
          <>
            <nav className="site-nav" aria-label="주요 메뉴">
              <NavLink to="/login">로그인</NavLink>
            </nav>
            <Link to="/login" className="btn-head">시작하기</Link>
          </>
        )}
      </div>
    </header>
  )
}

/**
 * 화면 제목줄.
 *
 * 흰 띠를 두르지 않는다. 본문과 같은 바탕 위에 글자만 놓아야 상단 내비와
 * 경계가 겹쳐 보이지 않는다.
 */
export function TopBar({
  title, sub, right,
}: {
  title: string
  sub?: string
  right?: React.ReactNode
}) {
  return (
    <header className="topbar">
      <div>
        <h1 className="topbar-title">{title}</h1>
        {sub && <p className="topbar-sub">{sub}</p>}
      </div>
      {right && <div className="topbar-right">{right}</div>}
    </header>
  )
}

/**
 * 여행 준비 3단계 표시줄.
 *
 * 지금 어디인지, 앞뒤로 무엇이 있는지 보여준다. 지나온 단계는 눌러서 돌아간다.
 */
export function Steps({ current, tripId }: { current: 1 | 2 | 3; tripId?: number | string }) {
  return (
    <ol className="steps" aria-label="여행 준비 단계">
      {STEPS.map((s, i) => {
        const n = (i + 1) as 1 | 2 | 3
        const state = n === current ? 'is-current' : n < current ? 'is-done' : ''
        const to = tripId ? stepPath(s, tripId) : s.path
        const body = (
          <>
            <span className="step-no" aria-hidden="true">{n < current ? '✓' : s.no}</span>
            <span>{s.name}</span>
          </>
        )
        return (
          <li key={s.no} className={`step ${state}`}>
            {n < current && tripId ? (
              <NavLink to={to} className="step-in">{body}</NavLink>
            ) : (
              <span className="step-in">{body}</span>
            )}
          </li>
        )
      })}
    </ol>
  )
}

/**
 * 챗봇 — 플로팅 버튼으로 열고 <b>모달</b>로 뜬다.
 *
 * 별도 페이지로 만들지 않는 이유는 03-wireframe 이 챗봇을 "주 경로 밖의 보조
 * 흐름" 으로 못박았기 때문이다. 화면을 옮기면 하던 일이 끊긴다.
 */
export function Chat() {
  const { user } = useAuth()
  const [open, setOpen] = useState(false)
  // 06:278 — 챗봇도 userId 가 필수다. 로그인 전에는 버튼 자체를 두지 않는다.
  if (!user) return null
  return (
    <>
      <button
        type="button"
        className="fab"
        onClick={() => setOpen(true)}
        aria-haspopup="dialog"
        aria-expanded={open}
      >
        <span className="fab-ico" aria-hidden="true">✦</span>
        <span>AI에게 물어보기</span>
      </button>
      {open && <ChatModal onClose={() => setOpen(false)} />}
    </>
  )
}
