FROM python:3.12-slim

ENV PYTHONDONTWRITEBYTECODE=1 \
    PYTHONUNBUFFERED=1

WORKDIR /app

RUN apt-get update \
    && apt-get install --yes --no-install-recommends libreoffice-writer fonts-nanum \
    && rm -rf /var/lib/apt/lists/*

RUN addgroup --system app && adduser --system --ingroup app app

COPY pyproject.toml uv.lock ./
RUN pip install --no-cache-dir uv==0.8.16 \
    && uv export --frozen --no-dev --no-emit-project --format requirements-txt --output-file requirements.txt \
    && pip uninstall --yes uv \
    && pip install --no-cache-dir -r requirements.txt
COPY app ./app
RUN pip install --no-cache-dir --no-deps .

USER app
EXPOSE 8100

CMD ["uvicorn", "app.main:app", "--host", "0.0.0.0", "--port", "8100"]
