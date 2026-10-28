import { describe, expect, it } from 'vitest';
import { addMoney, compareMoney, formatMoney, isNegative, normalizeMoney, parseMoney, plainDecimal, quoteMoneyNumbers, ratio, sumMoney, unquoteMoneyStrings, validateAmount } from './money';

describe('exact decimal arithmetic', () => {
  it('does not suffer from floating point error', () => {
    expect(0.1 + 0.2 === 0.3).toBe(false); // the problem we avoid
    expect(addMoney('0.10', '0.20')).toBe('0.30');
    expect(sumMoney(['0.1', '0.2', '0.3'])).toBe('0.60');
  });
  it('is exact beyond 2^53', () => {
    expect(addMoney('9007199254740993.00', '1.00')).toBe('9007199254740994.00');
    expect(sumMoney(['999999999999999.9999', '0.0001'])).toBe('1000000000000000.00');
  });
  it('handles negatives, zero and comparison', () => {
    expect(addMoney('-5.50', '2.25')).toBe('-3.25');
    expect(isNegative('-0.01')).toBe(true);
    expect(isNegative('0.00')).toBe(false);
    expect(compareMoney('10.0000', '9.9999')).toBe(1);
    expect(compareMoney('2.50', '2.5000')).toBe(0);
  });
  it('rejects more than 4 significant decimals', () => {
    expect(parseMoney('1.00001')).toBeNull();
    expect(parseMoney('1.00000')).toBe(10000n);
    expect(parseMoney('abc')).toBeNull();
  });
  it('normalizes input to a JSON-safe canonical string', () => {
    expect(normalizeMoney('007')).toBe('7.00');
    expect(normalizeMoney('1250.5')).toBe('1250.50');
    expect(normalizeMoney('0.0001')).toBe('0.0001');
  });
});

describe('formatMoney', () => {
  it('always includes the ISO currency code, so currencies cannot be confused', () => {
    expect(formatMoney('1250.5', 'INR')).toMatch(/1.?250\.50 INR$/);
    expect(formatMoney('1250.5', 'USD')).toMatch(/ USD$/);
  });
  it('shows up to 4 decimals only when needed and keeps the sign', () => {
    expect(formatMoney('0.1234', 'EUR')).toBe('0.1234 EUR');
    expect(formatMoney('-3.2', 'EUR')).toBe('-3.20 EUR');
    expect(formatMoney('0', 'EUR')).toBe('0.00 EUR');
  });
  it('formats very large values exactly', () => {
    expect(formatMoney('123456789012345.6789', 'INR').replace(/[^\d.]/g, '')).toBe('123456789012345.6789');
  });
  it('falls back safely for unparseable values', () => {
    expect(formatMoney('oops', 'INR')).toBe('oops INR');
  });
});

describe('validateAmount', () => {
  it('accepts valid amounts', () => {
    expect(validateAmount('1250.50')).toBeNull();
    expect(validateAmount('0.0001')).toBeNull();
    expect(validateAmount('0', { allowZero: true })).toBeNull();
  });
  it('rejects empty, zero, negative, malformed, too precise and too large', () => {
    expect(validateAmount('')).toMatch(/required/);
    expect(validateAmount('0')).toMatch(/greater than zero/);
    expect(validateAmount('0.0000')).toMatch(/greater than zero/);
    expect(validateAmount('-5')).not.toBeNull();
    expect(validateAmount('1e5')).not.toBeNull();
    expect(validateAmount('1,000')).not.toBeNull();
    expect(validateAmount('1.12345')).toMatch(/4 decimal/);
    expect(validateAmount('1234567890123456')).toMatch(/15 digits/);
  });
});

describe('JSON transport of money', () => {
  it('quotes money numbers so precision survives JSON.parse', () => {
    const raw = '{"id":"x","amount":12345678901234567.8901,"currentBalance":-0.10,"name":"a","incomeTransactionCount":3}';
    const parsed = JSON.parse(quoteMoneyNumbers(raw));
    expect(parsed.amount).toBe('12345678901234567.8901');
    expect(parsed.currentBalance).toBe('-0.10');
    expect(parsed.incomeTransactionCount).toBe(3); // counts stay numbers
  });
  it('expands scientific notation', () => {
    expect(plainDecimal('1E-7')).toBe('0.0000001');
    expect(plainDecimal('1.5E+2')).toBe('150');
    expect(JSON.parse(quoteMoneyNumbers('{"total":1E+3}')).total).toBe('1000');
  });
  it('does not touch lookalike text inside string values', () => {
    const raw = JSON.stringify({ description: '"amount":5', amount: 7 });
    const parsed = JSON.parse(quoteMoneyNumbers(raw));
    expect(parsed.description).toBe('"amount":5');
    expect(parsed.amount).toBe('7');
  });
  it('sends money strings as bare JSON numbers (backend contract)', () => {
    const out = unquoteMoneyStrings(JSON.stringify({ amount: '1250.50', description: 'amount' }));
    expect(out).toBe('{"amount":1250.50,"description":"amount"}');
    expect(JSON.parse(out).amount).toBe(1250.5);
  });
});

describe('ratio (bar widths only)', () => {
  it('is bounded and safe for zero', () => {
    expect(ratio('50', '100')).toBe(0.5);
    expect(ratio('5', '0')).toBe(0);
    expect(ratio('200', '100')).toBe(1);
  });
});
