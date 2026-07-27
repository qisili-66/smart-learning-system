import time
from threading import RLock
from typing import Dict, List

from langchain_community.chat_message_histories import ChatMessageHistory
from langchain_core.messages import BaseMessage


class ConversationMemoryManager:
    def __init__(self, max_turns: int, max_sessions: int = 1000, ttl_seconds: int = 3600):
        self.max_messages = max(1, max_turns) * 2
        self.max_sessions = max(1, max_sessions)
        self.ttl_seconds = max(1, ttl_seconds)
        self._store: Dict[str, ChatMessageHistory] = {}
        self._last_access: Dict[str, float] = {}
        self._lock = RLock()

    def get_history(self, session_id: str) -> ChatMessageHistory:
        if not session_id:
            raise ValueError("session_id 不能为空")

        with self._lock:
            self._evict_expired()
            self._evict_overflow()
            history = self._store.setdefault(session_id, ChatMessageHistory())
            self._last_access[session_id] = time.monotonic()
            self._trim(history)
            return history

    def stats(self, session_id: str) -> dict:
        with self._lock:
            self._evict_expired()
            history = self._store.get(session_id)
            message_count = len(history.messages) if history else 0
            return {
                "messageCount": message_count,
                "historyTurns": message_count // 2,
                "maxHistoryTurns": self.max_messages // 2,
                "activeSessions": len(self._store),
                "maxSessions": self.max_sessions,
            }

    def clear(self, session_id: str) -> bool:
        with self._lock:
            self._last_access.pop(session_id, None)
            return self._store.pop(session_id, None) is not None

    def set_messages(self, session_id: str, messages: List[BaseMessage]) -> None:
        if not session_id:
            raise ValueError("session_id 不能为空")

        with self._lock:
            self._evict_expired()
            self._evict_overflow(exclude_session_id=session_id)
            history = self._store.setdefault(session_id, ChatMessageHistory())
            history.messages = list(messages)[-self.max_messages :]
            self._last_access[session_id] = time.monotonic()
            self._trim(history)

    def _trim(self, history: ChatMessageHistory) -> None:
        if len(history.messages) > self.max_messages:
            history.messages = history.messages[-self.max_messages :]

    def _evict_expired(self) -> None:
        cutoff = time.monotonic() - self.ttl_seconds
        expired = [session_id for session_id, accessed in self._last_access.items() if accessed < cutoff]
        for session_id in expired:
            self._store.pop(session_id, None)
            self._last_access.pop(session_id, None)

    def _evict_overflow(self, exclude_session_id: str = "") -> None:
        while len(self._store) >= self.max_sessions:
            candidates = [item for item in self._last_access.items() if item[0] != exclude_session_id]
            if not candidates:
                return
            session_id, _ = min(candidates, key=lambda item: item[1])
            self._store.pop(session_id, None)
            self._last_access.pop(session_id, None)
