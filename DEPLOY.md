# Deploying KrugWorld

KrugWorld ships as **one jar** that serves both the React app and the API,
run in Docker next to its own Postgres, behind the host's nginx. This
matches how Krugger's Tavern runs on the shared Lighthouse box, and lives
alongside it without touching it.

| | Tavern (existing) | KrugWorld |
|---|---|---|
| Folder | `/opt/kruggers-tavern/` | `/opt/krugworld/` |
| App port (localhost only) | 8090 | **8096** |
| Database | own `postgres:16` container | own `postgres:16` container |
| Address | tavern.veteryr.online | **https://krugworld.veteryr.online** (live since 2026-10-15) |

## 1. Build (on your PC)

```powershell
.\build.ps1
```

Builds the React app, runs all backend tests (Docker must be running), and
writes `deploy\krugworld.jar`.

## 2. First time on the server

1. **DNS:** add an `A` record `krugworld.veteryr.online → 43.156.150.155`.
2. **Check port 8096 is free:** `ss -ltn | grep 8096` should print nothing.
3. **Copy the deploy folder up** (from the repo root on your PC):
   ```powershell
   scp -i $HOME\.ssh\krugworld_lighthouse -r deploy ubuntu@43.156.150.155:/tmp/krugworld
   ssh -i $HOME\.ssh\krugworld_lighthouse ubuntu@43.156.150.155 "sudo mv /tmp/krugworld /opt/krugworld && sudo chown -R ubuntu /opt/krugworld"
   ```
4. **Secrets:** on the server, `cp /opt/krugworld/.env.example /opt/krugworld/.env`
   and set `POSTGRES_PASSWORD` (`openssl rand -base64 24`). For the Steward,
   set `OPENAI_API_KEY` (the default provider; `KRUGWORLD_STEWARD_PROVIDER=anthropic`
   plus `ANTHROPIC_API_KEY` switches its brain to Claude). After editing `.env`, apply it with
   `docker compose up -d app` (a plain `restart` does not re-read `.env`).
   Check the file stays one line per key: a pasted key with a stray newline
   breaks the header it's sent in.
5. **Start it:** `cd /opt/krugworld && chmod +x redeploy.sh && ./redeploy.sh`
6. **nginx + HTTPS:**
   ```bash
   sudo cp /opt/krugworld/nginx-krugworld.conf /etc/nginx/sites-available/krugworld
   sudo ln -s /etc/nginx/sites-available/krugworld /etc/nginx/sites-enabled/krugworld
   sudo nginx -t && sudo systemctl reload nginx
   sudo certbot --nginx -d krugworld.veteryr.online
   ```

## 3. Every later release

```powershell
.\build.ps1
scp -i $HOME\.ssh\krugworld_lighthouse deploy\krugworld.jar ubuntu@43.156.150.155:/opt/krugworld/krugworld.jar
ssh -i $HOME\.ssh\krugworld_lighthouse ubuntu@43.156.150.155 "/opt/krugworld/redeploy.sh"
```

Database changes need no manual step: Flyway applies any new
`V<n>__*.sql` migrations when the app starts.

## Password reset by email (optional)

Without this, a forgotten passphrase can only be fixed by hand in the
database. To turn on "Forgot your passphrase?":

1. Pick a mailbox to send from. With Gmail: turn on 2-step verification, then
   create an **App password** at <https://myaccount.google.com/apppasswords>.
   Use that 16-character password, never your real one.
2. On the server, add to `/opt/krugworld/.env` (one line per key, no stray
   newlines):
   ```
   SMTP_HOST=smtp.gmail.com
   SMTP_PORT=587
   SMTP_USERNAME=you@gmail.com
   SMTP_PASSWORD=the-app-password
   MAIL_FROM=you@gmail.com
   PUBLIC_URL=https://krugworld.veteryr.online
   ```
3. Apply it: `cd /opt/krugworld && docker compose up -d app`.
4. Each person adds their email in **Settings → Recovery email** (new accounts
   can add it when registering). Accounts with no email can't use the reset.

Port 587 is used on purpose: Tencent Cloud blocks outbound port 25.

## Admin account (fixing data after a bug)

An admin gets **Admin** in the menu (`/admin`): pick a user, then correct their
entries or set an account's balance. The app can't grant this itself, so a
stolen login or a bug in the API can't create an admin. Register the account
normally, then on the server:

```bash
cd /opt/krugworld && docker compose exec -T db psql -U krugworld krugworld -c "UPDATE app_user SET is_admin = true WHERE lower(username) = lower('YOUR_USERNAME')"
```

Set `is_admin = false` to remove it. Either takes effect on the next request,
with no restart or new login; reload the page to see the menu change.

- **Wrong entry:** edit or delete that entry. This fixes money in/spent for its
  month, and the total follows.
- **Wrong total with no entry to blame:** use the pencil on the account. It
  moves the opening balance, so no month's figures change.
- Admin corrections earn no XP, and each one is saved with its before and after
  values (`admin_audit`, shown as the page's Change log). Back up first for a
  big repair (see Backups).

## Phone notifications (Web Push)

Nothing to configure. On first use the app generates its VAPID key pair and
keeps it in the `app_secret` table (row `vapid-keys`). **Never delete that
row**: every phone subscribed with its public key, and a new key silently
stops notifications to all of them until each one turns them on again.

The server must be able to make outbound HTTPS calls to the push services
(`fcm.googleapis.com` for Chrome on Android). To turn it on: open the site on
the phone, **Settings → Notifications → Turn on for this device**, then
**Send a test**. Reminders are checked every 5 minutes (see `ReminderJob`).

## 4. Bring your Tavern ledger across

Log in to KrugWorld, open **Settings → Import from Krugger's Tavern**, and
follow the three steps there (it reads Tavern's `/api/kv` export).

## Backups

```bash
docker compose -f /opt/krugworld/docker-compose.yml exec -T db pg_dump -U krugworld krugworld > krugworld-$(date +%F).sql
```

## Notes from the first real deploy (2026-10-15)

- **SSH key:** `~/.ssh/krugworld_lighthouse` (ed25519, made on the Windows PC). Its
  public half is in `ubuntu`'s `~/.ssh/authorized_keys` on the server. On a new PC,
  make a new key and add its `.pub` line the same way (Lighthouse console → OrcaTerm).
- **Building on Windows:** if `build.ps1` stops on a JVM warning ("Sharing is only
  supported for boot loader classes…"), that's PowerShell 5.1 treating any stderr as
  an error, not a failed build. Run the same steps from Git Bash instead:
  `cd frontend && npm run build`, then `cd backend && ./mvnw -q package`, then copy
  `backend/target/krugworld-0.1.0.jar` to `deploy/krugworld.jar`. Set `JAVA_HOME` to an
  installed JDK if the Tavern's portable one isn't there.
- **Swap the jar safely:** upload as `krugworld.jar.new`, then `mv` it over the old one
  and run `redeploy.sh`, so a half-finished upload is never the jar that starts.
- **nginx for Desk Gaby:** `client_max_body_size 2m` (voice clips) and
  `proxy_buffering off` on `/api/device/voice/speech/` so her voice streams instead of
  arriving all at once. Both are in `nginx-krugworld.conf`; certbot added the 443 block.
- **Fresh DNS names:** a home router may cache "no such name" for a while after the
  record is created. Public resolvers (`nslookup krugworld.veteryr.online 8.8.8.8`)
  show the truth; the desk device falls back to them on its own.
