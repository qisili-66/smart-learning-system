<script setup>
defineProps({
  label: { type: String, required: true },
  value: { type: [String, Number], default: '-' },
  hint: { type: String, default: '' },
  tone: { type: String, default: 'blue' },
  icon: { type: String, default: 'TrendCharts' }
})
</script>

<template>
  <div class="metric-card panel lift-card">
    <div class="metric-icon" :class="`tone-${tone}`">
      <el-icon><component :is="icon" /></el-icon>
    </div>
    <div>
      <p class="metric-label">{{ label }}</p>
      <strong>{{ value }}</strong>
      <span v-if="hint">{{ hint }}</span>
    </div>
  </div>
</template>

<style scoped>
.metric-card {
  display: flex;
  align-items: center;
  gap: 14px;
  min-height: 108px;
  padding: 18px;
}

.metric-icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 42px;
  height: 42px;
  border-radius: 8px;
  font-size: 22px;
}

.tone-blue {
  color: var(--primary);
  background: var(--primary-soft);
}

.tone-green {
  color: var(--success);
  background: #edf8e8;
}

.tone-orange {
  color: var(--orange);
  background: #fff4e6;
}

.tone-red {
  color: var(--danger);
  background: #fff0f0;
}

.metric-label {
  margin: 0 0 8px;
  color: var(--muted);
  font-size: 14px;
}

strong {
  display: block;
  color: var(--text);
  font-size: 28px;
  line-height: 1;
}

span {
  display: block;
  margin-top: 8px;
  color: var(--subtle);
  font-size: 12px;
}
</style>
