"""Resilient OpenAI-compatible chat client used by all AI capabilities."""

import json
import logging
import socket
import time
import urllib.error
import urllib.request
from threading import BoundedSemaphore, Lock
from typing import Any, Callable, Dict, List, Optional


logger = logging.getLogger(__name__)


class LlmCallError(RuntimeError):
    """A sanitized error suitable for logs, fallbacks, and HTTP responses."""

    def __init__(self, category: str, code: str, retryable: bool = False):
        super().__init__(code)
        self.category = category
        self.code = code
        self.retryable = retryable


class OpenAICompatibleClient:
    """One deep interface for bounded, observable external LLM requests."""

    def __init__(
        self,
        base_url: str,
        model: str,
        api_key: str,
        timeout_seconds: float,
        max_retries: int,
        max_concurrency: int,
        queue_timeout_seconds: float,
        circuit_failure_threshold: int,
        circuit_reset_seconds: float,
        opener: Callable[..., Any] = urllib.request.urlopen,
        sleeper: Callable[[float], None] = time.sleep,
        clock: Callable[[], float] = time.monotonic,
    ):
        self.base_url = base_url.rstrip("/")
        self.model = model
        self.api_key = api_key
        self.timeout_seconds = max(1.0, float(timeout_seconds))
        self.max_retries = max(0, int(max_retries))
        self.queue_timeout_seconds = max(0.0, float(queue_timeout_seconds))
        self.circuit_failure_threshold = max(1, int(circuit_failure_threshold))
        self.circuit_reset_seconds = max(1.0, float(circuit_reset_seconds))
        self._opener = opener
        self._sleeper = sleeper
        self._clock = clock
        self._semaphore = BoundedSemaphore(max(1, int(max_concurrency)))
        self._state_lock = Lock()
        self._consecutive_failures = 0
        self._circuit_open_until = 0.0

    def chat(self, messages: List[Dict[str, str]], temperature: float, max_tokens: int, operation: str) -> str:
        if not self.api_key:
            raise LlmCallError("configuration", "LLM_API_KEY_MISSING")
        self._ensure_circuit_closed()
        if not self._semaphore.acquire(timeout=self.queue_timeout_seconds):
            raise LlmCallError("overloaded", "LLM_CONCURRENCY_LIMIT")

        started = self._clock()
        try:
            return self._chat_with_retries(messages, temperature, max_tokens, operation, started)
        finally:
            self._semaphore.release()

    def _chat_with_retries(
        self,
        messages: List[Dict[str, str]],
        temperature: float,
        max_tokens: int,
        operation: str,
        started: float,
    ) -> str:
        last_error: Optional[LlmCallError] = None
        attempts = 0
        for attempt in range(self.max_retries + 1):
            attempts = attempt + 1
            try:
                answer = self._request(messages, temperature, max_tokens)
                self._record_success()
                logger.info(
                    "LLM call success operation=%s model=%s latencyMs=%s attempts=%s",
                    operation, self.model, int((self._clock() - started) * 1000), attempt + 1,
                )
                return answer
            except LlmCallError as exc:
                last_error = exc
                if not exc.retryable or attempt >= self.max_retries:
                    break
                self._sleeper(min(1.0, 0.2 * (2 ** attempt)))

        assert last_error is not None
        self._record_failure(last_error)
        logger.warning(
            "LLM call failed operation=%s model=%s latencyMs=%s category=%s errorCode=%s attempts=%s",
            operation, self.model, int((self._clock() - started) * 1000), last_error.category,
            last_error.code, attempts,
        )
        raise last_error

    def _request(self, messages: List[Dict[str, str]], temperature: float, max_tokens: int) -> str:
        payload = json.dumps(
            {
                "model": self.model,
                "messages": messages,
                "temperature": temperature,
                "max_tokens": max_tokens,
            },
            ensure_ascii=False,
        ).encode("utf-8")
        request = urllib.request.Request(
            self.base_url + "/chat/completions",
            data=payload,
            headers={"Authorization": f"Bearer {self.api_key}", "Content-Type": "application/json"},
            method="POST",
        )
        try:
            with self._opener(request, timeout=self.timeout_seconds) as response:
                raw = response.read().decode("utf-8")
        except urllib.error.HTTPError as exc:
            retryable = exc.code == 429 or exc.code >= 500
            raise LlmCallError("http_error", f"LLM_HTTP_{exc.code}", retryable) from exc
        except (urllib.error.URLError, socket.timeout, TimeoutError) as exc:
            category = "timeout" if isinstance(exc, (socket.timeout, TimeoutError)) else "connection"
            code = "LLM_TIMEOUT" if category == "timeout" else "LLM_CONNECTION_FAILED"
            raise LlmCallError(category, code, True) from exc
        except OSError as exc:
            raise LlmCallError("connection", "LLM_CONNECTION_FAILED", True) from exc

        try:
            parsed = json.loads(raw)
            choices = parsed.get("choices") or []
            content = choices[0].get("message", {}).get("content") if choices else None
        except (AttributeError, IndexError, TypeError, json.JSONDecodeError) as exc:
            raise LlmCallError("invalid_response", "LLM_INVALID_RESPONSE") from exc
        if not isinstance(content, str) or not content.strip():
            raise LlmCallError("invalid_response", "LLM_EMPTY_RESPONSE")
        return content.strip()

    def _ensure_circuit_closed(self) -> None:
        with self._state_lock:
            if self._clock() < self._circuit_open_until:
                raise LlmCallError("circuit_open", "LLM_CIRCUIT_OPEN")
            if self._circuit_open_until:
                self._circuit_open_until = 0.0
                self._consecutive_failures = 0

    def _record_success(self) -> None:
        with self._state_lock:
            self._consecutive_failures = 0
            self._circuit_open_until = 0.0

    def _record_failure(self, error: LlmCallError) -> None:
        if error.category in {"configuration", "invalid_response", "overloaded"}:
            return
        with self._state_lock:
            self._consecutive_failures += 1
            if self._consecutive_failures >= self.circuit_failure_threshold:
                self._circuit_open_until = self._clock() + self.circuit_reset_seconds
                logger.warning("LLM circuit opened failureCount=%s resetSeconds=%s", self._consecutive_failures, self.circuit_reset_seconds)
