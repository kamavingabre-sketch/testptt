# 📡 DIY Walkie-Talkie Android App

Aplikasi walkie-talkie berbasis WebSocket untuk Android. Push-to-Talk (PTT) via internet menggunakan Cloudflare Tunnel.

---

## 🗂️ Struktur Project

```
walkie-talkie-app/
├── server/               ← Node.js WebSocket server (jalankan di laptop)
│   ├── server.js
│   └── package.json
├── app/                  ← Android app (Kotlin)
│   └── src/main/
│       ├── java/com/diy/walkietalkie/
│       │   ├── MainActivity.kt
│       │   ├── WebSocketManager.kt
│       │   ├── AudioRecorder.kt
│       │   └── AudioPlayer.kt
│       └── res/
└── .github/workflows/    ← GitHub Actions (auto build APK)
    └── build.yml
```

---

## 🚀 Cara Pakai

### Step 1 — Jalankan Server di Laptop

```bash
cd server
npm install
node server.js
```

Server berjalan di `http://localhost:8080`

---

### Step 2 — Expose ke Internet via Cloudflare Tunnel

Install `cloudflared` di laptop:

**Windows:**
```powershell
winget install Cloudflare.cloudflared
```

**Linux:**
```bash
curl -L https://github.com/cloudflare/cloudflared/releases/latest/download/cloudflared-linux-amd64 -o cloudflared
chmod +x cloudflared && sudo mv cloudflared /usr/local/bin
```

Jalankan tunnel:
```bash
cloudflared tunnel --url http://localhost:8080
```

Catat URL yang muncul, contoh:
```
https://random-abc.trycloudflare.com
```

Ubah ke format WebSocket:
```
wss://random-abc.trycloudflare.com
```

---

### Step 3 — Build APK via GitHub Actions

1. Push seluruh folder ini ke GitHub repo baru
2. Buka tab **Actions** di repo
3. Klik workflow **Build Debug APK**
4. Klik **Run workflow**
5. Tunggu ~5 menit, download APK dari bagian **Artifacts**

> APK juga otomatis build setiap kali kamu push ke branch `main`

---

### Step 4 — Install & Konfigurasi APK di HP

1. Install APK (aktifkan "Unknown sources" di Settings HP)
2. Buka app → tap ⚙ Settings
3. Isi:
   - **Server URL**: `wss://random-abc.trycloudflare.com`
   - **Room ID**: `room-001` (sama di semua HP)
   - **Your Name**: nama kamu
4. Kembali → tap **Connect**
5. **Tahan tombol PTT** untuk bicara, lepas untuk berhenti

---

## ⚠️ Catatan Penting

| | |
|---|---|
| URL Cloudflare berubah tiap restart | Update di Settings app |
| Butuh izin mikrofon | Auto-request saat pertama buka |
| Audio format | PCM 16-bit, 16kHz, Mono |
| Noise suppressor | Aktif otomatis jika HP support |

---

## 🔧 Pengembangan Selanjutnya

- [ ] Kompresi Opus (hemat bandwidth ~5x)
- [ ] Indikator siapa yang sedang bicara
- [ ] Notifikasi saat ada pesan masuk
- [ ] Multiple channel/room
- [ ] Deploy server ke VPS agar URL permanen
