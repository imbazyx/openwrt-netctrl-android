# Исправление SERVER_URL для owm-agent

## Проблема
Агент owm-agent на роутере может не подключаться к серверу, если переменная окружения `SERVER_URL` не установлена или установлена неверно.

## Диагностика
Подключитесь к роутеру по SSH и выполните:

```bash
# Проверка запущенных процессов
ps | grep owm-agent

# Проверка переменных окружения процесса (если известен PID)
cat /proc/<PID>/environ | tr '\0' '\n' | grep SERVER

# Проверка логов
logread | grep owm-agent | tail -20
```

## Решение

### Вариант 1: Запуск с явной переменной окружения
```bash
# Остановить текущий процесс
kill $(pgrep owm-agent)

# Запустить с правильным URL
SERVER_URL="ws://95.174.102.25:9090/agent" /usr/local/bin/owm-agent &
```

### Вариант 2: Правка init-скрипта (/etc/init.d/owm-agent)
Найдите строку запуска и добавьте export:
```bash
#!/bin/sh /etc/rc.common

START=99

start() {
    export SERVER_URL="ws://95.174.102.25:9090/agent"
    /usr/local/bin/owm-agent &
}

stop() {
    kill $(pgrep owm-agent)
}
```

После правки:
```bash
/etc/init.d/owm-agent restart
```

## Проверка
Через 30 секунд после перезапуска проверьте:
1. В логах роутера: `logread | grep owm-agent | tail -5`
2. В API сервера: агент должен иметь статус `online: true`
