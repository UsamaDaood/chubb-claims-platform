# Frontend

Angular (standalone components, signals, zoneless). See the root README for how to run it.

- `npm start` serves on :4200 and proxies `/api` to the backend on :8080 (`proxy.conf.json`)
- `npm run build` produces a production bundle
- Structure: `core/` (api, auth, models), `shared/` (table, badge, notification bell), `features/` (login, claimant, staff, claim-detail)
