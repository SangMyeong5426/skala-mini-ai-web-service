/**
 * 아직 만들지 않은 화면. 라우팅과 레이아웃을 먼저 확정하려고 둔다.
 *
 * 각 화면 담당자가 이 자리를 실제 화면으로 바꾼다.
 * 화면 번호(S-xx)는 팀 내부 식별자라 사용자에게 보여주지 않는다.
 */
export function Placeholder({ name, note }: { name: string; note?: string }) {
  return (
    <div className="card">
      <h2 className="card-title" style={{ fontSize: 17 }}>{name}</h2>
      {note && <p className="card-sub" style={{ marginTop: 4 }}>{note}</p>}
      <p className="page-todo">준비 중입니다.</p>
    </div>
  )
}
