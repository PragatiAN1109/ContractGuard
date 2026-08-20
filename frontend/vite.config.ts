import { defineConfig } from 'vitest/config';
import react from '@vitejs/plugin-react';

const BACKEND = process.env.CONTRACTGUARD_API_URL ?? 'http://localhost:8081';

export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    // Proxying /api keeps the browser on one origin, so the backend needs no CORS config.
    proxy: { '/api': { target: BACKEND, changeOrigin: true } },
    // The sample .avsc files live in the backend resources, one level up.
    fs: { allow: ['..'] },
  },
  test: {
    environment: 'jsdom',
    globals: true,
    setupFiles: './src/setupTests.ts',
    css: false,
  },
});
