# Coda Bookmarker for Android

Native Android share target for the Coda Express bookmark backend.

## What It Does

- Appears in Android's **Share** sheet for `text/plain` content.
- Extracts an `http` or `https` URL from shared text.
- Uses the same backend request as the browser extension:
  - `Authorization: Bearer <Coda token>`
  - `x-api-key: <bookmark API key>`
  - `{ url, docId, tableId, properties }`
- Discovers Coda docs, tables, and table columns.
- Saves named forms for quick reuse.
- Persists manual form values per saved form.
- Sends multi-value fields as arrays. Enter those values separated by commas.

Credentials and forms are stored in app-private Android preferences. They are not
included in Android backups and are never logged.

## Build

Requirements:

- JDK 17 or newer
- Android SDK 36

```bash
cd coda-android
./gradlew assembleDebug
```

The debug APK is generated at:

```text
app/build/outputs/apk/debug/app-debug.apk
```

Install it on a connected Android device or emulator:

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

## Configure

1. Open **Coda Bookmarker**.
2. In **Settings**, enter the deployed backend URL, bookmark API key, and Coda API token.
3. Open **Forms** and tap **Refresh Coda docs**.
4. Select a doc and table, then tap **Load table fields**.
5. Name the form, select the manual fields to show when sharing, and tap **Save form**.

For a physical device, `http://localhost:3000` points to the phone itself. Use a
deployed HTTPS backend or a LAN address reachable from the phone. On the Android
emulator, the host machine is commonly available at `http://10.0.2.2:3000`.

## Share A Bookmark

From a browser or any app that shares text:

1. Tap **Share**.
2. Choose **Coda Bookmarker**.
3. Select a saved form and fill any manual fields.
4. Tap **Save bookmark**.

The activity closes shortly after the backend accepts the request.
