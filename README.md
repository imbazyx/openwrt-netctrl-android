
## Сборка UI из исходников
```bash
cd owm-ui
cp .env.example .env
# Отредактируй .env — укажи адрес своего сервера:
# VITE_SERVER_URL=http://YOUR_IP:9000
npm install
npm run build
# Готовый dist/ скопировать на сервер в /var/lib/owm-server/dist/
```
