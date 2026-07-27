import json
import socket
import sys
import unittest
from pathlib import Path


sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

from services.llm_client import LlmCallError, OpenAICompatibleClient


class FakeResponse:
    def __init__(self, payload):
        self.payload = payload

    def read(self):
        return self.payload.encode("utf-8")

    def __enter__(self):
        return self

    def __exit__(self, *_):
        return False


def client(opener, **overrides):
    options = {
        "base_url": "https://llm.test/v1",
        "model": "test-model",
        "api_key": "test-key",
        "timeout_seconds": 1,
        "max_retries": 1,
        "max_concurrency": 2,
        "queue_timeout_seconds": 0,
        "circuit_failure_threshold": 2,
        "circuit_reset_seconds": 30,
        "opener": opener,
        "sleeper": lambda _: None,
    }
    options.update(overrides)
    return OpenAICompatibleClient(**options)


class OpenAICompatibleClientTests(unittest.TestCase):
    def test_retries_timeout_then_returns_valid_content(self):
        calls = []

        def opener(*_args, **_kwargs):
            calls.append(1)
            if len(calls) == 1:
                raise socket.timeout("timed out")
            return FakeResponse(json.dumps({"choices": [{"message": {"content": "答案"}}]}))

        result = client(opener).chat([{"role": "user", "content": "问题"}], 0.2, 100, "qa_text")

        self.assertEqual("答案", result)
        self.assertEqual(2, len(calls))

    def test_invalid_response_is_rejected_without_retry(self):
        calls = []

        def opener(*_args, **_kwargs):
            calls.append(1)
            return FakeResponse("not-json")

        with self.assertRaisesRegex(LlmCallError, "LLM_INVALID_RESPONSE") as context:
            client(opener).chat([{"role": "user", "content": "问题"}], 0.2, 100, "qa_text")

        self.assertEqual("invalid_response", context.exception.category)
        self.assertEqual(1, len(calls))

    def test_circuit_opens_after_configured_consecutive_failures(self):
        calls = []

        def opener(*_args, **_kwargs):
            calls.append(1)
            raise socket.timeout("timed out")

        resilient_client = client(opener, max_retries=0, circuit_failure_threshold=1)
        with self.assertRaisesRegex(LlmCallError, "LLM_TIMEOUT"):
            resilient_client.chat([{"role": "user", "content": "问题"}], 0.2, 100, "qa_text")
        with self.assertRaisesRegex(LlmCallError, "LLM_CIRCUIT_OPEN") as context:
            resilient_client.chat([{"role": "user", "content": "问题"}], 0.2, 100, "qa_text")

        self.assertEqual("circuit_open", context.exception.category)
        self.assertEqual(1, len(calls))

    def test_concurrency_limit_fails_fast_when_queue_is_full(self):
        def opener(*_args, **_kwargs):
            self.fail("network call must not occur when the queue is full")

        resilient_client = client(opener, max_concurrency=1, queue_timeout_seconds=0)
        self.assertTrue(resilient_client._semaphore.acquire(blocking=False))
        try:
            with self.assertRaisesRegex(LlmCallError, "LLM_CONCURRENCY_LIMIT") as context:
                resilient_client.chat([{"role": "user", "content": "问题"}], 0.2, 100, "qa_text")
        finally:
            resilient_client._semaphore.release()

        self.assertEqual("overloaded", context.exception.category)


if __name__ == "__main__":
    unittest.main()
