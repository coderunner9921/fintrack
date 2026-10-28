# FinTrack frontend

React 18 + TypeScript + Vite single-page app for the FinTrack Spring Boot backend.
Runtime dependencies are deliberately minimal: `react-router-dom` and `lucide-react`. Charts are plain CSS bars.

## Folder layout (after extracting the ZIP)

```
fintrack-main\
  backend\    Spring Boot API  (port 8083)
  frontend\   this app         (port 5173)
  CHANGES.md  what changed in this version + verification report
```

## Prerequisites (Windows)

- Node.js 20+ (tested target: 24.x) and npm
- Java 21 and the included Maven wrapper (`backend\mvnw.cmd`)
- PostgreSQL running locally with a database named `fintrack` (user `postgres`, port 5432)

## 1. Start PostgreSQL

Make sure the Windows service is running (name varies, e.g. `postgresql-x64-16`):

```powershell
Get-Service *postgres*          # check status
Start-Service postgresql-x64-16 # start it (use the name shown above; may need an Administrator shell)
```

Create the database once if it does not exist: `psql -U postgres -c "CREATE DATABASE fintrack;"`. Flyway creates the tables on first backend start.

## 2. Configure backend secrets (never commit them)

The backend reads two **environment variables**. Set them for the current PowerShell window only:

```powershell
$env:DB_PASSWORD = "your-postgres-password"

# JWT_SECRET must be Base64 text that decodes to at least 32 bytes. Generate one:
$bytes = New-Object byte[] 48
[Security.Cryptography.RandomNumberGenerator]::Create().GetBytes($bytes)
$env:JWT_SECRET = [Convert]::ToBase64String($bytes)
```

Use the same `JWT_SECRET` each time you restart the backend, otherwise existing tokens stop working (you would simply sign in again). Do not paste real values into files that are committed.

## 3. Start the backend

```powershell
cd fintrack-main\backend
.\mvnw.cmd spring-boot:run
```

It listens on http://localhost:8083.

## 4. Start the frontend

In a second PowerShell window:

```powershell
cd fintrack-main\frontend
npm install        # first time only; this also creates package-lock.json (see note below)
npm run dev
```

Open http://localhost:5173.

> **Lockfile note:** this ZIP was prepared in an environment without registry access, so `package-lock.json` could not be generated. Your first `npm install` creates it. Commit that file afterwards so installs are reproducible, and use `npm ci` in CI.

## How the `/api` proxy works

The backend has no CORS configuration. In development the browser only talks to Vite (same origin, `localhost:5173`); `vite.config.ts` forwards every request starting with `/api` to `http://localhost:8083`. Therefore `VITE_API_BASE_URL` stays empty. If you ever serve the built app from a different origin than the API, the backend would need explicit CORS rules (not done here).

## Quality commands

```powershell
npm run typecheck   # tsc --noEmit
npm test            # Vitest (jsdom + React Testing Library)
npm run build       # typecheck + production build into dist\
```

## Manual test checklist

1. Register, then sign in. A wrong password shows "Invalid email or password."
2. Accounts → New account (e.g. INR, opening balance `1000.00`).
3. Transactions → record an income and an expense; balances on Accounts update.
4. Try an expense larger than the balance: it is blocked with the available amount.
5. Create a second account in the same currency and record a **transfer** (category must be of type *Both*; system categories are).
6. Reverse a transaction: a "Reversal" row appears and the original shows a "Reversed" badge with no second Reverse button.
7. Categories → create, rename, delete a custom category; deleting one used by a transaction is refused.
8. Analytics → pick a range; totals are per currency; transfers and reversed transactions are excluded.
9. Reconciliation → download the CSV template, upload it for an account, filter rows by status. Confirm that Accounts and Transactions did **not** change.
10. Wait 15 minutes (or stop the backend and restart it with a different `JWT_SECRET`) and click around: you are returned to the login page with a "session expired" notice.

## API contracts used

| Area | Endpoint |
|---|---|
| Auth | `POST /api/auth/register`, `POST /api/auth/login` → `{token, tokenType, expiresIn}` |
| Accounts | `GET/POST /api/accounts` (no update/delete exists) |
| Categories | `GET/POST /api/categories`, `PUT/DELETE /api/categories/{id}` (system categories read-only) |
| Transactions | `GET/POST /api/transactions` (**`Idempotency-Key` header required**), `POST /api/transactions/{id}/reverse` |
| Analytics | `GET /api/analytics/summary?from=YYYY-MM-DD&to=YYYY-MM-DD` (both dates inclusive; income/expense only) |
| Reconciliation | `POST /api/reconciliation/imports?accountId=<uuid>` (multipart field `file`), `GET /imports`, `GET /imports/{id}/rows` |

## Design decisions and limits

- **Money is exact.** Amounts are kept as decimal strings and calculated with `BigInt` (`src/money.ts`). Before `JSON.parse`, known money fields in responses are quoted so they never become doubles; they are sent back as plain JSON numbers, so the backend contract is unchanged. Backend values are NUMERIC(19,4): at most 4 decimals. The frontend never computes authoritative balances; the Overview "total balance" only adds up the balances the backend returned, per currency.
- **Currency display** always shows the ISO code (`1,250.50 INR`) instead of a symbol, and amounts in different currencies are never added.
- **Idempotency.** One key per logical request (`src/idempotency.ts`). Retrying the *same* payload (e.g. after a lost response) reuses the key and the backend replays the original result; changing any field creates a new key; success resets it.
- **Session.** JWT in `sessionStorage` (15 min, no refresh endpoint exists, so the user is signed out at expiry). This is readable by any script running on the page (XSS) and is *not* equivalent to an HttpOnly cookie; moving it to `localStorage` would be no safer and would persist it longer. 401 on an authenticated call ends the session; 403 does not.
- **Error messages.** The backend has no global exception handler, so most error bodies carry no readable message; the UI shows status-based text and validates client-side using the backend's rules. Reconciliation errors (`{"error": ...}`) are shown verbatim.
- Multipart limit is Spring's default **1 MB** (not overridden); the UI enforces it.

## Troubleshooting

| Symptom | Likely cause / fix |
|---|---|
| "Cannot reach the FinTrack server" | Backend not running or still starting; check port 8083. |
| Backend fails at startup with `Could not resolve placeholder 'JWT_SECRET'` / `DB_PASSWORD` | Set the environment variables in the *same* PowerShell window that runs `mvnw`. |
| Backend: `WeakKeyException` / invalid Base64 | `JWT_SECRET` must be Base64 of ≥ 32 bytes; use the generator above. |
| Backend: password authentication failed | Wrong `DB_PASSWORD` or PostgreSQL user. |
| Everything redirects to login | Token expired (15 min) or backend restarted with a different `JWT_SECRET`. |
| `npm install` errors about peer dependencies | Delete `node_modules`, run `npm install` again; report the exact message. |
| Browser shows CORS errors | `VITE_API_BASE_URL` is set to an absolute URL; clear it and use the proxy. |
