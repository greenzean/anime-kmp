# 0025: The server is hosted on the mini PC and published through Tailscale Funnel

## Status
Accepted (M2-25)

## Context
The release build needs an HTTPS server URL on the internet. Until now the server ran on the mini PC over plain HTTP on the LAN ([ADR 0014](0014-mini-pc-runs-server-and-deploys.md)). There is no own domain. The server is one Ktor container plus PostgreSQL for a handful of users. All prices were checked on 2026-11-10 from the pages listed; "not verified" means the page did not give the figure.

| Option | Cost | HTTPS | Postgres backups | Effort | Caveats |
|---|---|---|---|---|---|
| Mini PC + Tailscale Funnel | $0 | Yes, on a `<device>.<tailnet>.ts.net` name | Ours: `pg_dump` script | Low: already running | Funnel is in beta; ports 443, 8443 and 10000 only; bandwidth limit not published; uptime depends on home power and ISP |
| Fly.io (`shared-cpu-1x` 1 GB) | $6.70 per month plus $0.15 per GB volume; managed Postgres $38 per month plus $0.28 per GB | Yes, included | Volume snapshots, $0.08 per GB per month, first 10 GB free, daily, 5 days kept | Medium: `fly.toml`, self-run Postgres | Dedicated IPv4 $2 per month |
| Hetzner Cloud CX23 | EUR 5.99 per month, EUR 1.198 for backups (excluding VAT) | No: Caddy plus a domain | Whole-server daily images, 7 slots, not Postgres-aware | High: Docker, TLS, updates | All plans showed "currently unavailable" |
| Mini PC + Cloudflare named tunnel | Domain price not verified | Yes | Ours | Low | Needs a domain added to Cloudflare |
| Mini PC + Cloudflare quick tunnel | $0 | Yes | Ours | Low | URL changes at each start; no uptime guarantee; testing only |
| ngrok Free | $0 | Yes | Ours | Low | 1 GB and 20,000 requests per month; interstitial page on HTTP endpoints not tested |
| Railway Hobby | $5 per month with $5 of usage; my estimate for this setup $15-25 | Not verified | Volume backups, plan coverage not stated | Low | Estimate, not a quoted price |
| Render | Not verified | Not verified | Paid Postgres only | Low | Free web service sleeps; free Postgres is deleted after 44 days |
| DigitalOcean Droplet | $4 per month for 512 MiB, too small | No | Whole-disk, 20% or 30% of the price | High | 1 GiB and managed Postgres prices not verified |

Sources: tailscale.com/kb/1223/funnel, docs.fly.io/about/pricing, hetzner.com/cloud/cost-optimized, docs.hetzner.com/cloud/servers/backups-snapshots/overview, developers.cloudflare.com (quick tunnels and create-remote-tunnel), ngrok.com/pricing, railway.com/pricing, render.com/docs/free, digitalocean.com/pricing/droplets. None of the options were run; all were judged from docs.

## Decision
Use **the mini PC, published through Tailscale Funnel**, with backups we run ourselves.

- Two stacks run on the mini PC, each with its own Postgres volume, secrets and ports: dev (`anime-kmp`, LAN, deployed by `server-deploy`) and production (`anime-kmp-prod`, `127.0.0.1:8081`, deployed by hand with `scripts/deploy-prod.sh`). Production uses `compose.yaml` plus `compose.prod.yaml`, which sets `APP_ENV=prod` (JSON logs, introspection off) and requires `POSTGRES_PASSWORD`, `GOOGLE_CLIENT_ID` and `JWT_SECRET`; the server port comes from `SERVER_BIND` and `SERVER_PORT` in its `.env`. Production does not publish Postgres (`ports: !reset []`, which needs Compose 2.24 or newer; the box had an old 2023 plugin in `~/.docker/cli-plugins` that shadowed the installed 2.39.1, and it was renamed). The Docker Engine on the box is 28.3.3.
- `TRUST_FORWARDED_HEADERS=true` (set by the override) makes the server take the client address from the last `X-Forwarded-For` entry, the one the proxy appended, for rate limiting ([ADR 0024](0024-server-limits.md)). Without it every client would share the proxy's address. It is off by default and must be on only behind a proxy that sets the header.
- `scripts/backup-db.sh` writes a compressed `pg_dump` and deletes dumps older than 14 days; it runs daily from the mini PC's cron. `scripts/restore-db.sh` restores one into a scratch database and prints row counts of the live and restored databases.
- The release build takes its URL from `animekmp.serverUrl.release` (Gradle property or `local.properties`) and fails unless it is an `https` URL. The real name is not in the repository.

## Consequences
- Cost is $0. Availability is that of the mini PC, its power and its internet line, with no failover. If it goes down, the app keeps working offline and syncs later.
- Funnel is a beta feature with an unpublished bandwidth limit. If it becomes a problem, Fly.io is the next choice (about $7-10 per month); the compose files and Dockerfile are reusable.
- Terms of use of Funnel for a public API were not verified.
- Funnel sends the client's address in `X-Forwarded-For` and replaces a value the client sends itself (checked on 2026-11-10 with a request carrying a fake header), so `TRUST_FORWARDED_HEADERS=true` is safe behind it. Internet scanners reached the public name within minutes of enabling Funnel, so the endpoint must stay production-hardened (ADR 0024).
- Backups live on the same machine by default. Copying them off the box is not done; the dump directory can be synced to another place later.
- Log and backup retention for [ADR 0013](0013-account-deletion.md): server logs go to Docker's default log driver on the box and dumps are kept 14 days.
- Google sign-in needs an HTTPS origin ([ADR 0013](0013-account-deletion.md)); the Funnel name provides it.
- A backup was restored once into a scratch database on 2026-11-10: the live and restored databases both had 279 anime, 1 user and 279 list entries (this ran on the dev database, before production got its own). The script was run again on the new production database, which is empty, and the counts matched at zero.
