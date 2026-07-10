from threading import RLock
from typing import Dict, List

from langchain_community.chat_message_histories import ChatMessageHistory
from langchain_core.messages import BaseMessage


class ConversationMemoryManager:
    def __init__(self, max_turns: int):
        self.max_messages = max(1, max_turns) * 2
        self._store: Dict[str, ChatMessageHistory] = {}
        self._lock = RLock()

    def get_history(self, session_id: str) -> ChatMessageHistory:
        if not session_id:
            raise ValueError("session_id 不能为空")

        with self._lock:
            history = self._store.setdefault(session_id, ChatMessageHistory())
            self._trim(history)
            return history

    def stats(self, session_id: str) -> dict:
        with self._lock:
            history = self._store.get(session_id)
            message_count = len(history.messages) if history else 0
            return {
                "messageCount": message_count,
                "historyTurns": message_count // 2,
                "maxHistoryTurns": self.max_messages // 2,
            }

    def clear(self, session_id: str) -> bool:
        with self._lock:
            return self._store.pop(session_id, None) is not None

    def set_messages(self, session_id: str, messages: List[BaseMessage]) -> None:
        if not session_id:
            raise ValueError("session_id 不能为空")

        with self._lock:
            history = self._store.setdefault(session_id, ChatMessageHistory())
            history.messages = list(messages)[-self.max_messages :]
            self._trim(history)

    def _trim(self, history: ChatMessageHistory) -> None:
        if len(history.messages) > self.max_messages:
            history.messages = history.messages[-self.max_messages :]
