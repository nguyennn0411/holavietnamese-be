# Cloudinary audio migration

The backend has 443 course WAV files under `src/main/resources/static/media/` and the frontend has 46 alphabet WAV files under `public/media/alphabet/`. `scripts/upload-audio-cloudinary.cjs` uploads all 489 as Cloudinary `video` assets (Cloudinary's resource type for audio) with stable public IDs under `hola-vietnamese/audio/`. It writes the returned HTTPS URLs to `FE/holavietnamese_fe/src/components/common/cloudinaryAudioUrls.js`.

The frontend resolves existing `/media/...` audio paths to those URLs when playing audio, so course rows already saved in the database do not need to be reseeded. A file without a successful Cloudinary upload continues to use its local URL. The original WAV files remain available during migration.

1. Create `BE/.env.cloudinary` on the machine running the upload. This file is ignored by Git:

   ```dotenv
   CLOUDINARY_CLOUD_NAME=your-cloud-name
   CLOUDINARY_API_KEY=your-api-key
   CLOUDINARY_API_SECRET=your-api-secret
   # Optional: CLOUDINARY_AUDIO_FOLDER=hola-vietnamese/audio
   ```

2. From `BE`, check the source files, then upload them with Node 20 or later:

   ```powershell
   node scripts/upload-audio-cloudinary.cjs --dry-run
   node --env-file=.env.cloudinary scripts/upload-audio-cloudinary.cjs
   ```

3. Build and deploy the frontend after the upload. The generated URL map must be included in that build. The upload script saves hashes and returned URLs in ignored `BE/.verification/cloudinary-audio-upload-state.json`, so a retry only uploads new or changed WAV files.

The Cloudinary API secret stays on the uploader machine. Never add `.env.cloudinary` to Git. The upload uses signed HTTPS requests and validates every Cloudinary response before adding its URL to the frontend map.
