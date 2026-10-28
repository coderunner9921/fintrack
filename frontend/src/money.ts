// Exact decimal money helpers. The backend uses BigDecimal with NUMERIC(19,4), i.e. at most 4 decimal places.
// Amounts are kept as decimal STRINGS in the frontend and calculated with BigInt "units" (value x 10^4),
// so no floating-point rounding is ever involved. The backend remains authoritative for balances.

export type Money = string;
const SCALE = 4;
const FACTOR = 10000n;

/** Parse "-12.5" into integer units (-125000). Returns null if invalid or if it has more than 4 significant decimals. */
export function parseMoney(value: string): bigint | null {
  const m = /^(-)?(\d+)(?:\.(\d+))?$/.exec(value.trim());
  if (!m) return null;
  const frac = m[3] ?? '';
  if (frac.length > SCALE && /[1-9]/.test(frac.slice(SCALE))) return null;
  const units = BigInt(m[2]) * FACTOR + BigInt((frac + '0000').slice(0, SCALE));
  return m[1] ? -units : units;
}

function unitsToString(units: bigint): Money {
  const neg = units < 0n;
  const abs = neg ? -units : units;
  let frac = (abs % FACTOR).toString().padStart(SCALE, '0').replace(/0+$/, '');
  if (frac.length < 2) frac = frac.padEnd(2, '0');
  return `${neg ? '-' : ''}${(abs / FACTOR).toString()}.${frac}`;
}

const need = (v: string): bigint => {
  const u = parseMoney(v);
  if (u === null) throw new Error(`Invalid money value: ${v}`);
  return u;
};

/** Canonical decimal string (no leading zeros, at least 2 decimals) - safe to embed as a JSON number. */
export const normalizeMoney = (v: Money): Money => unitsToString(need(v));
export const addMoney = (a: Money, b: Money): Money => unitsToString(need(a) + need(b));
export const sumMoney = (values: Money[]): Money => unitsToString(values.reduce((t, v) => t + need(v), 0n));
export function compareMoney(a: Money, b: Money): -1 | 0 | 1 {
  const x = need(a), y = need(b);
  return x < y ? -1 : x > y ? 1 : 0;
}
export const isNegative = (v: Money): boolean => (parseMoney(v) ?? 0n) < 0n;
export const isZero = (v: Money): boolean => parseMoney(v) === 0n;

/**
 * Display format: "1,250.50 INR". The ISO code is always shown (instead of a symbol such as $ or ₹)
 * so amounts in different currencies can never be mistaken for one another.
 * Always at least 2 decimals, up to 4 when the value needs them. Exact for any size.
 */
export function formatMoney(value: Money, currency: string): string {
  const units = parseMoney(value);
  if (units === null) return `${value} ${currency}`;
  const neg = units < 0n;
  const abs = neg ? -units : units;
  const whole = new Intl.NumberFormat(undefined, { useGrouping: true }).format(abs / FACTOR);
  let frac = (abs % FACTOR).toString().padStart(SCALE, '0').replace(/0+$/, '');
  if (frac.length < 2) frac = frac.padEnd(2, '0');
  const dot = new Intl.NumberFormat(undefined).format(1.1).charAt(1) || '.';
  return `${neg ? '-' : ''}${whole}${dot}${frac} ${currency.toUpperCase()}`;
}

/** Validate user-typed amounts using the backend limits: up to 15 integer digits and 4 decimals. */
export function validateAmount(input: string, opts: { allowZero?: boolean } = {}): string | null {
  const v = input.trim();
  if (!v) return 'Amount is required.';
  if (!/^\d+(\.\d+)?$/.test(v)) return 'Use digits and an optional decimal point, e.g. 1250.50.';
  const [int, frac = ''] = v.split('.');
  if (int.replace(/^0+(?=\d)/, '').length > 15) return 'Amount supports up to 15 digits before the decimal point.';
  if (frac.length > SCALE) return 'Amount supports up to 4 decimal places.';
  if (!opts.allowZero && isZero(v)) return 'Amount must be greater than zero.';
  return null;
}

/** Ratio a/b in [0,1] for drawing bar widths only. Never used for displayed amounts. */
export function ratio(a: Money, b: Money): number {
  const x = parseMoney(a), y = parseMoney(b);
  if (x === null || y === null || y <= 0n || x <= 0n) return 0;
  return Math.min(1, Number((x * 10000n) / y) / 10000);
}

// ---- JSON transport -------------------------------------------------------------------------------------
// Jackson sends BigDecimal as bare JSON numbers. JSON.parse would turn them into doubles, so before parsing
// we quote the known money fields (without changing the backend contract), and unquote them when sending.
const MONEY_KEYS = ['amount', 'openingBalance', 'currentBalance', 'totalIncome', 'totalExpenses', 'netCashFlow', 'total'];
const IN_RE = new RegExp(`"(${MONEY_KEYS.join('|')})"\\s*:\\s*(-?\\d+(?:\\.\\d+)?(?:[eE][+-]?\\d+)?)`, 'g');
const OUT_RE = new RegExp(`"(${MONEY_KEYS.join('|')})"\\s*:\\s*"(-?\\d+(?:\\.\\d+)?)"`, 'g');

/** Expand scientific notation ("1E-7", "1.5E+2") to a plain decimal string. */
export function plainDecimal(n: string): string {
  const m = /^(-?)(\d+)(?:\.(\d+))?(?:[eE]([+-]?\d+))?$/.exec(n);
  if (!m) return n;
  const [, sign, i, f = '', e = '0'] = m;
  let digits = i + f;
  let point = i.length + parseInt(e, 10);
  if (point <= 0) { digits = '0'.repeat(1 - point) + digits; point = 1; }
  else if (point > digits.length) digits += '0'.repeat(point - digits.length);
  const intPart = digits.slice(0, point).replace(/^0+(?=\d)/, '');
  const frac = digits.slice(point);
  return sign + intPart + (frac ? `.${frac}` : '');
}

export const quoteMoneyNumbers = (json: string): string => json.replace(IN_RE, (_m, k: string, n: string) => `"${k}":"${plainDecimal(n)}"`);
export const unquoteMoneyStrings = (json: string): string => json.replace(OUT_RE, (_m, k: string, n: string) => `"${k}":${n}`);
