# Deploying the backend so testers on other devices can reach it

This gets the backend, Postgres, Redis, and file storage running on Railway
(free tier covers this comfortably for testing), then walks through building
a release APK that points at the deployed backend instead of `localhost`.

Everything below uses Railway's dashboard — no code changes are needed beyond
what's already in this repo (`backend/Dockerfile`, `.dockerignore`, and the
Redis/storage env-var support already added to `application.yml`).

## 1. Push this repo to GitHub

Railway deploys from a connected GitHub repo. If this repo isn't on GitHub
yet:

```
cd "path/to/Gotogether_build"
git init                      # skip if already a git repo
git add .
git commit -m "Ready for deployment"
gh repo create gotogether --private --source=. --push
```

(No `gh` CLI? Create an empty repo on github.com, then `git remote add origin <url>` and `git push -u origin main`.)

Double-check `.gitignore` excludes `.env` before this — it already does.

## 2. Create the Railway project

1. Go to railway.com → sign in with GitHub → **New Project** → **Deploy from GitHub repo** → pick this repo.
2. Railway will try to build the repo root. Since the backend lives in `backend/`, open the new service's **Settings → Build**, and set **Root Directory** to `backend`. It will then auto-detect `backend/Dockerfile`.
3. Rename the service to something clear, e.g. `gotogether-backend`.

## 3. Add Postgres, Redis, and a Storage Bucket

Still inside the same Railway project, click **Create** on the canvas three times:

- **Database → PostgreSQL**
- **Database → Redis**
- **Bucket** (Railway's own S3-compatible object storage — replaces MinIO for
  this deployment, no code changes needed since `StorageClientConfig` already
  talks plain S3-API)

Each becomes its own service with its own auto-generated credentials.

## 4. Set environment variables on the backend service

Open `gotogether-backend` → **Variables** tab → add these. Railway's
`${{ServiceName.VAR}}` syntax pulls a value live from another service in the
same project, so you don't copy/paste secrets by hand:

```
DB_URL=jdbc:postgresql://${{Postgres.PGHOST}}:${{Postgres.PGPORT}}/${{Postgres.PGDATABASE}}
DB_USERNAME=${{Postgres.PGUSER}}
DB_PASSWORD=${{Postgres.PGPASSWORD}}

REDIS_HOST=${{Redis.REDISHOST}}
REDIS_PORT=${{Redis.REDISPORT}}
REDIS_PASSWORD=${{Redis.REDISPASSWORD}}
REDIS_SSL_ENABLED=false

STORAGE_BUCKET=${{Bucket.BUCKET}}
STORAGE_ENDPOINT=${{Bucket.ENDPOINT}}
STORAGE_PUBLIC_ENDPOINT=${{Bucket.ENDPOINT}}
STORAGE_REGION=${{Bucket.REGION}}
STORAGE_ACCESS_KEY=${{Bucket.ACCESS_KEY_ID}}
STORAGE_SECRET_KEY=${{Bucket.SECRET_ACCESS_KEY}}

# Railway Buckets don't support public-read buckets (unlike local MinIO),
# so photo URLs must be served through the backend's own presigned-redirect
# endpoint instead of pointing straight at the bucket. Set these two —
# api-public-base-url is the backend's OWN public URL (set it after step 5,
# once you know the generated domain — Railway lets you set variables before
# or after a domain exists, just come back and fill this in).
STORAGE_SERVE_VIA_PROXY=true
API_PUBLIC_BASE_URL=https://<this-service's-generated-domain-from-step-5>

JWT_SECRET=<paste a fresh random 32+ byte string here — NOT the dev default>
PEXELS_API_KEY=<your Pexels key>
```

Exact variable names on the Postgres/Redis services can differ slightly by
Railway's current template — open each service's own **Variables** tab first
and confirm the names before referencing them (commonly `PGHOST`/`PGPORT`/
`PGDATABASE`/`PGUSER`/`PGPASSWORD` for Postgres and `REDISHOST`/`REDISPORT`/
`REDISPASSWORD` for Redis).

For `JWT_SECRET`, generate one instead of typing something by hand:

```
openssl rand -base64 48
```

(Railway Bucket objects are private by default, unlike local MinIO — the
`STORAGE_SERVE_VIA_PROXY`/`API_PUBLIC_BASE_URL` pair above is exactly what
handles that: the backend now presigns a fresh short-lived read URL on every
image load instead of relying on the bucket being public.)

## 5. Deploy

Railway auto-deploys on push once the service is wired up. Trigger the first
build from the service's **Deployments** tab if it doesn't fire automatically.
Watch the build logs — first build takes a few minutes (Maven dependency
download + compile).

Once it's live, open **Settings → Networking** and click **Generate Domain**
to get a public URL, e.g. `https://gotogether-backend-production.up.railway.app`.

Now go back to **Variables** and fill in the real `API_PUBLIC_BASE_URL` with
that domain (it couldn't be known until this step). Saving a variable
triggers a redeploy, which is expected.

## 6. Point the app at it and build the APK

```
cd "path/to/Gotogether_build/mobile"
flutter build apk --dart-define=API_BASE_URL=https://gotogether-backend-production.up.railway.app --release
```

Output: `mobile/build/app/outputs/flutter-apk/app-release.apk` — send this
file to testers directly (Drive, email, whatever); they just need "install
from unknown sources" enabled once.

## Ongoing

Every `git push` to the connected branch triggers a new Railway build
automatically. No further steps needed for future backend changes during
testing.
