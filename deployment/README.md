# Deployment files

Thư mục này chứa `Dockerfile`, Compose chính và các overlay theo từng bộ dữ liệu.
Chạy lệnh từ thư mục gốc `BE`, ví dụ:

```powershell
docker compose --env-file .env.mysql `
  -f deployment/compose.yml `
  -f deployment/compose.demo.yml `
  up -d --build --wait
```

Script tương ứng nằm trong `scripts/dev/`.
