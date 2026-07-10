import io
import os
from typing import Any, Dict, List

import numpy as np
from PIL import Image, UnidentifiedImageError

from config.settings import settings
from utils.common_utils import generate_uuid


class OCRService:
    def __init__(self):
        self._model = None

    @property
    def model(self):
        if self._model is None:
            self._prepare_runtime_home()
            try:
                from paddleocr import PaddleOCR
            except ImportError as exc:
                raise RuntimeError(f"PaddleOCR 或其依赖导入失败：{exc}") from exc

            self._model = PaddleOCR(
                use_angle_cls=settings.OCR_USE_ANGLE_CLS,
                lang=settings.OCR_LANG,
                show_log=settings.OCR_SHOW_LOG,
            )
        return self._model

    def _prepare_runtime_home(self) -> None:
        runtime_home = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", ".runtime"))
        os.makedirs(runtime_home, exist_ok=True)
        os.environ["USERPROFILE"] = runtime_home
        os.environ["HOME"] = runtime_home
        os.environ.setdefault("XDG_CACHE_HOME", os.path.join(runtime_home, ".cache"))

    def recognize_image(
        self,
        image_bytes: bytes,
        conversation_id: str | None = None,
        subject: str | None = None,
    ) -> Dict[str, Any]:
        if not image_bytes:
            raise ValueError("图片文件为空")

        session_id = conversation_id or generate_uuid()
        image_array = self._load_image(image_bytes)
        raw_result = self.model.ocr(image_array, cls=True)

        lines = self._parse_result(raw_result)
        full_text = "\n".join(item["text"] for item in lines)
        avg_confidence = 0.0
        if lines:
            avg_confidence = round(sum(item["confidence"] for item in lines) / len(lines), 4)

        return {
            "conversationId": session_id,
            "ocrText": full_text,
            "wordCount": len(lines),
            "confidence": avg_confidence,
            "lines": lines,
            "subject": subject or "通用",
        }

    def _load_image(self, image_bytes: bytes) -> np.ndarray:
        try:
            image = Image.open(io.BytesIO(image_bytes)).convert("RGB")
        except UnidentifiedImageError as exc:
            raise ValueError("无法识别图片格式") from exc
        return np.asarray(image)

    def _parse_result(self, raw_result: Any) -> List[Dict[str, Any]]:
        lines: List[Dict[str, Any]] = []
        if not raw_result:
            return lines

        page_result = raw_result[0] if isinstance(raw_result, list) else raw_result
        if not page_result:
            return lines

        for item in page_result:
            try:
                text = str(item[1][0]).strip()
                confidence = float(item[1][1])
            except (IndexError, TypeError, ValueError):
                continue
            if text:
                lines.append({"text": text, "confidence": confidence})
        return lines


ocr_service = OCRService()
