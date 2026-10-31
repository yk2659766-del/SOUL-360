FROM python:3.12-slim
WORKDIR /app
COPY gateway.py .
ENV PORT=8080
EXPOSE 8080
USER nobody
CMD ["python", "gateway.py"]
