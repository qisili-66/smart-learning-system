from typing import Any, Optional

from pydantic import BaseModel, Field

from utils.common_utils import get_now_timestamp


class Result(BaseModel):
    code: int = 200
    message: str = "操作成功"
    data: Optional[Any] = None
    timestamp: int = Field(default_factory=get_now_timestamp)
