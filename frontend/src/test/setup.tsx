import '@testing-library/jest-dom/vitest';
import { cleanup, configure } from '@testing-library/react';
import { afterAll, afterEach, beforeAll, vi } from 'vitest';
import { tokenStore } from '@/api/httpClient';
import { server } from './server';

// Tests render the whole app (session restore, queries, lazily imported routes). On a busy machine the first lazy
// import can exceed the 1 s default; findBy* still resolves as soon as the element appears.
configure({ asyncUtilTimeout: 5_000 });

// Canvas rendering is unavailable in jsdom; charts are covered through their option builders.
vi.mock('@/components/charts/EChart', () => ({
  default: ({ ariaLabel }: { ariaLabel: string }) => <div role="img" aria-label={ariaLabel} />,
}));

if (!window.matchMedia) {
  Object.defineProperty(window, 'matchMedia', {
    writable: true,
    value: (query: string) => ({
      matches: false, media: query, onchange: null,
      addListener: () => undefined, removeListener: () => undefined,
      addEventListener: () => undefined, removeEventListener: () => undefined, dispatchEvent: () => false,
    }),
  });
}

if (!('ResizeObserver' in window)) {
  class ResizeObserverStub {
    observe() {}
    unobserve() {}
    disconnect() {}
  }
  Object.defineProperty(window, 'ResizeObserver', { writable: true, value: ResizeObserverStub });
}

window.scrollTo = () => undefined;

beforeAll(() => server.listen({ onUnhandledFrame: 'error' }));
afterEach(() => {
  cleanup();
  server.resetHandlers();
  tokenStore.set(null);
  window.localStorage.clear();
});
afterAll(() => server.close());
