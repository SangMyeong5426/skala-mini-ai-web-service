import react from '@vitejs/plugin-react'
import { defineConfig } from 'vite'

// https://vite.dev/config/
export default defineConfig({
  plugins: [react()],
  server: {
    proxy: {
      // 짐 사진은 백엔드가 /uploads/** 로 내보낸다(UploadConfig).
      // 이게 없으면 <img src="/uploads/demo/bag-01.jpg"> 가 5173 으로 가서 404 다.
      // S-03 미리보기와 S-04 사진 태그가 통째로 깨진다.
      //
      // /api 는 프록시하지 않는다. 06-api-spec 과 04-architecture 가 절대 URL +
      // CORS 를 전제로 쓰였고 실제로 동작한다(5173 preflight → 200).
      '/uploads': 'http://localhost:8080',
    },
  },
})
