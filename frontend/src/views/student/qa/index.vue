<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { qaApi } from '@/api/student'
import { SUBJECTS } from '@/utils/format'

const STORAGE_KEY = 'smart_learning_ai_sessions'
const route = useRoute()
const subject = ref('数学')
const input = ref('')
const sending = ref(false)
const messagesBox = ref()
const sessions = ref([])
const activeId = ref('')
const metrics = ref(null)
const recording = ref(false)
const recognizing = ref(false)
const voiceBlob = ref(null)
const voiceUrl = ref('')
const recognizedText = ref('')
const correctedText = ref('')
const correctedTouched = ref(false)
const speechError = ref('')
const speechSupported = ref(
  typeof window !== 'undefined' && Boolean(window.SpeechRecognition || window.webkitSpeechRecognition)
)

let mediaRecorder = null
let mediaStream = null
let audioChunks = []
let recognition = null
let asrFinalText = ''

const activeSession = computed(() => sessions.value.find((item) => item.id === activeId.value))
const messages = computed(() => activeSession.value?.messages || [])
const speechLanguage = computed(() => (subject.value === '英语' ? 'en-US' : 'zh-CN'))

function welcomeMessage() {
  return {
    role: 'assistant',
    content: '把题目发给我，可以输入文字、上传图片，也可以录音后修正识别文本。',
    time: new Date().toISOString(),
    meta: { requiresConfirmation: false }
  }
}

function createSession() {
  const id = `conv-${Date.now()}`
  sessions.value.unshift({
    id,
    conversationId: id,
    title: '新的答疑会话',
    updatedAt: new Date().toISOString(),
    messages: [welcomeMessage()]
  })
  activeId.value = id
  saveLocalSessions()
}

async function loadSessions() {
  try {
    const page = await qaApi.conversations({ pageNum: 1, pageSize: 20 })
    sessions.value = Array.isArray(page?.list) ? page.list.map(toSession) : []
    if (!sessions.value.length) {
      createSession()
    } else {
      activeId.value = sessions.value[0].id
      await loadConversation(activeId.value)
    }
  } catch {
    loadLocalSessions()
  } finally {
    loadMetrics()
  }
}

function loadLocalSessions() {
  try {
    sessions.value = JSON.parse(localStorage.getItem(STORAGE_KEY) || '[]')
  } catch {
    sessions.value = []
  }
  if (!sessions.value.length) {
    createSession()
  } else {
    activeId.value = sessions.value[0].id
  }
}

function saveLocalSessions() {
  localStorage.setItem(STORAGE_KEY, JSON.stringify(sessions.value.slice(0, 12)))
}

async function loadMetrics() {
  try {
    metrics.value = await qaApi.evaluation({ days: 7 })
  } catch {
    metrics.value = null
  }
}

async function selectSession(id) {
  activeId.value = id
  await loadConversation(id)
}

async function loadConversation(id) {
  const session = sessions.value.find((item) => item.id === id)
  if (!session || session.localOnly) return
  try {
    const detail = await qaApi.conversationDetail(id)
    const loaded = toSession(detail)
    if (!loaded.messages.length) {
      loaded.messages = [welcomeMessage()]
    }
    replaceSession(loaded)
    await hydrateAudioMessages(loaded.messages)
    scrollToBottom()
  } catch {
    if (!session.messages?.length) {
      session.messages = [welcomeMessage()]
    }
  }
}

function toSession(raw) {
  const id = raw?.conversationId || raw?.id || `conv-${Date.now()}`
  return {
    id,
    conversationId: id,
    title: raw?.title || '新的答疑会话',
    updatedAt: raw?.updatedAt || raw?.updateTime || new Date().toISOString(),
    messages: Array.isArray(raw?.messages) ? raw.messages.map(toMessage) : []
  }
}

function toMessage(raw) {
  return {
    role: raw?.role || 'assistant',
    content: raw?.content || '',
    contentType: raw?.contentType || 'text',
    time: raw?.time || raw?.createTime || new Date().toISOString(),
    audioUrl: raw?.audioObjectUrl || raw?.audioUrl || '',
    serverAudioUrl: raw?.audioUrl || '',
    recognizedText: raw?.recognizedText || '',
    correctedText: raw?.correctedText || '',
    meta: {
      requiresConfirmation: Boolean(raw?.requiresConfirmation),
      confirmedAnswer: Boolean(raw?.confirmed),
      latencyMs: raw?.latencyMs,
      model: raw?.model
    }
  }
}

async function hydrateAudioMessages(items) {
  await Promise.all(
    items
      .filter((item) => item.serverAudioUrl)
      .map(async (item) => {
        try {
          const blob = await qaApi.downloadAudio(item.serverAudioUrl)
          item.audioUrl = URL.createObjectURL(blob)
        } catch {
          item.audioUrl = item.serverAudioUrl
        }
      })
  )
}

function replaceSession(session) {
  const index = sessions.value.findIndex((item) => item.id === session.id)
  if (index >= 0) {
    sessions.value.splice(index, 1, session)
  } else {
    sessions.value.unshift(session)
  }
  activeId.value = session.id
  saveLocalSessions()
}

function appendMessage(message) {
  if (!activeSession.value) createSession()
  activeSession.value.messages.push({
    ...message,
    time: new Date().toISOString()
  })
  const userMessageCount = activeSession.value.messages.filter((item) => item.role === 'user').length
  if (message.role === 'user' && (!activeSession.value.title || activeSession.value.title === '新的答疑会话' || userMessageCount <= 1)) {
    activeSession.value.title = (message.correctedText || message.recognizedText || message.content).slice(0, 28) || '图片题目'
  }
  activeSession.value.updatedAt = new Date().toISOString()
  sessions.value = [
    activeSession.value,
    ...sessions.value.filter((item) => item.id !== activeSession.value.id)
  ]
  activeId.value = sessions.value[0].id
  saveLocalSessions()
  scrollToBottom()
}

function scrollToBottom() {
  nextTick(() => {
    if (messagesBox.value) {
      messagesBox.value.scrollTop = messagesBox.value.scrollHeight
    }
  })
}

function normalizeAnswer(data) {
  if (typeof data === 'string') {
    return {
      answer: data || 'AI 暂未返回答案',
      usedTools: [],
      requiresConfirmation: false
    }
  }
  const payload = data?.data && typeof data.data === 'object' ? data.data : data
  return {
    answer: payload?.answer || payload?.content || data?.message || 'AI 暂未返回答案',
    conversationId: payload?.conversationId,
    ocrText: payload?.ocrText || '',
    recognizedText: payload?.recognizedText || '',
    correctedText: payload?.correctedText || '',
    audioUrl: payload?.audioUrl || '',
    audioAsrStatus: payload?.audioAsrStatus || '',
    confidence: payload?.confidence,
    usedTools: Array.isArray(payload?.usedTools) ? payload.usedTools : [],
    toolCalls: Array.isArray(payload?.toolCalls) ? payload.toolCalls : [],
    memory: payload?.memory || null,
    requiresConfirmation: Boolean(payload?.requiresConfirmation),
    confirmedAnswer: Boolean(payload?.confirmedAnswer),
    confirmationPrompt: payload?.confirmationPrompt || '',
    guardrailReason: payload?.guardrailReason || '',
    latencyMs: payload?.latencyMs,
    model: payload?.model
  }
}

async function sendText() {
  const question = input.value.trim()
  if (!question) return
  input.value = ''
  await askText(question)
}

async function askText(question, confirmAnswer = false, displayContent = question) {
  appendMessage({ role: 'user', content: displayContent, contentType: 'text' })
  sending.value = true
  try {
    const data = normalizeAnswer(
      await qaApi.text({
        question,
        conversationId: activeId.value,
        subject: subject.value,
        confirmAnswer
      })
    )
    appendMessage({
      role: 'assistant',
      content: data.answer,
      contentType: 'text',
      meta: { ...data, originalQuestion: question }
    })
    await loadMetrics()
  } catch (error) {
    appendMessage({
      role: 'assistant',
      content: `答疑失败：${error?.message || '请确认后端和 AI 服务已启动'}`
    })
  } finally {
    sending.value = false
  }
}

async function handleImage(file) {
  if (!file?.raw) return
  appendMessage({ role: 'user', content: `上传图片：${file.name}`, contentType: 'image' })
  sending.value = true
  try {
    const data = normalizeAnswer(
      await qaApi.image(file.raw, {
        conversationId: activeId.value,
        subject: subject.value
      })
    )
    appendMessage({
      role: 'assistant',
      content: data.answer,
      contentType: 'image',
      meta: { ...data, originalQuestion: data.ocrText || `上传图片：${file.name}` }
    })
    await loadMetrics()
  } catch (error) {
    appendMessage({
      role: 'assistant',
      content: `图片答疑失败：${error?.message || '请确认图片清晰，且 AI 服务已启动'}`
    })
  } finally {
    sending.value = false
  }
}

async function startRecording() {
  if (!window.MediaRecorder || !navigator.mediaDevices?.getUserMedia) {
    ElMessage.warning('当前浏览器不支持录音')
    return
  }
  clearVoiceDraft()
  speechError.value = ''
  try {
    mediaStream = await navigator.mediaDevices.getUserMedia({ audio: true })
    audioChunks = []
    const options = MediaRecorder.isTypeSupported('audio/webm') ? { mimeType: 'audio/webm' } : undefined
    mediaRecorder = new MediaRecorder(mediaStream, options)
    mediaRecorder.ondataavailable = (event) => {
      if (event.data?.size) audioChunks.push(event.data)
    }
    mediaRecorder.onstop = () => {
      voiceBlob.value = new Blob(audioChunks, { type: mediaRecorder?.mimeType || 'audio/webm' })
      voiceUrl.value = URL.createObjectURL(voiceBlob.value)
      correctedText.value = correctedText.value || recognizedText.value
      stopMediaStream()
    }
    mediaRecorder.start()
    recording.value = true
    startRecognition()
  } catch (error) {
    speechError.value = error?.message || '无法启动录音，请检查浏览器麦克风权限'
    ElMessage.error(error?.message || '无法启动录音')
    stopMediaStream()
  }
}

function stopRecording() {
  if (mediaRecorder && mediaRecorder.state !== 'inactive') {
    mediaRecorder.stop()
  }
  recording.value = false
  stopRecognition()
}

function startRecognition() {
  if (!speechSupported.value) {
    speechError.value = '当前浏览器不支持语音转文字，请在录音后手动输入识别文本'
    return
  }
  const Recognition = window.SpeechRecognition || window.webkitSpeechRecognition
  recognition = new Recognition()
  recognition.lang = speechLanguage.value
  recognition.continuous = true
  recognition.interimResults = true
  speechError.value = ''
  asrFinalText = ''
  recognizedText.value = ''
  correctedText.value = ''
  correctedTouched.value = false
  recognition.onresult = (event) => {
    let interim = ''
    for (let index = event.resultIndex; index < event.results.length; index += 1) {
      const text = event.results[index][0]?.transcript || ''
      if (event.results[index].isFinal) {
        asrFinalText = `${asrFinalText} ${text}`.trim()
      } else {
        interim = `${interim} ${text}`.trim()
      }
    }
    recognizedText.value = `${asrFinalText} ${interim}`.trim()
    if (!correctedTouched.value) {
      correctedText.value = recognizedText.value
    }
  }
  recognition.onend = () => {
    recognizing.value = false
  }
  recognition.onerror = (event) => {
    recognizing.value = false
    speechError.value = speechErrorMessage(event?.error)
  }
  recognition.onnomatch = () => {
    speechError.value = '没有识别到清晰语音，请靠近麦克风重试，或手动输入识别文本'
  }
  try {
    recognition.start()
    recognizing.value = true
  } catch (error) {
    recognizing.value = false
    speechError.value = error?.message || '语音转文字启动失败，请检查浏览器权限或手动输入文本'
  }
}

function stopRecognition() {
  if (recognition) {
    try {
      recognition.stop()
    } catch {
      // Recognition can already be stopped by the browser.
    }
  }
  recognition = null
  recognizing.value = false
}

function speechErrorMessage(error) {
  const messages = {
    'not-allowed': '麦克风或语音识别权限被拒绝，请在浏览器地址栏允许麦克风权限',
    'service-not-allowed': '浏览器语音识别服务不可用，请换用 Chrome/Edge 或手动输入文本',
    'network': '语音识别服务连接失败，请检查网络后重试，或手动输入文本',
    'no-speech': '没有识别到语音，请靠近麦克风重试，或手动输入文本',
    'audio-capture': '没有检测到可用麦克风，请检查录音设备',
    'language-not-supported': '当前语音识别语言不可用，请切换学科或手动输入文本'
  }
  return messages[error] || '语音转文字失败，请重试或手动输入识别文本'
}

function stopMediaStream() {
  mediaStream?.getTracks?.().forEach((track) => track.stop())
  mediaStream = null
}

async function sendVoice() {
  if (!voiceBlob.value) {
    ElMessage.warning('请先录音')
    return
  }
  const fixedText = correctedText.value.trim()
  const rawText = recognizedText.value.trim()
  if (!fixedText && !rawText) {
    ElMessage.warning('请先完成语音转文字，或在文本框手动输入识别内容')
    return
  }
  const file = new File([voiceBlob.value], `voice-${Date.now()}.webm`, { type: voiceBlob.value.type || 'audio/webm' })
  appendMessage({
    role: 'user',
    content: fixedText || rawText || '语音提问',
    contentType: 'voice',
    audioUrl: voiceUrl.value,
    recognizedText: rawText,
    correctedText: fixedText
  })
  sending.value = true
  try {
    const data = normalizeAnswer(
      await qaApi.voice(file, {
        conversationId: activeId.value,
        subject: subject.value,
        recognizedText: rawText,
        correctedText: fixedText
      })
    )
    if (data.audioUrl) {
      const lastUserVoice = [...messages.value].reverse().find((item) => item.role === 'user' && item.contentType === 'voice')
      if (lastUserVoice) {
        lastUserVoice.serverAudioUrl = data.audioUrl
      }
    }
    appendMessage({
      role: 'assistant',
      content: data.answer,
      contentType: 'voice',
      meta: { ...data, originalQuestion: fixedText || rawText }
    })
    clearVoiceDraft(false)
    await loadMetrics()
  } catch (error) {
    appendMessage({
      role: 'assistant',
      content: `语音答疑失败：${error?.message || '请确认录音和 AI 服务状态'}`
    })
  } finally {
    sending.value = false
  }
}

function clearVoiceDraft(revoke = true) {
  if (revoke && voiceUrl.value?.startsWith('blob:')) {
    URL.revokeObjectURL(voiceUrl.value)
  }
  voiceBlob.value = null
  voiceUrl.value = ''
  recognizedText.value = ''
  correctedText.value = ''
  correctedTouched.value = false
  speechError.value = ''
}

async function confirmFullAnswer(message, index) {
  const question = message.meta?.originalQuestion || previousUserQuestion(index)
  if (!question) {
    ElMessage.warning('没有可确认的原题文本')
    return
  }
  await askText(question, true, '已确认，需要查看完整答案')
}

function previousUserQuestion(index) {
  for (let cursor = index - 1; cursor >= 0; cursor -= 1) {
    const item = messages.value[cursor]
    if (item?.role === 'user') {
      return item.correctedText || item.recognizedText || item.content
    }
  }
  return ''
}

async function removeSession(id) {
  await ElMessageBox.confirm('删除这条会话记录？', '删除会话', {
    type: 'warning',
    confirmButtonText: '删除',
    cancelButtonText: '取消'
  })
  try {
    await qaApi.deleteConversation(id)
  } catch {
    // Local-only fallback sessions do not exist on the backend.
  }
  sessions.value = sessions.value.filter((item) => item.id !== id)
  if (!sessions.value.length) {
    createSession()
  } else if (activeId.value === id) {
    activeId.value = sessions.value[0].id
    await loadConversation(activeId.value)
  }
  saveLocalSessions()
  await loadMetrics()
}

function sendQuick(text) {
  input.value = text
  sendText()
}

function metricValue(value, suffix = '') {
  if (value === null || value === undefined || value === '') return '-'
  return `${value}${suffix}`
}

onMounted(loadSessions)

watch(
  () => route.query.subject,
  (value) => {
    const nextSubject = value?.toString()
    if (nextSubject && SUBJECTS.includes(nextSubject)) {
      subject.value = nextSubject
    }
  },
  { immediate: true }
)

onBeforeUnmount(() => {
  stopRecognition()
  stopMediaStream()
  clearVoiceDraft()
})
</script>

<template>
  <div class="page chat-page">
    <div class="page-title">
      <div>
        <h1>AI 智能答疑</h1>
        <p>文本、图片 OCR、语音识别和多轮上下文记录。</p>
      </div>
      <el-select v-model="subject" class="subject-select">
        <el-option v-for="item in SUBJECTS" :key="item" :label="item" :value="item" />
      </el-select>
    </div>

    <section class="chat-layout">
      <div class="panel chat-main">
        <div ref="messagesBox" class="message-list">
          <div
            v-for="(message, index) in messages"
            :key="`${message.time}-${index}`"
            class="message-row"
            :class="message.role"
          >
            <el-avatar :size="34">{{ message.role === 'user' ? '我' : 'AI' }}</el-avatar>
            <div class="bubble">
              <p>{{ message.content }}</p>
              <audio v-if="message.audioUrl" class="audio-player" :src="message.audioUrl" controls />
              <div v-if="message.meta?.ocrText" class="meta-box">
                <strong>识别文本</strong>
                <span>{{ message.meta.ocrText }}</span>
              </div>
              <div v-if="message.recognizedText || message.correctedText || message.meta?.recognizedText" class="meta-box">
                <strong>语音文本</strong>
                <span>{{ message.correctedText || message.meta?.correctedText || message.recognizedText || message.meta?.recognizedText }}</span>
              </div>
              <div v-if="message.meta?.usedTools?.length" class="tool-tags">
                <el-tag v-for="tool in message.meta.usedTools" :key="tool" size="small" type="success">
                  {{ tool }}
                </el-tag>
              </div>
              <div v-if="message.meta?.requiresConfirmation && !message.meta?.confirmedAnswer" class="confirm-box">
                <span>{{ message.meta.confirmationPrompt || '需要二次确认后查看完整答案。' }}</span>
                <el-button size="small" type="warning" @click="confirmFullAnswer(message, index)">
                  确认查看完整答案
                </el-button>
              </div>
            </div>
          </div>
          <div v-if="sending" class="message-row assistant">
            <el-avatar :size="34">AI</el-avatar>
            <div class="bubble typing">
              <span />
              <span />
              <span />
            </div>
          </div>
        </div>

        <div class="quick-tags">
          <el-tag @click="sendQuick('帮我分析这道函数题的解题思路')">函数题思路</el-tag>
          <el-tag @click="sendQuick('这个知识点的易错点有哪些？')">易错点</el-tag>
          <el-tag @click="sendQuick('给我出三道同类巩固题')">同类练习</el-tag>
        </div>

        <div v-if="voiceBlob || recognizedText || recording" class="voice-draft">
          <audio v-if="voiceUrl" class="audio-player" :src="voiceUrl" controls />
          <el-alert v-if="speechError" :title="speechError" type="warning" show-icon :closable="false" />
          <el-input
            v-model="correctedText"
            type="textarea"
            :autosize="{ minRows: 2, maxRows: 4 }"
            placeholder="修正语音识别文本"
            @input="correctedTouched = true"
          />
          <div class="voice-actions">
            <el-tag v-if="recognizing" type="warning">识别中</el-tag>
            <el-tag v-else-if="!speechSupported" type="info">可手动输入文本</el-tag>
            <el-tag v-else type="success">{{ speechLanguage }}</el-tag>
            <el-button size="small" :disabled="!voiceBlob" @click="sendVoice">发送语音</el-button>
            <el-button size="small" text @click="clearVoiceDraft">清空</el-button>
          </div>
        </div>

        <div class="composer">
          <el-upload :auto-upload="false" :show-file-list="false" accept="image/*" :on-change="handleImage">
            <el-button :icon="'UploadFilled'">图片</el-button>
          </el-upload>
          <el-button v-if="!recording" :icon="'Microphone'" @click="startRecording">录音</el-button>
          <el-button v-else type="danger" :icon="'VideoPause'" @click="stopRecording">停止</el-button>
          <el-input
            v-model="input"
            type="textarea"
            :autosize="{ minRows: 1, maxRows: 4 }"
            placeholder="输入题目或追问内容"
            @keydown.enter.exact.prevent="sendText"
          />
          <el-button type="primary" :loading="sending" :icon="'Position'" @click="sendText">发送</el-button>
        </div>
      </div>

      <aside class="chat-side">
        <div class="panel panel-body">
          <div class="side-head">
            <h2>最近会话</h2>
            <el-button circle size="small" :icon="'Plus'" @click="createSession" />
          </div>
          <div class="session-list">
            <button
              v-for="item in sessions"
              :key="item.id"
              class="session-item"
              :class="{ active: item.id === activeId }"
              type="button"
              @click="selectSession(item.id)"
            >
              <span>{{ item.title }}</span>
              <el-button text :icon="'Delete'" @click.stop="removeSession(item.id)" />
            </button>
          </div>
        </div>

        <div class="panel panel-body">
          <h2>答疑指标</h2>
          <div class="metric-list">
            <div>
              <span>近 7 天提问</span>
              <strong>{{ metricValue(metrics?.questionCount) }}</strong>
            </div>
            <div>
              <span>平均首响</span>
              <strong>{{ metricValue(metrics?.avgFirstResponseMs, 'ms') }}</strong>
            </div>
            <div>
              <span>启发式准确率</span>
              <strong>{{ metricValue(metrics?.heuristicAccuracyRate, '%') }}</strong>
            </div>
            <div>
              <span>语音提问</span>
              <strong>{{ metricValue(metrics?.voiceQuestionCount) }}</strong>
            </div>
          </div>
        </div>

        <div class="panel panel-body">
          <h2>相关知识点</h2>
          <div class="tag-row">
            <el-tag type="primary">函数与导数</el-tag>
            <el-tag type="success">阅读理解</el-tag>
            <el-tag type="warning">力学模型</el-tag>
          </div>
        </div>
      </aside>
    </section>
  </div>
</template>

<style scoped>
.subject-select {
  width: 160px;
}

.chat-layout {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 300px;
  gap: 16px;
  min-height: calc(100vh - 140px);
}

.chat-main {
  display: grid;
  grid-template-rows: 1fr auto auto auto;
  min-height: 620px;
  overflow: hidden;
}

.message-list {
  display: flex;
  flex-direction: column;
  gap: 16px;
  padding: 20px;
  overflow-y: auto;
}

.message-row {
  display: flex;
  align-items: flex-start;
  gap: 10px;
}

.message-row.user {
  flex-direction: row-reverse;
}

.bubble {
  max-width: min(720px, 76%);
  padding: 13px 15px;
  border: 1px solid var(--line);
  border-radius: 8px;
  background: #ffffff;
  color: var(--text);
}

.user .bubble {
  border-color: var(--primary);
  background: var(--primary);
  color: #ffffff;
}

.bubble p {
  margin: 0;
  white-space: pre-wrap;
  line-height: 1.7;
}

.audio-player {
  width: min(320px, 100%);
  height: 36px;
  margin-top: 10px;
}

.meta-box,
.confirm-box {
  display: grid;
  gap: 8px;
  margin-top: 12px;
  padding: 10px;
  border-radius: 8px;
  color: var(--text);
  background: #f8fafc;
}

.meta-box span,
.confirm-box span {
  color: var(--muted);
  font-size: 13px;
  line-height: 1.6;
}

.tool-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin-top: 10px;
}

.typing {
  display: inline-flex;
  gap: 5px;
  width: 58px;
}

.typing span {
  width: 7px;
  height: 7px;
  border-radius: 50%;
  background: var(--primary);
  animation: pulse 1s infinite ease-in-out;
}

.typing span:nth-child(2) {
  animation-delay: 0.12s;
}

.typing span:nth-child(3) {
  animation-delay: 0.24s;
}

@keyframes pulse {
  0%,
  100% {
    opacity: 0.3;
    transform: translateY(0);
  }
  50% {
    opacity: 1;
    transform: translateY(-3px);
  }
}

.quick-tags,
.voice-actions {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px;
}

.quick-tags {
  padding: 0 20px 14px;
}

.quick-tags .el-tag {
  cursor: pointer;
}

.voice-draft {
  display: grid;
  gap: 10px;
  padding: 14px 16px;
  border-top: 1px solid var(--line);
  background: #ffffff;
}

.composer {
  display: grid;
  grid-template-columns: auto auto minmax(0, 1fr) auto;
  gap: 10px;
  padding: 16px;
  border-top: 1px solid var(--line);
  background: #ffffff;
}

.chat-side {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.side-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 12px;
}

.side-head h2,
.chat-side h2 {
  margin: 0 0 12px;
  font-size: 16px;
}

.side-head h2 {
  margin-bottom: 0;
}

.session-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.session-item {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 32px;
  align-items: center;
  width: 100%;
  padding: 8px 10px;
  border: 1px solid var(--line);
  border-radius: 8px;
  background: #ffffff;
  color: var(--text);
  text-align: left;
  cursor: pointer;
}

.session-item.active {
  border-color: var(--primary);
  background: var(--primary-soft);
}

.session-item span {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.metric-list {
  display: grid;
  gap: 10px;
}

.metric-list div {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  padding: 8px 0;
  border-bottom: 1px solid var(--line);
}

.metric-list div:last-child {
  border-bottom: 0;
}

.metric-list span {
  color: var(--muted);
  font-size: 13px;
}

.metric-list strong {
  font-size: 15px;
}

@media (max-width: 980px) {
  .chat-layout {
    grid-template-columns: 1fr;
  }

  .chat-main {
    min-height: 560px;
  }
}

@media (max-width: 640px) {
  .composer {
    grid-template-columns: 1fr;
  }

  .bubble {
    max-width: 86%;
  }
}
</style>
