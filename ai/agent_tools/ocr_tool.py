import base64

from langchain_core.tools import tool

from services.ocr_service import ocr_service


@tool
def ocr_recognize(image_base64: str) -> str:
    """
    识别 base64 图片中的文字。仅当用户直接提供 base64 图片内容并要求 OCR 时使用。
    普通上传图片接口会先由 /qa/image 完成 OCR，再交给 Agent 解答。
    """
    try:
        image_bytes = base64.b64decode(image_base64, validate=True)
        result = ocr_service.recognize_image(image_bytes)
        return f"OCR原文：\n{result['ocrText']}\n平均置信度：{result['confidence']}"
    except Exception as exc:
        return f"OCR识别失败：{exc}"
