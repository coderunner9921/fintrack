// Runs before every test file (registered via `setupFiles` in vite.config.ts).
//
// React Testing Library only unmounts rendered components automatically when a global `afterEach`
// exists. This project imports Vitest functions explicitly (no `globals: true`), so cleanup must be
// registered by hand. Without it, every render stays in document.body and later tests see stale
// pages (duplicate buttons/headings, duplicate element ids, wrong label lookups).
import { cleanup } from '@testing-library/react';
import { afterEach } from 'vitest';

afterEach(() => {
  cleanup();
  sessionStorage.clear(); // never let a test's login session leak into the next test
});
