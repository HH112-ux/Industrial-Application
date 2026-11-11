import test from 'node:test';
import assert from 'node:assert/strict';

import {
  formatTime,
  formatWindow,
  deviceLabel,
  hoursText,
  isEstimated,
  queueLabel,
  operationLabel,
  scheduleSummary
} from './schedule.mjs';

test('formatTime drops the ISO marker and milliseconds', () => {
  assert.equal(formatTime('2026-11-11T08:00:00.123'), '2026-11-11 08:00:00');
  assert.equal(formatTime('2026-11-11T08:00:00'), '2026-11-11 08:00:00');
});

test('formatTime returns an empty string for missing values', () => {
  assert.equal(formatTime(null), '');
  assert.equal(formatTime(undefined), '');
  assert.equal(formatTime(''), '');
});

test('formatWindow joins both ends or reports no landing', () => {
  assert.equal(
    formatWindow('2026-11-11T08:00:00', '2026-11-11T10:30:00'),
    '2026-11-11 08:00:00 ~ 2026-11-11 10:30:00'
  );
  assert.equal(formatWindow(null, null), '未落位');
  assert.equal(formatWindow('2026-11-11T08:00:00', null), '未落位');
});

test('deviceLabel prefers name, then number, then type', () => {
  assert.equal(deviceLabel({ deviceName: 'CNC-01', deviceNo: 'D001', deviceType: 'CNC' }), 'CNC-01');
  assert.equal(deviceLabel({ deviceNo: 'D001', deviceType: 'CNC' }), 'D001');
  assert.equal(deviceLabel({ deviceType: 'CNC' }), 'CNC');
  assert.equal(deviceLabel({}), '—');
  assert.equal(deviceLabel(), '—');
});

test('hoursText keeps zero and hides missing durations', () => {
  assert.equal(hoursText({ durationHours: 1.5 }), '1.5 小时');
  assert.equal(hoursText({ durationHours: 0 }), '0 小时');
  assert.equal(hoursText({ durationHours: null }), '—');
  assert.equal(hoursText({}), '—');
});

test('isEstimated only trusts the ESTIMATED source', () => {
  assert.equal(isEstimated({ hoursSource: 'ESTIMATED' }), true);
  assert.equal(isEstimated({ hoursSource: 'STANDARD' }), false);
  assert.equal(isEstimated({}), false);
});

test('queueLabel renders the rank or a placeholder', () => {
  assert.equal(queueLabel({ queueRank: 2 }), '第 2 位');
  assert.equal(queueLabel({ queueRank: null }), '—');
  assert.equal(queueLabel({}), '—');
});

test('operationLabel falls back when the conflict has no operation', () => {
  assert.equal(operationLabel({ operationSeq: 10, operationName: '粗车' }), '10 · 粗车');
  assert.equal(operationLabel({ operationSeq: 10 }), '10 · —');
  assert.equal(operationLabel({ operationSeq: null }), '工序未定');
  assert.equal(operationLabel({}), '工序未定');
});

test('scheduleSummary distinguishes scheduled, mixed and empty results', () => {
  assert.equal(scheduleSummary([]), '');
  assert.equal(scheduleSummary([{ status: 'SCHEDULED' }]), '已排入 1 道工序');
  assert.equal(
    scheduleSummary([{ status: 'SCHEDULED' }, { status: 'SCHEDULED' }, { status: 'UNSCHEDULED' }]),
    '已排入 2 道 · 无法排入 1 道'
  );
  assert.equal(
    scheduleSummary([{ status: 'UNSCHEDULED' }, { status: 'UNSCHEDULED' }]),
    '无法排入 2 道工序'
  );
});
