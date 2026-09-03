import { NavLink, Route, Routes } from 'react-router-dom'
import { LOGIN_PATH, SCREENS, screenElement } from './routes'
import { Placeholder } from './pages/Placeholder'

/**
 * 공통 셸. 헤더 + 본문.
 *
 * 지금은 화면 이동을 눈으로 확인하려고 전체 링크를 노출한다.
 * 실제 화면이 붙으면 이 목록은 개발용으로만 남기거나 지운다.
 */
export default function App() {
  return (
    <div className="app">
      <header className="app-header">
        <NavLink to="/" className="brand">
          짐싸조
        </NavLink>
        <nav className="dev-nav" aria-label="개발용 화면 이동">
          {SCREENS.map((s) => (
            <NavLink
              key={s.id}
              to={s.path.replace(':tripId', '1')}
              // end 를 모두에 건다. 없으면 접두사 매칭이라 S-10(/trips/1) 이
              // S-05(/trips/1/items) 에서도 활성으로 표시된다.
              end
              className={({ isActive }) => `dev-link tier-${s.tier}${isActive ? ' is-active' : ''}`}
              title={s.name}
            >
              {s.id}
              {s.ai && <span className="ai-dot" aria-label="AI 확장 지점" />}
            </NavLink>
          ))}
          <NavLink to={LOGIN_PATH} className="dev-link tier-1">
            로그인
          </NavLink>
        </nav>
      </header>

      <main className="app-main">
        <Routes>
          {SCREENS.map((s) => (
            <Route key={s.id} path={s.path} element={screenElement(s)} />
          ))}
          <Route
            path={LOGIN_PATH}
            element={
              <Placeholder
                id="진입"
                name="로그인"
                note="S-01~S-10 앞의 공통 진입 단계. 화면 ID·인증 방식은 TBD"
              />
            }
          />
          <Route
            path="*"
            element={<Placeholder id="404" name="없는 화면" note="주소를 확인해 주세요" />}
          />
        </Routes>
      </main>
    </div>
  )
}
