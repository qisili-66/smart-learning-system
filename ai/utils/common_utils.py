import time
import uuid


def get_now_timestamp() -> int:
    return int(time.time() * 1000)


def generate_uuid() -> str:
    return uuid.uuid4().hex
