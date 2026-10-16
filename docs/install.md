# Installing Loop from an APK

Loop isn't on the Play Store yet, so you install it from a file called `loop-<version>.apk`. You
need an Android phone running Android 9 or newer, with about 150 MB free.

## 1. Get the file onto the phone

Any of these works:
- **Quick Share** (Nearby Share) from another Android phone or a Windows PC.
- **Google Drive**: open the shared link on the phone and tap Download.
- **WhatsApp or Telegram**: send it as a *document*, then tap it in the chat.
- **USB cable and a computer** with Android platform tools: `adb install loop-0.1.0.apk`
  (skip to step 3).

## 2. Install it

1. Tap the downloaded file. You'll find it in the Files app under Downloads, or in the chat.
2. If the phone says it isn't allowed to install unknown apps from this source, tap **Settings**,
   turn on **Allow from this source**, then go back.
3. Tap **Install**.
4. Google Play Protect may say it doesn't recognise the app, because Loop isn't on Play. Tap
   **More details › Install anyway**. Choosing not to send the app for scanning is fine.
5. In some countries, Android now asks for extra steps before it installs apps from developers
   who haven't registered with Google. If the phone says the developer isn't verified, ask the
   person who sent you the file.

## 3. Set it up

1. Open Loop and follow the welcome screens. Your name and gender are optional, and both stay on
   the phone.
2. Turn on the trackers you want under **Me › Permissions**. You can also use the card on the
   Today screen. Each one opens an Android settings screen. Find Loop there, switch it on, and
   come back.
3. **If Android says "Restricted setting"** when you switch on notification access (or another
   access screen), it's because the app came from a file:
   1. Open **Settings › Apps › Loop**.
   2. Tap **⋮** (top right) › **Allow restricted settings** and confirm with your PIN or
      fingerprint.
   3. Go back to Loop and switch the access on again.
4. Open **Me › Keep Loop running** and follow the steps for your phone, so the battery saver
   doesn't pause Loop. This matters most on Samsung, Xiaomi, OnePlus, Oppo and Vivo phones.
5. **Optional, for meal photos:** go to **Me › Food photo key › Get a free key**, create a free
   Gemini key with your Google account, then paste it and tap **Save key**. Without a key you can
   still log food by search or barcode.

## Updating

Install the newer APK over the old one, the same way as above. Don't uninstall first, because
uninstalling deletes your data.

An update only installs if it was built by the same person as your current copy. If Android says
"App not installed" or reports a conflict, you probably have a different build, such as a test
build. To switch:
1. Save a copy of your data under **Me › Backup and export › Export to a file**.
2. Uninstall Loop and install the new APK.
3. Import the file you saved.

## Privacy

Everything Loop records stays on the phone. There's no account. The only thing that ever leaves
the phone is a meal photo, and only if you add a Gemini key and take one. That photo goes to
Google to recognise the food.
