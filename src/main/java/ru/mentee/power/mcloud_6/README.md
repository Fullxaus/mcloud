# Docker-образы для Java-приложений

В этой папке собраны все файлы задания в одном месте.

## Структура проекта

- `NumberGuessGame.java` - интерактивная игра "Угадай число"
- `TextAnalyzer.java` - утилита анализа текста из командной строки
- `docker/mcloud_6/Dockerfile.game` - образ для игры
- `docker/mcloud_6/Dockerfile.analyzer` - образ для анализатора

## Сборка образов

```bash
cd <корень проекта>
docker build -f docker/mcloud_6/Dockerfile.game -t game:v1.0 .
```

```bash
docker build -f docker/mcloud_6/Dockerfile.analyzer -t analyzer:v1.0 .
```

## Запуск контейнеров

### Игра (интерактивный режим)

```bash
docker run -it game:v1.0
```

### Анализатор текста (аргументы передаются в entrypoint)

```bash
docker run analyzer:v1.0 "текст для анализа 123 текст"
```
