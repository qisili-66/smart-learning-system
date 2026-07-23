import json
import logging
import time
from typing import Dict, List, Optional

from fastapi import FastAPI, File, Form, UploadFile
from fastapi.middleware.cors import CORSMiddleware

from config.settings import settings
from models.common import Result
from models.qa import AssessmentPaperRequest, LearningPathRequest, SubjectiveScoreRequest, TextQARequest
from services.agent.qa_agent import qa_agent
from services.ocr_service import ocr_service
from utils.common_utils import generate_uuid


logger = logging.getLogger(__name__)


app = FastAPI(title="Smart Learning AI Service", version=settings.AI_SERVICE_VERSION)

app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)


@app.get("/health", response_model=Result, summary="服务健康检查")
def health_check():
    return Result(
        data={
            "status": "running",
            "version": settings.AI_SERVICE_VERSION,
            "agent": "Learning QA Agent",
            "model": settings.EXTERNAL_LLM_MODEL,
            "provider": "openai-compatible",
            "tools": ["math_calculate", "ocr_recognize"],
        }
    )


@app.post("/qa/text", response_model=Result, summary="文本智能答疑")
def text_qa(request: TextQARequest):
    try:
        result = qa_agent.ask(
            question=request.question,
            conversation_id=request.conversationId,
            subject=request.subject,
            history=[item.model_dump() for item in request.history],
            confirm_answer=request.confirmAnswer,
        )
        return Result(data=result)
    except ValueError as exc:
        return Result(code=400, message=str(exc), data=None)
    except Exception as exc:
        return Result(code=500, message=f"答疑服务异常：{exc}", data=None)


@app.post("/assessment/subjective-score", response_model=Result, summary="AI主观题语义评分")
def subjective_score(request: SubjectiveScoreRequest):
    started = time.perf_counter()
    try:
        result = qa_agent.score_subjective_answer(
            question_text=request.questionText,
            reference_answer=request.referenceAnswer,
            student_answer=request.studentAnswer,
            scoring_points=request.scoringPoints,
            max_score=request.maxScore,
            subject=request.subject,
            knowledge_point=request.knowledgePoint,
        )
        latency_ms = int((time.perf_counter() - started) * 1000)
        result["latencyMs"] = latency_ms
        result.setdefault("operation", "subjective_score")
        result.setdefault("endpoint", "/assessment/subjective-score")
        logger.info(
            "AI subjective score success latencyMs=%s provider=%s model=%s fallback=%s failureCategory=%s",
            latency_ms,
            result.get("provider", ""),
            result.get("model", ""),
            result.get("fallback", False),
            result.get("failureCategory", ""),
        )
        return Result(data=result)
    except Exception as exc:
        return Result(code=500, message=f"主观题评分异常：{exc}", data=None)


@app.post("/study-plan/path", response_model=Result, summary="AI生成可执行学习路径")
def study_plan_path(request: LearningPathRequest):
    try:
        result = qa_agent.generate_learning_path(request.model_dump())
        return Result(data=result)
    except Exception as exc:
        return Result(code=500, message=f"学习路径生成异常：{exc}", data=None)


@app.post("/assessment/generate-paper", response_model=Result, summary="AI按年级和知识范围生成测评试卷")
def generate_assessment_paper(request: AssessmentPaperRequest):
    started = time.perf_counter()
    try:
        result = qa_agent.generate_assessment_paper(request.model_dump())
        latency_ms = int((time.perf_counter() - started) * 1000)
        result["latencyMs"] = latency_ms
        result.setdefault("operation", "generate_assessment_paper")
        result.setdefault("endpoint", "/assessment/generate-paper")
        return Result(data=result)
    except Exception as exc:
        return Result(code=500, message=f"测评试卷生成异常：{exc}", data=None)


@app.post("/qa/image", response_model=Result, summary="图片OCR智能答疑")
async def image_qa(
    file: UploadFile = File(...),
    conversationId: Optional[str] = Form(default=None),
    subject: Optional[str] = Form(default=None),
    history: Optional[str] = Form(default=None),
    confirmAnswer: bool = Form(default=False),
):
    try:
        image_bytes = await file.read()
        ocr_result = ocr_service.recognize_image(image_bytes, conversationId, subject)
        ocr_text = ocr_result["ocrText"].strip()
        session_id = ocr_result["conversationId"]

        if not ocr_text:
            return Result(
                data={
                    "conversationId": session_id,
                    "answerId": session_id,
                    "answer": "未识别到题目文字。请重新上传更清晰、正向、文字完整的题目图片。",
                    "knowledgePoint": subject or "通用知识点",
                    "steps": ["检查图片是否清晰", "确认题目文字完整入镜", "重新上传后再试"],
                    "extendTips": "拍照时尽量避免反光、裁切和倾斜。",
                    "ocrText": "",
                    "wordCount": 0,
                    "confidence": 0,
                    "ocrLines": [],
                    "usedTools": ["ocr_recognize"],
                    "toolCalls": [],
                },
            )

        answer_result = qa_agent.ask(
            question=ocr_text,
            conversation_id=session_id,
            subject=subject,
            history=parse_history(history),
            confirm_answer=confirmAnswer,
        )
        if not answer_result.get("answer"):
            answer_result["answer"] = "已识别到图片文字，但 AI 暂未生成解答。请稍后重试或改用文字提问。"

        return Result(
            data={
                **answer_result,
                "ocrText": ocr_text,
                "wordCount": ocr_result["wordCount"],
                "confidence": ocr_result["confidence"],
                "ocrLines": ocr_result["lines"],
            }
        )
    except ValueError as exc:
        return Result(code=400, message=str(exc), data=None)
    except Exception as exc:
        return Result(code=500, message=f"图片答疑异常：{exc}", data=None)


@app.post("/qa/voice", response_model=Result, summary="语音ASR智能答疑")
async def voice_qa(
    file: UploadFile = File(...),
    conversationId: Optional[str] = Form(default=None),
    subject: Optional[str] = Form(default=None),
    recognizedText: Optional[str] = Form(default=None),
    correctedText: Optional[str] = Form(default=None),
    history: Optional[str] = Form(default=None),
    confirmAnswer: bool = Form(default=False),
):
    try:
        await file.read()
        session_id = (conversationId or "").strip() or generate_uuid()
        recognized = (recognizedText or "").strip()
        corrected = (correctedText or "").strip()
        question = corrected or recognized

        if not question:
            return Result(
                data={
                    "conversationId": session_id,
                    "answerId": session_id,
                    "answer": "暂未收到可用的语音识别文本。请在浏览器完成语音识别后修正文字，再发送语音答疑。",
                    "knowledgePoint": subject or "通用知识点",
                    "steps": ["点击录音", "完成浏览器语音识别", "检查并修正识别文本", "重新发送"],
                    "extendTips": "当前服务端未内置离线 ASR 模型，语音识别由前端 Web Speech API 完成，后端负责留存音频和修正后的文字。",
                    "recognizedText": recognized,
                    "correctedText": corrected,
                    "audioAsrStatus": "manual_correction_required",
                    "usedTools": [],
                    "toolCalls": [],
                    "requiresConfirmation": False,
                    "confirmedAnswer": False,
                }
            )

        answer_result = qa_agent.ask(
            question=question,
            conversation_id=session_id,
            subject=subject,
            history=parse_history(history),
            confirm_answer=confirmAnswer,
        )
        return Result(
            data={
                **answer_result,
                "recognizedText": recognized,
                "correctedText": corrected or recognized,
                "audioAsrStatus": "browser_asr_corrected" if corrected else "browser_asr",
            }
        )
    except ValueError as exc:
        return Result(code=400, message=str(exc), data=None)
    except Exception as exc:
        return Result(code=500, message=f"语音答疑异常：{exc}", data=None)


def parse_history(raw_history: Optional[str]) -> List[Dict[str, str]]:
    if not raw_history:
        return []
    try:
        parsed = json.loads(raw_history)
    except json.JSONDecodeError:
        return []
    if not isinstance(parsed, list):
        return []
    messages: List[Dict[str, str]] = []
    for item in parsed:
        if not isinstance(item, dict):
            continue
        role = str(item.get("role") or "").strip()
        content = str(item.get("content") or "").strip()
        if role and content:
            messages.append({"role": role, "content": content})
    return messages[-12:]
