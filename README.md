# LiteApp Android 1.0.0

Universal Android shell for LiteApp SaaS.

## Target devices
- Android phones/tablets.
- Sunmi T1 / Android POS (Android 6+).
- One APK; mode is selected automatically.

## Behavior
- Phone: normal LiteApp responsive/mobile layout.
- POS/Sunmi/tablet landscape: forces `liteapp-pos-lowres` on every page load and keeps it applied with a MutationObserver.
- POS mode uses immersive full screen to recover vertical space lost to browser/address/navigation bars.
- WebView cache is disabled so CSS/JS updates on liteapp.io.vn are picked up immediately.
- Session/cookies persist in Android WebView storage.
- Upload images/files works through Android picker.
- External non-LiteApp links open in another app/browser.
- Android print framework bridge available as `LiteAppAndroid.printCurrentPage()`.

## Compatibility
- minSdk 23 = Android 6.0.
- targetSdk 35.

## Build
### GitHub Actions
Push this folder to a GitHub repository. Open Actions -> `Build LiteApp Android APK` -> Run workflow. The workflow installs Gradle 8.9 + Android SDK 35 automatically and creates an installable debug APK. Download artifact `LiteApp-Android-debug`.

### Android Studio
Open folder -> let Gradle sync -> Build -> Build APK(s). Android Studio can provide its own Gradle tooling; no wrapper is required in this source package.

## Important about Sunmi integrated printer
The app currently supports Android's standard PrintManager. Direct printing to the built-in Sunmi thermal printer may need the exact Sunmi printer SDK/service available on the customer's T1 firmware. Add that only after confirming the printer service/SDK version on the real device.

## Debug on the web page
From DevTools/JS inside WebView-compatible page:
```js
window.LITEAPP_ANDROID
LiteAppAndroid.getDeviceMode()
LiteAppAndroid.getAppVersion()
```
Expected on Sunmi POS: `{version:'1.0.0', mode:'pos'}` and the HTML root contains `liteapp-pos-lowres liteapp-android-pos`.
