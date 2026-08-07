# WhatsApp Desktop Bot

Production-ready WhatsApp bot with a web dashboard, message queueing, backlog processing, and a mobile bulk-send app.

## Mobile bulk send
Open `/bulk.html` on a phone after login:
1. The app shows a conversation list.
2. Tap a conversation to open the bulk-send form.
3. Paste messages, choose messages-per-minute, then tap Send.
Sending requires WhatsApp to be linked and a conversation to be selected first. Users with send-only permission are redirected to this screen after login.

## Requirements
- Node.js 18+
- npm
- Linux VPS capable of running headless Chromium (install common dependencies such as `libnss3`, `libatk1.0-0`, `libx11-xcb1`, `libdrm2`, `libxcb-dri3-0`). Puppeteer may download its own Chromium build.

## Install
```bash
npm install
```

## Run locally
```bash
npm start
```

Then open http://localhost:3000 and log in with the dashboard credentials (env overridable).

## PM2 (24/7)
```bash
pm2 start server.js --name whatsappbot
pm2 save
pm2 startup
```

## Environment
- `PORT` (default 3000)
- `DASH_USER` (default `loorksy@gmail.com`)
- `DASH_PASS` (default `lork0009`)
- `JWT_SECRET` (set for production)
- `PUPPETEER_EXECUTABLE_PATH` (optional path to system Chromium)

Data persists under `/data` (JSON files) including sessions for WhatsApp auth.
