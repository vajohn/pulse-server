# Pulse on k2 (air-gapped, `tasdiq-dev`) — deployment runbook

Deploys Pulse co-located with `x4auth-server` so X4Auth is reached in-cluster
(`http://x4auth-server.tasdiq-dev.svc.cluster.local:5000`). Azure/Entra is **off**
here (no internet) — X4Auth is the sole auth path. Chart mirrors `PI/x4mahara`.

Validated: `helm lint` clean; `helm template` renders the correct in-cluster
`X4AUTH_BASE_URL`, the `x4auth-allow-pulse` Istio policy, and the pulse-server workload.

## ✅ Azure-conditional change — DONE (profile-based)
The k2-boot prerequisite is implemented. `SPRING_PROFILES_ACTIVE=k2` (set in values.yaml)
excludes the `azure` profile, so the Azure OAuth2 client config (`application-azure.yaml`),
the AAD security chain (`oauthFilterChain` is `@Profile("azure")`), and the Azure login
controllers (`OAuth2LoginController`, `AuthInitiateController` — both `@Profile("azure")`)
are all absent. A `noAzureFilterChain` (`@Profile("!azure")`) provides the catch-all. Result:
no `login.microsoftonline.com` resolution at startup; X4Auth is the sole login path.

Default/KPS/local/test behavior is unchanged (default profile is `azure`; `local` group adds
it). Verified: compiles clean, full suite 538 pass.

⚠️ **Still boot-verify before prod:** the live full-context boot under the `k2` profile has not
been run (project's `PulseApplicationTests` is `@Disabled`; needs Postgres/Redis). Before/at
first k2 deploy, confirm the pod reaches ready and `/api/auth/x4auth/config` → `{"enabled":true}`,
with no Microsoft call in logs. Locally: `SPRING_PROFILES_ACTIVE=k2` + DB/Redis reachable + no `AZURE_*`.

## Other prerequisites
- **Register a `pulse` OAuth client** in `x4auth-admin-portal` (tasdiq-dev) → gives `X4AUTH_CLIENT_SECRET`.
- **SNI host = `service15.nexedge.ae`** (assigned 2026-06-09; free per kps-lb HAProxy review — used: KPS 6-10, k2 1-5/11-14/16-20; 15 is the gap in the k2 block). Already set in `values.yaml` (`ingress.host` + `CORS_ALLOWED_ORIGINS`). **One LB edit required** — add service15 to the `sni_k2` ACL in `/etc/haproxy/haproxy.cfg` on `kps-lb` (172.26.34.34), so passthrough TCP routes it to `bk_k2_https` (k2 Istio ingressgateway). Needs root on kps-lb:
  ```
  # in frontend ft_https, alongside the other k2 ACLs:
  acl sni_k2 req.ssl_sni -i service15.nexedge.ae
  # then:  sudo haproxy -c -f /etc/haproxy/haproxy.cfg  &&  sudo systemctl reload haproxy
  ```
  ⚠️ Do NOT touch the `service7` (xdeal prod) routing. DNS for `service15.nexedge.ae` must resolve to the LB (the nexedge.ae service1-30 hosts are registered).
- **Confirm the k2 Postgres + Redis** reachable from `tasdiq-dev` → set `DB_HOST` / `REDIS_HOST`.
- Build/push `pulse-server` to `k2-registry` (172.26.34.205:5000/infra/pulse-server:<tag>) — see the `k2-deploy` skill (Dockerfile.k3s, native-arch, rsync `/node_modules` trap do NOT apply to the Spring/Gradle image; build the jar + a slim JRE image).

## Deploy
```bash
# 0. Create the Secret out-of-band (NOT in git) — see pulse-secrets.example.yaml
kubectl apply -f pulse-secrets.example.yaml   # after filling REPLACE_ME values

# 1. Adopt into k2-gitops: copy this chart to charts/pulse + values/pulse/dev.yaml,
#    add the Argo CD Application (argocd-application.yaml), commit + push (no --no-verify).

# 2. Bump/deploy the image tag via GitOps
bin/promote.sh pulse dev "v0.1-x4auth-$(git rev-parse --short HEAD)"
#    (add a `pulse` entry to bin/promote.sh's service→values map: chart=pulse, path=.image.tag)

# 3. tasdiq-dev does not auto-sync — trigger from k2-master:
kubectl patch application pulse-dev -n argocd --type merge -p \
  '{"operation":{"initiatedBy":{"username":"admin"},"sync":{"revision":"HEAD","syncOptions":["ServerSideApply=true"]}}}'
kubectl rollout status deploy/pulse-server -n tasdiq-dev --timeout=180s
```

## Verify (L2)
```bash
kubectl -n tasdiq-dev get pods -l app=pulse-server
kubectl -n tasdiq-dev exec deploy/pulse-server -- \
  curl -s localhost:8080/api/auth/x4auth/config         # {"enabled":true}
# Full push round-trip with a real Tasdiq device against email of a test user.
```
Then L3 (Flutter `--flavor development` pointed at the assigned host) → and only after
L2+L3 are green, remove Azure entirely + decommission `service9`.
