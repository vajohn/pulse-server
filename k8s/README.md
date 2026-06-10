# Pulse KPS manifests

Apply order: configmap → secret (created via CLI, see secret.example.yaml) → deployment → service → ingressroute.
Namespaces: `pulse` (prod, service9), `pulse-dev` (dev, service8). Redis DB 2 (prod) / 3 (dev). Postgres `pulse` / `pulse_dev`.
Images: `172.26.34.202:5000/pulse-server:{stable,dev}`, `imagePullPolicy: Always`. Deploy via `/deploy pulse server` / `/deploy pulse-dev server`.

External routing: HAProxy on `kps-lb` (SSL passthrough) → SNI ACL `service8`/`service9` → `bk_k3s_https` (NodePort 30443) → Traefik (`shared-infra`) → per-namespace IngressRoute (TLS `kps-tls-cert`, wildcard `*.nexedge.ae`) → `pulse-server:8080`.

## Adding the future admin portal (web)
The Flutter mobile app ships via app stores — NOT here. A web admin portal is added like xdeal's `dashboard`:
1. Build a web bundle into an image `172.26.34.202:5000/pulse-frontend:{stable,dev}`.
2. Add `pulse-frontend` Deployment + Service (port 80) per namespace.
3. Change the IngressRoute to path-split (Traefik matches most-specific first):
   - `Host(`service9.nexedge.ae`) && PathPrefix(`/api`)` → `pulse-server:8080`
   - `Host(`service9.nexedge.ae`)` → `pulse-frontend:80`
4. Add a `dashboard` component (`type: vite-pwa` or static) to the `pulse`/`pulse-dev` blocks in `~/.claude/commands/deploy.md`; the `vite-pwa` pipeline already exists.
No HAProxy or DNS change is needed — service8/9 already terminate at Pulse.
