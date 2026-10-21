// WorkTrack local learning server + dashboard.
// Runs on YOUR PC. The app uploads media here; you view it in a browser.
// Start: npm install && npm start   (listens on 0.0.0.0:3000)

const express = require('express');
const multer = require('multer');
const fs = require('fs');
const path = require('path');
const os = require('os');

const UPLOAD_DIR = path.join(__dirname, 'uploads');
fs.mkdirSync(UPLOAD_DIR, { recursive: true });

const app = express();

// Keep the original filename; prefix with a device label if the app sends one.
const storage = multer.diskStorage({
  destination: (req, file, cb) => cb(null, UPLOAD_DIR),
  filename: (req, file, cb) => {
    const device = (req.body.device || 'device').replace(/[^a-zA-Z0-9_-]/g, '');
    cb(null, `${device}__${file.originalname}`);
  },
});
const upload = multer({ storage });

// Upload endpoint — one file per request (field name "file").
app.post('/upload', upload.single('file'), (req, res) => {
  if (!req.file) return res.status(400).json({ error: 'no file' });
  console.log(`received: ${req.file.filename} (${req.file.size} bytes)`);
  res.json({ ok: true, name: req.file.filename, size: req.file.size });
});

// Serve the raw uploaded files.
app.use('/files', express.static(UPLOAD_DIR));

// Dashboard — thumbnails/players of everything uploaded.
app.get('/', (req, res) => {
  const files = fs.readdirSync(UPLOAD_DIR).sort().reverse();
  const isVideo = (f) => /\.(mp4|3gp|mkv|webm|mov)$/i.test(f);
  const isImage = (f) => /\.(jpe?g|png|gif|webp|bmp)$/i.test(f);
  const cards = files.map((f) => {
    const src = `/files/${encodeURIComponent(f)}`;
    const media = isVideo(f)
      ? `<video src="${src}" controls></video>`
      : isImage(f)
        ? `<img src="${src}" loading="lazy">`
        : `<div class="other">📄</div>`;
    return `<figure>${media}<figcaption>${f}</figcaption></figure>`;
  }).join('');
  res.send(`<!doctype html><html><head><meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1">
<title>WorkTrack dashboard</title>
<style>
  body{font-family:system-ui,sans-serif;margin:0;background:#1e1e2e;color:#cdd6f4}
  header{padding:16px 20px;background:#181825;position:sticky;top:0}
  h1{font-size:18px;margin:0}
  .grid{display:grid;grid-template-columns:repeat(auto-fill,minmax(160px,1fr));gap:10px;padding:16px}
  figure{margin:0;background:#313244;border-radius:8px;overflow:hidden}
  img,video{width:100%;height:160px;object-fit:cover;display:block;background:#000}
  .other{height:160px;display:flex;align-items:center;justify-content:center;font-size:40px}
  figcaption{font-size:11px;padding:6px;word-break:break-all;color:#a6adc8}
  .empty{padding:40px;text-align:center;color:#a6adc8}
</style></head><body>
<header><h1>WorkTrack dashboard — ${files.length} item(s)</h1></header>
${files.length ? `<div class="grid">${cards}</div>` : '<p class="empty">No uploads yet. Hit Sync in the app.</p>'}
</body></html>`);
});

const PORT = Number(process.env.PORT) || 3000;
app.listen(PORT, '0.0.0.0', () => {
  const nets = os.networkInterfaces();
  const ips = Object.values(nets).flat().filter((n) => n && n.family === 'IPv4' && !n.internal).map((n) => n.address);
  console.log(`WorkTrack server on port ${PORT}`);
  console.log(`Dashboard: http://localhost:${PORT}`);
  ips.forEach((ip) => console.log(`From your phone (same Wi-Fi): http://${ip}:${PORT}`));
});
