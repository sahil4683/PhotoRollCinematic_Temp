# 🎬 Photo Roll Cinematic

A professional Android app for creating cinematic videos from your photo gallery — with smooth scroll effects, Ken Burns, Slow Pan, Zoom In, and custom DaVinci Resolve-inspired styles.

[![Build Android APK](https://github.com/YOUR_USERNAME/photo-roll-cinematic/actions/workflows/build.yml/badge.svg)](https://github.com/YOUR_USERNAME/photo-roll-cinematic/actions/workflows/build.yml)

---

## ✨ Features

| Feature | Description |
|---|---|
| 📸 Photo Grid | Browse and multi-select photos from your gallery |
| ✅ Smart Selection | Green checkmark + order badge on selected photos |
| 🎞️ Timeline | Horizontal strip showing selected photos in sequence, with trash-can removal |
| ⬅️➡️⬆️⬇️ Scroll Direction | Choose Left, Right, Up, or Down cinematic pan |
| 🎨 Cinematic Styles | Slow Pan · Zoom In · Ken Burns · Custom (DaVinci Resolve inspired) |
| 🎬 Generate Video | One-tap video generation with progress feedback |

---

## 🏗️ Architecture

```
app/
├── model/          # Data classes (PhotoItem, ScrollDirection, CinematicStyle, VideoConfig)
├── viewmodel/      # MainViewModel — LiveData-driven state
├── adapter/        # PhotoGridAdapter, TimelineAdapter (ListAdapter + DiffUtil)
├── ui/
│   ├── home/       # MainActivity — full UI
│   └── preview/    # PreviewActivity — generation progress
└── utils/          # MediaStoreHelper — coroutine-based gallery loading
```

**Stack:** Kotlin · MVVM · LiveData · Coroutines · ViewBinding · RecyclerView · Glide · Material Components

---

## 🚀 GitHub Actions CI/CD

The workflow at `.github/workflows/build.yml` automatically:

1. **Triggers** on every push to `main`, `master`, or `develop`, and on PRs
2. **Builds** both Debug APK and Release APK (unsigned)
3. **Uploads** APKs as downloadable artifacts (retained 30 days)
4. **Runs** unit tests and uploads test reports
5. **Lints** the project and uploads results

### Downloading the APK from CI

1. Go to your repository on GitHub
2. Click **Actions** tab
3. Click the latest successful **Build Android APK** run
4. Scroll to **Artifacts** section
5. Download `photo-roll-cinematic-debug` or `photo-roll-cinematic-release`

---

## 🛠️ Local Build

**Requirements:**
- JDK 17
- Android SDK (API 26–34)
- Android Studio Hedgehog (2023.1) or newer

```bash
# Clone
git clone https://github.com/YOUR_USERNAME/photo-roll-cinematic.git
cd photo-roll-cinematic

# Build debug APK
./gradlew assembleDebug

# APK location:
# app/build/outputs/apk/debug/app-debug.apk

# Run tests
./gradlew test

# Lint check
./gradlew lint
```

---

## 📦 Publishing to GitHub

```bash
git init
git add .
git commit -m "Initial commit: Photo Roll Cinematic Android app"
git branch -M main
git remote add origin https://github.com/YOUR_USERNAME/photo-roll-cinematic.git
git push -u origin main
```

The GitHub Actions workflow will trigger automatically and build your APK.

---

## 📱 Permissions

| Permission | Purpose |
|---|---|
| `READ_MEDIA_IMAGES` (API 33+) | Access gallery photos |
| `READ_EXTERNAL_STORAGE` (API ≤ 32) | Access gallery photos (legacy) |
| `WRITE_EXTERNAL_STORAGE` (API ≤ 28) | Save generated video |

---

## 🎨 Design

- **Color scheme:** Cool blues (#2563EB), soft white backgrounds (#F0F4F8), dark gray text (#1F2937)
- **Generate button:** Prominent green (#16A34A) full-width CTA
- **Cards:** Clean white surfaces with subtle elevation
- **Typography:** Roboto Medium for headings, clean system fonts for body

---

## 📄 License

MIT License — see [LICENSE](LICENSE) file.
