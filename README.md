# Bài tập 6 – Thiết kế mô hình mạng bảo mật (VPS + Docker networks)

## Kiến trúc

```
                Internet
                   │  :80 (cổng DUY NHẤT được publish)
┌──────────────────▼───────────────────────── VPS ─┐
│  ┌──────────── app-network (bridge) ───────────┐ │
│  │   gateway (nginx)                            │ │
│  │     ├─ /api/users  ─► user-service:8080      │ │
│  │     └─ /api/orders ─► order-service:8080     │ │
│  │                          │                   │ │
│  │                          ▼                   │ │
│  │                       db:5432 (postgres:15)  │ │
│  └──────────────────────────────────────────────┘ │
└───────────────────────────────────────────────────┘
```

| Service         | Image                                        | Port ra Internet |
|-----------------|----------------------------------------------|------------------|
| `gateway`       | `ghcr.io/<owner>/md05-session12-bt6-gateway` | **80:80**        |
| `user-service`  | `ghcr.io/<owner>/md05-session12-bt6-app` (profile `user`)  | không |
| `order-service` | `ghcr.io/<owner>/md05-session12-bt6-app` (profile `order`) | không |
| `db`            | `postgres:15`                                | không            |

## Giải pháp sử dụng internal network của Docker

1. **Custom network `app-network`** (driver `bridge`) được khai báo trong `docker-compose.yml`
   và cả 4 service đều tham gia network này. Các container trong cùng network nói chuyện
   trực tiếp với nhau qua IP nội bộ (vd `172.18.0.x`), không đi qua host.

2. **Chỉ `gateway` có `ports: "80:80"`.** `ports` mới là thứ tạo rule NAT trên host để mở
   cổng ra ngoài. `user-service`, `order-service` chỉ có `expose: 8080` (thông tin nội bộ),
   `db` không khai báo gì → từ Internet không có đường nào tới 8080 hay 5432.
   Kiểm chứng: `docker compose ps` chỉ hiện `0.0.0.0:80->80/tcp` ở gateway.

3. **Nginx gọi `user-service` bằng Docker DNS như sau:**
   - Với *user-defined network*, Docker chạy một **DNS server nhúng tại `127.0.0.11`**
     trong mỗi container. Tên service trong compose (`user-service`, `order-service`, `db`)
     được đăng ký làm tên DNS trỏ tới IP container tương ứng.
     (Network `bridge` mặc định **không** có tính năng này – đó là lý do phải tạo custom network.)
   - Trong `nginx/nginx.conf`:
     ```nginx
     resolver 127.0.0.11 valid=10s;
     set $user_service http://user-service:8080;
     location /api/users { proxy_pass $user_service; }
     ```
   - Khi request `GET http://<vps-ip>/api/users` đến, Nginx hỏi `127.0.0.11`
     "`user-service` là IP nào?" → Docker DNS trả về IP của container user-service
     → Nginx proxy tới `IP:8080`. Nếu có nhiều replica, Docker DNS trả nhiều IP (round-robin).
   - Dùng **biến + `resolver`** (thay vì ghi cứng `proxy_pass http://user-service:8080`) để Nginx
     phân giải tên lúc runtime và cache 10s: khi container được tạo lại và đổi IP sau mỗi lần
     deploy, Nginx vẫn tìm đúng, và Nginx không bị crash lúc khởi động nếu service chưa lên.
   - Tương tự, user/order service kết nối DB bằng `jdbc:postgresql://db:5432/appdb` (`DB_HOST=db`).

4. **Lớp bảo vệ thêm trên VPS:** bật firewall chỉ cho 22 và 80 (`ufw allow 22,80/tcp`).
   Lưu ý Docker tự chèn rule iptables cho cổng đã publish nên *không* dựa vào ufw để chặn
   cổng container – cách đúng là **không publish** cổng đó, như đã làm ở trên.

## Chạy local

```bash
cp .env.example .env    # sửa POSTGRES_PASSWORD, GHCR_OWNER=local
docker compose -f docker-compose.yml -f docker-compose.local.yml up -d --build
curl localhost/api/users
curl localhost/api/orders
curl localhost/api/users/db-check   # chứng minh service -> db qua DNS "db"
```

## CI/CD (`.github/workflows/ci-cd.yaml`)

`push` lên `main`/`master` → **test** (gradle build + validate compose) → **build-and-push**
(2 image lên GHCR, tag `latest` + `sha`) → **deploy** (scp `docker-compose.yml` lên VPS,
sinh `.env`, `docker compose pull && up -d`, smoke test qua cổng 80).

### Secrets cần tạo (Settings → Secrets and variables → Actions)

| Secret              | Giá trị                                   |
|---------------------|-------------------------------------------|
| `VPS_HOST`          | IP VPS                                    |
| `VPS_USER`          | user SSH (có quyền chạy docker)           |
| `VPS_SSH_KEY`       | nội dung **private key** (`~/.ssh/md05_deploy`) |
| `VPS_PORT`          | (tùy chọn) mặc định 22                    |
| `POSTGRES_PASSWORD` | mật khẩu DB production                    |

GHCR dùng sẵn `GITHUB_TOKEN`, không cần tạo PAT.

### Chuẩn bị VPS (một lần)

```bash
# Trên máy local
ssh-keygen -t ed25519 -C "github-actions-deploy" -f ~/.ssh/md05_deploy -N ""
ssh-copy-id -i ~/.ssh/md05_deploy.pub <user>@<vps-ip>

# Trên VPS
curl -fsSL https://get.docker.com | sh
sudo usermod -aG docker $USER     # logout/login lại
sudo ufw allow 22/tcp && sudo ufw allow 80/tcp && sudo ufw enable
```
