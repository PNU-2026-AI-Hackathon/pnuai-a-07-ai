import { defineConfig } from 'vite'
import path from 'path'
import tailwindcss from '@tailwindcss/vite'
import react from '@vitejs/plugin-react'


function figmaAssetResolver() {
  return {
    name: 'figma-asset-resolver',
    resolveId(id) {
      if (id.startsWith('figma:asset/')) {
        const filename = id.replace('figma:asset/', '')
        return path.resolve(__dirname, 'src/assets', filename)
      }
    },
  }
}

export default defineConfig({
  // GitHub Pages serves this repository under /pnuai-a-07-ai/.
  base: process.env.GITHUB_ACTIONS === 'true' ? '/pnuai-a-07-ai/' : '/',
  plugins: [
    figmaAssetResolver(),
    // The React and Tailwind plugins are both required for Make, even if
    // Tailwind is not being actively used – do not remove them
    react(),
    tailwindcss(),
  ],
  resolve: {
    alias: {
      // Alias @ to the src directory
      '@': path.resolve(__dirname, './src'),
    },
  },
  server: {
    // 터널로 들어오는 요청을 허용합니다. Vite 는 모르는 Host 헤더를 기본으로 막기 때문에
    // 여기에 없는 도메인으로 접속하면 화면 대신 "This host is not allowed" 가 나옵니다.
    //   .ngrok-free.dev  — 시연용 고정 주소 (start-demo.ps1 -Tunnel)
    //   .trycloudflare.com — ngrok 이 안 될 때 쓰는 임시 터널
    allowedHosts: ['.ngrok-free.dev', '.ngrok-free.app', '.ngrok.io', '.trycloudflare.com'],
    proxy: {
      '/api': 'http://localhost:8080',
    },
  },

  // File types to support raw imports. Never add .css, .tsx, or .ts files to this.
  assetsInclude: ['**/*.svg', '**/*.csv'],
})
