# Deploying Coffold

Coffold ships as **one jar** that serves both the React app and the API,
run in Docker next to its own Postgres, behind the host's nginx. This
matches how Krugger's Tavern runs on the shared Lighthouse box, and lives
alongside it without touching it.

| | Tavern (existing) | Coffold |
|---|---|---|
| Folder | `/opt/kruggers-tavern/` | `/opt/coffold/` |
| App port (localhost only) | 8090 | **8095** |
| Database | own `postgres:16` container | own `postgres:16` container |
| Address | tavern.veteryr.online | **coffold.veteryr.online** (suggested) |

## 1. Build (on your PC)

```powershell
.\build.ps1
```

Builds the React app, runs all backend tests (Docker must be running), and
writes `deploy\coffold.jar`.

## 2. First time on the server

1. **DNS:** add an `A` record `coffold.veteryr.online → 43.156.150.155`.
2. **Check port 8095 is free:** `ss -ltn | grep 8095` should print nothing.
3. **Copy the deploy folder up** (from the repo root on your PC):
   ```powershell
   scp -i ..\KruggerTavern\.ssh\tavern_access -r deploy ubuntu@43.156.150.155:/tmp/coffold
   ssh -i ..\KruggerTavern\.ssh\tavern_access ubuntu@43.156.150.155 "sudo mv /tmp/coffold /opt/coffold && sudo chown -R ubuntu /opt/coffold"
   ```
4. **Secrets:** on the server, `cp /opt/coffold/.env.example /opt/coffold/.env`
   and set `POSTGRES_PASSWORD` (`openssl rand -base64 24`).
5. **Start it:** `cd /opt/coffold && chmod +x redeploy.sh && ./redeploy.sh`
6. **nginx + HTTPS:**
   ```bash
   sudo cp /opt/coffold/nginx-coffold.conf /etc/nginx/sites-available/coffold
   sudo ln -s /etc/nginx/sites-available/coffold /etc/nginx/sites-enabled/coffold
   sudo nginx -t && sudo systemctl reload nginx
   sudo certbot --nginx -d coffold.veteryr.online
   ```

## 3. Every later release

```powershell
.\build.ps1
scp -i ..\KruggerTavern\.ssh\tavern_access deploy\coffold.jar ubuntu@43.156.150.155:/opt/coffold/coffold.jar
ssh -i ..\KruggerTavern\.ssh\tavern_access ubuntu@43.156.150.155 "/opt/coffold/redeploy.sh"
```

Database changes need no manual step: Flyway applies any new
`V<n>__*.sql` migrations when the app starts.

## 4. Bring your Tavern ledger across

Log in to Coffold, open **Settings → Import from Krugger's Tavern**, and
follow the three steps there (it reads Tavern's `/api/kv` export).

## Backups

```bash
docker compose -f /opt/coffold/docker-compose.yml exec -T db pg_dump -U coffold coffold > coffold-$(date +%F).sql
```
