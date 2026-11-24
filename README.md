<p align="center"><img src="docs/icon.png" width="120" alt="Recompresseur icon"></p>

<h1 align="center">Recompresseur</h1>

An Android app that degrades images on purpose, so they look like a screenshot that was re-shared, re-saved and
re-compressed a hundred times, with a live preview.

It works fully offline: the app has no internet permission, so your images never leave the phone.

> The interface is currently in French.

<p align="center">
  <img src="docs/presets.png" width="31%" alt="Presets">
  <img src="docs/artifacts.png" width="31%" alt="Artifacts settings">
  <img src="docs/passes.png" width="31%" alt="Passes settings">
</p>

## Features

- **Live preview**: tap the image to zoom in
- **Presets**: "Screen of a screen", "2014 WhatsApp group", "Photo of a screen", "Internet archaeology",
  "2008 GIF", "Deep fried"… You can also save your own.
- **Passes**: number of re-compressions, cropping and rotation per pass, optional pixelated look
- **JPEG**: min/max quality, how much the image shrinks per pass, final quality
- **Color**: saturation, contrast, brightness, washed-out blacks, yellowing
- **Artifacts**: color bleeding, posterization, oversharpening halos, blur, noise, moiré
- **Frame**: black bars and a watermark
- **Share target**: share any image from another app straight into Recompresseur

Sliders only move when you drag horizontally, so scrolling the panel doesn't nudge them by accident.

## Build

### With GitHub Actions

Every push to `main` builds the APK. Open the **Actions** tab, pick the latest run and download the
`Recompresseur` artifact.

By default, each run signs the APK with a new throwaway key, so you have to uninstall the old version before
installing a new one. To install updates over the previous version, add these repository secrets under
**Settings → Secrets and variables → Actions**:

| Secret | Value |
| --- | --- |
| `KEYSTORE_BASE64` | your keystore file, base64-encoded (`base64 -w0 your.keystore`) |
| `KS_PASS` | keystore password |
| `KS_ALIAS` | key alias |

### Locally (Debian/Ubuntu, no Gradle)

```sh
sudo apt install aapt apksigner dalvik-exchange android-sdk-platform-23 zipalign
./build.sh
```

The APK lands in `build/Recompresseur.apk`. The first build creates a `recomp.keystore` signing key. Keep it
private and reuse it for every build, because Android only installs an update if it's signed with the same key.
It's ignored by git.

## How it works

The whole editor is a single HTML/JS page (`assets/index.html`) that runs in a WebView and uses the browser's
own JPEG encoder for every pass. The Java side (`src/app/recompresseur/MainActivity.java`) handles opening and
sharing images and saving the result.
