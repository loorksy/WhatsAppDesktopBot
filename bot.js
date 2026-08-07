require('./patch-wwebjs-puppeteer');
const { Client, LocalAuth } = require('whatsapp-web.js');
const qrcode = require('qrcode');
const EventEmitter = require('events');
const fs = require('fs-extra');
const path = require('path');
const store = require('./store');

class WhatsAppBot extends EventEmitter {
  constructor() {
    super();
    this.connected = false;
    this.clientReady = false;
    this.running = false;
    this.linkState = 'not_linked';
    this.queue = [];
    this.processing = false;
    this.settings = null;
    this.clients = [];
    this.selectedGroups = [];
    this.processed = [];
    this.lastChecked = {};
    this.groupDirectory = {};
    this.interactedLogs = [];
    this.skippedLogs = [];
    this.rateWindow = [];
    this.lastActionByGroup = {};
    this.bulkState = { state: 'idle', sent: 0, total: 0, groupId: null, paused: false, messages: [], delaySeconds: 6, rpm: 10 };
    this.bulkTimer = null;
    this.client = null;
    this.initialized = false;
    this.lastQr = null;
    this.lastPairingCode = null;
    this.pairingPhone = null;
    this.authMode = null;
    this.forwardQueue = [];
    this.forwardMeta = { lastForwardedAt: null };
    this.forwardFlushing = false;
    this.nameTracking = { pending: [], interacted: [], seenIds: [] };
    this.reconnectTimer = null;
    this.reconnectAttempts = 0;
  }

  async init() {
    await store.ensure();
    this.settings = await store.read('settings.json');
    this.applySettingsDefaults();
    this.emitLog(
      `Settings loaded (forwardEnabled=${this.settings.forwardEnabled}, target=${this.settings.forwardTargetChatId || 'none'})`
    );
    this.clients = await store.read('clients.json');
    this.selectedGroups = await store.read('groups.json');
    this.processed = await store.read('processed.json');
    this.lastChecked = await store.read('lastChecked.json');
    this.groupDirectory = await store.read('groupDirectory.json');
    this.bulkState = await store.read('bulkState.json');
    this.interactedLogs = await store.read('interactedLogs.json');
    this.skippedLogs = await store.read('skippedLogs.json');
    this.forwardQueue = await store.read('forwardQueue.json');
    this.forwardMeta = await store.read('forwardMeta.json');
    this.nameTracking = await store.read('nameTracking.json');
    if (!(this.nameTracking.pending || []).length && !(this.nameTracking.interacted || []).length && this.clients.length) {
      this.syncNameTrackingFromClients();
      await store.write('nameTracking.json', this.nameTracking);
    }

    this.initialized = true;
    const hasSession = await this.hasStoredSession();
    if (hasSession) {
      try {
        this.createClient();
        this.linkState = 'linking';
        await this.client.initialize();
        this.emitLog('Bot initialized with stored session.');
      } catch (err) {
        this.linkState = 'not_linked';
        this.emitLog(`Session restore failed: ${err.message}`);
      }
    } else {
      this.linkState = 'not_linked';
      this.emitLog('WhatsApp not linked. Choose QR or phone number to connect.');
    }
    this.emitStatus();
    if (this.settings.forwardFlushOnIdle && this.forwardQueue.length) {
      this.flushForwardBatch(true);
    }
  }

  getPuppeteerArgs() {
    const puppeteerArgs = {
      headless: true,
      args: ['--no-sandbox', '--disable-setuid-sandbox', '--disable-dev-shm-usage'],
    };
    if (process.env.PUPPETEER_EXECUTABLE_PATH) {
      puppeteerArgs.executablePath = process.env.PUPPETEER_EXECUTABLE_PATH;
    }
    return puppeteerArgs;
  }

  async hasStoredSession() {
    const sessionPath = path.join(store.dataDir, 'sessions');
    if (!(await fs.pathExists(sessionPath))) return false;
    const entries = await fs.readdir(sessionPath);
    return entries.length > 0;
  }

  createClient({ phoneNumber } = {}) {
    const options = {
      authStrategy: new LocalAuth({ dataPath: path.join(store.dataDir, 'sessions') }),
      puppeteer: this.getPuppeteerArgs(),
    };
    if (phoneNumber) {
      options.pairWithPhoneNumber = {
        phoneNumber,
        showNotification: true,
        intervalMs: 180000,
      };
    }
    this.client = new Client(options);
    this.applyReadyPatch();
    this.registerEvents();
  }

  applyReadyPatch() {
    this.client.on('ready', async () => {
      try {
        const page = this.client.pupPage;
        if (!page) return;
        await page.evaluate(() => {
          try {
            if (window.WWebJS && window.WWebJS.sendSeen) {
              window.WWebJS.sendSeen = async () => {};
            }
            if (window.Store && window.Store.Chat && window.Store.Chat._models) {
              Object.values(window.Store.Chat._models).forEach((chat) => {
                if (chat.sendSeen) chat.sendSeen = async () => {};
                if (chat.markUnread) chat.markUnread = async () => {};
                if (chat.markRead) chat.markRead = async () => {};
              });
            }
          } catch (e) {}
        });
      } catch (err) {}
    });
  }

  async destroyClient() {
    this.clearReconnectTimer();
    if (this.client) {
      try {
        await this.client.destroy();
      } catch (err) {
        this.emitLog(`Client destroy error: ${err.message}`);
      }
      this.client.removeAllListeners();
      this.client = null;
    }
    this.connected = false;
    this.clientReady = false;
    this.lastQr = null;
    this.lastPairingCode = null;
  }

  normalizePhoneNumber(phone) {
    if (!phone) return null;
    const digits = String(phone).replace(/\D/g, '');
    if (digits.length < 10 || digits.length > 15) return null;
    return digits;
  }

  maskPhone(phone) {
    if (!phone || phone.length < 4) return phone;
    return `${phone.slice(0, -4).replace(/\d/g, '*')}${phone.slice(-4)}`;
  }

  async startQrLink() {
    if (this.linkState === 'ready') {
      const err = new Error('ALREADY_LINKED');
      err.code = 'ALREADY_LINKED';
      throw err;
    }
    await this.destroyClient();
    this.authMode = 'qr';
    this.pairingPhone = null;
    this.lastPairingCode = null;
    this.createClient();
    this.linkState = 'linking';
    this.emitStatus();
    await this.client.initialize();
    this.emitLog('QR linking started.');
  }

  async startPhoneLink(phoneNumber) {
    if (this.linkState === 'ready') {
      const err = new Error('ALREADY_LINKED');
      err.code = 'ALREADY_LINKED';
      throw err;
    }
    const normalized = this.normalizePhoneNumber(phoneNumber);
    if (!normalized) {
      const err = new Error('INVALID_PHONE');
      err.code = 'INVALID_PHONE';
      throw err;
    }
    await this.destroyClient();
    this.authMode = 'phone';
    this.pairingPhone = normalized;
    this.lastQr = null;
    this.createClient({ phoneNumber: normalized });
    this.linkState = 'linking';
    this.emitStatus();
    await this.client.initialize();
    this.emitLog(`Phone linking started for ${this.maskPhone(normalized)}.`);
  }

  async clearSession() {
    await this.destroyClient();
    const sessionPath = path.join(store.dataDir, 'sessions');
    if (await fs.pathExists(sessionPath)) {
      await fs.remove(sessionPath);
    }
    this.linkState = 'not_linked';
    this.authMode = null;
    this.pairingPhone = null;
    this.emitStatus();
    this.emitLog('WhatsApp session cleared.');
  }

  getPairingState() {
    return {
      authMode: this.authMode,
      qr: this.lastQr,
      pairingCode: this.lastPairingCode,
      phone: this.pairingPhone ? this.maskPhone(this.pairingPhone) : null,
      linkState: this.linkState,
    };
  }

  applySettingsDefaults() {
    this.settings = {
      rpm: 20,
      cooldownSeconds: 3,
      normalizeArabicEnabled: true,
      replyMode: false,
      defaultEmoji: '✅',
      forwardEnabled: true,
      forwardTargetChatId: '',
      forwardBatchSize: 10,
      forwardFlushOnIdle: true,
      bulkDelaySeconds: 6,
      bulkRpm: 10,
      bulkMessagesPerMinute: 10,
      ...this.settings,
    };
    this.settings = this.applySafetyLimits(this.settings);
  }

  clampNumber(value, min, max, fallback) {
    const number = Number(value);
    if (!Number.isFinite(number)) return fallback;
    return Math.min(max, Math.max(min, number));
  }

  applySafetyLimits(settings = {}) {
    const rpm = this.clampNumber(settings.rpm, 1, 20, 20);
    const cooldownSeconds = this.clampNumber(settings.cooldownSeconds, 3, 3600, 3);
    const bulkRpm = this.clampNumber(settings.bulkMessagesPerMinute ?? settings.bulkRpm, 1, 10000, 10);
    const minDelayFromRpm = 60 / bulkRpm;
    const requestedBulkDelay = this.clampNumber(settings.bulkDelaySeconds, minDelayFromRpm, 3600, minDelayFromRpm);
    const bulkDelaySeconds = Math.max(requestedBulkDelay, minDelayFromRpm);

    return {
      ...settings,
      rpm,
      cooldownSeconds,
      bulkDelaySeconds,
      bulkRpm,
      bulkMessagesPerMinute: bulkRpm,
    };
  }

  getNameTrackingPublic() {
    return {
      pending: [...(this.nameTracking?.pending || [])],
      interacted: [...(this.nameTracking?.interacted || [])],
    };
  }

  async persistNameTracking() {
    if (this.nameTracking.seenIds.length > 5000) {
      this.nameTracking.seenIds = this.nameTracking.seenIds.slice(-5000);
    }
    await store.write('nameTracking.json', this.nameTracking);
    this.emit('names:update', this.getNameTrackingPublic());
  }

  syncNameTrackingFromClients() {
    const names = (this.clients || []).map((c) => c.name).filter(Boolean);
    const interactedSet = new Set(this.nameTracking.interacted || []);
    const pendingSet = new Set(this.nameTracking.pending || []);
    names.forEach((name) => {
      if (!interactedSet.has(name)) pendingSet.add(name);
    });
    const nameSet = new Set(names);
    this.nameTracking.pending = [...pendingSet].filter((n) => nameSet.has(n) && !interactedSet.has(n));
    this.nameTracking.interacted = [...interactedSet].filter((n) => nameSet.has(n));
  }

  async trackClientInteraction(name, id) {
    if (!name) return;
    if (id && (this.nameTracking.seenIds || []).includes(id)) return;
    if (id) this.nameTracking.seenIds.push(id);
    this.nameTracking.pending = (this.nameTracking.pending || []).filter((n) => n !== name);
    if (!(this.nameTracking.interacted || []).includes(name)) {
      this.nameTracking.interacted.push(name);
    }
    await this.persistNameTracking();
  }

  async resetNameTracking() {
    this.nameTracking = { pending: [], interacted: [], seenIds: [] };
    this.syncNameTrackingFromClients();
    await this.persistNameTracking();
  }

  clearReconnectTimer() {
    if (this.reconnectTimer) {
      clearTimeout(this.reconnectTimer);
      this.reconnectTimer = null;
    }
  }

  scheduleReconnect(reason) {
    if (this.reconnectTimer || this.authMode) return;
    const delay = Math.min(60000, 5000 * Math.max(1, this.reconnectAttempts + 1));
    this.emitLog(`Scheduling reconnect in ${Math.round(delay / 1000)}s (${reason || 'unknown'})`);
    this.reconnectTimer = setTimeout(async () => {
      this.reconnectTimer = null;
      if (!(await this.hasStoredSession())) return;
      this.attemptReconnect();
    }, delay);
  }

  async attemptReconnect() {
    try {
      const hasSession = await this.hasStoredSession();
      if (!hasSession) {
        this.linkState = 'not_linked';
        this.emitStatus();
        return;
      }
      await this.destroyClient();
      this.createClient();
      this.linkState = 'linking';
      this.emitStatus();
      await this.client.initialize();
      this.reconnectAttempts = 0;
      this.emitLog('Reconnect attempt started.');
    } catch (err) {
      this.reconnectAttempts += 1;
      this.emitLog(`Reconnect failed: ${err.message}`);
      this.scheduleReconnect('retry');
    }
  }

  async refreshGroupDirectory() {
    if (!this.clientReady || !this.client) return this.groupDirectory;
    let chats;
    try {
      chats = await this.client.getChats();
    } catch (err) {
      this.emitLog(`Group directory refresh failed: ${err.message}`);
      return this.groupDirectory;
    }
    const groups = chats.filter((c) => c.isGroup);
    const updated = { ...this.groupDirectory };
    for (const g of groups) {
      updated[g.id._serialized] = g.name || g.id._serialized;
    }
    this.groupDirectory = updated;
    await store.write('groupDirectory.json', this.groupDirectory);
    return this.groupDirectory;
  }

  async recordGroupMeta(id, name) {
    if (!id || !id.endsWith('@g.us')) return;
    if (this.groupDirectory[id] && !name) return;
    const updated = { ...this.groupDirectory, [id]: name || this.groupDirectory[id] || id };
    this.groupDirectory = updated;
    await store.write('groupDirectory.json', this.groupDirectory);
  }

  registerEvents() {
    this.client.on('qr', async (qr) => {
      const qrImage = await qrcode.toDataURL(qr);
      this.lastQr = qrImage;
      this.clientReady = false;
      this.linkState = 'qr';
      this.authMode = 'qr';
      this.emit('qr', qrImage);
      this.emitLog('QR code generated.');
      this.emitStatus();
    });

    this.client.on('code', (code) => {
      this.lastPairingCode = code;
      this.clientReady = false;
      this.linkState = 'pairing';
      this.authMode = 'phone';
      this.emit('pairing-code', { code, phone: this.pairingPhone ? this.maskPhone(this.pairingPhone) : null });
      this.emitLog(`Pairing code generated: ${code}`);
      this.emitStatus();
    });

    this.client.on('ready', () => {
      this.connected = true;
      this.clientReady = true;
      this.linkState = 'ready';
      this.lastQr = null;
      this.lastPairingCode = null;
      this.authMode = null;
      this.reconnectAttempts = 0;
      this.clearReconnectTimer();
      this.emitStatus();
      this.emitLog('WhatsApp client ready.');
      this.refreshGroupDirectory();
      if (this.queue.length > 0 && this.running) {
        this.processQueue();
      }
      if (this.forwardQueue.length > 0) {
        this.flushForwardBatch(true);
      }
    });

    this.client.on('authenticated', () => {
      this.clientReady = false;
      this.linkState = 'linking';
      this.emitLog('Authenticated with WhatsApp.');
      this.emitStatus();
    });

    this.client.on('authenticated_failure', (msg) => {
      this.connected = false;
      this.clientReady = false;
      this.linkState = 'not_linked';
      this.emitStatus();
      this.emitLog(`Authenticated failure: ${msg}`);
    });

    this.client.on('auth_failure', (msg) => {
      this.connected = false;
      this.clientReady = false;
      this.linkState = 'not_linked';
      this.emitStatus();
      this.emitLog(`Auth failure: ${msg}`);
    });

    this.client.on('disconnected', (reason) => {
      this.connected = false;
      this.clientReady = false;
      this.linkState = 'disconnected';
      this.emitStatus();
      this.emitLog(`Disconnected: ${reason}`);
      this.scheduleReconnect(reason);
    });

    this.client.on('message', async (message) => {
      try {
        if (message?.from?.endsWith('@g.us')) {
          await this.recordGroupMeta(message.from, message._data?.notifyName || message._data?.sender?.pushname);
          if (!this.lastChecked[message.from]) {
            this.lastChecked[message.from] = (message.timestamp || Date.now() / 1000) * 1000;
            await store.write('lastChecked.json', this.lastChecked);
          }
        }
        await this.handleIncoming(message);
      } catch (err) {
        this.emitLog(`Message handler error: ${err.message}`);
      }
    });
  }

  emitLog(msg) {
    this.emit('log', `[${new Date().toISOString()}] ${msg}`);
  }

  emitStatus() {
    this.emit('status', {
      connected: this.connected,
      running: this.running,
      linkState: this.linkState,
      authMode: this.authMode,
      pairingCode: this.lastPairingCode,
      pairingPhone: this.pairingPhone ? this.maskPhone(this.pairingPhone) : null,
      bulk: this.getBulkPublicState(),
      lastChecked: this.lastChecked,
      forward: this.getForwardState(),
    });
  }

  assertClientReady(action = 'operation') {
    if (!this.clientReady) {
      this.emitLog(`Cannot ${action}: WhatsApp not ready.`);
      const err = new Error('WA_NOT_READY');
      err.code = 'WA_NOT_READY';
      throw err;
    }
  }

  getBulkPublicState() {
    const { state, sent, total, groupId, paused } = this.bulkState;
    return { state, sent, total, groupId, paused };
  }

  getForwardState() {
    return {
      enabled: this.settings?.forwardEnabled,
      targetChatId: this.settings?.forwardTargetChatId || '',
      batchSize: this.settings?.forwardBatchSize || 10,
      flushOnIdle: this.settings?.forwardFlushOnIdle,
      queueLength: this.forwardQueue.length,
      lastForwardedAt: this.forwardMeta?.lastForwardedAt || null,
    };
  }

  getLastQr() {
    return this.lastQr;
  }

  async setRunning(running) {
    this.running = running;
    this.emitStatus();
    this.emitLog(`Bot ${running ? 'started' : 'stopped'}.`);
  }

  async shouldProcessMessage(message) {
    if (!this.running) return { eligible: false, reason: 'bot stopped' };
    if (!message.from.endsWith('@g.us')) return { eligible: false, reason: 'not a group' };
    if (!this.selectedGroups.includes(message.from)) return { eligible: false, reason: 'group not selected' };
    if (message.fromMe) return { eligible: false, reason: 'from self' };

    const text = await this.extractText(message);
    if (!text) return { eligible: false, reason: 'no text' };

    return { eligible: true, reason: null, text };
  }

  async handleIncoming(message) {
    const { eligible, reason } = await this.shouldProcessMessage(message);
    if (!eligible) {
      await this.recordSkipped(message, reason);
      return;
    }

    if (this.isProcessed(message.id._serialized)) {
      await this.recordSkipped(message, 'already processed');
      return;
    }

    this.queue.push(message);
    this.processQueue();
  }

  async processQueue() {
    if (this.processing || this.forwardFlushing) return;
    this.processing = true;
    while (this.queue.length > 0) {
      if (!this.running) break;
      if (!this.clientReady) {
        this.emitLog('WhatsApp not ready, queue paused.');
        break;
      }
      if (this.forwardFlushing) break;
      const msg = this.queue.shift();
      try {
        await this.processMessage(msg);
        if (this.shouldFlushForwardOnBatch()) {
          await this.flushForwardBatch();
        }
      } catch (err) {
        this.emitLog(`Error processing message ${msg.id._serialized}: ${err.message}`);
      }
    }
    this.processing = false;
    if (!this.forwardFlushing && this.settings.forwardFlushOnIdle && this.queue.length === 0) {
      await this.flushForwardBatch(true);
    }
  }

  normalizeArabic(text) {
    if (!text) return '';
    let normalized = text.normalize('NFKD');
    normalized = normalized.replace(/[\u0610-\u061A\u064B-\u065F\u0670\u06D6-\u06ED]/g, '');
    normalized = normalized.replace(/[أإآ]/g, 'ا').replace(/ى/g, 'ي');
    const arabicIndic = '٠١٢٣٤٥٦٧٨٩';
    arabicIndic.split('').forEach((num, idx) => {
      normalized = normalized.replace(new RegExp(num, 'g'), idx.toString());
    });
    normalized = normalized.replace(/\s+/g, ' ').trim();
    return normalized;
  }

  escapeRegex(str) {
    return str.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
  }

  async extractText(message) {
    let text = message.body || '';
    if (!text && message.caption) text = message.caption;
    if (!text && message._data?.caption) text = message._data.caption;
    if (!text && message._data?.body) text = message._data.body;
    if (!text && message.hasQuotedMsg) {
      try {
        const quoted = await message.getQuotedMessage();
        if (quoted?.body) text = quoted.body;
      } catch (err) {
        this.emitLog(`Quoted fetch failed: ${err.message}`);
      }
    }
    if (!text && message._data?.quotedMsg?.body) {
      text = message._data.quotedMsg.body;
    }
    return text;
  }

  getSnippet(text) {
    if (!text) return '';
    return text.length > 80 ? `${text.slice(0, 77)}...` : text;
  }

  matchClient(text) {
    if (!text) return null;
    const target = this.settings.normalizeArabicEnabled ? this.normalizeArabic(text) : text;
    for (const client of this.clients) {
      const name = this.settings.normalizeArabicEnabled ? this.normalizeArabic(client.name) : client.name;
      if (!name) continue;
      const regex = new RegExp(this.escapeRegex(name), 'i');
      if (regex.test(target)) {
        return { match: name, emoji: client.emoji || this.settings.defaultEmoji };
      }
    }
    return null;
  }

  isProcessed(id) {
    return this.processed.includes(id);
  }

  markProcessed(id) {
    if (this.isProcessed(id)) return;
    this.processed.push(id);
    if (this.processed.length > 50000) {
      this.processed = this.processed.slice(-50000);
    }
    store.write('processed.json', this.processed);
  }

  async recordInteraction(message, matchResult, action, text) {
    const entry = {
      ts: Date.now(),
      groupId: message.from,
      groupName: message._data?.notifyName || message.from,
      match: matchResult?.match || '',
      action,
      snippet: this.getSnippet(text || (await this.extractText(message))),
      id: message.id?._serialized,
    };
    this.interactedLogs = await store.appendLimited('interactedLogs.json', entry, 2000);
    if (matchResult?.match) {
      await this.trackClientInteraction(matchResult.match, message.id?._serialized);
    }
    this.emit('interaction:log', { interacted: this.interactedLogs, skipped: this.skippedLogs });
  }

  async recordSkipped(message, reason) {
    const entry = {
      ts: Date.now(),
      groupId: message?.from,
      groupName: message?._data?.notifyName || message?.from,
      reason,
      snippet: this.getSnippet(message?.body || ''),
      id: message?.id?._serialized,
    };
    this.skippedLogs = await store.appendLimited('skippedLogs.json', entry, 2000);
    this.emit('interaction:log', { interacted: this.interactedLogs, skipped: this.skippedLogs });
  }

  shouldFlushForwardOnBatch() {
    return (
      this.settings.forwardEnabled &&
      this.settings.forwardTargetChatId &&
      this.forwardQueue.length >= (this.settings.forwardBatchSize || 10)
    );
  }

  async enqueueForward(message) {
    if (!this.settings.forwardEnabled || !this.settings.forwardTargetChatId) return;
    const id = message?.id?._serialized;
    if (!id) return;
    if (this.forwardQueue.find((f) => f.messageId === id)) return;
    const item = { sourceChatId: message.from, messageId: id, timestamp: Date.now() };
    this.forwardQueue.push(item);
    await store.write('forwardQueue.json', this.forwardQueue);
    this.emitStatus();
  }

  async clearForwardQueue() {
    this.forwardQueue = [];
    await store.write('forwardQueue.json', this.forwardQueue);
    this.emitStatus();
  }

  async recordForwarded(message) {
    if (!message) return;
    const entry = {
      ts: Date.now(),
      groupId: message.from,
      groupName: message._data?.notifyName || message.from,
      match: 'forward',
      action: 'forwarded',
      snippet: this.getSnippet(message.body || message.caption || ''),
      id: message.id?._serialized,
    };
    this.interactedLogs = await store.appendLimited('interactedLogs.json', entry, 2000);
    this.emit('interaction:log', { interacted: this.interactedLogs, skipped: this.skippedLogs });
  }

  async flushForwardBatch(force = false) {
    if (this.forwardFlushing) return;
    if (!this.settings.forwardEnabled || !this.settings.forwardTargetChatId) return;
    const batchSize = this.settings.forwardBatchSize || 10;
    if (!force && this.forwardQueue.length < batchSize) return;
    if (!this.clientReady) {
      this.emitLog('Cannot flush forward queue: WhatsApp not ready.');
      return;
    }
    this.forwardFlushing = true;
    this.emitLog('Flushing forward queue...');
    try {
      const target = this.settings.forwardTargetChatId;
      const items = [...this.forwardQueue];
      for (const item of items) {
        try {
          const msg = await this.client.getMessageById(item.messageId);
          if (!msg) {
            this.emitLog(`Forward lookup failed for ${item.messageId}`);
            continue;
          }
          await this.ensureRateLimit();
          await this.respectCooldown(target);
          await msg.forward(target);
          this.forwardQueue = this.forwardQueue.filter((f) => f.messageId !== item.messageId);
          await store.write('forwardQueue.json', this.forwardQueue);
          this.forwardMeta.lastForwardedAt = Date.now();
          await store.write('forwardMeta.json', this.forwardMeta);
          await this.recordForwarded(msg);
          this.emitLog(`Forwarded ${item.messageId} to ${target}`);
        } catch (err) {
          this.emitLog(`Forward error for ${item.messageId}: ${err.message}`);
        }
      }
      this.emitStatus();
    } finally {
      this.forwardFlushing = false;
      if (this.queue.length > 0 && this.running) {
        this.processQueue();
      }
    }
  }

  async ensureRateLimit(rpmOverride) {
    const now = Date.now();
    const requestedRpm = rpmOverride || this.settings.rpm || 20;
    const maxRpm = rpmOverride ? 10000 : 20;
    const rpmLimit = this.clampNumber(requestedRpm, 1, maxRpm, rpmOverride ? 10 : 20);
    this.rateWindow = this.rateWindow.filter((t) => now - t < 60000);
    while (this.rateWindow.length >= rpmLimit) {
      await new Promise((res) => setTimeout(res, 1000));
      const nowInner = Date.now();
      this.rateWindow = this.rateWindow.filter((t) => nowInner - t < 60000);
    }
    this.rateWindow.push(Date.now());
  }

  async respectCooldown(groupId) {
    const last = this.lastActionByGroup[groupId] || 0;
    const cooldown = this.clampNumber(this.settings.cooldownSeconds, 3, 3600, 3) * 1000;
    const delta = Date.now() - last;
    if (delta < cooldown) {
      await new Promise((res) => setTimeout(res, cooldown - delta));
    }
    this.lastActionByGroup[groupId] = Date.now();
  }

  async processMessage(message) {
    const text = await this.extractText(message);
    const matchResult = this.matchClient(text);
    if (!matchResult) {
      this.markProcessed(message.id._serialized);
      await this.recordSkipped(message, 'no match');
      return;
    }

    await this.ensureRateLimit();
    await this.respectCooldown(message.from);

    if (this.settings.replyMode) {
      await message.reply(matchResult.emoji);
      await this.recordInteraction(message, matchResult, 'reply', text);
    } else {
      await message.react(matchResult.emoji);
      await this.recordInteraction(message, matchResult, 'reaction', text);
    }

    this.markProcessed(message.id._serialized);
    await this.enqueueForward(message);
    this.emitLog(`Processed message ${message.id.id} in ${message.from}`);
  }

  async refreshGroups() {
    this.assertClientReady('refresh groups');
    const chats = await this.client.getChats();
    const groups = chats
      .filter((c) => c.isGroup)
      .map((c) => ({ id: c.id._serialized, name: c.name, selected: this.selectedGroups.includes(c.id._serialized) }));
    for (const g of groups) {
      await this.recordGroupMeta(g.id, g.name);
    }
    await store.write('groups.json', this.selectedGroups);
    return groups;
  }

  chatDisplayName(chat) {
    if (!chat) return 'محادثة';
    return (
      chat.name ||
      chat.formattedTitle ||
      chat.pushname ||
      (chat.id && (chat.id.user || chat.id._serialized)) ||
      'محادثة'
    );
  }

  async refreshChats() {
    this.assertClientReady('refresh chats');
    const chats = await this.client.getChats();
    const mapped = [];
    for (const c of chats) {
      if (!c || !c.id || !c.id._serialized || c.isStatus) continue;
      const id = c.id._serialized;
      const isGroup = !!c.isGroup;
      const name = this.chatDisplayName(c);
      if (isGroup) {
        await this.recordGroupMeta(id, name);
      }
      mapped.push({
        id,
        name,
        isGroup,
        selected: isGroup ? this.selectedGroups.includes(id) : false,
        unreadCount: c.unreadCount || 0,
        timestamp: c.timestamp || 0,
      });
    }
    mapped.sort((a, b) => (b.timestamp || 0) - (a.timestamp || 0));
    return mapped;
  }

  async setSelectedGroups(ids) {
    this.selectedGroups = ids || [];
    await store.write('groups.json', this.selectedGroups);
    this.emitLog('Selected groups updated.');
  }

  async setClients(rawText) {
    const lines = rawText.split(/\n+/).map((l) => l.trim()).filter(Boolean);
    const parsed = lines.map((line) => {
      const [name, emoji] = line.split('|').map((s) => (s || '').trim());
      return { name, emoji };
    });
    this.clients = parsed;
    await store.write('clients.json', this.clients);
    this.syncNameTrackingFromClients();
    await this.persistNameTracking();
    this.emitLog('Clients updated.');
  }

  async setSettings(newSettings) {
    this.settings = { ...this.settings, ...newSettings };
    this.applySettingsDefaults();
    try {
      await store.write('settings.json', this.settings);
      this.emitLog(
        `Settings updated (forwardEnabled=${this.settings.forwardEnabled}, target=${this.settings.forwardTargetChatId || 'none'})`
      );
      this.emitStatus();
    } catch (err) {
      this.emitLog(`Failed to save settings: ${err.message}`);
      throw err;
    }
  }

  async checkBacklog({ sinceTimestamp, hours, limitCap = 500 }) {
    const result = await this.scanBacklog({ sinceTimestamp, hours, limitCap, process: false });
    this.emit('backlog:update', result);
    return result;
  }

  async processBacklog({ sinceTimestamp, hours, limitCap = 500 }) {
    const result = await this.scanBacklog({ sinceTimestamp, hours, limitCap, process: true });
    this.emit('backlog:update', result);
    return result;
  }

  async scanBacklog({ sinceTimestamp, hours, limitCap, process }) {
    this.assertClientReady('scan backlog');
    await this.refreshGroupDirectory();
    const now = Date.now();
    const targetSince = sinceTimestamp || (hours ? now - hours * 3600000 : null);
    const response = [];

    const targetGroups = Array.from(new Set([...(this.selectedGroups || []), ...Object.keys(this.groupDirectory || {})]));

    for (const groupId of targetGroups) {
      if (!this.clientReady) {
        this.assertClientReady('scan backlog');
      }
      const since = targetSince || this.lastChecked[groupId] || 0;
      let limit = 50;
      let done = false;
      let chat;
      try {
        chat = await this.client.getChatById(groupId);
      } catch (err) {
        this.emitLog(`Backlog: unable to load ${groupId} (${err.message})`);
        continue;
      }
      const collected = [];
      let newestTs = since;

      while (!done) {
        const messages = await chat.fetchMessages({ limit });
        if (!messages || messages.length === 0) {
          break;
        }
        messages.forEach((m) => {
          const ts = m.timestamp * 1000;
          if (ts >= since) {
            collected.push(m);
            if (ts > newestTs) newestTs = ts;
          }
        });

        const lastMsg = messages[messages.length - 1];
        if (messages.length < limit || limit >= limitCap || (lastMsg && lastMsg.timestamp * 1000 <= since)) {
          done = true;
        } else {
          limit = Math.min(limit + 50, limitCap);
        }
      }

      collected.sort((a, b) => a.timestamp - b.timestamp);
      let eligibleCount = 0;
      for (const msg of collected) {
        if (this.isProcessed(msg.id._serialized)) {
          await this.recordSkipped(msg, 'already processed');
          continue;
        }
        const { eligible, reason } = await this.shouldProcessMessage(msg);
        if (!eligible) {
          await this.recordSkipped(msg, reason || 'filtered');
          continue;
        }
        eligibleCount += 1;
        if (process) {
          this.queue.push(msg);
        }
      }
      if (process && eligibleCount > 0) {
        this.processQueue();
      }
      this.lastChecked[groupId] = newestTs || now;
      await store.write('lastChecked.json', this.lastChecked);
      const groupName = this.groupDirectory[groupId] || groupId;
      response.push({
        groupId,
        groupName,
        found: eligibleCount,
        processed: process ? eligibleCount : 0,
        lastChecked: this.lastChecked[groupId],
      });
    }

    return response;
  }

  async startBulk({ groupId, messages, delaySeconds = 2, rpm = 10 }) {
    this.assertClientReady('start bulk');
    if (!groupId || !Array.isArray(messages) || messages.length === 0) {
      throw new Error('Invalid bulk payload');
    }
    const safeRpm = this.clampNumber(rpm, 1, 10000, this.settings?.bulkRpm || 10);
    const minDelay = 60 / safeRpm;
    const requestedDelay = Number(delaySeconds);
    const safeDelaySeconds = Math.max(
      Number.isFinite(requestedDelay) ? requestedDelay : minDelay,
      minDelay
    );
    this.bulkState = {
      state: 'running',
      sent: 0,
      total: messages.length,
      groupId,
      paused: false,
      messages,
      delaySeconds: safeDelaySeconds,
      rpm: safeRpm,
    };
    await store.write('bulkState.json', this.bulkState);
    this.emit('bulk:update', this.getBulkPublicState());
    this.runBulkLoop();
  }

  async pauseBulk() {
    if (this.bulkState.state !== 'running') return;
    this.bulkState.paused = true;
    await store.write('bulkState.json', this.bulkState);
    this.emit('bulk:update', this.getBulkPublicState());
  }

  async resumeBulk() {
    if (this.bulkState.state !== 'running') return;
    this.bulkState.paused = false;
    await store.write('bulkState.json', this.bulkState);
    this.emit('bulk:update', this.getBulkPublicState());
  }

  async stopBulk() {
    this.bulkState.state = 'idle';
    this.bulkState.paused = false;
    this.bulkState.sent = 0;
    this.bulkState.total = 0;
    this.bulkState.messages = [];
    await store.write('bulkState.json', this.bulkState);
    this.emit('bulk:update', this.getBulkPublicState());
  }

  async runBulkLoop() {
    if (this.bulkTimer) clearTimeout(this.bulkTimer);
    const loop = async () => {
      if (this.bulkState.state !== 'running') return;
      if (this.bulkState.paused) {
        this.bulkTimer = setTimeout(loop, 1000);
        return;
      }
      if (!this.clientReady) {
        this.emitLog('Bulk waiting for WhatsApp readiness...');
        this.bulkTimer = setTimeout(loop, 3000);
        return;
      }
      if (this.bulkState.sent >= this.bulkState.total) {
        await this.stopBulk();
        return;
      }

      const message = this.bulkState.messages[this.bulkState.sent];
      try {
        const chat = await this.client.getChatById(this.bulkState.groupId);
        await this.ensureRateLimit(this.bulkState.rpm);
        await chat.sendMessage(message);
        this.bulkState.sent += 1;
        await store.write('bulkState.json', this.bulkState);
        this.emit('bulk:update', this.getBulkPublicState());
        this.emitLog(`Bulk message sent (${this.bulkState.sent}/${this.bulkState.total}).`);
      } catch (err) {
        this.emitLog(`Bulk error: ${err.message}`);
      }

      const safeRpm = this.clampNumber(this.bulkState.rpm, 1, 10000, 10);
      const minDelay = 60 / safeRpm;
      const safeDelaySeconds = Math.max(
        Number(this.bulkState.delaySeconds) || minDelay,
        minDelay
      );
      this.bulkTimer = setTimeout(loop, Math.max(1, Math.round(safeDelaySeconds * 1000)));
    };

    loop();
  }

  getInteractionLogs() {
    return { interacted: this.interactedLogs, skipped: this.skippedLogs };
  }

  async clearInteractionLogs(type) {
    if (type === 'interacted') {
      this.interactedLogs = [];
      await store.write('interactedLogs.json', this.interactedLogs);
    }
    if (type === 'skipped') {
      this.skippedLogs = [];
      await store.write('skippedLogs.json', this.skippedLogs);
    }
    this.emit('interaction:log', { interacted: this.interactedLogs, skipped: this.skippedLogs });
  }
}

module.exports = WhatsAppBot;
