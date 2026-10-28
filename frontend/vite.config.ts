/// <reference types="vitest" />
import { defineConfig } from 'vitest/config';
import react from '@vitejs/plugin-react';

// The backend has no CORS configuration, so in development the browser talks to Vite (same origin)
// and Vite forwards /api to Spring Boot on port 8083.
export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    proxy: { '/api': { target: 'http://localhost:8083', changeOrigin: true } },
  },
  test: {
    environment: 'jsdom',
    include: ['src/**/*.test.{ts,tsx}'],
    restoreMocks: true,
    setupFiles: ['./src/test/setup.ts'],
  },
});