# Task Tracker Pro — Apps Script Deploy Notes

> No credentials, URLs, or deployment IDs are included here — you generate
> every one of them during the steps below.

## Before you start

- You need a Google account.
- Create a **brand-new, separate spreadsheet** for this project
  (e.g. name it `Task Tracker Pro`). Do **not** reuse an existing personal
  spreadsheet and do **not** add a tab to one — the backend creates its own
  `Tasks` tab with the 21 canonical headers on first use.

## One-time setup (menu by menu)

1. Open the new spreadsheet, then click **Extensions → Apps Script**.
   A new Apps Script project opens in a browser tab.
2. In the editor, select everything in `Code.gs`, delete it, and paste the
   full contents of `apps-script/Code.gs` from this repository.
   Press **Ctrl+S** (Windows) / **Cmd+S** (Mac), or File → Save.
3. (Optional) Rename the project: click the untitled-project name at the
   top-left and call it `Task Tracker Pro Backend`.
4. In the left sidebar click **Project Settings** (gear icon).
5. Scroll to **Script Properties** → **Add script property**.
   - **Property:** `SECRET_TOKEN`
   - **Value:** a long random secret you generate yourself, e.g. with a
     password manager, or by running `openssl rand -hex 32` on your computer.
     Keep this value private — it is the password the app uses to talk to
     this backend.
   - Click **Save script properties**.
6. Click **Deploy → New deployment**.
7. Click the **gear icon** next to "Select type" → choose **Web app**.
8. Fill in:
   - **Description:** `Task Tracker Pro v1`
   - **Execute as:** `Me`
   - **Who has access:** `Anyone`
9. Click **Deploy**. Google will ask you to **Authorize access**:
   choose your Google account → **Advanced** → **Go to Task Tracker Pro
   Backend (unsafe)** → **Allow**. (The "unsafe" warning appears because
   this is your own unverified script — that is expected.)
10. Copy the **Web App URL** shown on the success screen. It ends in `/exec`.
11. In the Android app, open the **Connect Google Sheets** screen and enter
    the Web App URL plus the `SECRET_TOKEN` value. Tap **Test Connection** —
    only a real authenticated `ping` response counts as connected.

## Rotating the token (if it leaks or you just want a fresh one)

1. In Apps Script, go to **Project Settings → Script Properties**,
   edit `SECRET_TOKEN`, paste the new value, and save. The property is read
   live on every request, so the old token stops working immediately —
   no redeploy needed for the property change alone.
2. In the app, open **Connect Google Sheets → Change Connection Settings**
   and enter the new token, then **Test Connection**.
3. Never commit the token to GitHub, never paste it into chat logs or
   screenshots, and never reuse a token across different projects.

## ⚠️ Important: GitHub edits do NOT update the deployed script

If you change `Code.gs` in this GitHub repository, the running Web App is
**not** affected. To ship script changes you must, in the Apps Script editor:

1. **Deploy → Manage deployments** → click the pencil (edit) icon.
2. Under **Version**, choose **New version**.
3. Click **Deploy**.

The same `/exec` URL then serves the new code. There is no way to update
the deployment from GitHub alone.

## Troubleshooting

| Symptom | Likely cause / fix |
|---|---|
| `Server not configured: SECRET_TOKEN missing` | Script property `SECRET_TOKEN` was not saved (step 5). Property name is case-sensitive. |
| `Unauthorized` | Token in the app does not match the script property. Check for trailing spaces when copying. |
| App gets an HTML login page instead of JSON | "Who has access" is not set to **Anyone**, or you pasted the wrong URL (must end in `/exec`). |
| `Unknown action` | App and script versions disagree; redeploy the latest `Code.gs` as a new version. |
| `Validation failed: …` | The app sent a field in the wrong format (see the DATA FORMATS comment at the top of `Code.gs`). |
| Changes to `Code.gs` have no effect | You edited GitHub but did not create a **New version** deployment (see warning above). |

## What the script does on first request

On the first authenticated call it creates the `Tasks` tab (if missing) and
writes the exact 21 headers in canonical order into row 1. It never renames
or reorders existing headers and never deletes user data.
