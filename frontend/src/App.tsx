import { Route, Routes } from 'react-router-dom'
import { AuthProvider } from './auth/AuthContext'
import { RequireAuth } from './components/RequireAuth'
import { Shell, TopBar } from './components/Shell'
import { Placeholder } from './pages/Placeholder'
import { MockCheck } from './pages/MockCheck'
import Landing from './pages/Landing'
import MyTrips from './pages/MyTrips'
import Photos from './pages/Photos'
import DetectionsPage from './pages/Detections'
import ItemsPage from './pages/Items'
import Login from './pages/Login'

/** 아직 안 만든 화면. */
function Todo({ name }: { name: string }) {
  return (
    <Shell>
      <TopBar title={name} />
      <div className="content"><Placeholder name={name} /></div>
    </Shell>
  )
}

/** 로그인해야 볼 수 있는 화면. 감싸는 것을 빠뜨리면 그 화면만 무방비가 된다. */
function Private({ children }: { children: React.ReactNode }) {
  return <RequireAuth>{children}</RequireAuth>
}

export default function App() {
  return (
    <AuthProvider>
      <Routes>
        {/* 로그인 없이 볼 수 있는 곳 — 소개와 인증뿐이다 */}
        <Route path="/" element={<Landing />} />
        {/* S-00 은 한 화면이다. /signup 은 두지 않는다 — 03-wireframe */}
        <Route path="/login" element={<Login />} />

        {/* 2 여행 준비 3단계 */}
        <Route path="/trips/new" element={<Private><Todo name="여행 정보" /></Private>} />
        <Route path="/trips/:tripId/photos" element={<Private><Photos /></Private>} />
        <Route path="/trips/:tripId/detections" element={<Private><DetectionsPage /></Private>} />
        <Route path="/trips/:tripId/items" element={<Private><ItemsPage /></Private>} />
        {/* 예전 경로. 북마크·링크가 깨지지 않게 남긴다 */}
        <Route path="/trips/:tripId/review" element={<Private><ItemsPage /></Private>} />
        {/* Items 의 "검수하기" 가 여기로 보낸다. 화면은 아직 없어 자리만 잡아 둔다 */}
        <Route path="/trips/:tripId/inspection" element={<Private><Todo name="검수 결과" /></Private>} />

        {/* 2차 — 1차의 요약을 눌러서 들어간다. 화면은 아직 없다 */}
        <Route path="/trips/:tripId/weight" element={<Private><Todo name="무게 상세" /></Private>} />
        <Route path="/trips/:tripId/rules" element={<Private><Todo name="반입 규정 상세" /></Private>} />
        {/* #42 가 백엔드를 넣은 화면들. 여행 하위로 들어간다 */}
        <Route path="/trips/:tripId/itinerary" element={<Private><Todo name="여행 일정" /></Private>} />
        <Route path="/trips/:tripId/layout" element={<Private><Todo name="3D 가방 정리" /></Private>} />

        {/* 3 내 여행 */}
        <Route path="/trips" element={<Private><MyTrips /></Private>} />
        <Route path="/trips/:tripId" element={<Private><Todo name="여행 기록" /></Private>} />

        <Route path="/__mock" element={<MockCheck />} />
        <Route path="*" element={<Todo name="없는 화면" />} />
      </Routes>
    </AuthProvider>
  )
}
