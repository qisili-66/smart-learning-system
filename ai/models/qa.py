from typing import Any, Dict, List, Optional

from pydantic import BaseModel, Field


class HistoryMessage(BaseModel):
    role: str = Field(..., description="user/assistant")
    content: str = Field(..., description="历史消息内容")


class TextQARequest(BaseModel):
    question: str = Field(..., min_length=1, description="学生问题")
    conversationId: Optional[str] = Field(default=None, description="会话ID，多轮对话时传入")
    subject: Optional[str] = Field(default=None, description="学科")
    confirmAnswer: bool = Field(default=False, description="是否已二次确认查看完整答案")
    history: List[HistoryMessage] = Field(default_factory=list, description="后端持久化历史上下文")


class TextQAResponse(BaseModel):
    conversationId: str
    answerId: str
    answer: str
    knowledgePoint: str
    steps: List[str]
    extendTips: str


class SubjectiveScoreRequest(BaseModel):
    questionText: str = Field(default="", description="题干")
    referenceAnswer: str = Field(default="", description="参考答案")
    studentAnswer: str = Field(default="", description="学生答案")
    scoringPoints: List[str] = Field(default_factory=list, description="评分要点")
    maxScore: float = Field(default=100.0, ge=0, description="单题满分")
    subject: Optional[str] = Field(default=None, description="学科")
    knowledgePoint: Optional[str] = Field(default=None, description="知识点")


class LearningPathRequest(BaseModel):
    planName: str = Field(default="", description="计划名称")
    subject: str = Field(default="", description="学科")
    targetDesc: str = Field(default="", description="目标描述")
    currentScore: Optional[float] = Field(default=None, description="当前分数")
    targetScore: Optional[float] = Field(default=None, description="目标分数")
    days: int = Field(default=7, ge=1, le=120, description="学习周期天数")
    dailyMinutes: int = Field(default=40, ge=5, le=240, description="每日可学习分钟")
    weakPoints: List[str] = Field(default_factory=list, description="薄弱知识点")
    metrics: Dict[str, Any] = Field(default_factory=dict, description="画像指标")
    wrongStats: Dict[str, Any] = Field(default_factory=dict, description="错题统计")
    recentAssessments: List[Dict[str, Any]] = Field(default_factory=list, description="最近测评")
    resources: List[Dict[str, Any]] = Field(default_factory=list, description="可用资源")
    provider: Optional[str] = Field(default=None, description="兼容旧请求字段；服务端统一调用 OpenAI 兼容 API")


class AssessmentPaperRequest(BaseModel):
    subject: str = Field(default="", description="学科")
    gradeLevel: str = Field(default="九年级", description="年级")
    knowledgeScope: str = Field(default="", description="知识范围")
    difficulty: int = Field(default=2, ge=1, le=3, description="难度：1基础 2中等 3提升")
    assessmentType: int = Field(default=2, description="测评模式")
    totalScore: float = Field(default=100.0, ge=1, description="试卷总分")
    questionCount: int = Field(default=8, ge=1, le=45, description="题目数量")
    sections: List[Dict[str, Any]] = Field(default_factory=list, description="试卷大题结构")
