<template>
  <el-tag :type="dict?.type || 'info'" size="small" effect="light" disable-transitions>
    {{ dict?.label || value || '-' }}
  </el-tag>
</template>

<script setup lang="ts">
import { computed } from 'vue';
import type { DictItem } from '@/utils/dict';

const props = defineProps<{
  value?: string;
  map: Record<string, DictItem>;
}>();

const dict = computed(() => (props.value ? props.map[props.value] : undefined));
</script>
