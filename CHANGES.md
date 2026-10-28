# FinTrack — v2 change summary and verification report

## Confirmed defects fixed (checked against the source)

| # | Defect | Fix |
|---|---|---|
| 1 | `TransactionResponse` had a private `reversalOfTransactionId` field but **no getter**, so Jackson never serialized it. Reversal links were guessed from the description text. | **Backend:** added `getReversalOfTransactionId()` (one additive method, `backend/.../transaction/dto/TransactionResponse.java`). **Frontend:** uses the field when present, keeps the description fallback for older backends. Reversal rows now say what they reverse. |
| 2 | Money handled as JS `number` (doubles): precision loss > 2^53, float comparisons for insufficient-funds and sorting. | New `src/money.ts`: decimal strings + `BigInt` arithmetic, exact formatting, strict input validation. Responses are quoted before `JSON.parse`; requests send plain JSON numbers (backend contract unchanged). Amounts are canonicalised (`007` → `7.00`) so they are always valid JSON. |
| 3 | A manual logout after an expired session showed a stale "session expired" notice on the next login page; a sleeping tab could stay "signed in" past expiry. | `logout()` clears the flag; expiry is re-checked when the tab becomes visible. |
| 4 | `crypto.randomUUID()` is undefined over plain `http://<LAN-IP>`, which would crash transaction creation. | `newId()` with a `getRandomValues` fallback. |
| 5 | Double clicks could start two requests before React re-rendered (`busy` state is async). | Synchronous `useSubmitLock` on all create/update/delete/reverse/upload actions. |
| 6 | No request timeout; a hung request spun forever. | 30 s timeout (120 s for uploads) → "server took too long"; for transactions the message explains that retrying is safe. |
| 7 | Dialog had no focus management; closed mobile sidebar was still keyboard-focusable; errors not linked to inputs; bar chart had no values. | Focus moves into dialogs, is trapped, restored on close, `aria-labelledby`; hidden sidebar not focusable; `Field` sets `aria-invalid`/`aria-describedby`; skip link; table captions/scopes; income/expense bars show amounts with currency. |
| 8 | Reconciliation rows showed only a truncated transaction id for matches. | Shows the matched transaction's description (falls back to id if the lookup fails). |

Unchanged on purpose: visual identity, routes, API paths, backend security, migrations, all database schema. Reconciliation still never creates transactions or changes balances (the UI says so after every import).

## Verified facts about the backend (source inspection)

- Replaying the same `Idempotency-Key` with the same payload returns the original transaction (`TransactionService`, `return previous;`), so retries after a lost response are safe. Same key with a different payload is rejected.
- All 11 seeded system categories are type `BOTH`, so transfers work out of the box.
- No global exception handler / `server.error.include-message` → most error bodies have no readable message. **Not changed**: enabling it would expose exception text on 5xx responses, and I cannot run the backend here to test it. Optional improvement for you to consider.

## Verification report

| Check | Result | Evidence or limitation |
|---|---|---|
| Dependency installation | **Blocked** | The sandbox has no registry access (`npm view react` times out). No `node_modules` and **no `package-lock.json` could be generated**. Your first `npm install` creates it. |
| TypeScript typecheck (`npm run typecheck`) | **Blocked** for the full project | `@types/react`, react-router, lucide-react unavailable. **What I did run:** `tsc 6.0.3 --strict` on `money`, `idempotency`, `txForm`, `api` and their 4 test files → exit 0. A smoke check of *all* files with the three third-party modules stubbed as `any` found no genuine errors (only stub artefacts). This is weaker than the real typecheck. |
| Frontend unit tests | **Partly executed** | 44 tests in 4 files (`money`, `idempotency`, `txForm`, `api`) ran and **passed 44/44**, but under a small Vitest-compatible shim over Node's `node:test` (real source and assertions, not Vitest itself). The React Testing Library tests in `src/test/` (auth form validation, route protection, accounts page states, transaction form + idempotency, reversed badge; ~13 tests) were written but **never executed**. They may need small fixes. |
| Production build (`vite build`) | **Blocked** | Vite not installable offline. |
| Backend tests | **Not run** | Backend changed (one getter). No Maven repository/PostgreSQL offline. The change is additive and uses only `java.util.UUID`; please run `.\mvnw.cmd test` (previously reported 119 passing). |
| Login and registration | Not verified by me | Source reviewed; component tests written, unrun. **You confirmed login works locally.** |
| Account creation | Not verified by me | **You confirmed it works.** Opening balance is now sent via the exact-decimal path — please recheck once. |
| Income and expense | Not verified by me | **You confirmed they work.** Payload now carries `amount` from a canonical string; recheck once. |
| Transfers and reversals | **Not verified** | Logic unit-tested (category/currency/funds rules); end-to-end needs you. |
| Categories and analytics | **Not verified** | Source reviewed only. |
| CSV reconciliation | **Not verified** | Source reviewed; multipart request shape unit-tested against a mock. |
| Responsive and accessibility checks | **Not verified** | No browser run. Changes were made by code review only. |

## Still to do on your machine

1. `cd frontend; npm install; npm run typecheck; npm test; npm run build` — send me any errors verbatim.
2. `cd backend; .\mvnw.cmd test`.
3. Walk through the checklist in `frontend/README.md`, especially: income/expense (regression), transfer, reversal (check the response JSON now contains `reversalOfTransactionId`), reconciliation upload, 15-minute expiry.
4. Look at what a failed transaction (e.g. insufficient funds forced past the UI) returns in the browser Network tab; if it has a `message`, the UI could show it.

## Known limitations

- `sessionStorage` JWT is readable by scripts on the page (XSS); no refresh tokens exist.
- Not production-ready: untested end to end here, no CORS/production deployment setup, no CI.
- Backend NUMERIC(19,4): the UI accepts at most 4 decimals and 15 integer digits.
- Upload shows a spinner, not a progress bar (`fetch` has no upload progress).
- Login/Register forms use the older `busy` flag guard (the lock is on all other actions).
