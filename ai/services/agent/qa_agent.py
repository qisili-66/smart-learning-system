import json
import logging
import re
import urllib.error
import urllib.request
from typing import Any, Dict, List, Optional

from langchain_core.messages import AIMessage, BaseMessage, HumanMessage, SystemMessage

from config.settings import settings
from services.agent.memory import ConversationMemoryManager
from agent_tools.calculator_tool import math_calculate
from agent_tools.ocr_tool import ocr_recognize
from utils.common_utils import generate_uuid


logger = logging.getLogger(__name__)


SYSTEM_PROMPT = """
你是智慧学习系统的专业学科答疑 Agent，面向中小学生提供清晰、准确、可复盘的解答。

工作规则：
1. 普通学习提问可以包含核心知识点、解题步骤、结论和拓展提示。
2. 涉及算术、平方根、幂、三角函数、对数、单位换算等数值计算时，必须调用 math_calculate 工具。
3. 用户直接提供 base64 图片并要求识别时，调用 ocr_recognize 工具。
4. 对多轮对话要结合历史上下文，但不要编造用户没有给出的条件。
5. 如果题目信息不足，先说明缺少的信息，再给出可继续推进的思路。
6. 对疑似作业、考试、测验、试卷原题，未经二次确认时只给思路、关键步骤和自查问题，不直接输出标准答案。
"""


class LearningQAAgent:
    def __init__(self):
        self.tools = [math_calculate, ocr_recognize]
        self.memory = ConversationMemoryManager(settings.MAX_HISTORY_TURNS)

    def ask(
        self,
        question: str,
        conversation_id: Optional[str] = None,
        subject: Optional[str] = None,
        history: Optional[List[Dict[str, str]]] = None,
        confirm_answer: bool = False,
    ) -> Dict[str, Any]:
        clean_question = (question or "").strip()
        if not clean_question:
            raise ValueError("问题不能为空")

        session_id = (conversation_id or "").strip() or generate_uuid()
        clean_subject = (subject or "").strip()
        requires_confirmation = self._requires_confirmation(clean_question)
        agent_input = self._build_input(clean_question, clean_subject, requires_confirmation, confirm_answer)
        base_messages = self._history_messages(history)
        if base_messages is None:
            base_messages = self.memory.get_history(session_id).messages

        if requires_confirmation and not confirm_answer:
            answer = self._guided_answer(clean_question, clean_subject)
            self.memory.set_messages(session_id, [*base_messages, HumanMessage(content=agent_input), AIMessage(content=answer)])
            return {
                "conversationId": session_id,
                "answerId": generate_uuid(),
                "answer": answer,
                "knowledgePoint": clean_subject or "通用知识点",
                "steps": self._extract_steps(answer),
                "extendTips": "先独立完成一版答案，再用二次确认核对完整解法和最终结论。",
                "usedTools": [],
                "toolCalls": [],
                "memory": self.memory.stats(session_id),
                "model": self._active_model(),
                "provider": self._active_provider(),
                "fallback": False,
                "failureCategory": "",
                "errorCode": "",
                "requiresConfirmation": True,
                "confirmedAnswer": False,
                "confirmationPrompt": "如果你已经尝试过并需要核对完整答案，请点击“确认查看完整答案”。",
                "guardrailReason": "HOMEWORK_EXAM_CONFIRMATION",
            }

        tool_calls = self._manual_tool_calls(clean_question)
        if tool_calls:
            agent_input = agent_input + "\n\n可用工具结果：\n" + "\n".join(
                str(item.get("toolOutput") or "") for item in tool_calls
            )

        input_messages = [SystemMessage(content=SYSTEM_PROMPT), *base_messages, HumanMessage(content=agent_input)]
        answer = self._invoke_llm(input_messages)

        self.memory.set_messages(session_id, [*base_messages, HumanMessage(content=agent_input), AIMessage(content=answer)])

        return {
            "conversationId": session_id,
            "answerId": generate_uuid(),
            "answer": answer,
            "knowledgePoint": clean_subject or "通用知识点",
            "steps": self._extract_steps(answer),
            "extendTips": "建议再完成 2-3 道同类型题目，检查是否真正掌握了解题方法。",
            "usedTools": [item["toolName"] for item in tool_calls if item.get("toolName")],
            "toolCalls": tool_calls,
            "memory": self.memory.stats(session_id),
            "model": self._active_model(),
            "provider": self._active_provider(),
            "fallback": False,
            "failureCategory": "",
            "errorCode": "",
            "requiresConfirmation": False,
            "confirmedAnswer": bool(confirm_answer and requires_confirmation),
        }

    def score_subjective_answer(
        self,
        question_text: str,
        reference_answer: str,
        student_answer: str,
        scoring_points: Optional[List[str]] = None,
        max_score: float = 100.0,
        subject: Optional[str] = None,
        knowledge_point: Optional[str] = None,
    ) -> Dict[str, Any]:
        clean_answer = (student_answer or "").strip()
        max_score_value = max(0.0, float(max_score or 0.0))
        points = self._normalize_scoring_points(scoring_points, reference_answer)
        if not clean_answer or max_score_value <= 0:
            return {
                "score": 0,
                "maxScore": round(max_score_value, 2),
                "scoreRatio": 0,
                "confidence": 100,
                "matchedPoints": [],
                "missingPoints": points,
                "comment": "学生未作答，主观题得 0 分。",
                "scoringMode": "blank_answer",
                "model": self._active_model(),
                "provider": "rule",
                "fallback": False,
                "failureCategory": "",
                "errorCode": "",
            }

        prompt = self._build_subjective_score_prompt(
            question_text=question_text,
            reference_answer=reference_answer,
            student_answer=clean_answer,
            scoring_points=points,
            subject=subject,
            knowledge_point=knowledge_point,
        )
        try:
            raw_answer = self._invoke_llm(
                [
                    SystemMessage(
                        content=(
                            "你是严格但公平的主观题阅卷老师。只输出一个 JSON 对象，"
                            "不要输出 Markdown、代码块或多余解释。"
                        )
                    ),
                    HumanMessage(content=prompt),
                ]
            )
            parsed = self._parse_score_json(raw_answer)
            if parsed:
                return self._score_payload_from_model(parsed, points, max_score_value)
        except Exception as exc:
            logger.warning(
                "AI subjective scoring call failed model=%s category=call_error",
                self._active_model(),
                exc_info=True,
            )
            return self._heuristic_subjective_score(
                reference_answer,
                clean_answer,
                points,
                max_score_value,
                failure_category="call_error",
                error_code="LLM_API_CALL_FAILED",
            )

        return self._heuristic_subjective_score(
            reference_answer,
            clean_answer,
            points,
            max_score_value,
            failure_category="invalid_response",
            error_code="MODEL_JSON_PARSE_FAILED",
        )

    def generate_learning_path(self, request: Dict[str, Any]) -> Dict[str, Any]:
        prompt = self._build_learning_path_prompt(request)
        if settings.EXTERNAL_LLM_API_KEY:
            try:
                content = self._invoke_external_chat(prompt)
                parsed = self._parse_score_json(content)
                if parsed:
                    return self._normalize_learning_path(parsed, request, self._active_provider(), self._active_model())
            except Exception:
                return self._fallback_learning_path(request, "external_failed")

        return self._fallback_learning_path(request, "api_key_missing")

    def generate_assessment_paper(self, request: Dict[str, Any]) -> Dict[str, Any]:
        prompt = self._build_assessment_paper_prompt(request)
        if settings.EXTERNAL_LLM_API_KEY:
            try:
                content = self._invoke_external_chat_messages(
                    [
                        SystemMessage(
                            content=(
                                "你是广东中小学测评命题 Agent。你会参考公开教材要求、"
                                "公开考试题型和真实卷面风格，生成原创题。只输出 JSON，"
                                "不要输出 Markdown、代码块或多余解释。"
                            )
                        ),
                        HumanMessage(content=prompt),
                    ],
                    max_tokens=max(settings.AGENT_MAX_TOKENS, 4200),
                )
                parsed = self._parse_score_json(content)
                if parsed:
                    normalized = self._normalize_assessment_paper(parsed, request, self._active_provider(), self._active_model())
                    if normalized.get("questions"):
                        return normalized
            except Exception:
                logger.warning("AI assessment paper generation failed", exc_info=True)
                return self._fallback_assessment_paper(request, "external_failed")

        return self._fallback_assessment_paper(request, "api_key_missing")

    def _build_input(self, question: str, subject: str, requires_confirmation: bool, confirm_answer: bool) -> str:
        prefixes: List[str] = []
        if subject:
            prefixes.append(f"学科：{subject}")
        if requires_confirmation and confirm_answer:
            prefixes.append("用户已二次确认需要核对完整解法，可以给出最终结论，但仍要先说明思路。")
        prefixes.append(f"问题：{question}")
        return "\n".join(prefixes)

    def _extract_answer(self, messages: List[BaseMessage]) -> str:
        for message in reversed(messages):
            if isinstance(message, AIMessage) and message.content:
                return self._content_to_text(message.content)
        return ""

    def _invoke_llm(self, messages: List[BaseMessage]) -> str:
        return self._invoke_external_chat_messages(messages)

    def _chat_message(self, message: BaseMessage) -> Dict[str, str]:
        if isinstance(message, SystemMessage):
            role = "system"
        elif isinstance(message, AIMessage):
            role = "assistant"
        else:
            role = "user"
        return {
            "role": role,
            "content": self._content_to_text(message.content),
        }

    def _active_provider(self) -> str:
        return "openai-compatible"

    def _active_model(self) -> str:
        return settings.EXTERNAL_LLM_MODEL

    def _content_to_text(self, content: Any) -> str:
        if isinstance(content, str):
            return content
        if isinstance(content, list):
            parts = []
            for item in content:
                if isinstance(item, dict):
                    parts.append(str(item.get("text") or item.get("content") or ""))
                else:
                    parts.append(str(item))
            return "\n".join(part for part in parts if part)
        return str(content)

    def _extract_steps(self, answer: str) -> List[str]:
        steps: List[str] = []
        for raw_line in answer.splitlines():
            line = raw_line.strip()
            if line.startswith(("1.", "2.", "3.", "4.", "5.", "步骤", "第一步", "第二步", "第三步")):
                steps.append(line)
        return steps or ["详见 answer 字段中的完整解题过程"]

    def _manual_tool_calls(self, question: str) -> List[Dict[str, Any]]:
        expression = self._extract_math_expression(question)
        if not expression:
            return []
        output = math_calculate.invoke({"expression": expression})
        return [
            {
                "toolCallId": generate_uuid(),
                "toolName": "math_calculate",
                "toolInput": {"expression": expression},
                "toolOutput": self._content_to_text(output),
            }
        ]

    def _extract_math_expression(self, question: str) -> Optional[str]:
        text = question.strip()
        if not re.search(r"\d", text):
            return None
        candidates = re.findall(r"[\d\s+\-*/×÷^().（）]+", text)
        candidates = [item.strip() for item in candidates if re.search(r"\d", item) and re.search(r"[+\-*/×÷^]", item)]
        if not candidates:
            return None
        expression = max(candidates, key=len)
        expression = expression.strip("，。；;：:= ")
        return expression if len(expression) >= 3 else None

    def _history_messages(self, history: Optional[List[Dict[str, str]]]) -> Optional[List[BaseMessage]]:
        if history is None:
            return None
        messages: List[BaseMessage] = []
        for item in history[-self.memory.max_messages :]:
            role = str(item.get("role") or "").strip().lower()
            content = str(item.get("content") or "").strip()
            if not content:
                continue
            if role in {"assistant", "ai"}:
                messages.append(AIMessage(content=content))
            elif role in {"user", "human"}:
                messages.append(HumanMessage(content=content))
        return messages

    def _requires_confirmation(self, question: str) -> bool:
        normalized = question.replace(" ", "").lower()
        risk_keywords = [
            "作业",
            "考试",
            "测验",
            "试卷",
            "原题",
            "真题",
            "标准答案",
            "直接给答案",
            "答案是什么",
            "帮我做",
            "拍照搜题",
            "选择题",
            "填空题",
        ]
        return any(keyword in normalized for keyword in risk_keywords)

    def _guided_answer(self, question: str, subject: str) -> str:
        point = subject or "这道题"
        return "\n".join(
            [
                "我先不给出标准答案，先帮你把解题路径搭起来。",
                f"核心知识点：围绕{point}判断题目条件、目标量和可用公式/概念。",
                "解题步骤：",
                "1. 先圈出题干中的已知条件，并把要求的问题单独写出来。",
                "2. 判断它对应的知识点或题型，列出可能用到的公式、定义或解题模板。",
                "3. 按步骤代入、化简或推理，每一步都检查单位、符号和条件是否满足。",
                "4. 最后用题干条件反查一遍，确认结果没有漏条件。",
                "自查问题：你能说清楚第一步为什么这样列式吗？中间有没有跳步或直接套答案？",
                "如果你已经独立尝试过，需要核对完整解法和最终结论，可以二次确认后继续。",
            ]
        )

    def _invoke_external_chat(self, prompt: str) -> str:
        messages = [
            SystemMessage(content="你是学习路径规划 Agent。只输出 JSON，不要输出 Markdown 或解释文字。"),
            HumanMessage(content=prompt),
        ]
        return self._invoke_external_chat_messages(messages, max_tokens=1800)

    def _invoke_external_chat_messages(self, messages: List[BaseMessage], max_tokens: Optional[int] = None) -> str:
        if not settings.EXTERNAL_LLM_API_KEY:
            raise RuntimeError("EXTERNAL_LLM_API_KEY is not configured")
        url = settings.EXTERNAL_LLM_BASE_URL.rstrip("/") + "/chat/completions"
        payload = {
            "model": settings.EXTERNAL_LLM_MODEL,
            "messages": [self._chat_message(message) for message in messages],
            "temperature": settings.AGENT_TEMPERATURE,
            "max_tokens": max_tokens or settings.AGENT_MAX_TOKENS,
        }
        data = json.dumps(payload, ensure_ascii=False).encode("utf-8")
        request = urllib.request.Request(
            url,
            data=data,
            headers={
                "Authorization": f"Bearer {settings.EXTERNAL_LLM_API_KEY}",
                "Content-Type": "application/json",
            },
            method="POST",
        )
        with urllib.request.urlopen(request, timeout=90) as response:
            raw = response.read().decode("utf-8", errors="ignore")
        parsed = json.loads(raw)
        choices = parsed.get("choices") or []
        if not choices:
            return ""
        message = choices[0].get("message") or {}
        return str(message.get("content") or "")

    def _build_learning_path_prompt(self, request: Dict[str, Any]) -> str:
        allowed = "diagnostic_test, practice, wrong_review, resource_study, stage_test"
        context = {
            "planName": request.get("planName") or "",
            "subject": request.get("subject") or "",
            "targetDesc": request.get("targetDesc") or "",
            "currentScore": request.get("currentScore"),
            "targetScore": request.get("targetScore"),
            "days": request.get("days") or 7,
            "dailyMinutes": request.get("dailyMinutes") or 40,
            "weakPoints": request.get("weakPoints") or [],
            "metrics": request.get("metrics") or {},
            "wrongStats": request.get("wrongStats") or {},
            "recentAssessments": request.get("recentAssessments") or [],
            "resources": request.get("resources") or [],
        }
        return "\n".join(
            [
                "请根据学生目标、画像、错题、历史测评和资源库，生成可执行学习路径。",
                f"步骤类型只能使用：{allowed}。",
                "不要生成系统外链接，不要要求购买资料，不要直接写任意跳转地址。",
                "输出 JSON 格式：",
                '{"planSummary":"一句话策略","steps":[{"stepType":"diagnostic_test","title":"步骤标题","knowledgePoint":"知识点","targetCorrectRate":80,"estimatedMinutes":15,"day":1,"reason":"为什么先做这一步"}]}',
                "上下文：",
                json.dumps(context, ensure_ascii=False),
            ]
        )

    def _build_assessment_paper_prompt(self, request: Dict[str, Any]) -> str:
        context = {
            "subject": request.get("subject") or "数学",
            "gradeLevel": request.get("gradeLevel") or "九年级",
            "knowledgeScope": request.get("knowledgeScope") or "综合知识",
            "difficulty": request.get("difficulty") or 2,
            "assessmentType": request.get("assessmentType") or 2,
            "totalScore": request.get("totalScore") or 100,
            "questionCount": request.get("questionCount") or 8,
            "sections": request.get("sections") or [],
        }
        return "\n".join(
            [
                "请生成一份原创测评卷，风格参考广东省初中学业水平考试卷面：标题清楚、分大题、题干严谨、选择项整齐。",
                "可以参考公开教材知识要求和常见考试题型，但不得照搬真题原文；不要输出来源链接，不要生成图片文件。",
                "数学/物理等需要图形时，用文字描述或 ASCII/坐标说明表达图形；英语可生成语篇、完形、阅读类短文本；语文可生成阅读材料和作文题。",
                "题目必须贴合年级、学科、知识范围和难度；每题给出参考答案、解析、评分要点。",
                "题型 questionType 只能使用：1单选、2多选、3填空、4主观/解答。options 是数组，选择题必须有 3-4 个选项，非选择题为空数组。",
                "输出 JSON 格式：" ,
                '{"paperTitle":"2026年广东省初中学业水平模拟测评 数学","instructions":["本试卷为AI原创模拟测评。"],"questions":[{"sectionTitle":"一、单项选择题","knowledgePoint":"一次函数","difficulty":2,"questionType":1,"questionText":"题干","options":["A. ...","B. ...","C. ...","D. ..."],"answer":"A","analysis":"解析","scoringPoints":["评分要点"]}]}',
                "上下文：",
                json.dumps(context, ensure_ascii=False),
            ]
        )

    def _normalize_assessment_paper(
        self,
        parsed: Dict[str, Any],
        request: Dict[str, Any],
        provider: str,
        model: str,
    ) -> Dict[str, Any]:
        raw_questions = parsed.get("questions") if isinstance(parsed.get("questions"), list) else []
        limit = max(1, min(45, int(self._safe_float(request.get("questionCount"), 8))))
        subject = str(request.get("subject") or "数学").strip()
        difficulty = max(1, min(3, int(self._safe_float(request.get("difficulty"), 2))))
        questions: List[Dict[str, Any]] = []
        for item in raw_questions[:limit]:
            if not isinstance(item, dict):
                continue
            question_text = str(item.get("questionText") or "").strip()
            answer = str(item.get("answer") or "").strip()
            if not question_text or not answer:
                continue
            question_type = max(1, min(4, int(self._safe_float(item.get("questionType"), 1))))
            options = self._as_string_list(item.get("options"))
            if question_type in {1, 2} and len(options) < 2:
                options = ["A. 正确", "B. 错误", "C. 无法判断", "D. 以上都不对"]
                answer = "A"
            if question_type not in {1, 2}:
                options = []
            questions.append(
                {
                    "sectionTitle": str(item.get("sectionTitle") or "").strip(),
                    "subject": subject,
                    "knowledgePoint": str(item.get("knowledgePoint") or request.get("knowledgeScope") or "综合知识").strip(),
                    "difficulty": difficulty,
                    "questionType": question_type,
                    "questionText": question_text,
                    "options": options[:6],
                    "answer": answer,
                    "analysis": str(item.get("analysis") or "参考解析：根据题干条件和对应知识点逐步判断。 ").strip(),
                    "scoringPoints": self._as_string_list(item.get("scoringPoints"))[:8],
                }
            )
        if not questions:
            return self._fallback_assessment_paper(request, f"{provider}_empty")
        return {
            "provider": provider,
            "model": model,
            "fallback": False,
            "failureCategory": "",
            "errorCode": "",
            "paperTitle": str(parsed.get("paperTitle") or self._default_paper_title(request)).strip(),
            "instructions": self._as_string_list(parsed.get("instructions"))[:6],
            "questions": questions,
        }

    def _fallback_assessment_paper(self, request: Dict[str, Any], mode: str) -> Dict[str, Any]:
        subject = str(request.get("subject") or "数学").strip()
        grade = str(request.get("gradeLevel") or "九年级").strip()
        scope = str(request.get("knowledgeScope") or "综合知识").strip()
        difficulty = max(1, min(3, int(self._safe_float(request.get("difficulty"), 2))))
        limit = max(1, min(45, int(self._safe_float(request.get("questionCount"), 8))))
        raw_sections = request.get("sections") if isinstance(request.get("sections"), list) else []
        sections = [item for item in raw_sections if isinstance(item, dict)]
        if not sections:
            sections = [{"title": "一、选择题", "questionType": 1, "count": min(limit, 5)}, {"title": "二、综合题", "questionType": 4, "count": max(0, limit - 5)}]
        questions: List[Dict[str, Any]] = []
        for section in sections:
            count = int(self._safe_float(section.get("count"), 1))
            question_type = max(1, min(4, int(self._safe_float(section.get("questionType"), 1))))
            for _ in range(max(0, count)):
                if len(questions) >= limit:
                    break
                number = len(questions) + 1
                questions.append(self._fallback_question(subject, grade, scope, difficulty, question_type, str(section.get("title") or "测评题"), number))
        return {
            "provider": mode,
            "model": settings.EXTERNAL_LLM_MODEL,
            "fallback": True,
            "failureCategory": mode,
            "errorCode": "ASSESSMENT_PAPER_FALLBACK",
            "paperTitle": self._default_paper_title(request),
            "instructions": ["本试卷由 AI 根据年级和知识范围实时生成。", "题目为原创模拟测评，提交后自动生成报告。"],
            "questions": questions,
        }

    def _fallback_question(self, subject: str, grade: str, scope: str, difficulty: int, question_type: int, section_title: str, number: int) -> Dict[str, Any]:
        stem = f"{grade}{subject}{scope}第{number}题：请根据所学知识完成本题。"
        if question_type in {1, 2}:
            return {
                "sectionTitle": section_title,
                "subject": subject,
                "knowledgePoint": scope,
                "difficulty": difficulty,
                "questionType": question_type,
                "questionText": stem + "下列说法最符合题意的是（ ）。",
                "options": ["A. 能正确体现核心概念", "B. 与题干条件相反", "C. 忽略了限制条件", "D. 与本知识点无关"],
                "answer": "A",
                "analysis": "A 项符合题干中的核心概念和条件，其余选项存在概念或条件错误。 ",
                "scoringPoints": ["能识别核心概念", "能排除干扰选项"],
            }
        if question_type == 3:
            return {
                "sectionTitle": section_title,
                "subject": subject,
                "knowledgePoint": scope,
                "difficulty": difficulty,
                "questionType": 3,
                "questionText": stem + "请写出本知识点中最关键的结论或方法。 ",
                "options": [],
                "answer": scope,
                "analysis": "围绕题干指定知识范围作答，关键词完整即可。 ",
                "scoringPoints": ["关键词准确", "表达完整"],
            }
        return {
            "sectionTitle": section_title,
            "subject": subject,
            "knowledgePoint": scope,
            "difficulty": difficulty,
            "questionType": 4,
            "questionText": stem + "请结合概念、步骤和结论进行完整解答。 ",
            "options": [],
            "answer": f"围绕{scope}说明概念、列出关键步骤，并得到合理结论。 ",
            "analysis": "主观题按步骤、关键概念和结论完整性评分。 ",
            "scoringPoints": ["概念准确", "步骤清晰", "结论合理"],
        }

    def _default_paper_title(self, request: Dict[str, Any]) -> str:
        subject = str(request.get("subject") or "综合").strip()
        grade = str(request.get("gradeLevel") or "九年级").strip()
        return f"{grade}{subject}AI原创模拟测评"

    def _normalize_learning_path(
        self,
        parsed: Dict[str, Any],
        request: Dict[str, Any],
        provider: str,
        model: str,
    ) -> Dict[str, Any]:
        allowed = {"diagnostic_test", "practice", "wrong_review", "resource_study", "stage_test"}
        raw_steps = parsed.get("steps") if isinstance(parsed.get("steps"), list) else []
        steps: List[Dict[str, Any]] = []
        weak_points = self._as_string_list(request.get("weakPoints"))
        default_point = weak_points[0] if weak_points else self._first_target_point(str(request.get("targetDesc") or "基础知识"))
        for index, item in enumerate(raw_steps[:12]):
            if not isinstance(item, dict):
                continue
            step_type = str(item.get("stepType") or "").strip()
            if step_type not in allowed:
                continue
            point = str(item.get("knowledgePoint") or default_point).strip() or default_point
            target_rate = max(0, min(100, int(self._safe_float(item.get("targetCorrectRate"), 80))))
            minutes = max(5, min(90, int(self._safe_float(item.get("estimatedMinutes"), 15))))
            steps.append(
                {
                    "stepType": step_type,
                    "title": str(item.get("title") or self._default_step_title(step_type, point)).strip(),
                    "knowledgePoint": point,
                    "targetCorrectRate": target_rate,
                    "estimatedMinutes": minutes,
                    "day": max(1, int(self._safe_float(item.get("day"), index + 1))),
                    "reason": str(item.get("reason") or "根据目标和薄弱点安排该步骤。").strip(),
                }
            )
        if not steps:
            return self._fallback_learning_path(request, f"{provider}_empty")
        return {
            "provider": provider,
            "model": model,
            "planSummary": str(parsed.get("planSummary") or "按诊断、练习、复盘、资源学习和阶段测评推进。").strip(),
            "steps": steps,
        }

    def _fallback_learning_path(self, request: Dict[str, Any], mode: str) -> Dict[str, Any]:
        points = self._as_string_list(request.get("weakPoints"))
        if not points:
            points = [self._first_target_point(str(request.get("targetDesc") or "基础知识"))]
        point = points[0]
        second = points[1] if len(points) > 1 else point
        steps = [
            ("diagnostic_test", f"{point}基础诊断", point, 70, 12, "先用诊断题确认当前薄弱程度。"),
            ("practice", f"{point}专项练习", point, 80, 18, "通过同类题巩固核心方法。"),
            ("wrong_review", f"{point}错题复盘", point, 80, 12, "复盘错误原因，避免重复失分。"),
            ("resource_study", f"{second}资源学习", second, 0, 15, "用资源补齐概念和例题理解。"),
            ("stage_test", f"{point}阶段测评", point, 85, 20, "用阶段测评判断是否可以进入下一轮。"),
        ]
        return {
            "provider": mode,
            "model": settings.EXTERNAL_LLM_MODEL,
            "planSummary": "根据目标、画像和薄弱点生成诊断-练习-复盘-资源-测评闭环。",
            "steps": [
                {
                    "stepType": step_type,
                    "title": title,
                    "knowledgePoint": knowledge_point,
                    "targetCorrectRate": target_rate,
                    "estimatedMinutes": minutes,
                    "day": index + 1,
                    "reason": reason,
                }
                for index, (step_type, title, knowledge_point, target_rate, minutes, reason) in enumerate(steps)
            ],
        }

    def _first_target_point(self, text: str) -> str:
        parts = [part.strip() for part in re.split(r"[,，、;；\s]+", text or "") if part.strip()]
        return parts[0] if parts else "基础知识"

    def _default_step_title(self, step_type: str, point: str) -> str:
        mapping = {
            "diagnostic_test": "诊断测评",
            "practice": "专项练习",
            "wrong_review": "错题复盘",
            "resource_study": "资源学习",
            "stage_test": "阶段测评",
        }
        return f"{point}{mapping.get(step_type, '学习任务')}"

    def _build_subjective_score_prompt(
        self,
        question_text: str,
        reference_answer: str,
        student_answer: str,
        scoring_points: List[str],
        subject: Optional[str],
        knowledge_point: Optional[str],
    ) -> str:
        points_text = "\n".join(f"- {point}" for point in scoring_points) or "- 参考答案的核心语义"
        return "\n".join(
            [
                f"学科：{subject or '未指定'}",
                f"知识点：{knowledge_point or '未指定'}",
                f"题干：{question_text or '未提供'}",
                f"参考答案：{reference_answer or '未提供'}",
                "评分要点：",
                points_text,
                f"学生答案：{student_answer}",
                "",
                "请按语义等价性评分，不要求学生答案逐字匹配参考答案。",
                "输出 JSON 字段：scoreRatio(0到1小数), confidence(0到100), matchedPoints(数组), missingPoints(数组), comment(一句中文评语)。",
            ]
        )

    def _parse_score_json(self, raw_answer: str) -> Optional[Dict[str, Any]]:
        text = (raw_answer or "").strip()
        if not text:
            return None
        start = text.find("{")
        end = text.rfind("}")
        if start >= 0 and end > start:
            text = text[start : end + 1]
        try:
            parsed = json.loads(text)
        except json.JSONDecodeError:
            return None
        return parsed if isinstance(parsed, dict) else None

    def _score_payload_from_model(
        self,
        parsed: Dict[str, Any],
        scoring_points: List[str],
        max_score: float,
    ) -> Dict[str, Any]:
        ratio = self._safe_float(parsed.get("scoreRatio"), -1)
        if ratio < 0 and parsed.get("score") is not None:
            ratio = self._safe_float(parsed.get("score"), 0) / max_score if max_score > 0 else 0
        ratio = max(0.0, min(1.0, ratio))
        confidence = max(0.0, min(100.0, self._safe_float(parsed.get("confidence"), 75)))
        matched = self._as_string_list(parsed.get("matchedPoints"))
        missing = self._as_string_list(parsed.get("missingPoints"))
        if not missing and scoring_points:
            matched_set = {item.strip() for item in matched}
            missing = [point for point in scoring_points if point.strip() not in matched_set]
        return {
            "score": round(max_score * ratio, 2),
            "maxScore": round(max_score, 2),
            "scoreRatio": round(ratio, 4),
            "confidence": round(confidence, 2),
            "matchedPoints": matched,
            "missingPoints": missing,
            "comment": str(parsed.get("comment") or "AI 已根据参考答案和评分要点完成语义评分。").strip(),
            "scoringMode": "llm_semantic",
            "model": self._active_model(),
            "provider": self._active_provider(),
            "fallback": False,
            "failureCategory": "",
            "errorCode": "",
        }

    def _heuristic_subjective_score(
        self,
        reference_answer: str,
        student_answer: str,
        scoring_points: List[str],
        max_score: float,
        failure_category: str = "fallback",
        error_code: str = "HEURISTIC_FALLBACK",
    ) -> Dict[str, Any]:
        matched: List[str] = []
        missing: List[str] = []
        if scoring_points:
            for point in scoring_points:
                if self._text_overlap_ratio(point, student_answer) >= 0.42:
                    matched.append(point)
                else:
                    missing.append(point)
            ratio = len(matched) / len(scoring_points)
        else:
            ratio = self._text_overlap_ratio(reference_answer, student_answer)
        if not matched and ratio > 0.72 and scoring_points:
            matched = scoring_points[:1]
            missing = scoring_points[1:]
        return {
            "score": round(max_score * max(0.0, min(1.0, ratio)), 2),
            "maxScore": round(max_score, 2),
            "scoreRatio": round(max(0.0, min(1.0, ratio)), 4),
            "confidence": 58 if scoring_points else 50,
            "matchedPoints": matched,
            "missingPoints": missing,
            "comment": "AI 模型不可用或未返回有效 JSON，已按评分要点和参考答案相似度进行兜底评分。",
            "scoringMode": "heuristic_fallback",
            "model": self._active_model(),
            "provider": "rule_fallback",
            "fallback": True,
            "failureCategory": failure_category,
            "errorCode": error_code,
        }

    def _normalize_scoring_points(
        self,
        scoring_points: Optional[List[str]],
        reference_answer: str,
    ) -> List[str]:
        raw_points = scoring_points or []
        points: List[str] = []
        for item in raw_points:
            for part in re.split(r"[\n;；]+", str(item or "")):
                clean = part.strip(" -\t\r\n")
                if clean and clean not in points:
                    points.append(clean)
        if points:
            return points[:8]
        derived = [
            part.strip(" -\t\r\n")
            for part in re.split(r"[。！？!?；;\n]+", reference_answer or "")
            if part.strip()
        ]
        return derived[:5]

    def _text_overlap_ratio(self, expected: str, actual: str) -> float:
        expected_tokens = self._semantic_tokens(expected)
        actual_tokens = self._semantic_tokens(actual)
        if not expected_tokens:
            return 0.0
        if not actual_tokens:
            return 0.0
        hit_count = len(expected_tokens.intersection(actual_tokens))
        return hit_count / len(expected_tokens)

    def _semantic_tokens(self, text: str) -> set[str]:
        words = set(re.findall(r"[A-Za-z0-9_]+", text.lower()))
        chinese_chars = {
            ch
            for ch in text
            if "\u4e00" <= ch <= "\u9fff" and ch not in {"的", "了", "是", "和", "与", "中", "可", "以", "则"}
        }
        return words.union(chinese_chars)

    def _safe_float(self, value: Any, default: float) -> float:
        try:
            return float(value)
        except (TypeError, ValueError):
            return default

    def _as_string_list(self, value: Any) -> List[str]:
        if not isinstance(value, list):
            return []
        result: List[str] = []
        for item in value:
            text = str(item or "").strip()
            if text:
                result.append(text)
        return result


qa_agent = LearningQAAgent()
