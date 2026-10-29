# WorkTrack — Backend Implementation Guide

This guide is for setting up the WorkTrack backend **on a different machine** from the one
that builds the Android app. Hand this file (and `firestore.rules`) to that machine and follow it
top to bottom.

---

## 1. What "the backend" is (read this first)

WorkTrack has **no custom server**. Don't write one. The app is offline-first:

- **Room (SQLite) on the phone is the source of truth.** Every feature works with no network.
- The backend is **Firebase**, used for exactly two things:
  1. **Google Sign-In** (Firebase Authentication, Google provider)
  2. **Cloud backup / restore** of the whole database (Cloud Firestore), daily + on demand
- Plus one Google Cloud API, used directly from the phone:
  3. **Google Tasks API**, read-only (`tasks.readonly`), to show Google Tasks on Today

The client code for all of this is already built (`app/src/main/java/com/wac/worktrack/cloud/`
and `calendar/GoogleTasks.kt`). Firebase switches itself on when `app/google-services.json`
exists at build time; without it the app runs fully offline.

So "building the backend" means: **create and configure a Firebase project, deploy the
security rules, and produce `google-services.json`** for the build machine.

**Deliverables back to the build machine:**

| Item | Where it goes |
|---|---|
| `google-services.json` | `app/google-services.json` (git-ignored, never commit it) |
| Your Firebase Auth UID (optional) | for locking the rules to just you (step 6) |

---

## 2. Contract the client expects

The backend has to match this exactly. Nothing here needs code on the backend side; it's the
shape the rules and console config must allow.

### 2.1 Authentication

- Provider: **Google**, via Android Credential Manager → `GoogleAuthProvider.getCredential(idToken)`
  → `FirebaseAuth.signInWithCredential`.
- The app reads the OAuth **web client id** from the string resource `default_web_client_id`,
  which the Google Services Gradle plugin generates from `google-services.json`. That entry
  only exists if the **Google sign-in provider was enabled before you downloaded the file**.
- Package name: `com.wac.worktrack`
- The SHA-1 of the key that signs the APK must be registered in Firebase (see 3.2).

### 2.2 Firestore data layout

Everything for a user lives under `users/{uid}`. One device writes, one user reads; it's a
backup, not live sync.

```
users/{uid}                              ← summary doc
  chunks:    map<string, int>            ← table name → number of chunk docs
  updatedAt: int (epoch millis)          ← time of last backup
  schema:    int                         ← backup format (currently 4)

users/{uid}/tables/{table}_{i}           ← one doc per chunk, i = 0..chunks[table]-1
  table: string                          ← e.g. "daily_tasks"
  chunk: int                             ← i
  json:  string                          ← JSON array of rows (SQLite row → JSON object), ≤ 900 KB UTF-8
```

Tables backed up (25): `task_templates, daily_tasks, reminders, habits, habit_logs, goals,
transactions, budgets, recurring_txns, merchant_rules, weekly_reviews, period_reviews, bills,
focus_sessions, journal, inbox, places, learning, debts, assets, net_worth, medicines,
med_logs, important_dates, rest_days`.

Behaviour:

- **Backup** (`CloudBackup.backup`): reads `users/{uid}` to get the old chunk counts, writes all
  chunk docs in batched writes (≤450 ops / ≤8 MB per batch), deletes leftover chunks when a
  table shrank, then writes the summary doc last.
- **Restore** (`CloudBackup.restore`): reads `users/{uid}`; if it has no `chunks`, there's no
  backup. Otherwise it lists `users/{uid}/tables` in one query and **replaces** all local data.
  Older backups (lower `schema`) are accepted; missing columns are filled with defaults.
- Restore also runs automatically on first sign-in if the phone has no tasks yet.
- Auto backup: WorkManager job once a day, only when online and signed in.
- Every cloud call times out after 60 s with "No connection to the cloud".

### 2.3 Security rules

The required rule is in `firestore.rules` at the repo root:

```
rules_version = '2';
service cloud.firestore {
  match /databases/{database}/documents {
    match /users/{uid}/{document=**} {
      allow read, write: if request.auth != null && request.auth.uid == uid;
    }
  }
}
```

The app needs `get` on `users/{uid}`, `list` on `users/{uid}/tables`, and `create/update/delete`
on both. The rule above grants all of that and nothing outside the user's own tree.

### 2.4 Google Tasks API

- Called from the phone with an OAuth access token from `Identity.getAuthorizationClient`
  (scope `https://www.googleapis.com/auth/tasks.readonly`).
- Endpoint: `https://tasks.googleapis.com/tasks/v1`. Read only, never writes.
- Needs the **Google Tasks API enabled** in the same Google Cloud project as Firebase.

---

## 3. Setup steps

Use a **personal Google account**. The work account (webandcrafts.com) got a 403 when adding
Firebase to a project.

### 3.1 Create the project

1. Go to <https://console.firebase.google.com> → **Add project**.
2. Name it e.g. `worktrack-personal`. Google Analytics: **off** (the app doesn't use it).
3. Plan: **Spark (free)** is enough (see section 7).

### 3.2 Register the Android app

1. Project overview → **Add app** → Android.
2. Package name: `com.wac.worktrack` (must match exactly).
3. Nickname: `WorkTrack`.
4. **Debug signing certificate SHA-1**, from the build machine's debug keystore:

   ```
   41:BB:CC:D3:56:D0:76:A3:0F:8B:01:07:3A:E8:D1:4E:30:1C:C6:4B
   ```

   > ⚠️ The SHA-1 belongs to the keystore that **signs the APK**, not to the machine doing the
   > Firebase setup. This value is the build PC's `~/.android/debug.keystore`. If the APK is ever
   > built on another machine (or with a release key), add that key's SHA-1 too:
   > Project settings → Your apps → **Add fingerprint**. Get it with:
   >
   > ```bash
   > keytool -list -v -keystore ~/.android/debug.keystore \
   >   -alias androiddebugkey -storepass android | grep SHA1
   > ```
   >
   > Adding the SHA-256 as well does no harm.

5. **Don't download `google-services.json` yet.** Skip the SDK steps (the app already has them).

### 3.3 Enable Google sign-in

1. Build → **Authentication** → Get started.
2. Sign-in method → **Google** → Enable.
3. Set the project support email (your personal Gmail) → Save.

This creates the OAuth **Web client** that becomes `default_web_client_id`. It's why the JSON
has to be downloaded *after* this step.

### 3.4 Create Firestore

1. Build → **Firestore Database** → Create database.
2. Edition: **Standard**. Database ID: `(default)`. The app uses the default database.
3. Location: `asia-south1` (Mumbai), or whatever is closest. **This can't be changed later.**
4. Start in **production mode** (everything denied), since you replace the rules next anyway.

### 3.5 Deploy the security rules

**Option A (console):** Firestore → **Rules** tab → paste the contents of `firestore.rules`
→ **Publish**.

**Option B (CLI, repeatable):** on the backend machine:

```bash
npm install -g firebase-tools
firebase login                      # personal account
mkdir worktrack-backend && cd worktrack-backend
cp /path/to/firestore.rules .
cat > firebase.json <<'EOF'
{ "firestore": { "rules": "firestore.rules" } }
EOF
firebase use --add                  # pick the project, alias "default"
firebase deploy --only firestore:rules
```

You can keep this folder in its own small private repo if you like. It only holds config.

### 3.6 Enable the Google Tasks API

1. <https://console.cloud.google.com> → select the **same project** (Firebase projects are GCP projects).
2. APIs & Services → Library → **Google Tasks API** → Enable.
3. APIs & Services → **OAuth consent screen** (Google Auth Platform → Audience):
   - User type: **External**
   - App name `WorkTrack`, support email = your Gmail
   - Add yourself as a test user
   - Publishing status: **In production**. While it's "Testing", Google revokes the
     `tasks.readonly` grant every 7 days. You'll get an "unverified app" warning on the phone;
     tap *Advanced → Continue*. No verification is needed for personal use.

### 3.7 Download `google-services.json`

1. Project settings → General → Your apps → WorkTrack → **Download google-services.json**.
2. Check that it has an `oauth_client` entry with `"client_type": 3` (the web client). If
   `oauth_client` is empty, Google sign-in wasn't enabled yet: do 3.3, then download it again.

```bash
python3 -c "import json;d=json.load(open('google-services.json'));print([c['client_type'] for c in d['client'][0]['oauth_client']])"
# expect a list containing 3 (and 1 once a SHA-1 is registered)
```

### 3.8 Move it to the build machine

Copy the file to the build PC at `app/google-services.json`. Use a private channel (scp, USB, a
password manager); **don't commit it**, since `.gitignore` already excludes it. It isn't a
password, but it identifies your project.

Then rebuild on the build PC:

```bash
JAVA_HOME=/home/wac/.local/jdk/jdk-17.0.20.1+1 \
ANDROID_HOME=/home/wac/.local/android-sdk \
./gradlew assembleDebug
```

---

## 4. Optional: lock the backend to only you

The default rule lets *any* Google user who signs in use their own `users/{theirUid}` tree,
which costs you quota. To lock it to just you:

1. Install the app, sign in once.
2. Firebase console → Authentication → **Users** → copy your **User UID**.
3. In `firestore.rules`, swap the active line for the commented stricter one and put your UID in:

   ```
   allow read, write: if request.auth != null && request.auth.uid == uid && uid == "YOUR_UID";
   ```

4. Publish / `firebase deploy --only firestore:rules`.
5. Optional: Authentication → Settings → **User actions** → turn off *Enable create (sign-up)*,
   so no new accounts can be created at all.

---

## 5. Verify the backend

### 5.1 Rules Playground (console → Firestore → Rules → Playground)

| Simulation | Auth | Path | Expected |
|---|---|---|---|
| get | none | `users/abc` | ❌ deny |
| get | signed in, uid `abc` | `users/abc` | ✅ allow |
| create | signed in, uid `abc` | `users/abc/tables/daily_tasks_0` | ✅ allow |
| delete | signed in, uid `abc` | `users/abc/tables/daily_tasks_3` | ✅ allow |
| get | signed in, uid `xyz` | `users/abc` | ❌ deny |
| get | signed in, uid `abc` | `other/thing` | ❌ deny |

### 5.2 End-to-end on the phone

1. Fresh install → sign in with Google → lands in the app (no error toast).
2. Add a couple of tasks → Settings → **Back up now** → toast shows the row count.
3. Console → Firestore → `users/{uid}` has `chunks`, `updatedAt`, `schema: 4`, and
   `tables/` holds docs like `daily_tasks_0`.
4. Clear app data (or reinstall) → sign in → data comes back (auto restore on an empty phone).
5. Settings → enable Google Tasks → consent → a Google Task with a due date shows on Today.
6. Turn on airplane mode → Back up now → "No connection to the cloud…" after up to 60 s.

---

## 6. Troubleshooting

| Symptom on phone | Cause | Fix |
|---|---|---|
| Sign-in option missing / "Firebase is not configured" | App built without `google-services.json`, or the JSON has no web client | Do 3.3 then 3.7, rebuild |
| Sign-in fails with `DEVELOPER_ERROR` / code 10 / "[16] Account reauth failed" | SHA-1 of the signing key not registered | Add the right SHA-1 (3.2), re-download JSON, rebuild |
| "No Google account found on this phone" | No account on the device | Add one in Android Settings → Accounts |
| Backup fails with `PERMISSION_DENIED` | Rules not published, or the locked UID is wrong | Re-deploy rules, check the UID |
| "A row in … is too big for the cloud" | One row > ~900 KB (see 8) | Shorten that entry, or use Settings → Export |
| Google Tasks: "access was not granted" or stops after 7 days | API off, or consent screen still in Testing | Do 3.6 |
| "No connection to the cloud" | Offline or Firestore unreachable | Expected offline; retry |

---

## 7. Cost and quotas (Spark free plan)

One backup = 1 read + ~30 chunk writes + 1 summary write (more if tables are big).
One restore = 1 read + ~30 reads. Daily auto backup ≈ 1,000 writes/month.

Free limits: 50k reads/day, 20k writes/day, 20k deletes/day, 1 GiB stored. A single user will
use well under 1% of this. No billing account is needed. If you ever upgrade to Blaze, set a
budget alert at a small amount (e.g. ₹100) in Google Cloud Billing.

---

## 8. Known limitations (client-side, not backend work)

- **Firestore's 1 MiB per document.** The client packs rows into chunks of at most 900 KB
  (UTF-8), so big tables just use more chunk docs. A *single* row over ~900 KB can't be backed
  up; the backup then fails naming the table, and the local **JSON backup file**
  (Settings → Export) still works. Normal use never gets near that.
- **Backup isn't encrypted on the client.** Firestore encrypts at rest and the rules restrict
  access, but Google-side admins of the project (you) can read the data in the console.
- **Last write wins.** Built for one phone. Backing up from two phones overwrites each other.
- **No backup history.** Each backup replaces the last one. For point-in-time copies, enable
  Firestore **Point-in-time recovery** (Blaze only) or export the JSON file now and then.

---

## 9. Moving to a new Firebase account (or recovering from a lost one)

The phone is the source of truth, so losing the Firebase account doesn't lose data as long as
you still have **either** the phone **or** an export file. Firebase only holds a copy.

### 9.1 Keep an export file somewhere safe

Settings → Backup file → **Export** saves `worktrack-backup-YYYY-MM-DD.json`. Save it to Google
Drive (another account), a laptop, or email it to yourself. Do this every week or two. It's
the only copy that survives losing **both** the phone and the Firebase account.

### 9.2 Switch accounts (phone still working)

1. Set up the new Firebase project: sections 3.1–3.8 of this guide.
2. Replace `app/google-services.json` with the new one, rebuild, install over the old app
   (`adb install -r …`; data stays on the phone).
3. Settings → Sign out → **Sign in with Google** using the new account.
4. Settings → **Back up now**. Everything on the phone is now in the new project.

### 9.3 Recover from an export file (phone lost or wiped)

1. New Firebase project + new `google-services.json`, rebuild, install.
2. Open the app, choose offline mode or sign in. Then Settings → Backup file → **Import** →
   pick the file. This *replaces* everything on the phone with the file's contents.
3. Settings → **Back up now** to copy it into the new Firestore.

### 9.4 Export file format (for an agent or script)

```jsonc
{
  "app": "WorkTrack",            // required; import refuses other files
  "format": 4,                   // same as the Firestore "schema" field
  "exportedAt": 1761650000000,   // epoch millis
  "tables": {
    "daily_tasks": [ { "id": 1, "title": "...", ... }, ... ],
    "habits": [ ... ],
    ...                          // the 25 tables listed in 2.2; missing tables import as empty
  }
}
```

Each row is a SQLite row as JSON: column name → value (integers, floats, strings or null;
booleans are 0/1, dates are epoch-day or epoch-millis integers as stored by the app).

**Feeding it straight into Firestore without the app.** An agent with admin access to the new
project (a service-account key, or `gcloud auth application-default login`) can write the file
into the layout from 2.2:

1. Sign in once in the app with the new account to get the user's `uid`
   (Authentication → Users).
2. For each table, pack its rows into JSON arrays of at most 900 KB (UTF-8), in order, and write
   `users/{uid}/tables/{table}_{i}` = `{ table, chunk: i, json: "<array>" }`.
3. Write `users/{uid}` = `{ chunks: {table: count, ...}, updatedAt: <millis>, schema: 4 }`
   **last**.
4. In the app: Settings → **Restore**.

The app route in 9.3 does exactly this and needs no admin key, so prefer it. Use the direct
route only if you can't run the app.

---

## 10. Checklist

- [ ] Firebase project created on personal Gmail
- [ ] Android app `com.wac.worktrack` registered with SHA-1 `41:BB:…:4B`
- [ ] Authentication → Google provider enabled
- [ ] Firestore `(default)` created in `asia-south1`
- [ ] `firestore.rules` published
- [ ] Google Tasks API enabled, consent screen **In production**
- [ ] `google-services.json` downloaded **after** enabling Google sign-in, has `client_type: 3`
- [ ] JSON copied to build PC at `app/google-services.json` (not committed)
- [ ] App rebuilt, sign-in + backup + restore verified (section 5.2)
- [ ] (Optional) Rules locked to your UID, sign-up disabled
