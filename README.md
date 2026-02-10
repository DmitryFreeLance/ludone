# Rolls Roms Telegram Bot

## Что реализовано
- Telegram bot на Java (telegrambots 6.9.7.1)
- Каталог с пагинацией, инлайн-кнопками и кэшированием медиа
- Корзина, поштучный ввод количества, телефон, адрес
- Инвойсы Telegram с YooKassa (через `SendInvoice`) и отправкой чеков (receipt)
- Админ панель с добавлением админов
- SQLite для хранения админов и заказов
- Dockerfile + пример `docker run`

## Быстрый старт локально
```bash
mvn -q -DskipTests package
BOT_TOKEN=xxx \
BOT_USERNAME=your_bot \
PAYMENT_PROVIDER_TOKEN=xxx \
BOT_ADMIN_IDS=123456789 \
java -jar target/rollsroms-bot-1.0.0.jar
```

## Docker
```bash
docker build -t rollsroms-bot .

docker run -d --name rollsroms-bot --restart unless-stopped \
  -e BOT_TOKEN=xxx \
  -e BOT_USERNAME=your_bot \
  -e PAYMENT_PROVIDER_TOKEN=xxx \
  -e BOT_ADMIN_IDS=123456789 \
  -e DB_PATH=/app/data/bot.db \
  -e TAX_SYSTEM_CODE=1 \
  -v $(pwd)/data:/app/data \
  rollsroms-bot
```

## Переменные окружения
- `BOT_TOKEN` – токен бота
- `BOT_USERNAME` – username бота без `@`
- `PAYMENT_PROVIDER_TOKEN` – провайдер токен YooKassa для Telegram Payments
- `PAYMENT_CURRENCY` – валюта (по умолчанию `RUB`)
- `BOT_ADMIN_IDS` – список Telegram ID админов через запятую
- `DB_PATH` – путь к SQLite (по умолчанию `./data/bot.db`)
- `TAX_SYSTEM_CODE` – код системы налогообложения для чеков (по умолчанию `1`)

## Важно про чеки YooKassa
Для отправки чеков бот формирует `provider_data` с `receipt` и `customer.phone`.
Проверьте, что номер телефона вводится в корректном формате (например, `+79990000000`).
При необходимости можно расширить `ReceiptBuilder` под вашу кассу (НДС, предмет расчета, режим оплаты).

## Медиа
Файлы каталога лежат в `src/main/resources/images/1.jpg` и `src/main/resources/images/1s.jpg`.
Сейчас это технические заглушки 1x1. Замените их на реальные изображения с такими же именами.

## Админ панель
Команда `/admin` доступна только администраторам.
В панели есть кнопка добавления нового админа по Telegram ID.
