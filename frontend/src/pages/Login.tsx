import { useEffect, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { ApiFailure } from '../api/client'
import { useAuth } from '../auth/context'
import { AuthShell, Field } from '../components/AuthShell'
import { USE_MOCK } from '../api/mock'
import { EMAIL_RE, LOGIN_ID_RE, PASSWORD_MAX_BYTES, PASSWORD_MIN } from '../types/api'

/**
 * S-00 로그인 · 회원가입.
 *
 * <b>한 화면에서 모드를 전환한다.</b> 03-wireframe 이 <i>"경로 `/login`.
 * 로그인 모드와 회원가입 모드를 한 화면에서 전환"</i> 으로 못박았고,
 * <i>"로그인·회원가입은 S-00 `/login` 에서만 제공한다"</i> 고 덧붙였다.
 * 그래서 `/signup` 라우트를 두지 않는다.
 *
 * 가입은 <b>자동 로그인하지 않는다</b>(06). 끝나면 아이디만 남기고 비밀번호를
 * 비운 뒤 로그인 모드로 돌아온다.
 *
 * 서버가 알려준 필드 오류(`field`)는 그 칸 아래에 <b>오류 모양</b>으로 붙이고
 * `aria-invalid` 를 준다. 회색 안내문으로 바꿔치기하면 무엇이 잘못됐는지
 * 화면에도 스크린리더에도 남지 않는다.
 */
type Mode = 'login' | 'signup'

/**
 * 가입 칸 하나의 규칙. <b>제출 검사와 실시간 검사가 같은 함수를 쓴다.</b>
 *
 * 두 벌로 두면 한쪽만 고쳐서 "칸에서는 통과인데 제출하면 거절" 이 된다.
 * 06 "회원가입·로그인 계약" 을 그대로 옮긴 것이고, 서버·Mock 과도 같은 기준이다.
 */
function ruleError(name: string, value: string): string | null {
  const v = value.trim()
  switch (name) {
    case 'nickname':
      return v.length < 2 || v.length > 50 ? '닉네임은 2~50자로 입력해 주세요.' : null
    case 'loginId':
      return LOGIN_ID_RE.test(v.toLowerCase()) ? null : '아이디는 영문 소문자·숫자·밑줄 4~30자입니다.'
    case 'password':
      // 공백도 비밀번호의 일부다. trim 하지 않는다
      if (value.length < PASSWORD_MIN) return `비밀번호는 ${PASSWORD_MIN}자 이상이어야 합니다.`
      /*
       * <b>BCrypt 가 72바이트에서 자른다.</b> 그래서 06 이 상한을 글자 수가 아니라
       * 바이트로 정했다 — 한글은 한 자에 3바이트라 24자면 넘는다.
       *
       * 예전에는 이 검사가 서버와 Mock 에만 있어서, 한글 비밀번호를 쓴 사람은
       * 가입 버튼을 눌러야 거절당했다.
       */
      if (new TextEncoder().encode(value).length > PASSWORD_MAX_BYTES) {
        return `비밀번호가 너무 깁니다. 한글은 ${Math.floor(PASSWORD_MAX_BYTES / 3)}자까지 됩니다.`
      }
      return null
    case 'email':
      return EMAIL_RE.test(v) && v.length <= 255 ? null : '이메일 형식을 확인해 주세요.'
    default:
      return null
  }
}

export default function Login() {
  const { user, loading, login, signup } = useAuth()
  const nav = useNavigate()

  const [mode, setMode] = useState<Mode>('login')
  const [loginId, setLoginId] = useState('')
  const [password, setPassword] = useState('')
  const [nickname, setNickname] = useState('')
  const [email, setEmail] = useState('')
  const [showPw, setShowPw] = useState(false)

  const [error, setError] = useState<string | null>(null)
  /*
   * 칸을 떠날 때 검사한다. <b>타이핑 중에는 하지 않는다</b> — 두 글자 쳤을 때
   * "4자 이상" 이 뜨면 아직 쓰는 중인 사람을 나무라는 꼴이다.
   *
   * 로그인 모드에서는 하지 않는다. 예전 규칙으로 만든 아이디를 쓰는 사람에게
   * 화면이 먼저 "아이디가 틀렸다" 고 단정하면 안 된다(아래 제출 검사와 같은 이유).
   */
  const checkOnBlur = (name: string, value: string) => {
    if (mode !== 'signup' || !value.trim()) return
    const msg = ruleError(name, value)
    if (msg) { setField(name); setError(msg) }
    else if (field === name) { setField(null); setError(null) }
  }
  const [field, setField] = useState<string | null>(null)
  const [notice, setNotice] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)

  // 03: "로그인된 사용자가 S-00 에 오면 S-01 로 이동"
  useEffect(() => {
    if (!loading && user) nav('/trips', { replace: true })
  }, [loading, user, nav])

  const swap = (next: Mode) => {
    setMode(next)
    setError(null); setField(null); setNotice(null)
    setPassword('')
  }

  const submit = async (e: React.FormEvent) => {
    e.preventDefault()
    setError(null); setField(null); setNotice(null)

    /*
     * <b>검사한 값과 보내는 값이 같아야 한다.</b>
     *
     * 예전에는 `loginId.trim().toLowerCase()` 로 검사해 놓고 서버에는 입력
     * 원문을 보냈다. 서버는 `@Pattern("[A-Za-z0-9_]{4,30}")` 을 <b>원문에</b>
     * 걸므로(AuthDtos:26, 정규화는 그 뒤 AuthService:37), 앞뒤 공백이 섞이면
     * 프런트 검사는 통과하고 서버가 400 으로 거절했다. 이메일도 같다.
     *
     * 비밀번호는 건드리지 않는다 — 공백도 비밀번호의 일부다.
     */
    const id = loginId.trim().toLowerCase()
    const mail = email.trim()
    const nick = nickname.trim()

    /*
     * <b>형식 규칙은 가입에서만 건다.</b>
     *
     * 로그인은 서버가 두 칸의 빈 값만 보고(AuthDtos `@NotBlank`), 형식이
     * 어떻든 틀리면 401 <i>"아이디 또는 비밀번호를 확인해 주세요."</i> 하나로
     * 답한다. 03:210 이 로그인 실패 문구를 그것 하나로 정했다.
     *
     * 그런데 프런트가 가입용 규칙을 먼저 걸어서, 예전 아이디를 쓰는 사람이나
     * 대문자를 넣은 사람에게 <b>"아이디는 영문 소문자…"</b> 라는 다른 문구가
     * 나왔다. 아이디가 틀렸다는 사실을 화면이 먼저 단정한 셈이다.
     */
    if (mode === 'signup') {
      // 위에서 아래로 처음 걸리는 칸 하나만 짚는다. 한꺼번에 다 띄우면 어디부터 고칠지 모른다
      for (const [name, value] of [
        ['nickname', nickname], ['loginId', id], ['password', password], ['email', email],
      ] as const) {
        const msg = ruleError(name, value)
        if (msg) { setField(name); setError(msg); return }
      }
    } else if (!id || !password) {
      setField(id ? 'password' : 'loginId')
      setError('아이디와 비밀번호를 입력해 주세요.')
      return
    }

    setBusy(true)
    try {
      if (mode === 'login') {
        await login(id, password)
        /*
         * <b>언제나 S-01 이다.</b> 원래 있던 화면으로 돌려보내지 않는다.
         *
         * 03:41 — "비로그인·만료 시 S-00 으로 이동하고 <b>로그인 성공 후
         * S-01 에서 다시 시작한다</b>". 03:208 도 같다.
         *
         * 세션이 만료됐다는 것은 그 사이 상태가 달라졌을 수 있다는 뜻이다.
         * S-06 검수 화면 한가운데로 되돌려 놓으면 옛 화면을 새 세션으로 보게
         * 된다. 여행 목록에서 다시 고르는 편이 안전하다.
         *
         * 이 파일 46행의 "이미 로그인한 사람" 리다이렉트도 `/trips` 고정이라
         * 두 경로가 이제 같은 곳으로 간다.
         */
        nav('/trips', { replace: true })
      } else {
        await signup({ nickname: nick, loginId: id, password, email: mail })
        // 06: 가입만으로 세션을 만들지 않는다. 아이디만 남기고 로그인 모드로.
        setMode('login')
        setPassword('')
        setNotice('가입이 끝났어요. 이제 로그인해 주세요.')
      }
    } catch (err) {
      setError(err instanceof Error ? err.message : '요청을 처리하지 못했습니다.')
      if (err instanceof ApiFailure && err.field) setField(err.field)
    } finally {
      setBusy(false)
    }
  }

  const isLogin = mode === 'login'
  const errFor = (name: string) => (field === name ? error : null)

  return (
    <AuthShell
      title={isLogin ? '다시 오셨네요' : '계정을 만들어 주세요'}
      sub={isLogin
        ? '아이디로 로그인하고 여행 준비를 이어가세요.'
        : '아이디·비밀번호·닉네임·이메일만 받아요.'}
      foot={isLogin ? (
        <>아직 계정이 없나요?{' '}
          <button type="button" className="link-btn" onClick={() => swap('signup')}>회원가입으로 이동</button>
        </>
      ) : (
        <>이미 계정이 있나요?{' '}
          <button type="button" className="link-btn" onClick={() => swap('login')}>로그인으로 돌아가기</button>
        </>
      )}
    >
      {notice && <p className="auth-note" role="status">{notice}</p>}

      <form className="auth-form" onSubmit={submit} noValidate>
        {!isLogin && (
          <Field label="닉네임" htmlFor="nickname" error={errFor('nickname')} hint="화면에 표시되는 이름이에요. 2~50자.">
            <input
              id="nickname" name="nickname" autoComplete="nickname"
              value={nickname} onChange={(e) => setNickname(e.target.value)}
              onBlur={(e) => checkOnBlur('nickname', e.target.value)}
            />
          </Field>
        )}

        <Field label="아이디" htmlFor="loginId" error={errFor('loginId')} hint="영문 소문자·숫자·밑줄 4~30자.">
          <input
            id="loginId" name="username" autoComplete="username" autoFocus
            value={loginId} onChange={(e) => setLoginId(e.target.value)}
            onBlur={(e) => checkOnBlur('loginId', e.target.value)}
          />
        </Field>

        <Field label="비밀번호" htmlFor="password" error={errFor('password')} hint={isLogin ? undefined : `${PASSWORD_MIN}자 이상. 한글은 ${Math.floor(PASSWORD_MAX_BYTES / 3)}자까지.`}>
          <div className="pw">
            <input
              id="password" name="password"
              aria-invalid={errFor('password') ? true : undefined}
              aria-describedby="password-msg"
              className={errFor('password') ? 'is-bad' : undefined}
              type={showPw ? 'text' : 'password'}
              autoComplete={isLogin ? 'current-password' : 'new-password'}
              value={password} onChange={(e) => setPassword(e.target.value)}
              onBlur={(e) => checkOnBlur('password', e.target.value)}
            />
            <button
              type="button" className="pw-toggle"
              onClick={() => setShowPw((v) => !v)}
              aria-pressed={showPw}
            >
              {showPw ? '숨기기' : '표시'}
            </button>
          </div>
        </Field>

        {!isLogin && (
          <Field label="이메일" htmlFor="email" error={errFor('email')}>
            <input
              id="email" name="email" type="email" autoComplete="email"
              value={email} onChange={(e) => setEmail(e.target.value)}
              onBlur={(e) => checkOnBlur('email', e.target.value)}
            />
          </Field>
        )}

        {error && !field && <p className="auth-error" role="alert">{error}</p>}

        {/* 세션·CSRF 를 아직 못 받았으면 제출을 막는다. 토큰 없이 나가면 403 이다 */}
        <button type="submit" className="btn-cta btn-block" disabled={busy || loading}>
          {busy ? '확인하는 중…' : isLogin ? '로그인' : '가입하기'}
        </button>
      </form>

      {/*
        * 데모 계정은 Mock 일 때만 보여준다. 실서버에 붙이면 로그인 화면에
        * 비밀번호 문자열이 상시 노출된다. 아이디는 seed.sql 과 같은 값이라
        * Mock ↔ 실서버 전환에서 대본이 바뀌지 않는다.
        */}
      {USE_MOCK && isLogin && (
        <p className="auth-hint">데모 계정 — 아이디 <b>jiwoo28</b> · 비밀번호 <b>skala1234</b></p>
      )}
    </AuthShell>
  )
}
