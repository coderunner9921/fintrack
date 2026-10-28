// One Idempotency-Key per logical request.
// The same payload keeps the same key (so retrying after a lost response is a safe replay on the backend);
// any change to the payload produces a new key; after a confirmed success the key must be reset.

/** crypto.randomUUID only exists in secure contexts (https/localhost); fall back for plain-http LAN access. */
export function newId(): string {
  const c = globalThis.crypto;
  if (c && typeof c.randomUUID === 'function') return c.randomUUID();
  const b = c.getRandomValues(new Uint8Array(16));
  b[6] = (b[6] & 0x0f) | 0x40; b[8] = (b[8] & 0x3f) | 0x80;
  const h = Array.from(b, (x) => x.toString(16).padStart(2, '0')).join('');
  return `${h.slice(0, 8)}-${h.slice(8, 12)}-${h.slice(12, 16)}-${h.slice(16, 20)}-${h.slice(20)}`;
}

export function createKeyStore(generate: () => string = newId) {
  let current: { fp: string; key: string } | null = null;
  return {
    keyFor(payload: unknown): string {
      const fp = JSON.stringify(payload);
      if (!current || current.fp !== fp) current = { fp, key: generate() };
      return current.key;
    },
    reset() { current = null; },
  };
}
