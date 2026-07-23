import json
import os
from pathlib import Path


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

    # OpenAI-compatible provider. The service always calls the configured external API.
    EXTERNAL_LLM_BASE_URL = os.getenv(
        "EXTERNAL_LLM_BASE_URL",
        "https://ws-2gca4xhi5wbpqj12.cn-beijing.maas.aliyuncs.com/compatible-mode/v1",
    ).rstrip("/")
    EXTERNAL_LLM_MODEL = os.getenv("EXTERNAL_LLM_MODEL", "qwen3.7-max")
    EXTERNAL_LLM_API_KEY = (
        os.getenv("EXTERNAL_LLM_API_KEY")
        or os.getenv("OPENAI_API_KEY")
        or _read_auth_json_key()
        or ""
    )

    # Generic LLM generation controls.
    MAX_HISTORY_TURNS = _get_int("MAX_HISTORY_TURNS", 10)
    AGENT_TEMPERATURE = _get_float("AGENT_TEMPERATURE", 0.2)
    AGENT_MAX_TOKENS = _get_int("AGENT_MAX_TOKENS", 2000)
    AGENT_MAX_ITERATIONS = _get_int("AGENT_MAX_ITERATIONS", 6)

    # OCR
    OCR_LANG = os.getenv("OCR_LANG", "ch")
    OCR_USE_ANGLE_CLS = _get_bool("OCR_USE_ANGLE_CLS", True)
    OCR_SHOW_LOG = _get_bool("OCR_SHOW_LOG", False)


settings = Settings()
