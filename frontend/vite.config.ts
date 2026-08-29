/// <reference types="vitest/config" />
import { fileURLToPath, URL } from 'node:url';
import react from '@vitejs/plugin-react';
import { defineConfig, loadEnv } from 'vite';

export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, process.cwd(), '');
  // The dev server proxies /api so the SPA stays same-origin, exactly like behind nginx in production.
  const apiProxy = {
    '/api': { target: env.VITE_DEV_PROXY_TARGET || 'http://localhost:8080', changeOrigin: false },
  };

  return {
    plugins: [react()],
    resolve: {
      alias: { '@': fileURLToPath(new URL('./src', import.meta.url)) },
    },
    server: { port: 5173, strictPort: true, proxy: apiProxy },
    preview: { port: 4173, proxy: apiProxy },
    build: { sourcemap: true, chunkSizeWarningLimit: 1200 },
    test: {
      environment: 'jsdom',
      setupFiles: ['./src/test/setup.tsx'],
      include: ['src/**/*.test.{ts,tsx}'],
      css: false,
      restoreMocks: true,
      testTimeout: 15_000,
    },
  };
});
