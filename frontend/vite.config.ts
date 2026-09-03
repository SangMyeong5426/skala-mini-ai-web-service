import { readFileSync, existsSync } from 'node:fs'
import { extname, join, normalize } from 'node:path'

import react from '@vitejs/plugin-react'
import { defineConfig, loadEnv, type Plugin } from 'vite'

/**
 * Mock 으로 개발할 때 데모 사진을 내보낸다.
 *
 * 사진은 `database/demo-photos/` 에 있다(저장소에 커밋됨). 프런트로 복사하지
 * 않는 이유는 같은 파일이 두 벌 생기기 때문이다 — 한쪽만 바뀌면 어긋난다.
 *
 * 백엔드가 있을 때는 이 플러그인이 아니라 아래 프록시가 8080 으로 넘긴다.
 */
function demoPhotos(): Plugin {
  const root = new URL('../database/demo-photos', import.meta.url).pathname
  return {
    name: 'demo-photos',
    configureServer(server) {
      server.middlewares.use('/uploads', (req, res) => {
        const raw = decodeURIComponent((req.url ?? '').split('?')[0])
        const name = normalize(raw).replace(/^\/+/, '')

        // 경로 탈출을 막는다. `demo/파일명` 한 단계만 받는다.
        const ok = /^demo\/[\w.-]+$/.test(name) && !name.includes('..')
        const file = ok ? join(root, name.slice('demo/'.length)) : null

        if (!file || !existsSync(file)) {
          // next() 로 넘기면 SPA 폴백이 index.html 을 200 으로 준다.
          // 이미지 요청에 HTML 이 오면 <img> 가 조용히 깨져서 원인을 못 찾는다.
          res.statusCode = 404
          res.setHeader('Content-Type', 'text/plain; charset=utf-8')
          res.end('데모 사진을 찾을 수 없습니다. database/demo-photos/ 를 확인하세요.')
          return
        }
        res.setHeader('Content-Type', extname(file) === '.png' ? 'image/png' : 'image/jpeg')
        res.end(readFileSync(file))
      })
    },
  }
}

export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, process.cwd(), '')
  const useMock = env.VITE_USE_MOCK === 'true'

  return {
    plugins: [react(), ...(useMock ? [demoPhotos()] : [])],
    server: {
      // Mock 일 때는 프록시를 끈다. 백엔드가 없으므로 8080 으로 보내면
      // ECONNREFUSED 가 나고 S-03·S-04 사진이 깨진다.
      // 프록시가 살아 있으면 위 플러그인보다 먼저 가로채므로 둘을 함께 켜지 않는다.
      proxy: useMock
        ? undefined
        : {
            // 짐 사진은 백엔드가 /uploads/** 로 내보낸다(UploadConfig).
            // 이게 없으면 <img src="/uploads/..."> 가 5173 으로 가서 404 다.
            //
            // /api 는 프록시하지 않는다. 06-api-spec 과 04-architecture 가
            // 절대 URL + CORS 를 전제로 쓰였고 실제로 동작한다(preflight → 200).
            '/uploads': 'http://localhost:8080',
          },
    },
  }
})
