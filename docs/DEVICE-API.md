# Desk Gaby: device API

What the ESP32 firmware talks to. Everything is under `/api/device/`, and
except for pairing every call needs `Authorization: Bearer <token>`. No
cookies and no CSRF token are involved. Errors are the usual `{code, message}` JSON.

## 1. Pairing (once)

The user clicks **Settings → Desk Gaby → Pair a device** and gets a six-digit
code, valid for 10 minutes. The device sends it:

```
POST /api/device/pair
Content-Type: application/json

{"code": "482913", "name": "Desk Gaby"}
```

```json
{"deviceId": 3, "token": "kgd_…", "username": "Krugger"}
```

Store the token in NVS (flash). A wrong or expired code returns `401 bad_code`.
Each wrong guess also uses up one of every live code's 5 attempts, so the
digits can't be brute-forced. A 401 on any later call means the device was
removed in Settings, so go back to the pairing screen.

## 2. Glance screen

```
GET /api/device/summary
```

```json
{
  "date": "2026-10-12", "username": "Krugger",
  "level": 4, "title": "Squire", "xp": 610, "levelXp": 110, "levelXpNeeded": 300, "streak": 6,
  "gold": 2450.30, "spentThisMonth": 812.40,
  "budget": {"left": 388.00, "total": 1200.00, "daysLeft": 19, "tightest": "Food", "tightestLeft": 42.50},
  "calories": 1350, "calorieGoal": 2000,
  "workouts": [{"id": 7, "name": "Push-ups", "done": false}],
  "events": [{"date": "2026-10-13", "time": "14:30", "title": "Dentist"}],
  "voiceReady": true, "voiceRemaining": 38
}
```

`budget` is `null` when no budgets are set, and `time` is `null` for all-day events.
Polling once a minute is plenty.

Tick a workout (`false` unticks it):

```
POST /api/device/workouts/7/done
{"done": true}
```

## 3. Tap to talk

Record while the button is held, then send the WAV clip as the raw body
(not multipart). 16 kHz, 16-bit mono is ideal. The clip must be between about
0.2 s and 1 MB (about 30 s).

```
POST /api/device/voice
Content-Type: audio/wav

<wav bytes>
```

```json
{
  "transcript": "spent 12 on nasi lemak tadi",
  "reply": "'Tis written: twelve ringgit for nasi lemak.",
  "subtitle": "Logged RM 12.00 under Food.",
  "speech": "/api/device/voice/speech/5b0c…",
  "sampleRate": 24000,
  "remaining": 37
}
```

Show `subtitle` on screen (fall back to `reply` when it's null), then fetch
`speech` and play it as it streams in:

```
GET /api/device/voice/speech/5b0c…
→ application/octet-stream, X-Audio-Format: pcm_s16le;rate=24000;channels=1
```

The audio is raw PCM, ready for I2S to the ES8311. Each speech link works
**once**, within 2 minutes.

Gaby remembers the last few turns per device for 5 quiet minutes, so a
follow-up like "pukul 7" works after she asks "what hour?". To start over:

```
DELETE /api/device/voice/conversation
```

Each voice message counts against the same daily allowance as typed chat.
Errors: `400 clip_too_short`, `413 clip_too_long`, `422 heard_nothing`,
`429 user_limit`, `502 steward_failed`, `503 not_configured`.

## Try it without the device

```bash
CODE=482913   # from Settings
TOKEN=$(curl -s localhost:8085/api/device/pair -H 'Content-Type: application/json' \
  -d "{\"code\":\"$CODE\"}" | jq -r .token)
curl -s localhost:8085/api/device/summary -H "Authorization: Bearer $TOKEN" | jq
curl -s localhost:8085/api/device/voice -H "Authorization: Bearer $TOKEN" \
  -H 'Content-Type: audio/wav' --data-binary @question.wav | tee reply.json | jq
curl -s "localhost:8085$(jq -r .speech reply.json)" -H "Authorization: Bearer $TOKEN" -o reply.pcm
ffplay -f s16le -ar 24000 -ac 1 reply.pcm
```
