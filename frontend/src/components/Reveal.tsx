import { useEffect } from 'react'

/**
 * 스크롤 등장 — <code>data-reveal</code> 이 붙은 요소를 화면에 들어올 때 띄운다.
 *
 * 라이브러리를 쓰지 않는다(CLAUDE.md "기능을 늘리지 않는다"). 브라우저의
 * <code>IntersectionObserver</code> 하나면 되고, 스크롤 이벤트와 달리
 * 매 프레임 계산하지 않는다.
 *
 * <b>숨기는 일은 CSS 가 아니라 JS 가 시작한다.</b> 루트에 <code>reveal-on</code>
 * 을 붙인 뒤에야 요소가 투명해진다. 스크립트가 죽으면 아무것도 숨지 않아서,
 * 글이 통째로 안 보이는 사고가 나지 않는다.
 *
 * 한 번 나타난 요소는 관찰을 끊는다. 다시 올라갈 때 사라지면 읽던 사람이
 * 놀란다.
 */
export function useReveal() {
  useEffect(() => {
    const root = document.documentElement
    const targets = Array.from(document.querySelectorAll<HTMLElement>('[data-reveal]'))
    if (!targets.length) return

    // 움직임을 줄여 달라는 설정이면 그냥 다 보여 준다
    if (window.matchMedia('(prefers-reduced-motion: reduce)').matches) return

    root.classList.add('reveal-on')

    const io = new IntersectionObserver(
      (entries) => {
        for (const e of entries) {
          if (!e.isIntersecting) continue
          e.target.classList.add('is-in')
          io.unobserve(e.target)
        }
      },
      // 화면 아래 끝에 걸치자마자가 아니라 조금 올라온 뒤에 띄운다
      { rootMargin: '0px 0px -12% 0px', threshold: 0.05 },
    )
    targets.forEach((el) => io.observe(el))

    return () => {
      io.disconnect()
      root.classList.remove('reveal-on')
    }
  }, [])
}
