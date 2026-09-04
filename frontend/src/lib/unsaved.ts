import { useEffect } from 'react'

/**
 * 저장하지 않은 입력이 있는 화면에서 <b>떠나기 전에 묻는다.</b>
 *
 * 예전에는 단계 표시줄에만 가드를 걸었다. 그런데 표시줄은 <b>지나온</b> 단계만
 * 링크로 만든다 — 여행 정보는 1단계라 링크가 하나도 없고, 그래서 그 가드는
 * 애초에 호출될 수 없었다. 실제로 사람이 빠져나가는 길은 셋이다.
 *
 *   1. 상단 브랜드 로고 · 내 여행 링크 · 여행 등록 버튼
 *   2. 단계 표시줄 (2단계 이상에서만)
 *   3. 새로고침 · 탭 닫기 · 브라우저 뒤로가기
 *
 * 1·2 는 앱 안에서 일어나므로 링크가 직접 물어보고, 3 은 브라우저가 대신 묻는다
 * (`beforeunload`).
 *
 * <b>브라우저 뒤로가기는 못 막는다(#54).</b> 그 버튼은 우리가 그린 것이 아니라
 * 가로챌 클릭이 없고, SPA 의 뒤로가기는 문서를 버리지 않아 `beforeunload` 도
 * 안 뜬다 — 라우터가 `popstate` 를 받아 화면만 바꿔 낀다.
 *
 * React Router 의 `useBlocker` 가 이걸 위한 API 인데 <b>데이터 라우터에서만</b>
 * 된다. `main.tsx` 가 `<BrowserRouter>` 라 지금은 부르면 던진다. 옮기는 일은
 * 라우트 전체를 다시 쓰는 이사라 #54 로 뺐다.
 *
 * <b>상태를 모듈 수준에 둔다.</b> 컨텍스트를 만들면 상단 헤더가 그 공급자 안에
 * 있어야 하는데, 헤더는 랜딩·인증 화면까지 함께 쓰는 공용이라 감싸는 자리가
 * 마땅치 않다. 한 번에 한 화면만 편집 중이므로 전역 플래그 하나로 충분하다.
 *
 * <b>편집 화면이 둘 이상 동시에 뜨면 서로 덮는다.</b> 지금 구조에서는 생기지
 * 않는 상황이지만, 데이터 라우터로 옮길 때 함께 정리한다(#54).
 */
let dirty = false

/** 지금 저장하지 않은 입력이 있나. 링크가 떠나기 직전에 묻는다 */
export const isDirty = () => dirty

/**
 * 떠나도 되는지 사용자에게 묻는다. 깨끗하면 묻지 않고 `true`.
 *
 * <b>자동 저장하지 않는다.</b> 채우다 만 폼은 서버가 400 으로 거절해서
 * "왜 저장이 안 되지" 만 남는다. 사실을 알리고 고르게 한다.
 */
export function confirmLeave(): boolean {
  if (!dirty) return true
  return window.confirm('저장하지 않은 변경이 있습니다. 그대로 나가시겠습니까?')
}

/**
 * 이 화면이 저장하지 않은 입력을 들고 있다고 알린다.
 *
 * 화면을 벗어나면 플래그를 반드시 되돌린다 — 안 그러면 다음 화면에서 아무
 * 이유 없이 확인창이 뜬다.
 */
export function useUnsavedGuard(isUnsaved: boolean) {
  useEffect(() => {
    dirty = isUnsaved
    if (!isUnsaved) return

    // 새로고침·탭 닫기. 문구는 브라우저가 정한다 — preventDefault 만 하면 된다.
    const onBeforeUnload = (e: BeforeUnloadEvent) => e.preventDefault()
    window.addEventListener('beforeunload', onBeforeUnload)
    return () => {
      window.removeEventListener('beforeunload', onBeforeUnload)
      dirty = false
    }
  }, [isUnsaved])

  // 화면 자체를 떠날 때도 반드시 푼다
  useEffect(() => () => { dirty = false }, [])
}
