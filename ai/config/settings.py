import json
import os
from pathlib import Path
from urllib.parse import urlparse


def _get_int(name: str, default: int) -> int:
    value = os.getenv(name)
    if value is None:
        return default
    try:
        return int(value)
    except ValueError:
        return default


def _get_float(name: str, default: float) -> float:
    value = os.getenv(name)
    if value is None:
        return default
    try:
        return float(value)
    except ValueError:
        return default


def _get_bool(name: str, default: bool) -> bool:
    value = os.getenv(name)
    if value is None:
        return default
    return value.strip().lower() in {"1", "true", "yes", "y", "on"}


def _normalize_ollama_host(value: str | None) -> str:
    host = (value or "http://127.0.0.1:11434").strip()
    if not host:
        return "http://127.0.0.1:11434"
    if "://" not in host:
        host = "http://" + host

    parsed = urlparse(host)
    if parsed.hostname in {"0.0.0.0", "::"}:
        port = f":{parsed.port}" if parsed.port else ""
        return f"{parsed.scheme}://127.0.0.1{port}"
    return host.rstrip("/")


def _read_auth_json_key() -> str:
    project_root = Path(__file__).resolve().parents[2]
    auth_paths = (
        project_root / "auth.json",
        project_root / "ai" / "auth.json",
    )
    for auth_path in auth_paths:
        if not auth_path.exists():
            continue
        try:
            data = json.loads(auth_path.read_text(encoding="utf-8-sig"))
        except (OSError, UnicodeError, json.JSONDecodeError):
            continue
        value = data.get("OPENAI_API_KEY") or data.get("EXTERNAL_LLM_API_KEY") or ""
        if value:
            return str(value).strip()
    return ""


class Settings:
    AI_SERVICE_VERSION = os.getenv("AI_SERVICE_VERSION", "2.0.0")

    # Ollama
    OLLAMA_HOST = _normalize_ollama_host(os.getenv("OLLAMA_HOST"))
    LLM_MODEL_NAME = os.getenv("LLM_MODEL_NAME", "qwen2.5:3b-instruct-q4_0")

    # External OpenAI-compatible provider for higher-quality planning.
    LEARNING_PLAN_PROVIDER = os.getenv("LEARNING_PLAN_PROVIDER", "external")
    EXTERNAL_LLM_BASE_URL = os.getenv("EXTERNAL_LLM_BASE_URL", "https://apihub.agnes-ai.com/v1").rstrip("/")
    EXTERNAL_LLM_MODEL = os.getenv("EXTERNAL_LLM_MODEL", "agnes-2.0-flash")
    EXTERNAL_LLM_API_KEY = (
        os.getenv("EXTERNAL_LLM_API_KEY")
        or os.getenv("OPENAI_API_KEY")
        or _read_auth_json_key()
        or ""
    )

    # LangChain Agent
    MAX_HISTORY_TURNS = _get_int("MAX_HISTORY_TURNS", 10)
    AGENT_TEMPERATURE = _get_float("AGENT_TEMPERATURE", 0.2)
    AGENT_MAX_TOKENS = _get_int("AGENT_MAX_TOKENS", 2000)
    AGENT_MAX_ITERATIONS = _get_int("AGENT_MAX_ITERATIONS", 6)

    # OCR
    OCR_LANG = os.getenv("OCR_LANG", "ch")
    OCR_USE_ANGLE_CLS = _get_bool("OCR_USE_ANGLE_CLS", True)
    OCR_SHOW_LOG = _get_bool("OCR_SHOW_LOG", False)


settings = Settings()
