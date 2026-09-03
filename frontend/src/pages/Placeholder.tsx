/**
 * 화면 껍데기. 라우팅과 레이아웃을 먼저 확정하려고 둔다.
 *
 * 각 화면 담당자가 이 파일을 지우고 실제 화면으로 바꾼다.
 * 지금 내용을 채우지 않는 이유는 docs/01·02·03 이 아직 정리 중이기 때문이다
 * (로그인 정책 · Notion 개정안 반영).
 */
export function Placeholder({ id, name, note }: { id: string; name: string; note?: string }) {
  return (
    <section className="page">
      <p className="page-id">{id}</p>
      <h1 className="page-title">{name}</h1>
      {note && <p className="page-note">{note}</p>}
      <p className="page-todo">아직 만들지 않았습니다. 담당자가 이 화면을 채웁니다.</p>
    </section>
  )
}
