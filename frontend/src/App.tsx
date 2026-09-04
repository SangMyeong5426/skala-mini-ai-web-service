import { Route, Routes } from 'react-router-dom'
import { AuthProvider } from './auth/AuthContext'
import { RequireAuth } from './components/RequireAuth'
import { Shell, TopBar } from './components/Shell'
import { MockCheck } from './pages/MockCheck'
import Landing from './pages/Landing'
import MyTrips from './pages/MyTrips'
import Photos from './pages/Photos'
import DetectionsPage from './pages/Detections'
import ItemsPage from './pages/Items'
import InspectionPage from './pages/Inspection'
import NewTrip from './pages/NewTrip'
import Login from './pages/Login'
import TripRecord from './pages/TripRecord'
import Weight from './pages/Weight'
import Rules from './pages/Rules'
import ItineraryPage from './pages/Itinerary'
import PackingLayoutPage from './pages/PackingLayout'

/** 없는 주소로 들어왔을 때. 화면은 전부 만들었으므로 이제 이것뿐이다. */
function NotFound() {
  return (
    <Shell>
      <TopBar title="없는 화면" />
      <div className="content">
        <div className="card">
          <p className="state-title">주소를 찾을 수 없습니다.</p>
          <p className="card-sub">상단의 내 여행에서 다시 시작하세요.</p>
        </div>
      </div>
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
        <Route path="/trips/new" element={<Private><NewTrip /></Private>} />
        {/*
          * 같은 화면이 만들기와 고치기를 함께 한다. `tripId` 가 있으면 고치기다 —
          * 짐 사진에서 `이전` 으로 돌아왔을 때 빈 폼이 뜨지 않게 하는 길이다.
          */}
        <Route path="/trips/:tripId/edit" element={<Private><NewTrip /></Private>} />
        <Route path="/trips/:tripId/photos" element={<Private><Photos /></Private>} />
        <Route path="/trips/:tripId/detections" element={<Private><DetectionsPage /></Private>} />
        <Route path="/trips/:tripId/items" element={<Private><ItemsPage /></Private>} />
        {/* 예전 경로. 북마크·링크가 깨지지 않게 남긴다 */}
        <Route path="/trips/:tripId/review" element={<Private><ItemsPage /></Private>} />
        {/* Items 의 "검수하기" 가 여기로 보낸다 */}
        <Route path="/trips/:tripId/inspection" element={<Private><InspectionPage /></Private>} />

        {/* 2차 — S-06 검수 결과의 요약을 눌러서 들어간다 */}
        <Route path="/trips/:tripId/weight" element={<Private><Weight /></Private>} />
        <Route path="/trips/:tripId/rules" element={<Private><Rules /></Private>} />
        {/* #42 가 백엔드를 넣은 화면들. 여행 하위로 들어간다 */}
        <Route path="/trips/:tripId/itinerary" element={<Private><ItineraryPage /></Private>} />
        <Route path="/trips/:tripId/layout" element={<Private><PackingLayoutPage /></Private>} />

        {/* 3 내 여행 */}
        <Route path="/trips" element={<Private><MyTrips /></Private>} />
        <Route path="/trips/:tripId" element={<Private><TripRecord /></Private>} />

        <Route path="/__mock" element={<MockCheck />} />
        <Route path="*" element={<NotFound />} />
      </Routes>
    </AuthProvider>
  )
}
