import { describe, expect, it } from 'vitest';
import { createKeyStore, newId } from './idempotency';

describe('Idempotency-Key store', () => {
  const counter = () => { let n = 0; return () => `key-${++n}`; };
  it('reuses the key when the same payload is retried', () => {
    const s = createKeyStore(counter());
    const p = { amount: '10.00', description: 'Lunch' };
    expect(s.keyFor(p)).toBe('key-1');
    expect(s.keyFor({ ...p })).toBe('key-1'); // lost response, user clicks again
  });
  it('creates a new key when anything in the payload changes', () => {
    const s = createKeyStore(counter());
    const a = s.keyFor({ amount: '10.00' });
    const b = s.keyFor({ amount: '11.00' });
    expect(a).not.toBe(b);
  });
  it('creates a new key after a confirmed success (reset)', () => {
    const s = createKeyStore(counter());
    const first = s.keyFor({ amount: '10.00' });
    s.reset();
    expect(s.keyFor({ amount: '10.00' })).not.toBe(first);
  });
  it('newId returns a v4-shaped UUID', () => {
    expect(newId()).toMatch(/^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/);
  });
});
