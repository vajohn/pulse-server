# Microsoft Login Flow (Spring Boot + Azure AD)

## 1. Initiate Login (Frontend)
- Frontend calls `/api/auth/login` (GET).
- Backend responds with a 302 redirect to Microsoft login (Azure AD OAuth2 endpoint).

## 2. Microsoft Authentication
- User authenticates with Microsoft.
- Microsoft redirects to `/login/oauth2/code/azure` (backend callback).

## 3. Backend Callback Handling
- Spring Security processes the callback.
- On success, user is redirected to `/login/success`.
- `/login/success` endpoint extracts user info, persists/updates in DB, and can redirect to frontend (e.g., `/auth/callback?token=...`).

## 4. Frontend Receives Auth State
- Frontend handles `/auth/callback` route, extracts token/state, and updates UI.

---

## Endpoints
- `GET /api/auth/login` — Initiates login, redirects to Microsoft.
- `GET /login/success` — Handles post-login, persists user, can redirect to frontend.
- `GET /auth/callback` — (Frontend) Handles final redirect and token processing.

---

## Example Frontend Flow
```js
// React/Angular/Vue pseudo-code
window.location.href = '/api/auth/login';
// After login, handle /auth/callback?token=... in your router
```

