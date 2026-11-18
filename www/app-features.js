/*
 * PauzFirst website: the app's features, on the same database as the Android app.
 *
 * Everything here reads and writes the SAME tables, columns and formats as the app (see app/.../Api.kt):
 *  - presets and "How did it feel?" ratings:  user_stats.extras = { presets: [...], ratings: {date: 1..3}, ts }
 *  - question bank:                            community_questions (all of it, page by page)
 *  - used questions:                           user_stats.used_questions = { default: [...], saved: [...] }
 *  - sharing:                                  share_list / get_shared_list / link_imported_list,
 *                                              share_preset / get_shared_preset
 *  - profile:                                  auth user (name, password), delete_my_account
 * The website's own code is not rewritten; a few of its functions are wrapped (the original still runs).
 */
(function () {
    'use strict';
    if (!document.body.classList.contains('pf-app') || typeof sb === 'undefined') return;

    // ================================================================ small helpers
    var $ = function (id) { return document.getElementById(id); };
    var I = {
        BACK: 'M15 18l-6-6 6-6', NEXT: 'M9 18l6-6-6-6', CLOSE: 'M18 6L6 18 M6 6l12 12',
        SEARCH: 'M21 21l-4.35-4.35 M11 18a7 7 0 1 0 0-14 7 7 0 0 0 0 14z',
        SHARE: 'M18 8a3 3 0 1 0 0-6 3 3 0 0 0 0 6z M6 15a3 3 0 1 0 0-6 3 3 0 0 0 0 6z M18 22a3 3 0 1 0 0-6 3 3 0 0 0 0 6z M8.6 13.5l6.8 4 M15.4 6.5l-6.8 4',
        EDIT: 'M12 20h9 M16.5 3.5a2.1 2.1 0 0 1 3 3L7 19l-4 1 1-4L16.5 3.5z',
        COPY: 'M9 9h11v11H9z M5 15H4a1 1 0 0 1-1-1V4a1 1 0 0 1 1-1h10a1 1 0 0 1 1 1v1',
        TRASH: 'M3 6h18 M19 6l-1 14a2 2 0 0 1-2 2H8a2 2 0 0 1-2-2L5 6 M10 11v6 M14 11v6 M9 6V4a1 1 0 0 1 1-1h4a1 1 0 0 1 1 1v2',
        CLOCK: 'M12 2a10 10 0 1 0 0 20 10 10 0 0 0 0-20z M12 6v6l4 2',
        CHECK: 'M5 13l4 4L19 7', HELP: 'M12 2a10 10 0 1 0 0 20 10 10 0 0 0 0-20z M9.09 9a3 3 0 0 1 5.83 1c0 2-3 3-3 3 M12 17h.01',
        UPLOAD: 'M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4 M17 8l-5-5-5 5 M12 3v12',
        DOWNLOAD: 'M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4 M7 10l5 5 5-5 M12 15V3',
        FLIP: 'M1 4v6h6 M23 20v-6h-6 M20.49 9A9 9 0 0 0 5.64 5.64L1 10m22 4l-4.64 4.36A9 9 0 0 1 3.51 15',
        MOON: 'M21 12.79A9 9 0 1 1 11.21 3 7 7 0 0 0 21 12.79z',
        SUN: 'M12 8a4 4 0 1 0 0 8 4 4 0 0 0 0-8z M12 1v2 M12 21v2 M4.22 4.22l1.42 1.42 M18.36 18.36l1.42 1.42 M1 12h2 M21 12h2 M4.22 19.78l1.42-1.42 M18.36 5.64l1.42-1.42',
        FACE_SAD: 'M12 2a10 10 0 1 0 0 20 10 10 0 0 0 0-20z M9 9h.01 M15 9h.01 M16 16s-1.5-2-4-2-4 2-4 2',
        FACE_OKAY: 'M12 2a10 10 0 1 0 0 20 10 10 0 0 0 0-20z M9 9h.01 M15 9h.01 M8 15h8',
        FACE_HAPPY: 'M12 2a10 10 0 1 0 0 20 10 10 0 0 0 0-20z M9 9h.01 M15 9h.01 M8 14s1.5 2 4 2 4-2 4-2',
        VOLUME: 'M11 5L6 9H2v6h4l5 4V5z M19.07 4.93a10 10 0 0 1 0 14.14 M15.54 8.46a5 5 0 0 1 0 7.07',
        VIBRATE: 'M7 4a1 1 0 0 1 1-1h8a1 1 0 0 1 1 1v16a1 1 0 0 1-1 1H8a1 1 0 0 1-1-1V4z M3 9v6 M21 9v6',
        BELL: 'M18 8A6 6 0 0 0 6 8c0 7-3 9-3 9h18s-3-2-3-9 M13.73 21a2 2 0 0 1-3.46 0',
        EYE: 'M2 12s3.5-7 10-7 10 7 10 7-3.5 7-10 7S2 12 2 12z M12 9a3 3 0 1 0 0 6 3 3 0 0 0 0-6z',
        VIDEO: 'M23 7l-7 5 7 5V7z M3 5h13a2 2 0 0 1 2 2v10a2 2 0 0 1-2 2H3a2 2 0 0 1-2-2V7a2 2 0 0 1 2-2z',
        HEADPHONES: 'M3 18v-6a9 9 0 0 1 18 0v6 M21 19a2 2 0 0 1-2 2h-1a2 2 0 0 1-2-2v-3a2 2 0 0 1 2-2h3z M3 19a2 2 0 0 0 2 2h1a2 2 0 0 0 2-2v-3a2 2 0 0 0-2-2H3z',
        USER: 'M12 4a4 4 0 1 0 0 8 4 4 0 0 0 0-8z M4 21c0-4 4-6 8-6s8 2 8 6',
        MAIL: 'M4 6h16v12H4z M4 7l8 6 8-6', LOCK: 'M6 11h12v9H6z M8 11V8a4 4 0 0 1 8 0v3',
        SHIELD: 'M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10z', PLAY: 'M7 4l13 8-13 8z'
    };
    function icon(path, cls) {
        return '<svg class="pf-ic ' + (cls || '') + '" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2" ' +
            'stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="' + path + '"/></svg>';
    }
    function esc(s) {
        return String(s == null ? '' : s).replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/"/g, '&quot;').replace(/'/g, '&#39;');
    }
    function el(tag, cls, html) {
        var e = document.createElement(tag);
        if (cls) e.className = cls;
        if (html != null) e.innerHTML = html;
        return e;
    }
    function safe(fn) { try { return fn(); } catch (e) { console.warn('[app features]', e); } }
    function n(v) { return Number(v || 0).toLocaleString('en-US'); }
    function lsGet(k, d) { try { var v = localStorage.getItem(k); return v == null ? d : JSON.parse(v); } catch (e) { return d; } }
    function lsSet(k, v) { try { localStorage.setItem(k, JSON.stringify(v)); } catch (e) { /* storage full or blocked */ } }

    var toastTimer = null;
    function toast(msg) {
        var t = $('pfToast');
        if (!t) { t = el('div', 'pf-toast'); t.id = 'pfToast'; t.setAttribute('role', 'status'); document.body.appendChild(t); }
        t.textContent = msg;
        t.classList.add('on');
        clearTimeout(toastTimer);
        toastTimer = setTimeout(function () { t.classList.remove('on'); }, 2600);
    }

    /** App-style dialog. buttons: [{label, kind: 'soft'|'primary'|'danger', onClick(close) }]. content: optional element. */
    function dialog(title, message, buttons, content) {
        var o = el('div', 'pf-dialog-back');
        var d = el('div', 'pf-dialog');
        d.setAttribute('role', 'dialog');
        d.setAttribute('aria-modal', 'true');
        d.appendChild(el('h3', null)).textContent = title;
        if (message) d.appendChild(el('p', null)).textContent = message;
        if (content) d.appendChild(content);
        var row = el('div', 'pf-dialog-btns');
        function close() { o.remove(); }
        (buttons || [{ label: 'OK', kind: 'primary' }]).forEach(function (b) {
            var btn = el('button', b.kind === 'danger' ? 'pf-btn-danger' : b.kind === 'primary' ? 'pf-btn-primary' : 'pf-btn-soft');
            btn.type = 'button';
            btn.textContent = b.label;
            btn.addEventListener('click', function () {
                if (b.onClick) { var keep = b.onClick(close, btn); if (keep === true) return; }
                close();
            });
            row.appendChild(btn);
        });
        d.appendChild(row);
        o.appendChild(d);
        o.addEventListener('click', function (e) { if (e.target === o) close(); });
        document.addEventListener('keydown', function onKey(e) {
            if (e.key === 'Escape') { close(); document.removeEventListener('keydown', onKey); }
        });
        document.body.appendChild(o);
        var f = d.querySelector('input, textarea') || row.lastChild;
        if (f) setTimeout(function () { f.focus(); }, 30);
        return { close: close, box: d };
    }
    function field(placeholder, value, type) {
        var i = el('input', 'pf-field');
        i.type = type || 'text';
        i.placeholder = placeholder || '';
        if (value != null) i.value = value;
        return i;
    }

    /** A full-screen page with the app's blue header and a back button (sub-screens hide the bottom bar, like the app). */
    function subPage(id, title, opts) {
        opts = opts || {};
        var old = $(id);
        if (old) old.remove();
        var p = el('section', 'pf-subpage' + (opts.cls ? ' ' + opts.cls : ''));
        p.id = id;
        p.setAttribute('role', 'dialog');
        p.setAttribute('aria-label', title);
        var head = el('div', 'pf-header pf-sub-head');
        head.innerHTML = '<span class="pf-side"><button type="button" class="pf-hicon" aria-label="Back">' + icon(I.BACK) + '</button></span>' +
            '<h2></h2><span class="pf-side pf-right"></span>';
        head.querySelector('h2').textContent = title;
        if (opts.trailing) head.querySelector('.pf-right').appendChild(opts.trailing);
        var top = el('div', 'pf-sub-top');
        top.appendChild(head);
        p.appendChild(top);
        var body = el('div', 'pf-sub-body');
        p.appendChild(body);
        function close() {
            p.remove();
            document.body.classList.toggle('pf-sub-open', !!document.querySelector('.pf-subpage'));
            if (opts.onClose) opts.onClose();
        }
        head.querySelector('.pf-side button').addEventListener('click', function () {
            if (history.state && history.state.pfSub === id) history.back(); else close();
        });
        p._close = close;
        document.body.appendChild(p);
        document.body.classList.add('pf-sub-open');
        history.pushState({ pfSub: id }, '');
        return { page: p, head: head, top: top, body: body, close: close };
    }
    // phone back button closes the top sub-page
    // (registered in index.html before the website's own back handler, so closing a page doesn't also close the List page under it)
    (window.__pfBack || []).push(function () {
        var pages = document.querySelectorAll('.pf-subpage');
        if (!pages.length) return false;
        var last = pages[pages.length - 1];
        if (last._close) last._close();
        return true;
    });

    // ================================================================ account
    var UID = null;
    async function uid() {
        if (UID) return UID;
        try {
            var r = await sb.auth.getSession();
            var s = r && r.data && r.data.session;
            UID = s && s.user ? s.user.id : null;
        } catch (e) { UID = null; }
        return UID;
    }
    async function currentUser() {
        try { var r = await sb.auth.getSession(); return r.data.session ? r.data.session.user : null; } catch (e) { return null; }
    }

    // ================================================================ extras: presets + ratings (same as ExtrasSync.kt)
    /*
     * Every change is an operation (add preset, delete preset, rate...). It is applied on this device at once, and
     * then applied to the account's CURRENT copy (read, change, write), so a change made on the phone in the meantime
     * is never overwritten. Changes made while offline wait and are sent next time.
     */
    var Extras = {
        data: { presets: [], ratings: {}, ts: 0 },
        listeners: [],
        key: function () { return 'pf_extras_' + UID; },
        opsKey: function () { return 'pf_extras_ops_' + UID; },
        loadLocal: function () {
            var d = lsGet(this.key(), null);
            this.data = d && typeof d === 'object' ? d : { presets: [], ratings: {}, ts: 0 };
            if (!Array.isArray(this.data.presets)) this.data.presets = [];
            if (!this.data.ratings || typeof this.data.ratings !== 'object') this.data.ratings = {};
        },
        saveLocal: function () { lsSet(this.key(), this.data); this.emit(); },
        emit: function () { this.listeners.forEach(function (f) { safe(f); }); },
        apply: function (d, op) {
            var out = { presets: (d.presets || []).slice(), ratings: Object.assign({}, d.ratings || {}), ts: d.ts || 0 };
            var same = function (a, b) { return String(a || '').trim().toLowerCase() === String(b || '').trim().toLowerCase(); };
            if (op.t === 'put') {               // save: same name (any case) replaces, new one goes to the top
                out.presets = out.presets.filter(function (p) { return !same(p && p.name, op.p.name); });
                out.presets.unshift(op.p);
            } else if (op.t === 'addIfMissing') {
                if (!out.presets.some(function (p) { return same(p && p.name, op.p.name); })) out.presets.push(op.p);
            } else if (op.t === 'del') {
                out.presets = out.presets.filter(function (p) { return !(p && p.name === op.name); });
            } else if (op.t === 'rename') {
                out.presets = out.presets.map(function (p) {
                    if (!p || p.name !== op.old) return p;
                    var c = Object.assign({}, p); c.name = op.name; return c;
                });
            } else if (op.t === 'renameSource') {
                out.presets = out.presets.map(function (p) {
                    if (!p || p.source !== op.old) return p;
                    var c = Object.assign({}, p); c.source = op.name; return c;
                });
            } else if (op.t === 'rate') {
                out.ratings[op.date] = op.v;
            }
            return out;
        },
        change: function (op) {
            this.data = this.apply(this.data, op);
            this.data.ts = Date.now();
            this.saveLocal();
            var ops = lsGet(this.opsKey(), []);
            ops.push(op);
            lsSet(this.opsKey(), ops);
            return this.flush();
        },
        busy: null,
        flush: function () {
            var self = this;
            if (self.busy) return self.busy.then(function () { return self.flush(); });
            self.busy = (async function () {
                var ops = lsGet(self.opsKey(), []);
                if (!ops.length || !UID) return true;
                var res = await sb.from('user_stats').select('extras').eq('id', UID).maybeSingle();
                if (res.error) return false;                          // offline, or no extras column yet: try later
                var remote = (res.data && res.data.extras) || {};
                var base = { presets: Array.isArray(remote.presets) ? remote.presets : [], ratings: remote.ratings || {}, ts: remote.ts || 0 };
                ops.forEach(function (op) { base = self.apply(base, op); });
                base.ts = Date.now();
                var w = await sb.from('user_stats').upsert({ id: UID, extras: base }, { onConflict: 'id' });
                if (w.error) return false;
                // only clear the operations that were sent (more may have been added meanwhile)
                var now = lsGet(self.opsKey(), []);
                lsSet(self.opsKey(), now.slice(ops.length));
                if (now.length === ops.length) { self.data = base; self.saveLocal(); }
                return true;
            })().catch(function () { return false; }).then(function (ok) { self.busy = null; return ok; });
            return self.busy;
        },
        /** Fresh copy from the account (waiting changes are sent first). */
        pull: async function () {
            if (!UID) return;
            if (lsGet(this.opsKey(), []).length) { await this.flush(); }
            var res = await sb.from('user_stats').select('extras').eq('id', UID).maybeSingle();
            if (res.error) return;
            var remote = (res.data && res.data.extras) || {};
            if (lsGet(this.opsKey(), []).length) return;              // something changed meanwhile: next pull
            this.data = { presets: Array.isArray(remote.presets) ? remote.presets : [], ratings: remote.ratings || {}, ts: remote.ts || 0 };
            this.saveLocal();
        }
    };

    // ================================================================ presets (same fields as Presets.kt)
    var DEFAULT_SOURCE = 'Default questions';
    var MODE_NAMES = { 'eyes-closed': 'Eyes Closed', 'eyes-open': 'Eyes Open', 'mirror': 'Mirror', 'animated-eyes': 'Animated Eyes' };
    var MODE_DESCS = {
        'eyes-closed': 'Close your eyes and answer',
        'eyes-open': 'Read the question on screen and answer',
        'mirror': 'Practice in front of the camera and watch your facial expressions and body language.',
        'animated-eyes': 'Practice with a face that blinks and keeps eye contact with you.'
    };
    var Presets = {
        all: function () { return (Extras.data.presets || []).filter(function (p) { return p && String(p.name || '').trim(); }); },
        find: function (name) { return this.all().filter(function (p) { return p.name === name; })[0] || null; },
        exists: function (name) {
            var c = String(name).trim().toLowerCase();
            return this.all().some(function (p) { return p.name.toLowerCase() === c; });
        },
        /** The website's current Training settings in the app's preset format. */
        fromCurrent: function (name) {
            var mode = (trainingMode === 'mirror' && mirrorEyesMode) ? 'animated-eyes' : trainingMode;
            var source = (questionSource.value === 'saved' && activeListName && savedQuestionLists[activeListName]) ? activeListName : DEFAULT_SOURCE;
            return {
                name: String(name).trim(), mode: mode || 'eyes-closed', deep: !!deepPauseMode,
                total: parseInt(numQuestionsInput.value, 10) || 5,
                pause: parseInt(pauseTimeInput.value, 10) || 10,
                answer: Math.max(1, parseInt(answerTimeInput.value, 10) || 5),
                voice: !!handsFreeMode, tip: !!tipEnabled, tipInterval: tipRepeatInterval || 30,
                threeWhy: !!threeWhyEnabled, phaseGuide: !!showFsPhaseGuide, source: source,
                recordAudio: !!Rec.settings().audio, recordScreen: !!Rec.settings().video
            };
        },
        /** Puts a preset's settings on Training (the website's own apply function does the work). */
        apply: function (p) {
            var mode = p.mode || 'eyes-closed';
            var webMode = mode === 'animated-eyes' ? 'mirror' : mode;
            var camera = mode === 'mirror' || mode === 'animated-eyes';
            applySettingsProfile({
                trainingMode: webMode, pauseTime: p.pause || 10, answerTime: Math.max(1, p.answer || 5),
                totalQuestions: p.total || 5, handsFreeMode: p.voice !== false, deepPauseMode: !!p.deep,
                mirrorMode: camera, tipEnabled: p.tip !== false, tipRepeatInterval: p.tipInterval || 30,
                threeWhyEnabled: !!p.threeWhy, showFsPhaseGuide: p.phaseGuide !== false
            });
            if (!!mirrorEyesMode !== (mode === 'animated-eyes')) toggleMirrorEyes();
            Rec.set({ audio: !!p.recordAudio || !!p.recordScreen, video: !!p.recordScreen });
            var src = p.source || DEFAULT_SOURCE;
            if (src !== DEFAULT_SOURCE && savedQuestionLists[src]) {
                safe(function () { useList(src); });
                safe(function () { hideListsModal(); });
            } else {
                safe(function () { selectSource('default'); });
                if (src !== DEFAULT_SOURCE) toast('Its question list isn\'t in your lists, so it uses the default questions.');
            }
            Modes.sync();
            safe(updateTimeEstimate);
        },
        estimate: function (p) {
            var gap = p.deep ? 3 : 2;
            var rest = Math.max(0, p.total - 1) * gap;
            return p.total * ((p.deep ? 0 : p.pause) + p.answer) + rest;
        },
        summary: function (p) { return (MODE_NAMES[p.mode] || 'Eyes Closed') + '  ·  ' + p.total + 'Q'; },
        autoName: function () {
            var base = MODE_NAMES[(trainingMode === 'mirror' && mirrorEyesMode) ? 'animated-eyes' : trainingMode] || 'Eyes Closed';
            var i = 1;
            while (this.exists(base + ' ' + i)) i++;
            return base + ' ' + i;
        },
        save: function (name) { var p = this.fromCurrent(name); Recent.mark(p.name); return Extras.change({ t: 'put', p: p }); },
        remove: function (name) { return Extras.change({ t: 'del', name: name }); },
        rename: function (old, name) {
            var clean = String(name).trim();
            if (!clean) return 'Give the preset a name.';
            if (clean === old) return null;
            if (this.all().some(function (p) { return p.name !== old && p.name.toLowerCase() === clean.toLowerCase(); })) return 'You already have a preset with that name.';
            Extras.change({ t: 'rename', old: old, name: clean });
            Recent.rename(old, clean);
            return null;
        },
        duplicate: function (name) {
            var src = this.find(name);
            if (!src) return null;
            var i = 2;
            while (this.exists(name + ' ' + i)) i++;
            var copy = Object.assign({}, src, { name: name + ' ' + i });
            Extras.change({ t: 'put', p: copy });
            Recent.mark(copy.name);
            return copy.name;
        }
    };
    // most recently started presets first (kept on this device, like the app)
    var Recent = {
        key: function () { return 'pf_recent_presets_' + UID; },
        names: function () { return lsGet(this.key(), []); },
        mark: function (name) {
            var a = this.names().filter(function (x) { return x.toLowerCase() !== String(name).toLowerCase(); });
            a.unshift(name);
            lsSet(this.key(), a.slice(0, 20));
        },
        rename: function (old, name) { lsSet(this.key(), this.names().map(function (x) { return x === old ? name : x; })); },
        list: function (max) {
            var used = this.names();
            return Presets.all().slice().sort(function (a, b) {
                var ia = used.indexOf(a.name), ib = used.indexOf(b.name);
                return (ia < 0 ? 1e9 : ia) - (ib < 0 ? 1e9 : ib);
            }).slice(0, max || 3);
        }
    };
    var openPreset = null;   // the preset shown on Training right now (its name goes in the header)

    /** The website used to keep its "saved settings" only in this browser. They are added to the account once. */
    function migrateLocalProfiles() {
        var KEY = 'PAUZFIRST_settings_profiles';
        var list = lsGet(KEY, null);
        if (!Array.isArray(list) || !list.length || lsGet('pf_profiles_migrated_' + UID, false)) return;
        list.forEach(function (prof) {
            var s = prof && prof.settings || {};
            if (!prof || !String(prof.name || '').trim()) return;
            var mode = s.trainingMode === 'mirror' && s.mirrorEyesMode ? 'animated-eyes' : (s.trainingMode || 'eyes-closed');
            Extras.change({ t: 'addIfMissing', p: {
                name: String(prof.name).trim(), mode: mode, deep: !!s.deepPauseMode, total: s.totalQuestions || 5,
                pause: s.pauseTime || 10, answer: Math.max(1, s.answerTime || 5), voice: s.handsFreeMode !== false,
                tip: s.tipEnabled !== false, tipInterval: s.tipRepeatInterval || 30, threeWhy: !!s.threeWhyEnabled,
                phaseGuide: s.showFsPhaseGuide !== false, source: DEFAULT_SOURCE, recordAudio: false, recordScreen: false
            } });
        });
        lsSet('pf_profiles_migrated_' + UID, true);
    }

    // ================================================================ question bank (community_questions, like Defaults.kt)
    var Bank = {
        list: null, fallback: false,
        key: 'pf_bank_cache', tsKey: 'pf_bank_cache_ts',
        cached: function () { var a = lsGet(this.key, []); return Array.isArray(a) ? a : []; },
        due: function () { return !this.cached().length || Date.now() - (lsGet(this.tsKey, 0) || 0) > 24 * 3600 * 1000; },
        /** Reads the whole bank page by page (not cut off at 1,000), sorted like the app. */
        fetchAll: async function () {
            var out = [], page = 1000, offset = 0;
            while (offset < 100000) {
                var r = await sb.from('community_questions').select('question').order('question', { ascending: true }).range(offset, offset + page - 1);
                if (r.error || !r.data) return out.length ? out : null;
                r.data.forEach(function (row) { var q = String(row.question || '').trim(); if (q) out.push(q); });
                if (r.data.length < page) break;
                offset += r.data.length;
            }
            return out;
        },
        refresh: async function (force) {
            if (!force && !this.due()) return;
            var all = await this.fetchAll();
            if (all && all.length) {
                try { localStorage.setItem(this.key, JSON.stringify(all)); localStorage.setItem(this.tsKey, String(Date.now())); } catch (e) { /* too big for storage: keep in memory */ }
                this.list = all;
            }
        },
        /** The Default pool: the whole bank (saved copy when offline), the 100 built-in questions only as a last resort. */
        pool: async function () {
            if (this.due()) await this.refresh(true).catch(function () {});
            var a = this.list && this.list.length ? this.list : this.cached();
            if (a.length) { this.fallback = false; return uniq(a); }
            this.fallback = true;
            return uniq(defaultQuestions.slice());
        }
    };
    function uniq(a) { var s = new Set(), out = []; a.forEach(function (q) { if (!s.has(q)) { s.add(q); out.push(q); } }); return out; }

    // The website's Default mode now draws from the whole bank, like the app
    window.loadDefaultPool = function () { return Bank.pool(); };
    // Start the list over only when EVERY question of the pool has been used (the old check compared counts,
    // which wiped the app's progress when the website only knew 500 questions)
    window.resetUsedQuestionsIfNeeded = function (pool, type) {
        var used = new Set(usedQuestions[type] || []);
        if (type === 'default' && Bank.fallback) return;          // built-in fallback list: never clear the account's progress
        if (pool.length && pool.every(function (q) { return used.has(q); })) {
            usedQuestions[type] = [];
            saveUsedQuestions();
        }
    };
    // While only the built-in fallback list is available (offline, first visit), progress is kept on this device only,
    // so a short offline pool can never overwrite the account's list.
    var origSaveUsed = window.saveUsedQuestions;
    window.saveUsedQuestions = async function () {
        if (Bank.fallback) { try { localStorage.setItem('PAUZFIRST_used_questions', JSON.stringify(usedQuestions)); } catch (e) {} return; }
        return origSaveUsed.apply(this, arguments);
    };

    window.PF = { Extras: Extras, Presets: Presets, Bank: Bank, dialog: dialog, toast: toast };
    // ================================================================ training modes: 4 chips like the app
    var Modes = {
        init: function () {
            var grid = document.querySelector('.training-mode-grid');
            if (!grid || $('pfModeAnimated')) return;
            var card = el('div', 'training-mode-card', '<div class="training-mode-name">Animated Eyes</div>');
            card.id = 'pfModeAnimated';
            card.setAttribute('role', 'button');
            card.tabIndex = 0;
            grid.appendChild(card);
            grid.classList.add('pf-mode-grid4');
            var pick = function (mode) {
                var camera = mode === 'mirror' || mode === 'animated-eyes';
                if (mode === 'animated-eyes') selectTrainingMode('mirror');
                if (!!mirrorMode !== camera) toggleMirrorMode();          // Mirror and Animated Eyes use the camera, like the app
                if (!!mirrorEyesMode !== (mode === 'animated-eyes')) toggleMirrorEyes();
                Modes.sync();
                safe(updateTimeEstimate);
            };
            card.addEventListener('click', function () { pick('animated-eyes'); });
            card.addEventListener('keydown', function (e) { if (e.key === 'Enter' || e.key === ' ') { e.preventDefault(); pick('animated-eyes'); } });
            [['modeEyesClosed', 'eyes-closed'], ['modeEyesOpen', 'eyes-open'], ['modeMirror', 'mirror']].forEach(function (m) {
                var c = $(m[0]);
                if (c) c.addEventListener('click', function () { setTimeout(function () { pick(m[1]); }, 0); });
            });
            Modes.sync();
        },
        current: function () { return (trainingMode === 'mirror' && mirrorEyesMode) ? 'animated-eyes' : trainingMode; },
        sync: function () {
            var m = Modes.current();
            var map = { modeEyesClosed: 'eyes-closed', modeEyesOpen: 'eyes-open', modeMirror: 'mirror', pfModeAnimated: 'animated-eyes' };
            Object.keys(map).forEach(function (id) { var c = $(id); if (c) c.classList.toggle('selected', map[id] === m); });
            var d = $('trainingModeDesc');
            if (d) d.textContent = MODE_DESCS[m] || '';
            document.body.classList.toggle('pf-camera-mode', m === 'mirror' || m === 'animated-eyes');
        }
    };

    // ================================================================ cues and sounds (Voice, Vibrate, Chime, Phases)
    var Cues = {
        get: function () { return lsGet('pf_cues', { vibrate: true, chime: true }); },
        set: function (c) { lsSet('pf_cues', c); },
        ctx: null,
        /** Soft chime: 1 note for Pause, 2 for Respond (same pattern as the app). */
        chime: function (pulses) {
            try {
                var AC = window.AudioContext || window.webkitAudioContext;
                if (!AC) return;
                this.ctx = this.ctx || new AC();
                var ctx = this.ctx;
                for (var i = 0; i < pulses; i++) {
                    var t = ctx.currentTime + i * 0.3;
                    var o = ctx.createOscillator(), g = ctx.createGain();
                    o.type = 'sine';
                    o.frequency.value = i === 0 ? 880 : 1175;
                    g.gain.setValueAtTime(0.0001, t);
                    g.gain.exponentialRampToValueAtTime(0.25, t + 0.02);
                    g.gain.exponentialRampToValueAtTime(0.0001, t + 0.45);
                    o.connect(g); g.connect(ctx.destination);
                    o.start(t); o.stop(t + 0.5);
                }
            } catch (e) { /* no audio */ }
        },
        buzz: function (pulses) {
            if (navigator.vibrate) safe(function () { navigator.vibrate(pulses <= 1 ? [60] : [60, 240, 60]); });
        },
        phase: function (label) {
            var c = Cues.get();
            var p = /pause/i.test(label) ? 1 : /respond|answer/i.test(label) ? 2 : 0;
            if (!p) return;
            if (c.chime) Cues.chime(p);
            if (c.vibrate) Cues.buzz(p);
        },
        init: function () {
            var lbl = $('timerLabel');
            if (lbl) {
                var last = '';
                new MutationObserver(function () {
                    var t = lbl.textContent.trim();
                    if (t !== last && app.classList.contains('training-active')) { last = t; Cues.phase(t); }
                    if (!app.classList.contains('training-active')) last = '';
                }).observe(lbl, { childList: true, characterData: true, subtree: true });
            }
            // the app's "Cues and sounds" card: one round button per cue
            var tts = $('handsFreeToggle');
            var group = tts && tts.closest('.config-group');
            if (!group || $('pfCues')) return;
            var card = el('div', 'config-group pf-cues');
            card.id = 'pfCues';
            card.innerHTML = '<div class="pf-cues-head"><span>Cues and sounds</span></div><div class="pf-cue-row"></div>';
            var row = card.querySelector('.pf-cue-row');
            function cue(id, ic, name, get, set, onlyMirror) {
                var b = el('button', 'pf-cue', '<span class="pf-cue-dot">' + icon(ic) + '</span><span class="pf-cue-name">' + name + '</span>');
                b.type = 'button';
                b.id = id;
                if (onlyMirror) b.classList.add('pf-cue-mirror');
                function paint() { var on = get(); b.classList.toggle('on', on); b.setAttribute('aria-pressed', on ? 'true' : 'false'); }
                b.addEventListener('click', function () { set(!get()); paint(); });
                b._paint = paint;
                paint();
                row.appendChild(b);
            }
            cue('pfCueVoice', I.VOLUME, 'Voice', function () { return !!handsFreeMode; }, function () { tts.click(); });
            cue('pfCueVibrate', I.VIBRATE, 'Vibrate', function () { return Cues.get().vibrate; }, function (v) { var c = Cues.get(); c.vibrate = v; Cues.set(c); if (v) Cues.buzz(1); });
            cue('pfCueChime', I.BELL, 'Chime', function () { return Cues.get().chime; }, function (v) { var c = Cues.get(); c.chime = v; Cues.set(c); if (v) Cues.chime(1); });
            cue('pfCuePhases', I.EYE, 'Phases', function () { return !!showFsPhaseGuide; }, function () { toggleFsPhaseGuide(); }, true);
            group.parentNode.insertBefore(card, group);
            group.classList.add('pf-tts-group');
            // keep the buttons right when a preset changes the settings
            Cues.repaint = function () { row.querySelectorAll('.pf-cue').forEach(function (b) { b._paint && b._paint(); }); };
            [tts, $('fsPhaseGuideToggle')].forEach(function (t) { if (t) new MutationObserver(Cues.repaint).observe(t, { attributes: true, attributeFilter: ['class'] }); });
        }
    };

    // ================================================================ recording (audio, or camera video with sound)
    var Rec = {
        settings: function () { return lsGet('pf_rec', { audio: false, video: false }); },
        set: function (s) { lsSet('pf_rec', { audio: !!s.audio, video: !!s.video }); Rec.paint && Rec.paint(); },
        rec: null, chunks: [], stream: null, last: null,
        start: async function () {
            var s = Rec.settings();
            if (!s.audio && !s.video) return;
            if (!window.MediaRecorder || !navigator.mediaDevices) { toast('This browser can\'t record.'); return; }
            try {
                Rec.stream = await navigator.mediaDevices.getUserMedia(s.video ? { audio: true, video: { facingMode: 'user' } } : { audio: true });
                Rec.chunks = [];
                Rec.rec = new MediaRecorder(Rec.stream);
                Rec.rec.ondataavailable = function (e) { if (e.data && e.data.size) Rec.chunks.push(e.data); };
                Rec.rec.onstop = function () {
                    var type = (Rec.rec && Rec.rec.mimeType) || (s.video ? 'video/webm' : 'audio/webm');
                    if (Rec.last && Rec.last.url) URL.revokeObjectURL(Rec.last.url);
                    var blob = new Blob(Rec.chunks, { type: type });
                    Rec.last = blob.size ? { url: URL.createObjectURL(blob), video: !!s.video, type: type, at: Date.now() } : null;
                    if (Rec.stream) Rec.stream.getTracks().forEach(function (t) { t.stop(); });
                    Rec.stream = null;
                    Rec.rec = null;
                    Summary.attachRecording && Summary.attachRecording();
                };
                Rec.rec.start(1000);
            } catch (e) { toast('Recording is off: allow the microphone' + (s.video ? ' and camera' : '') + ' to record.'); }
        },
        stop: function () { if (Rec.rec && Rec.rec.state !== 'inactive') safe(function () { Rec.rec.stop(); }); },
        init: function () {
            var anchor = $('pfCues');
            if (!anchor || $('pfRecCard')) return;
            var label = el('div', 'pf-group-label', 'Recording');
            var card = el('div', 'config-group pf-rec');
            card.id = 'pfRecCard';
            card.innerHTML = '<div class="tts-option"><span class="pf-sw-col"><span class="tts-label">Record training</span><small>Save your session as audio or video</small></span>' +
                '<div class="toggle" id="pfRecToggle" role="switch" tabindex="0"><div class="toggle-slider"></div></div></div>' +
                '<div class="pf-rec-chips"><button type="button" id="pfRecAudio">Record audio</button><button type="button" id="pfRecVideo">Record video</button></div>';
            anchor.parentNode.insertBefore(label, anchor.nextSibling);
            label.parentNode.insertBefore(card, label.nextSibling);
            Rec.paint = function () {
                var s = Rec.settings(), on = s.audio || s.video;
                $('pfRecToggle').classList.toggle('active', on);
                $('pfRecToggle').setAttribute('aria-checked', on ? 'true' : 'false');
                card.querySelector('.pf-rec-chips').style.display = on ? '' : 'none';
                $('pfRecAudio').classList.toggle('on', on && !s.video);
                $('pfRecVideo').classList.toggle('on', !!s.video);
            };
            var flip = function () { var s = Rec.settings(); Rec.set((s.audio || s.video) ? { audio: false, video: false } : { audio: true, video: false }); };
            $('pfRecToggle').addEventListener('click', flip);
            $('pfRecToggle').addEventListener('keydown', function (e) { if (e.key === 'Enter' || e.key === ' ') { e.preventDefault(); flip(); } });
            $('pfRecAudio').addEventListener('click', function () { Rec.set({ audio: true, video: false }); });
            $('pfRecVideo').addEventListener('click', function () { Rec.set({ audio: true, video: true }); });
            Rec.paint();
            // start with the training, stop when it ends
            new MutationObserver(function () {
                if (app.classList.contains('training-active')) { if (!Rec.rec) Rec.start(); }
            }).observe(app, { attributes: true, attributeFilter: ['class'] });
        }
    };

    // ================================================================ dark mode (the app's moon / sun button)
    var Theme = {
        get: function () { var v = lsGet('pf_theme', null); return v === 'dark' || v === 'light' ? v : null; },
        isDark: function () { var v = Theme.get(); return v ? v === 'dark' : !!(window.matchMedia && matchMedia('(prefers-color-scheme: dark)').matches); },
        apply: function () {
            var d = Theme.isDark();
            document.body.classList.toggle('pf-dark', d);
            var m = document.querySelector('meta[name="theme-color"]');
            if (m) m.setAttribute('content', d ? '#0B1220' : '#1E90FF');
            var b = $('pfThemeBtn');
            if (b) { b.innerHTML = icon(d ? I.SUN : I.MOON); b.setAttribute('aria-label', d ? 'Switch to light mode' : 'Switch to dark mode'); }
        },
        toggle: function () { lsSet('pf_theme', Theme.isDark() ? 'light' : 'dark'); Theme.apply(); },
        follow: function () { try { localStorage.removeItem('pf_theme'); } catch (e) {} Theme.apply(); toast('Dark mode now follows your device'); }
    };

    // ================================================================ Home: header buttons, Recent presets
    var app = $('appContainer');
    function initHome() {
        var top = document.querySelector('#pfHome .pf-hero-top');
        var logout = $('pfLogoutBtn');
        if (top && logout && !$('pfThemeBtn')) {
            var mk = function (id, path, label, fn) {
                var b = el('button', 'pf-hicon', icon(path)); b.type = 'button'; b.id = id; b.setAttribute('aria-label', label);
                b.addEventListener('click', fn); top.insertBefore(b, logout); return b;
            };
            var th = mk('pfThemeBtn', I.MOON, 'Switch to dark mode', Theme.toggle);
            var hold = null;
            th.addEventListener('pointerdown', function () { hold = setTimeout(function () { hold = 'done'; Theme.follow(); }, 650); });
            th.addEventListener('pointerup', function () { if (hold && hold !== 'done') clearTimeout(hold); });
            th.addEventListener('click', function (e) { if (hold === 'done') { e.stopImmediatePropagation(); hold = null; } }, true);
            mk('pfSyncBtn', I.FLIP, 'Sync: export and import', Sync.open);
            // tapping the logo opens your profile, like the app
            var brand = document.querySelector('#pfHome .pf-brand');
            if (brand) {
                brand.setAttribute('role', 'button'); brand.tabIndex = 0; brand.setAttribute('aria-label', 'Your account');
                brand.addEventListener('click', Profile.open);
                brand.addEventListener('keydown', function (e) { if (e.key === 'Enter') Profile.open(); });
            }
        }
        Theme.apply();
        // the Presets pill opens the Preset list
        rebind('pfPresetsPill', function () { ListPage.open(0); });
        // the save icon on Training: update the open preset or save a new one
        rebind('pfSaveBtn', chooseSave);
        // the Presets button on Training: pick one from a sheet
        rebind('settingsListBtn', presetPicker);
    }
    /** Replaces a button's click handlers (the old ones opened the website's browser-only settings list). */
    function rebind(id, fn) {
        var b = $(id);
        if (!b || b._pfBound) return;
        var c = b.cloneNode(true);
        c._pfBound = true;
        b.parentNode.replaceChild(c, b);
        c.addEventListener('click', function (e) { e.preventDefault(); fn(); });
    }

    function renderRecent(box) {
        box = box || $('pfRecent');
        if (!box) return;
        box.innerHTML = '';
        var list = Recent.list(3);
        if (!list.length) {
            box.appendChild(el('p', 'pf-note', 'No presets yet. Set things up on Training, then tap the save icon.'));
            return;
        }
        list.forEach(function (p) {
            var c = el('button', 'pf-card pf-preset');
            c.type = 'button';
            c.innerHTML = '<span class="pf-preset-top"><b></b><i>›</i></span>' + presetInfo(p);
            c.querySelector('b').textContent = p.name;
            c.addEventListener('click', function () { startPreset(p); });
            box.appendChild(c);
        });
    }
    function presetInfo(p) {
        var rec = (p.recordScreen || p.recordAudio) ? '<span class="pf-dot">·</span>' + icon(p.recordScreen ? I.VIDEO : I.HEADPHONES, 'pf-ic-sm') : '';
        return '<span class="pf-preset-meta">' + esc(Presets.summary(p)) + rec + '<span class="pf-dot">·</span>' + icon(I.CLOCK, 'pf-ic-sm') +
            '<span>' + esc(fmtDur(Presets.estimate(p))) + '</span></span>';
    }
    function fmtDur(s) { s = Math.max(0, Math.round(s)); var m = Math.floor(s / 60), r = s % 60; return m ? (m + 'm' + (r ? ' ' + r + 's' : '')) : r + 's'; }

    /** Home's Recent: in the app, tapping one starts that training straight away. */
    function startPreset(p) {
        Presets.apply(p);
        openPreset = p.name;
        Recent.mark(p.name);
        history.pushState({ pf: 'training' }, '');
        pfGo('training');
        setTrainTitle(p.name);
        setTimeout(function () { var s = $('startBtn'); if (s && !s.disabled) s.click(); }, 150);
    }
    /** Opens a preset on Training to check it first (Preset list, Training's Presets button). */
    function showPreset(p) {
        Presets.apply(p);
        openPreset = p.name;
        if (!document.body.classList.contains('pf-page-training')) { history.pushState({ pf: 'training' }, ''); }
        safe(function () { hideListsModal(); });
        pfGo('training');
        setTrainTitle(p.name);
    }
    function setTrainTitle(t) { var h = document.querySelector('#pfTrainHeader h2'); if (h) h.textContent = t || 'Training'; }

    function chooseSave() {
        if (openPreset && Presets.find(openPreset)) {
            var name = openPreset;
            dialog('Save', 'Update "' + name + '" with the settings on screen, or save them as a new preset.', [
                { label: 'Save as new', kind: 'soft', onClick: function () { setTimeout(askName, 0); } },
                { label: 'Update', kind: 'primary', onClick: function () { Presets.save(name); toast('Updated "' + name + '"'); } }
            ]);
        } else askName();
        function askName() {
            var f = field('Preset name', Presets.autoName());
            f.maxLength = 60;
            var d = dialog('Save as preset', 'It saves the mode, timing, cues and question list on screen.', [
                { label: 'Cancel', kind: 'soft' },
                { label: 'Save', kind: 'primary', onClick: function () {
                    var v = f.value.trim();
                    if (!v) { toast('Give the preset a name.'); return true; }
                    var existed = Presets.exists(v);
                    Presets.save(v);
                    openPreset = v;
                    setTrainTitle(v);
                    toast((existed ? 'Updated "' : 'Saved "') + v + '"');
                } }
            ], f);
            f.addEventListener('keydown', function (e) { if (e.key === 'Enter') d.box.querySelector('.pf-btn-primary').click(); });
            setTimeout(function () { f.select(); }, 40);
        }
    }

    function presetPicker() {
        var list = Presets.all();
        var box = el('div', 'pf-pick-list');
        if (!list.length) box.appendChild(el('p', 'pf-note', 'No presets yet. Set things up, then tap the save icon.'));
        var d;
        list.forEach(function (p) {
            var b = el('button', 'pf-pick', '<b></b>' + presetInfo(p));
            b.type = 'button';
            b.querySelector('b').textContent = p.name;
            if (p.name === openPreset) b.classList.add('on');
            b.addEventListener('click', function () { d.close(); showPreset(p); });
            box.appendChild(b);
        });
        d = dialog('Presets', null, [{ label: 'Close', kind: 'soft' }], box);
        d.box.classList.add('pf-sheet');
    }

    // leaving Training without starting closes the open preset (its name leaves the header), like the app
    document.addEventListener('click', function (e) {
        var t = e.target.closest && e.target.closest('#pfNav .pf-tab');
        if (t && t.getAttribute('data-page') !== 'training') { openPreset = null; setTrainTitle('Training'); }
    }, true);
    // starting a training from an open preset puts it at the top of Recent
    document.addEventListener('click', function (e) {
        if (e.target.closest && e.target.closest('#startBtn') && openPreset) Recent.mark(openPreset);
    }, true);
    // ================================================================ List page: Preset list | Question list (ListsActivity)
    var ListPage = {
        tab: 1, selectMode: false, selected: new Set(), built: false,
        build: function () {
            var modal = $('listsFullscreenModal');
            var content = modal && modal.querySelector('.lists-fullscreen-content');
            var head = content && content.querySelector('.pf-header');
            if (!content || !head || ListPage.built) return;
            ListPage.built = true;
            // blue top: title row + search box inside the blue, like the app
            var top = el('div', 'pf-blue-top');
            head.parentNode.insertBefore(top, head);
            top.appendChild(head);
            head.classList.add('pf-flat');
            var search = el('label', 'pf-search', icon(I.SEARCH) + '<input type="search" id="pfListSearch" placeholder="Search question list" autocomplete="off">');
            top.appendChild(search);
            var tabs = el('div', 'pf-toggle', '<button type="button" data-tab="0">Preset list</button><button type="button" data-tab="1">Question list</button>');
            tabs.id = 'pfListTabs';
            top.parentNode.insertBefore(tabs, top.nextSibling);
            tabs.addEventListener('click', function (e) { var b = e.target.closest('button'); if (b) ListPage.show(+b.getAttribute('data-tab')); });

            // Question list: AI card + Default questions card + select row, then the website's own list controls
            var qbox = el('div', 'pf-qtab');
            qbox.id = 'pfQuestionTab';
            var kids = Array.prototype.slice.call(content.children).filter(function (c) { return c !== top && c !== tabs; });
            tabs.parentNode.insertBefore(qbox, tabs.nextSibling);
            var cards = el('div', 'pf-list-cards');
            cards.innerHTML =
                '<button type="button" class="pf-card pf-link-card" id="pfAiCard"><span><b>Generate with AI</b><small>Create a fresh list of questions</small></span><i>›</i></button>' +
                '<button type="button" class="pf-card pf-link-card" id="pfBankCard"><span><b>Default questions</b><small id="pfBankCount">Ready-made questions from us</small></span><i>›</i></button>' +
                '<div class="pf-select-row" id="pfSelectRow"><span class="pf-st" id="pfSelLabel"></span>' +
                '<button type="button" class="pf-chip-sm" id="pfSelAll" hidden>All</button><button type="button" class="pf-chip-sm pf-grey" id="pfSelCancel" hidden>Cancel</button>' +
                '<button type="button" class="pf-chip-sm" id="pfSelMain">Select</button></div>';
            qbox.appendChild(cards);
            kids.forEach(function (k) { qbox.appendChild(k); });
            $('pfAiCard').addEventListener('click', AiPage.open);
            $('pfBankCard').addEventListener('click', BankPage.open);
            Bank.pool().then(function (pool) { var c = $('pfBankCount'); if (c) c.textContent = n(pool.length) + ' ready-made questions from us'; });
            $('pfSelMain').addEventListener('click', function () { ListPage.selectMode ? ListPage.deleteSelected() : ListPage.setSelect(true); });
            $('pfSelCancel').addEventListener('click', function () { ListPage.setSelect(false); });
            $('pfSelAll').addEventListener('click', function () {
                var names = ListPage.names();
                if (ListPage.selected.size === names.length) ListPage.selected.clear(); else names.forEach(function (x) { ListPage.selected.add(x); });
                ListPage.paintSelect();
            });

            // Preset list
            var pbox = el('div', 'pf-ptab');
            pbox.id = 'pfPresetTab';
            qbox.parentNode.insertBefore(pbox, qbox.nextSibling);

            // the website's search box drives the question cards; ours fills it in
            var webSearch = $('listSearchInput');
            $('pfListSearch').addEventListener('input', function () {
                if (ListPage.tab === 0) { ListPage.renderPresets(); return; }
                if (webSearch) { webSearch.value = this.value; displaySavedLists(); }
            });
            // header buttons: + is a new preset on the Preset list and a new list on the Question list
            rebind('pfNewListBtn', function () {
                if (ListPage.tab === 0) { safe(function () { hideListsModal(); }); openPreset = null; setTrainTitle('Training'); history.pushState({ pf: 'training' }, ''); pfGo('training'); toast('Set things up, then tap the save icon.'); }
                else { var f = $('createListFormModal'); if (f && f.style.display === 'none') toggleCreateListModal(); setTimeout(function () { var i = $('newListNameModal'); if (i) { i.focus(); i.scrollIntoView({ block: 'center' }); } }, 50); }
            });
            rebind('pfImportBtn', importDialog);

            // selecting lists to delete: tapping a card ticks it instead of opening it
            var disp = $('listsFullscreenDisplay');
            disp.addEventListener('click', function (e) {
                if (!ListPage.selectMode) return;
                var card = e.target.closest('.list-card');
                if (!card) return;
                e.stopPropagation(); e.preventDefault();
                var name = decodeURIComponent(card.dataset.list);
                if (ListPage.selected.has(name)) ListPage.selected.delete(name); else ListPage.selected.add(name);
                ListPage.paintSelect();
            }, true);
            new MutationObserver(function () { ListPage.paintSelect(); ListPage.tagCards(); }).observe(disp, { childList: true });
            ListPage.show(1);
        },
        names: function () { return Object.keys(savedQuestionLists || {}).filter(function (k) { return k !== '__ai_custom_session__'; }); },
        show: function (i) {
            ListPage.tab = i;
            if (ListPage.selectMode) ListPage.setSelect(false);
            var tabs = $('pfListTabs');
            if (tabs) tabs.querySelectorAll('button').forEach(function (b) { var on = +b.getAttribute('data-tab') === i; b.classList.toggle('on', on); b.setAttribute('aria-selected', on ? 'true' : 'false'); });
            $('pfQuestionTab').hidden = i !== 1;
            $('pfPresetTab').hidden = i !== 0;
            var t = document.querySelector('#listsFullscreenModal .lists-fullscreen-title');
            if (t) t.textContent = i === 0 ? 'My presets' : 'My questions';
            var s = $('pfListSearch');
            if (s) { if (s.value) { s.value = ''; var w = $('listSearchInput'); if (w) { w.value = ''; displaySavedLists(); } } s.placeholder = i === 0 ? 'Search preset list' : 'Search question list'; }
            if (i === 0) ListPage.renderPresets();
            ListPage.paintSelect();
        },
        want: null,   // the tab to show the next time the page opens (Presets buttons ask for the Preset list)
        open: function (tab) {
            ListPage.build();
            ListPage.want = tab == null ? 1 : tab;
            pfGo('list');
            ListPage.show(ListPage.want);
        },
        setSelect: function (on) {
            ListPage.selectMode = on;
            ListPage.selected.clear();
            document.body.classList.toggle('pf-selecting', on);
            ListPage.paintSelect();
        },
        paintSelect: function () {
            var names = ListPage.names();
            var lbl = $('pfSelLabel');
            if (!lbl) return;
            $('pfSelectRow').hidden = !names.length && !ListPage.selectMode;
            var main = $('pfSelMain');
            if (ListPage.selectMode) {
                lbl.textContent = ListPage.selected.size ? ListPage.selected.size + ' selected' : 'Tap list';
                $('pfSelAll').hidden = false; $('pfSelAll').textContent = ListPage.selected.size === names.length && names.length ? 'None' : 'All';
                $('pfSelCancel').hidden = false;
                main.textContent = 'Delete'; main.classList.add('pf-red'); main.disabled = !ListPage.selected.size;
            } else {
                lbl.textContent = names.length + ' question list' + (names.length === 1 ? '' : 's');
                $('pfSelAll').hidden = true; $('pfSelCancel').hidden = true;
                main.textContent = 'Select'; main.classList.remove('pf-red'); main.disabled = false;
            }
            document.querySelectorAll('#listsFullscreenDisplay .list-card').forEach(function (c) {
                var nm = decodeURIComponent(c.dataset.list || '');
                c.classList.toggle('pf-ticked', ListPage.selectMode && ListPage.selected.has(nm));
            });
        },
        /** "In use" on the list Training is using right now, like the app. */
        tagCards: function () {
            document.querySelectorAll('#listsFullscreenDisplay .list-card').forEach(function (c) {
                var nm = decodeURIComponent(c.dataset.list || '');
                var head = c.querySelector('.list-name');
                if (!head || head.querySelector('.pf-inuse')) return;
                if (questionSource.value === 'saved' && activeListName === nm) head.insertAdjacentHTML('beforeend', '<span class="pf-inuse">In use</span>');
            });
        },
        deleteSelected: function () {
            var names = Array.from(ListPage.selected);
            if (!names.length) return;
            dialog('Delete ' + names.length + ' list' + (names.length === 1 ? '' : 's') + '?', 'This removes them from your account on every device.', [
                { label: 'Cancel', kind: 'soft' },
                { label: 'Delete', kind: 'danger', onClick: async function () {
                    for (var i = 0; i < names.length; i++) {
                        delete savedQuestionLists[names[i]];
                        delete listBookmarks[names[i]];
                        await deleteListFromDB(names[i]);
                    }
                    ListPage.setSelect(false);
                    displaySavedLists();
                    toast('Deleted ' + names.length + ' list' + (names.length === 1 ? '' : 's'));
                } }
            ]);
        },
        renderPresets: function () {
            var box = $('pfPresetTab');
            if (!box) return;
            box.innerHTML = '';
            var all = Presets.all();
            if (!all.length) { box.appendChild(el('p', 'pf-note', 'No presets yet. Set things up on the Training screen, then tap the save icon.')); return; }
            var q = ($('pfListSearch') && ListPage.tab === 0 ? $('pfListSearch').value.trim().toLowerCase() : '');
            var found = 0;
            all.forEach(function (p) {
                if (q && p.name.toLowerCase().indexOf(q) < 0) return;
                found++;
                var c = el('div', 'pf-card pf-preset-card');
                c.innerHTML = '<div class="pf-pc-top"><button type="button" class="pf-pc-name"></button>' +
                    '<button type="button" class="pf-round" data-a="share" aria-label="Share preset">' + icon(I.SHARE) + '</button>' +
                    '<button type="button" class="pf-round" data-a="rename" aria-label="Rename preset">' + icon(I.EDIT) + '</button>' +
                    '<button type="button" class="pf-round" data-a="copy" aria-label="Duplicate preset">' + icon(I.COPY) + '</button>' +
                    '<button type="button" class="pf-round" data-a="delete" aria-label="Delete preset">' + icon(I.TRASH) + '</button></div>' +
                    '<button type="button" class="pf-pc-info">' + presetInfo(p) + '</button>';
                c.querySelector('.pf-pc-name').textContent = p.name;
                c.addEventListener('click', function (e) {
                    var b = e.target.closest('button');
                    if (!b) return;
                    var a = b.getAttribute('data-a');
                    if (!a) return showPreset(p);
                    if (a === 'share') sharePreset(p);
                    else if (a === 'rename') renamePreset(p);
                    else if (a === 'copy') { var nm = Presets.duplicate(p.name); if (nm) toast('Duplicated as "' + nm + '"'); }
                    else if (a === 'delete') dialog('Delete "' + p.name + '"?', 'This removes the saved setup from your account.', [
                        { label: 'Cancel', kind: 'soft' },
                        { label: 'Delete', kind: 'danger', onClick: function () { Presets.remove(p.name); if (openPreset === p.name) { openPreset = null; setTrainTitle('Training'); } } }
                    ]);
                });
                box.appendChild(c);
            });
            if (!found) box.appendChild(el('p', 'pf-note', 'No presets match "' + ($('pfListSearch').value.trim()) + '".'));
        }
    };
    function renamePreset(p) {
        var f = field('Preset name', p.name);
        dialog('Rename preset', null, [
            { label: 'Cancel', kind: 'soft' },
            { label: 'Save', kind: 'primary', onClick: function () {
                var err = Presets.rename(p.name, f.value);
                if (err) { toast(err); return true; }
                if (openPreset === p.name) { openPreset = f.value.trim(); setTrainTitle(openPreset); }
            } }
        ], f);
    }
    function codeBox(title, sub, code) {
        var box = el('div', 'pf-code-box');
        box.innerHTML = '<div class="pf-code"></div><small>Anyone can import it in the app or on the website.</small>';
        box.querySelector('.pf-code').textContent = code;
        dialog(title, sub, [
            { label: 'Done', kind: 'soft' },
            { label: 'Copy code', kind: 'primary', onClick: function () {
                (navigator.clipboard ? navigator.clipboard.writeText(code) : Promise.reject()).then(function () { toast('Code copied'); }, function () { toast('Code: ' + code); });
            } }
        ], box);
    }
    /** Same as the app: the database gives the code; the preset's question list goes along with it. */
    async function sharePreset(p) {
        var listName = null, qs = null;
        if (p.source && p.source !== DEFAULT_SOURCE && savedQuestionLists[p.source]) {
            var lines = savedQuestionLists[p.source].map(qText).filter(Boolean);
            if (lines.length) { listName = p.source; qs = lines; }
        }
        var settings = { mode: p.mode, deep: p.deep, total: p.total, pause: p.pause, answer: p.answer, voice: p.voice, tip: p.tip,
            tipInterval: p.tipInterval, threeWhy: p.threeWhy, phaseGuide: p.phaseGuide, recordAudio: !!p.recordAudio, recordScreen: !!p.recordScreen };
        toast('Creating share code…');
        var r = await sb.rpc('share_preset', { p_name: p.name, p_settings: settings, p_list_name: listName, p_questions: qs });
        var code = r.error ? null : String(r.data || '').replace(/"/g, '').trim();
        if (!code || code.length !== 6) { toast('Couldn\'t create a share code. ' + (r.error && /fetch|network/i.test(r.error.message) ? 'No internet connection.' : 'Try again.')); return; }
        codeBox('Share "' + p.name + '"', null, code);
    }
    function qText(q) { return typeof q === 'string' ? q.trim() : q && typeof q === 'object' ? String(q.question || q.text || '').trim() : ''; }

    /** Import by code: a question list code, or a preset code (tried in that order, like the app). */
    function importDialog() {
        var f = field('Share code, e.g. AB3K9Z');
        f.autocapitalize = 'characters';
        f.style.textTransform = 'uppercase';
        var msg = el('div', 'pf-dialog-msg');
        var wrap = el('div'); wrap.appendChild(f); wrap.appendChild(msg);
        var d = dialog('Import by code', 'Enter the share code you were sent. It can be a question list or a preset.', [
            { label: 'Cancel', kind: 'soft' },
            { label: 'Import', kind: 'primary', onClick: function (close, btn) { doImport(f.value, msg, btn).then(function (ok) { if (ok) setTimeout(close, 900); }); return true; } }
        ], wrap);
        f.addEventListener('keydown', function (e) { if (e.key === 'Enter') d.box.querySelector('.pf-btn-primary').click(); });
    }
    async function doImport(raw, msg, btn) {
        var code = String(raw || '').replace(/\s+/g, '').toUpperCase();
        if (!code) { msg.textContent = 'Enter the share code.'; return false; }
        btn.disabled = true; msg.className = 'pf-dialog-msg'; msg.textContent = 'Looking up code…';
        try {
            var r = await sb.rpc('get_shared_list', { p_code: code });
            if (!r.error && r.data && r.data.length) {
                // the website's own import does the naming and saving
                var input = $('importCodeInput'), webMsg = $('importCodeMsg');
                input.value = code;
                await importListByCode();
                var text = webMsg ? webMsg.textContent : '';
                var ok = /imported/i.test(text);
                msg.className = 'pf-dialog-msg ' + (ok ? 'pf-ok' : 'pf-err');
                msg.textContent = text || 'Imported.';
                $('importListForm').style.display = 'none';
                if (ok) linkImported(code);
                ListPage.show(1);
                return ok;
            }
            var pr = await sb.rpc('get_shared_preset', { p_code: code });
            if (pr.error) { msg.className = 'pf-dialog-msg pf-err'; msg.textContent = 'Couldn\'t reach the server. Try again.'; return false; }
            var sp = pr.data && pr.data[0];
            if (!sp) { msg.className = 'pf-dialog-msg pf-err'; msg.textContent = 'Code not found. Check the code and try again.'; return false; }
            var s = sp.settings || {};
            var source = DEFAULT_SOURCE, note = '';
            var wanted = sp.list_name ? String(sp.list_name) : '';
            if (wanted) {
                var have = Object.keys(savedQuestionLists).filter(function (k) { return k.toLowerCase() === wanted.toLowerCase(); })[0];
                var qs = Array.isArray(sp.questions) ? sp.questions.map(qText).filter(Boolean) : [];
                if (have) source = have;
                else if (qs.length) { savedQuestionLists[wanted] = qs; await saveList(wanted, qs); displaySavedLists(); source = wanted; }
                else note = ' Its question list wasn\'t shared, so it uses the default questions.';
            }
            var base = String(sp.preset_name || 'Shared preset').trim() || 'Shared preset', name = base, i = 2;
            while (Presets.exists(name)) { name = base + ' (' + i + ')'; i++; }
            Extras.change({ t: 'put', p: {
                name: name, mode: s.mode || 'eyes-closed', deep: !!s.deep, total: s.total || 5, pause: s.pause || 10, answer: Math.max(1, s.answer || 5),
                voice: s.voice !== false, tip: s.tip !== false, tipInterval: s.tipInterval || 30, threeWhy: !!s.threeWhy,
                phaseGuide: s.phaseGuide !== false, source: source, recordAudio: !!s.recordAudio, recordScreen: !!s.recordScreen } });
            Recent.mark(name);
            msg.className = 'pf-dialog-msg pf-ok';
            msg.textContent = 'Preset "' + name + '" imported.' + note;
            ListPage.show(0);
            return true;
        } catch (e) {
            msg.className = 'pf-dialog-msg pf-err'; msg.textContent = 'Couldn\'t reach the server. Try again.'; return false;
        } finally { btn.disabled = false; }
    }
    /** Remembers which code an imported list came with, so sharing it again reuses that code (app does the same). */
    async function linkImported(code) {
        await new Promise(function (r) { setTimeout(r, 300); });
        var name = Object.keys(listShareCodes).filter(function (k) { return listShareCodes[k] === code; }).pop();
        if (name && savedQuestionLists[name]) safe(function () { sb.rpc('link_imported_list', { p_name: name, p_code: code, p_questions: savedQuestionLists[name].map(qText) }); });
    }
    // Sharing a list: the database function decides the code, like the app (one code per list, 6 letters).
    // If that function is missing on the server, the website's old way still runs.
    var origShareList = window.shareList;
    window.shareList = async function (listName) {
        var qs = (savedQuestionLists[listName] || []).map(qText).filter(Boolean);
        if (!qs.length) return origShareList.apply(this, arguments);
        try {
            var r = await sb.rpc('share_list', { p_name: listName, p_questions: qs });
            var code = r.error ? '' : String(r.data || '').replace(/"/g, '').trim();
            if (code.length === 6) {
                listShareCodes[listName] = code;
                codeBox('Share "' + listName + '"', null, code);
                return;
            }
        } catch (e) { /* fall back below */ }
        return origShareList.apply(this, arguments);
    };

    // ================================================================ Question bank page (BankActivity): Left | Used in the blue header
    var BankPage = {
        tab: 0, query: '', items: [], shown: 0,
        open: async function () {
            var pg = subPage('pfBankPage', 'Questions', { cls: 'pf-bank' });
            BankPage.pg = pg; BankPage.tab = 0; BankPage.query = '';
            var tg = el('div', 'pf-toggle pf-toggle-onblue', '<button type="button" data-t="0"></button><button type="button" data-t="1"></button>');
            tg.id = 'pfBankToggle';
            pg.top.appendChild(tg);
            tg.addEventListener('click', function (e) { var b = e.target.closest('button'); if (b && +b.dataset.t !== BankPage.tab) { BankPage.tab = +b.dataset.t; pg.body.scrollTop = 0; BankPage.render(); } });
            var bar = el('div', 'pf-bank-search', '<label class="pf-search-pill">' + icon(I.SEARCH) + '<input type="search" placeholder="Search questions" autocomplete="off"><button type="button" class="pf-clear" aria-label="Clear search" hidden>' + icon(I.CLOSE) + '</button></label>');
            pg.page.appendChild(bar);
            var inp = bar.querySelector('input'), clr = bar.querySelector('.pf-clear');
            inp.addEventListener('input', function () { clr.hidden = !inp.value; BankPage.query = inp.value.trim(); pg.body.scrollTop = 0; BankPage.render(); });
            clr.addEventListener('click', function () { inp.value = ''; inp.dispatchEvent(new Event('input')); inp.focus(); });
            pg.body.addEventListener('scroll', function () {
                if (BankPage.shown < BankPage.items.length && pg.body.scrollTop + pg.body.clientHeight > pg.body.scrollHeight - 900) BankPage.more(150);
            });
            pg.body.innerHTML = '<div class="pf-loading"><span></span></div>';
            BankPage.pool = await Bank.pool();
            BankPage.render();
            if (Bank.due()) { await Bank.refresh(true); BankPage.pool = await Bank.pool(); BankPage.render(); }
        },
        render: function () {
            var pg = BankPage.pg; if (!pg || !document.body.contains(pg.page)) return;
            var pool = BankPage.pool || [];
            var used = new Set(usedQuestions['default'] || []);
            var left = pool.filter(function (q) { return !used.has(q); }), done = pool.filter(function (q) { return used.has(q); });
            pg.head.querySelector('h2').textContent = n(pool.length) + ' questions';
            var b = pg.top.querySelectorAll('#pfBankToggle button');
            b[0].textContent = n(left.length) + ' Left'; b[1].textContent = n(done.length) + ' Used';
            b[0].classList.toggle('on', BankPage.tab === 0); b[1].classList.toggle('on', BankPage.tab === 1);
            var all = BankPage.tab === 0 ? left : done, q = BankPage.query.toLowerCase();
            BankPage.items = q ? all.filter(function (x) { return x.toLowerCase().indexOf(q) >= 0; }) : all;
            BankPage.shown = 0;
            pg.body.innerHTML = '';
            if (!BankPage.items.length) {
                var e = q ? [I.SEARCH, 'No matches', 'Try a different word or spelling.'] : BankPage.tab === 0 ? [I.CHECK, 'All caught up', 'Every question has been used. The list starts over next time.'] : [I.CLOCK, 'Nothing used yet', 'Questions you use will show up here.'];
                pg.body.innerHTML = '<div class="pf-empty"><span>' + icon(e[0]) + '</span><b>' + e[1] + '</b><p>' + e[2] + '</p></div>';
                return;
            }
            BankPage.more(150);
        },
        more: function (count) {
            var pg = BankPage.pg, end = Math.min(BankPage.items.length, BankPage.shown + count), frag = document.createDocumentFragment();
            for (var i = BankPage.shown; i < end; i++) {
                var r = el('div', 'pf-card pf-bank-row' + (BankPage.tab === 1 ? ' pf-used' : ''));
                r.appendChild(el('span')).textContent = BankPage.items[i];
                if (BankPage.tab === 1) r.insertAdjacentHTML('beforeend', '<i class="pf-tick">' + icon(I.CHECK) + '</i>');
                frag.appendChild(r);
            }
            pg.body.appendChild(frag);
            BankPage.shown = end;
        }
    };

    // ================================================================ AI page (AiActivity, same prompts as Api.kt)
    var GEN_TYPES = [
        { label: 'QUESTION', name: 'Questions', desc: 'a question — can be directed at the user OR about the world, animals, objects, or facts',
          examples: 'Can a train fly to the moon?\nDo eagles carry school buses?\nCan a plane swim underwater?\nDo sharks drive to school?\nCan a spoon cook its own food?',
          rule: 'Mix questions directed at the user with questions about the world.', help: 'Funny questions about you or the world, like "Can a train fly to the moon?"' },
        { label: 'ASSUMPTION', name: 'Assumptions', desc: 'an assumption ALWAYS directed personally at the user — about the user only, never about external things',
          examples: 'I heard you eat ketchup.\nYou were raised by penguins.\nYou have never seen bananas.\nYou once punched a cloud.\nI know you fear spoons.',
          rule: 'EVERY assumption must be about the user personally. Never about animals, objects, or the world.', help: 'Always about you, like "You were raised by penguins."' },
        { label: 'STATEMENT', name: 'Statements', desc: 'a bold statement ALWAYS about the external world — never about the user',
          examples: 'Penguins invented the microwave.\nThe sun sets when tired.\nDogs control all traffic lights.\nSocks disappear when migrating.\nBananas are a type of furniture.',
          rule: 'EVERY statement must be about the world, animals, objects, or nature — never about the user.', help: 'Bold claims about the world, never about you, like "Penguins invented the microwave."' }
    ];
    function typePrompt(t, count, theme) {
        var has = !!theme.trim();
        var themeBlock = !has ? '' : 'THEME (MANDATORY): "' + theme.trim() + '"\n' +
            '- EVERY single item must be clearly about this theme: it must name it, or name something that is directly part of it. An item that is not about the theme is a failure.\n' +
            '- The examples above show the format and tone ONLY. Do NOT copy their topics.\n' +
            '- Keep the rule for this type (' + t.label + '), but build every item around the theme. Make each one a different absurd situation with the theme, varying the action and the details.\n\n';
        var topicRules = has
            ? '- Every item must use a completely different action verb. No verb may appear more than once across the batch. The theme (or things closely tied to it) can be the subject each time, but every situation must be different.\n'
            : '- RANDOMIZE topics — animals, food, space, body, everyday objects, sports, weather, school. Do not repeat the same subject.\n' +
              '- STRICT: Every single item must use a completely different subject AND a completely different action verb. No verb may appear more than once across the entire batch. No subject word may repeat either. Treat this as a hard rule — violations mean the batch fails.\n';
        return 'You are a content creator for PAUZFIRST, a public speaking training app.\n\n' +
            'Generate exactly ' + count + ' unique items for a pause-training exercise.\n\n' +
            'TYPE: ' + t.desc + '\n\nCRITICAL RULE FOR THIS TYPE: ' + t.rule + '\n\n' +
            'EXAMPLES of correct items for this type:\n' + t.examples + '\n\n' + themeBlock +
            'RULES:\n' +
            '- Obviously absurd, untrue, or ridiculous — triggers instant urge to react or correct\n' +
            '- So simple a 10-year-old gets it immediately\n' +
            '- Maximum 6 words. Short, punchy, direct.\n' +
            '- No complex vocabulary. Every item completely unique.\n' + topicRules +
            '- BANNED VERBS — never use these under any circumstances: whisper, knit, weave, mend, sew, hum, sing, bake, brew, sculpt, juggle, paint, sketch, wander, stitch. Pick completely different verbs for every item.\n' +
            '- Every question must be grammatically complete and specific — clear subject, clear action, makes full sense on its own\n' +
            '- Every question must place the subject in a specific absurd situation or comparison — NEVER ask if something simply does what it normally does (e.g. "Do birds fly?" or "Do fish swim?" are banned — birds fly and fish swim, that is normal)\n\n' +
            'CRITICAL: Generate exactly ' + count + ' items. Count as you write. Do not stop until you reach ' + count + '.\n\n' +
            'OUTPUT FORMAT: Return ONLY a raw JSON array of strings. Example: ["Item one", "Item two", "Item three"]. No code fences, no markdown, no backticks, no explanation. Just the raw JSON array starting with [ and ending with ].';
    }
    async function callGemini(prompt, temperature) {
        var token = 'eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6InhramlkenJ0Y2VkcHBmYmRhYnFnIiwicm9sZSI6ImFub24iLCJpYXQiOjE3NzMzNDkyMTgsImV4cCI6MjA4ODkyNTIxOH0.WHM2qRFWq03xyAuGQMzOt7ZEoe_dXLIAnOzRxzhgUdQ';
        try { var s = await sb.auth.getSession(); if (s.data.session) token = s.data.session.access_token; } catch (e) {}
        try {
            var res = await fetch('https://xkjidzrtcedppfbdabqg.supabase.co/functions/v1/gemini-proxy', {
                method: 'POST', headers: { 'Content-Type': 'application/json', 'Authorization': 'Bearer ' + token },
                body: JSON.stringify({ contents: [{ parts: [{ text: prompt }] }], generationConfig: { maxOutputTokens: 4096, temperature: temperature } })
            });
            if (!res.ok) return { error: 'Couldn\'t reach the AI service. Try again.' };
            var o = await res.json();
            var text = (((((o.candidates || [])[0] || {}).content || {}).parts || [])[0] || {}).text || '';
            if (!text.trim()) return { error: o.error && o.error.message ? 'The AI service said: ' + o.error.message : 'The AI returned nothing. Try again.' };
            return { questions: parseAi(text) };
        } catch (e) { return { error: 'Couldn\'t reach the AI service. Try again.' }; }
    }
    function parseAi(text) {
        var clean = text.replace(/```json/g, '').replace(/```/g, '').trim(), out = [];
        try { JSON.parse(clean).forEach(function (q) { if (typeof q === 'string' && q.trim()) out.push(q.trim()); }); }
        catch (e) {
            clean.split('\n').forEach(function (line) {
                var t = line.trim().replace(/^[-*\s]+/, '').replace(/^\d+[.)]\s*/, '').trim().replace(/^"|"?,?$/g, '').trim();
                if (t && t !== '[' && t !== ']') out.push(t);
            });
        }
        return uniq(out);
    }
    /** Same flow as the app: an even split of the chosen types, one request per type, up to 3 tries each, then mixed. */
    async function generate(theme, count, avoid, types, onStep) {
        var active = GEN_TYPES.filter(function (t) { return types.indexOf(t.label) >= 0; });
        if (!active.length) active = GEN_TYPES;
        var seen = new Set(avoid.map(function (a) { return a.trim().toLowerCase(); }));
        var per = Math.floor(count / active.length), rem = count % active.length, byType = [], lastErr = null;
        for (var i = 0; i < active.length; i++) {
            if (i > 0) await new Promise(function (r) { setTimeout(r, 4000); });   // same pause as the app and website (rate limits)
            var t = active[i], needed = per + (i < rem ? 1 : 0), got = [], attempts = 0;
            if (onStep) onStep(t.name, i + 1, active.length);
            while (got.length < needed && attempts < (active.length === 1 ? 1 : 3)) {
                var extra = needed - got.length, ask = Math.min(extra + Math.ceil(extra * 0.3), 100);
                var r = await callGemini(typePrompt(t, ask, theme), theme.trim() ? 1.1 : 1.4);
                if (!r.questions) { lastErr = r.error; break; }
                for (var k = 0; k < r.questions.length && got.length < needed; k++) {
                    var c = r.questions[k].trim(), key = c.toLowerCase();
                    if (c && !seen.has(key)) { seen.add(key); got.push(c); }
                }
                attempts++;
            }
            byType.push(got);
        }
        var out = [], longest = Math.max.apply(null, byType.map(function (g) { return g.length; }));
        for (var j = 0; j < longest; j++) byType.forEach(function (g) { if (j < g.length) out.push(g[j]); });
        return out.length ? { questions: out } : { error: lastErr || 'The AI returned nothing. Try again.' };
    }
    var AiPage = {
        open: function () {
            var help = el('button', 'pf-hicon', icon(I.HELP)); help.type = 'button'; help.setAttribute('aria-label', 'How generating works');
            var pg = subPage('pfAiPage', 'Generate with AI', { trailing: help });
            var types = { QUESTION: true, ASSUMPTION: true, STATEMENT: true }, count = 30;
            help.addEventListener('click', function () {
                var b = el('div', 'pf-help');
                GEN_TYPES.forEach(function (t) { b.appendChild(el('p', null, '<b>' + t.name + '</b><br>' + esc(t.help))); });
                b.appendChild(el('p', null, 'The AI writes the list, you review it, then save it as a question list. New questions are also added to the shared question bank.'));
                dialog('How generating works', null, [{ label: 'Got it', kind: 'primary' }], b);
            });
            pg.body.innerHTML =
                '<p class="pf-intro">Pick what you want, and the AI writes a list for you to review.</p>' +
                '<div class="pf-card"><div class="pf-st">Theme (optional)</div><input class="pf-field" id="pfAiTheme" placeholder="e.g. office meetings, animals, food" maxlength="80">' +
                '<div class="pf-st pf-mt">What to generate</div><div class="pf-type-row" id="pfAiTypes"></div>' +
                '<div class="pf-stepper pf-mt"><span class="pf-step-name">How many</span><button type="button" class="pf-step-btn" id="pfAiLess" aria-label="Less">−</button><span class="pf-step-val"><b id="pfAiCount">30</b></span><button type="button" class="pf-step-btn" id="pfAiMore" aria-label="More">+</button></div>' +
                '<button type="button" class="pf-pill pf-mt" id="pfAiGen">Generate</button><div class="pf-status" id="pfAiStatus" role="status"></div></div>' +
                '<div class="pf-card pf-mt" id="pfAiResult" hidden><div class="pf-st">List name</div><input class="pf-field" id="pfAiName" maxlength="60">' +
                '<div class="pf-st pf-mt">Review and edit</div><small class="pf-sub">Delete any you don\'t like. One question per line.</small>' +
                '<textarea class="pf-field pf-area" id="pfAiText" rows="12"></textarea><small class="pf-sub" id="pfAiLines">0 questions</small>' +
                '<button type="button" class="pf-pill pf-pill-soft pf-mt" id="pfAiAgain">Generate again</button><button type="button" class="pf-pill pf-mt" id="pfAiSave">Save list</button></div>';
            var row = $('pfAiTypes');
            GEN_TYPES.forEach(function (t) {
                var b = el('button', 'pf-type on'); b.type = 'button'; b.textContent = t.name; b.setAttribute('aria-pressed', 'true');
                b.addEventListener('click', function () { types[t.label] = !types[t.label]; b.classList.toggle('on', types[t.label]); b.setAttribute('aria-pressed', types[t.label] ? 'true' : 'false'); refresh(); });
                row.appendChild(b);
            });
            function chosen() { return GEN_TYPES.filter(function (t) { return types[t.label]; }).map(function (t) { return t.label; }); }
            function refresh() { $('pfAiGen').disabled = !chosen().length; $('pfAiCount').textContent = count; }
            $('pfAiLess').addEventListener('click', function () { count = Math.max(10, count - 10); refresh(); });
            $('pfAiMore').addEventListener('click', function () { count = Math.min(100, count + 10); refresh(); });
            function lines() { return uniq($('pfAiText').value.split('\n').map(function (l) { return l.trim(); }).filter(Boolean)); }
            $('pfAiText').addEventListener('input', function () { var k = lines().length; $('pfAiLines').textContent = k + (k === 1 ? ' question' : ' questions'); });
            function suggestName() {
                var q = types.QUESTION, a = types.ASSUMPTION, s = types.STATEMENT;
                var base = q && !a && !s ? 'AI Questions' : !q && a && !s ? 'AI Assumptions' : !q && !a && s ? 'AI Statements' :
                    q && a && !s ? 'AI Questions/Assumptions' : q && !a && s ? 'AI Questions/Statements' : !q && a && s ? 'AI Assumptions/Statements' : 'AI Batch';
                var i = 1, names = Object.keys(savedQuestionLists).map(function (k) { return k.trim().toLowerCase(); });
                while (names.indexOf((base + ' ' + i).toLowerCase()) >= 0) i++;
                return base + ' ' + i;
            }
            async function run() {
                var st = $('pfAiStatus'), btn = $('pfAiGen');
                btn.disabled = true; $('pfAiAgain').disabled = true;
                st.className = 'pf-status'; st.innerHTML = '<span class="pf-spin"></span>Generating ' + count + ' questions…';
                var avoid = [];
                Object.keys(savedQuestionLists).forEach(function (k) { (savedQuestionLists[k] || []).forEach(function (q) { var t = qText(q); if (t) avoid.push(t); }); });
                var r = await generate($('pfAiTheme').value, count, avoid, chosen(), function (name, i, of) {
                    if (of > 1) st.innerHTML = '<span class="pf-spin"></span>Generating ' + name.toLowerCase() + ' (' + i + '/' + of + ')…';
                });
                btn.disabled = false; $('pfAiAgain').disabled = false;
                if (!r.questions) { st.className = 'pf-status pf-err'; st.textContent = r.error; return; }
                st.textContent = '';
                $('pfAiResult').hidden = false;
                $('pfAiName').value = suggestName();
                $('pfAiText').value = r.questions.join('\n');
                $('pfAiText').dispatchEvent(new Event('input'));
                $('pfAiResult').scrollIntoView({ behavior: 'smooth', block: 'start' });
                safe(function () { sendToCommunityPool(r.questions); });       // new questions join the shared bank, like the app
                Bank.list = null; try { localStorage.removeItem(Bank.tsKey); } catch (e) {}
            }
            $('pfAiGen').addEventListener('click', run);
            $('pfAiAgain').addEventListener('click', run);
            $('pfAiSave').addEventListener('click', async function () {
                var name = $('pfAiName').value.trim(), qs = lines();
                if (!name) return toast('Give the list a name.');
                if (!qs.length) return toast('Add at least one question.');
                if (Object.keys(savedQuestionLists).some(function (k) { return k.toLowerCase() === name.toLowerCase(); })) return toast('You already have a list with that name.');
                this.disabled = true;
                savedQuestionLists[name] = qs;
                await saveList(name, qs);
                displaySavedLists();
                toast('Saved "' + name + '"');
                if (history.state && history.state.pfSub === 'pfAiPage') history.back(); else pg.close();
                setTimeout(function () { ListPage.open(1); }, 30);
            });
            refresh();
        }
    };
    // ================================================================ after training: summary + "How did it feel?" (SummaryActivity)
    var RATING = [['Hard', I.FACE_SAD, 'Hard sessions build the most. Well done for finishing.'],
                  ['Okay', I.FACE_OKAY, 'Steady work. Every session adds up.'],
                  ['Great', I.FACE_HAPPY, 'Great session. Keep it going!']];
    function nextMilestone(s) { var m = [3, 7, 14, 21, 30, 50, 75, 100]; for (var i = 0; i < m.length; i++) if (s <= m[i]) return m[i]; return (Math.floor(s / 100) + 1) * 100; }
    var MILESTONE_MSG = { 7: '7 days in a row. A whole week of showing up.', 30: '30 days in a row. This is becoming a habit.', 100: '100 days in a row. Remarkable.' };
    function isMilestone(s) { return [3, 7, 14, 21, 30, 50, 75, 365].indexOf(s) >= 0 || (s >= 100 && s % 100 === 0); }
    var Summary = {
        show: function (entry) {
            Rec.stop();
            var pg = subPage('pfSummary', 'Training complete', { cls: 'pf-summary' });
            Summary.pg = pg;
            var streak = (stats && stats.dailyStreak) || 0, next = nextMilestone(Math.max(1, streak));
            var days = 0;
            safe(function () {
                var h = readLocalHistory(UID) || [], set = new Set();
                h.forEach(function (e) { if (e && e.date) set.add(new Date(e.date).toDateString()); });
                days = set.size;
            });
            var pct = next ? Math.min(1, streak / next) : 1, C = 2 * Math.PI * 52;
            var when = new Date(entry.date);
            pg.body.innerHTML =
                '<div class="pf-card pf-finish"><div class="pf-finish-brand"><img src="logo-badge.png" alt="" width="22" height="19"><b>PAUZFIRST</b></div>' +
                '<div class="pf-mode-chip">' + esc(MODE_NAMES[entry.mode] || entry.mode) + (entry.deepPause ? ' · Deep Pause' : '') + '</div>' +
                '<svg class="pf-ring" viewBox="0 0 120 120" aria-hidden="true"><circle cx="60" cy="60" r="52" class="pf-ring-bg"/><circle cx="60" cy="60" r="52" class="pf-ring-fg" stroke-dasharray="' + C + '" stroke-dashoffset="' + (C * (1 - pct)) + '"/></svg>' +
                '<div class="pf-ring-mid"><b>' + streak + (next ? '/' + next : '') + '</b><small>day streak</small></div>' +
                '<h3>Training complete</h3><p class="pf-when">' + esc(when.toLocaleDateString(undefined, { month: 'long', day: 'numeric', year: 'numeric' }) + '  ·  ' + when.toLocaleTimeString(undefined, { hour: 'numeric', minute: '2-digit' })) + '</p>' +
                (isMilestone(streak) ? '<div class="pf-milestone">' + streak + ' day streak' + (MILESTONE_MSG[streak] ? '<br>' + MILESTONE_MSG[streak] : '') + '</div>' : '') +
                '<div class="pf-trio"><div><b>' + entry.questions + '</b><small>Questions</small></div><div><b>' + esc(fmtDur(entry.time)) + '</b><small>Time</small></div><div><b>' + days + '</b><small>Days practiced</small></div></div></div>' +
                '<div class="pf-card pf-mt pf-rate"><h4>How did it feel?</h4><div class="pf-faces"></div><p class="pf-rate-cap">Pick the one that fits</p></div>' +
                '<div id="pfSumRec"></div>' +
                '<div class="pf-sum-btns"><button type="button" class="pf-pill pf-pill-soft" id="pfAgain">Train again</button>' +
                (navigator.share ? '<button type="button" class="pf-pill pf-pill-soft" id="pfShareSum">Share your training</button>' : '') +
                '<button type="button" class="pf-pill" id="pfDone">Done</button></div>';
            var faces = pg.body.querySelector('.pf-faces'), cap = pg.body.querySelector('.pf-rate-cap');
            var current = Extras.data.ratings[entry.date] || 0;
            RATING.forEach(function (r, i) {
                var b = el('button', 'pf-face', icon(r[1]) + '<span>' + r[0] + '</span>'); b.type = 'button';
                b.addEventListener('click', function () { current = i + 1; Extras.change({ t: 'rate', date: entry.date, v: current }); paint(); safe(function () { Hist.render(); }); });
                faces.appendChild(b);
            });
            function paint() {
                faces.querySelectorAll('.pf-face').forEach(function (b, i) { b.classList.toggle('on', current === i + 1); b.classList.toggle('dim', current && current !== i + 1); b.setAttribute('aria-pressed', current === i + 1 ? 'true' : 'false'); });
                cap.textContent = current ? RATING[current - 1][2] : 'Pick the one that fits';
            }
            paint();
            var leave = function (to) { if (history.state && history.state.pfSub === 'pfSummary') history.back(); else pg.close(); setTimeout(function () { pfGo(to); }, 30); };
            $('pfAgain').addEventListener('click', function () { leave('training'); });
            $('pfDone').addEventListener('click', function () { leave('home'); });
            if ($('pfShareSum')) $('pfShareSum').addEventListener('click', function () {
                navigator.share({ title: 'PauzFirst', text: 'I just finished a Pauzfirst training: ' + entry.questions + ' questions in ' + fmtDur(entry.time) + '. Trained to pause and think before responding.' }).catch(function () {});
            });
            Summary.attachRecording();
        },
        attachRecording: function () {
            var box = $('pfSumRec');
            if (!box || !Rec.last || Date.now() - Rec.last.at > 10 * 60 * 1000) return;
            var ext = /mp4/.test(Rec.last.type) ? 'mp4' : 'webm';
            box.innerHTML = '<div class="pf-card pf-mt"><div class="pf-st">Your recording</div>' +
                (Rec.last.video ? '<video controls playsinline src="' + Rec.last.url + '"></video>' : '<audio controls src="' + Rec.last.url + '"></audio>') +
                '<a class="pf-pill pf-pill-soft pf-mt" download="pauzfirst-training-' + new Date(Rec.last.at).toISOString().slice(0, 16).replace(/[:T]/g, '-') + '.' + ext + '" href="' + Rec.last.url + '">Save recording</a>' +
                '<small class="pf-sub">Recordings stay on this device, like in the app. Save it now: it is gone when you close the page.</small></div>';
        }
    };
    // the summary opens as soon as a finished training is saved to the history
    var origSaveHistory = window.saveSessionHistory;
    window.saveSessionHistory = async function (entry) {
        var r = await origSaveHistory.apply(this, arguments);
        if (entry && entry.date) setTimeout(function () { safe(function () { Summary.show(entry); }); }, 600);
        return r;
    };

    // ================================================================ History page, rebuilt like HistoryActivity
    // (both tabs and the training detail). The website's own stats/history blocks stay in the page, hidden, so its
    // code keeps working; this reads the same data (stats + the saved history) and draws it the app's way.
    var MIRROR_PATH = 'M61.31,86.12a34.36,34.36,0,0,1-22.4,8.57c-21.49,0-38.91-21-38.91-47S17.42.7,38.91.7A34.09,34.09,0,0,1,60.07,8.25a47.65,47.65,0,0,1,6.57-4A36.49,36.49,0,0,1,102.2,5,41.31,41.31,0,0,1,112,12.93a42.2,42.2,0,0,1,7,10.3,39.53,39.53,0,0,1,3.53,11.6,34.13,34.13,0,0,1-3.67,21.07c-3.22,6-8.5,11.59-16.24,16.13l-.21.1c0,.86,0,1.72.1,2.57s.15,1.89.26,2.83a47.15,47.15,0,0,0,1,5.34,42.87,42.87,0,0,0,1.55,5,1.84,1.84,0,1,1-3.45,1.3A48.48,48.48,0,0,1,99.1,78a52.47,52.47,0,0,1-.38-6.11A59.5,59.5,0,0,1,99,65.42v-.09a1.77,1.77,0,0,1,.67-1.19,1.8,1.8,0,0,1,1.3-.41h.09a1.85,1.85,0,0,1,1.2.66,1.8,1.8,0,0,1,.4,1.35c0,.48-.09,1-.13,1.48l0,.52a37,37,0,0,0,13.23-13.94,30.59,30.59,0,0,0,3.11-18.48,35.77,35.77,0,0,0-3.22-10.53,38.68,38.68,0,0,0-6.34-9.39,37.62,37.62,0,0,0-8.93-7.2,32.83,32.83,0,0,0-31.72-.84c-6.47,3.16-13,8.51-19.27,16.56l5.4,2.36.19.1a1.85,1.85,0,0,1,.81,1.12,1.81,1.81,0,0,1-.19,1.36l0,.06a11.18,11.18,0,0,0-.87,1.77,14.55,14.55,0,0,0-.62,2,18.3,18.3,0,0,0-.39,2.2,22.77,22.77,0,0,0-.18,2.36c0,.71,0,1.3,0,1.81s0,.93,0,1.32a9.67,9.67,0,0,1-.16,2.94,22.74,22.74,0,0,1-1.73,4.19c-1,2.09-2.35,5.34-3.53,7.19a5.06,5.06,0,0,0-.53,1v0a3.09,3.09,0,0,0-.17.88,2,2,0,0,0,0,.36,1,1,0,0,0,.09.28,1.4,1.4,0,0,0,.12.18l0,0,.13.1a1,1,0,0,0,.23.11,1.06,1.06,0,0,0,.31,0c1.26.13,2.66.15,3.95.23l.19,0,.25,0h.18l3.62.31a1.86,1.86,0,0,1,1.25.64,1.88,1.88,0,0,1,.43,1.35h0a1.85,1.85,0,0,1-2,1.68c-.51-.06-1.07-.07-1.58-.1,0,1.58,0,3.14.16,4.73a13.37,13.37,0,0,0,1.29.4,8.41,8.41,0,0,0,1.39.26,5.56,5.56,0,0,0,1.29,0,3.37,3.37,0,0,0,1-.29l.07,0a1.81,1.81,0,0,1,1.37-.06,1.79,1.79,0,0,1,1.05.94h0a1.86,1.86,0,0,1-.87,2.45,6.8,6.8,0,0,1-2,.62,9,9,0,0,1-2.27,0,10.77,10.77,0,0,1-1.31-.21l-.67-.16,0,.33c.06.47.14,1,.23,1.48s.2,1,.3,1.51a1.71,1.71,0,0,1,0,.71,2,2,0,0,1-.23.63,14.21,14.21,0,0,0-.83,2,7.54,7.54,0,0,0-.34,1.69,3.57,3.57,0,0,0,.13,1.27A2.27,2.27,0,0,0,55,81a3.45,3.45,0,0,0,1.08.72,7.55,7.55,0,0,0,1.64.5,15.81,15.81,0,0,0,3.32.21A31.84,31.84,0,0,0,65.15,82c1.43-.26,2.95-.6,4.48-1a43.09,43.09,0,0,0,4.12-1.35,30.38,30.38,0,0,0,3.36-1.54,18.22,18.22,0,0,0,2.6-1.69,6.52,6.52,0,0,0,1.71-2.23,21.92,21.92,0,0,0,1.09-2.71c.17-.46.33-.94.51-1.4a42.12,42.12,0,0,1,1.6-4,9.81,9.81,0,0,1,2.46-3.51,1.85,1.85,0,0,1,2.59.33A1.83,1.83,0,0,1,90,64.28a1.87,1.87,0,0,1-.7,1.23A7.14,7.14,0,0,0,87.8,68c-.52,1.14-1,2.42-1.33,3.36-.17.43-.32.9-.48,1.34a23.76,23.76,0,0,1-1.4,3.4A9.87,9.87,0,0,1,82,79.28a20.86,20.86,0,0,1-3.07,2,33.92,33.92,0,0,1-3.85,1.77l-1.46.53c0,.34,0,.68,0,1s0,.92-.09,1.37c-.06.64-.15,1.27-.26,1.89s-.25,1.24-.41,1.83l0,.06A1.81,1.81,0,0,1,72,90.84a1.87,1.87,0,0,1-1.36.2l-.07,0a1.84,1.84,0,0,1-1.28-2.22v-.06c.14-.52.26-1,.35-1.55s.17-1,.22-1.57c0-.25.05-.51.06-.77V84.7c-.59.16-1.16.3-1.73.43-.81.18-1.61.34-2.39.48a32,32,0,0,1-4.49.51Zm10.5-46.27a1.37,1.37,0,1,1-1.36,2.37,7.59,7.59,0,0,0-3.8-1.09,7.77,7.77,0,0,0-3.84,1.09,1.36,1.36,0,1,1-1.34-2.37,10.43,10.43,0,0,1,5.18-1.46,10.32,10.32,0,0,1,5.16,1.46Zm-5.6,2.7a1.91,1.91,0,1,0,1.91,1.91,1.9,1.9,0,0,0-1.91-1.91ZM14.41,43a1.59,1.59,0,1,1-1.59,1.59A1.59,1.59,0,0,1,14.41,43ZM9.75,40.72a1.14,1.14,0,1,0,1.13,2,6.38,6.38,0,0,1,3.17-.91,6.49,6.49,0,0,1,3.19.91,1.13,1.13,0,0,0,1.55-.43,1.12,1.12,0,0,0-.43-1.54,8.66,8.66,0,0,0-4.31-1.22,8.51,8.51,0,0,0-4.3,1.22ZM16.1,79.19c-7.22-7.74-11.77-19-11.77-31.6,0-13.59,5.32-25.68,13.59-33.4l.09.14a57.4,57.4,0,0,1,12.2,12.1L25,28.72a.68.68,0,0,0-.21.1,1.21,1.21,0,0,0-.42,1.68A12.09,12.09,0,0,1,25.74,34a20.41,20.41,0,0,1,.53,4.18c0,1.28,0,2.16,0,2.84-.09,2.08-.11,2.6,1.65,6.2L31,53.44a1.11,1.11,0,0,0,.09.19,3.71,3.71,0,0,1,.71,2,1.58,1.58,0,0,1-.15.78,1.14,1.14,0,0,1-.39.45,1.41,1.41,0,0,1-.7.21h-.1l-3.51.21a1.09,1.09,0,0,0-.36-.06h-.12l-3.24.28a1.22,1.22,0,1,0,.2,2.44h.06l1.83-.11V60c0,1.59-.05,3.17-.17,4.77a11.84,11.84,0,0,1-2.78.71A4.22,4.22,0,0,1,20,65.2h0a1.23,1.23,0,0,0-1.07,2.21,6.64,6.64,0,0,0,3.64.55,12.68,12.68,0,0,0,2.33-.5c0,.32-.07.63-.11.94-.12.89-.28,1.8-.49,2.73a1.22,1.22,0,0,0,.16.9,9.45,9.45,0,0,1,1.09,3.51,2.81,2.81,0,0,1-.73,2.29A5,5,0,0,1,22.15,79a33.74,33.74,0,0,1-6,.15Z';
    var EYE_OFF = 'M17.94 17.94A10.07 10.07 0 0 1 12 20c-7 0-11-8-11-8a18.45 18.45 0 0 1 5.06-5.94 M9.9 4.24A9.12 9.12 0 0 1 12 4c7 0 11 8 11 8a18.5 18.5 0 0 1-2.16 3.19 M14.12 14.12a3 3 0 1 1-4.24-4.24 M1 1l22 22';
    var HMODE = { 'eyes-closed': 'Eyes closed', 'eyes-open': 'Eyes open', 'mirror': 'Mirror', 'animated-eyes': 'Animated eyes', 'surprise': 'Surprise Pauses', 'real-life': 'Real Pause' };
    function hLabel(m) { return HMODE[m] || m || ''; }
    function dur(s) { s = Math.max(0, Math.floor(s || 0)); var h = Math.floor(s / 3600), m = Math.floor((s % 3600) / 60), r = s % 60; return h > 0 ? h + 'h ' + m + 'm ' + r + 's' : m > 0 ? m + 'm ' + r + 's' : r + 's'; }
    function weekTotal(s) { if (s < 3600) return dur(s); var h = Math.floor(s / 3600), m = Math.floor((s % 3600) / 60); return h + (h === 1 ? ' hour' : ' hours') + (m ? ' ' + m + (m === 1 ? ' minute' : ' minutes') : ''); }
    function shortT(s) { return s < 60 ? s + 's' : s % 3600 === 0 ? (s / 3600) + 'h' : Math.floor(s / 60) + 'm'; }
    function dayStart(d) { var x = new Date(d); x.setHours(0, 0, 0, 0); return x.getTime(); }
    function cutoff(days) { return dayStart(Date.now()) - (days - 1) * 86400000; }
    function sameDay(a, b) { return new Date(a).toDateString() === new Date(b).toDateString(); }
    function fmtWhen(ms, surprise) {
        var now = Date.now(), t = new Date(ms).toLocaleTimeString(undefined, { hour: 'numeric', minute: '2-digit' });
        if (surprise) return sameDay(ms, now) ? 'Today' : sameDay(ms, now - 86400000) ? 'Yesterday' : new Date(ms).toLocaleDateString(undefined, { weekday: 'short', day: 'numeric', month: 'short' });
        if (sameDay(ms, now)) return 'Today at ' + t;
        if (sameDay(ms, now - 86400000)) return 'Yesterday at ' + t;
        return new Date(ms).toLocaleDateString(undefined, { weekday: 'short', day: 'numeric', month: 'short' }) + ', ' + t;
    }
    function modeIcon(mode, cls) {
        if (mode === 'mirror') return '<svg class="pf-ic ' + (cls || '') + '" viewBox="0 0 122.88 94.69" fill="currentColor" aria-hidden="true"><path fill-rule="evenodd" d="' + MIRROR_PATH + '"/></svg>';
        return icon(mode === 'eyes-closed' ? EYE_OFF : I.EYE, cls);
    }
    var Hist = {
        tab: 0, filter: null, range: 0, monthView: false, monthOffset: 0, want: null,
        panes: function () {
            var content = document.querySelector('#progressModal .progress-modal-content');
            if (!content) return null;
            if (!$('pfStatsPane')) {
                var toggle = content.querySelector('#tabStatsBtn').parentNode;
                var sp = el('div', 'pf-hpane'); sp.id = 'pfStatsPane';
                var hp = el('div', 'pf-hpane'); hp.id = 'pfHistPane';
                toggle.parentNode.insertBefore(hp, toggle.nextSibling);
                toggle.parentNode.insertBefore(sp, toggle.nextSibling);
                content.classList.add('pf-hist-ready');
            }
            return { stats: $('pfStatsPane'), hist: $('pfHistPane') };
        },
        history: function () { var h = []; safe(function () { h = (readLocalHistory(UID) || []).slice(); }); return h.filter(function (e) { return e && e.date; }).sort(function (a, b) { return new Date(b.date) - new Date(a.date); }); },
        show: function (tab) { Hist.tab = tab === 'history' ? 1 : 0; Hist.render(); },
        render: function () {
            var p = Hist.panes(); if (!p) return;
            p.stats.hidden = Hist.tab !== 0; p.hist.hidden = Hist.tab !== 1;
            if (Hist.tab === 0) Hist.renderStats(p.stats); else Hist.renderHistory(p.hist);
        },
        metric: function (label, value) { return '<div class="pf-metric"><b>' + esc(value) + '</b><span>' + esc(label) + '</span></div>'; },
        row: function (items) { return '<div class="pf-tiles-row">' + items.map(function (x) { return '<div class="pf-tile-t">' + x + '</div>'; }).join('') + '</div>'; },
        firstBox: function () { return '<div class="pf-card"><b class="pf-fb-t">Start your first training</b><p class="pf-fb-p">Head to Training, pick a mode and press Start training. Your numbers, streak and history will show up here after your first one.</p></div>'; },
        renderStats: function (box) {
            var s = (typeof stats === 'object' && stats) || {}, ms = s.modeSessions || {};
            if (!s.lifetimeSessions) { box.innerHTML = Hist.firstBox(); return; }
            var last = s.lastPracticeDate ? new Date(s.lastPracticeDate) : null;
            var current = last && (sameDay(last, Date.now()) || sameDay(last, Date.now() - 86400000)) ? (s.dailyStreak || 0) : 0;
            var M = Hist.metric;
            box.innerHTML =
                Hist.row([M('Eyes closed', String(ms['eyes-closed'] || 0)), M('Eyes open', String(ms['eyes-open'] || 0))]) +
                Hist.row([M('Mirror', String(ms['mirror'] || 0)), M('Animated eyes', String(ms['animated-eyes'] || 0))]) +
                Hist.row([M('Total trainings', String(s.lifetimeSessions || 0)), M('Total questions', String(s.lifetimeQuestions || 0))]) +
                Hist.row([M('Lifetime streak', String(s.lifetimeStreak || 0)), M('Current streak', String(current))]) +
                Hist.row([M('Total training time', dur(s.lifetimeTime || 0))]) +
                '<div class="pf-practice" id="pfPractice"></div>' +
                (Hist.history().some(function (e) { return sameDay(e.date, Date.now()); }) ? '<p class="pf-note pf-note-tight">Great job! You practiced today.</p>' : '');
            Hist.practice($('pfPractice'));
        },
        daySecs: function (from, n) {
            var out = []; for (var i = 0; i < n; i++) out.push(0);
            Hist.history().forEach(function (e) {
                var t = new Date(e.date).getTime(); if (t < from) return;
                var idx = Math.floor((dayStart(t) - from) / 86400000 + 0.5);
                if (idx >= 0 && idx < n) out[idx] += e.time || 0;
            });
            return out;
        },
        practice: function (card) {
            var head = '<div class="pf-pr-head"><span class="pf-pr-title">YOUR PRACTICE</span><div class="pf-seg"><button type="button" data-v="0"' + (!Hist.monthView ? ' class="on"' : '') + '>Week</button><button type="button" data-v="1"' + (Hist.monthView ? ' class="on"' : '') + '>Month</button></div></div>';
            card.innerHTML = head + (Hist.monthView ? Hist.month() : Hist.week());
            card.querySelector('.pf-seg').addEventListener('click', function (e) {
                var b = e.target.closest('button'); if (!b) return;
                var m = b.dataset.v === '1'; if (m !== Hist.monthView) { Hist.monthView = m; if (m) Hist.monthOffset = 0; Hist.practice(card); }
            });
            var back = card.querySelector('.pf-mback'), fwd = card.querySelector('.pf-mfwd');
            if (back) back.addEventListener('click', function () { Hist.monthOffset--; Hist.practice(card); });
            if (fwd && !fwd.disabled) fwd.addEventListener('click', function () { Hist.monthOffset++; Hist.practice(card); });
        },
        week: function () {
            var from = cutoff(7), secs = Hist.daySecs(from, 7), max = Math.max.apply(null, secs.concat([1])), total = secs.reduce(function (a, b) { return a + b; }, 0);
            var html = '<p class="pf-pr-sub">' + esc(weekTotal(total)) + ' this week</p><div class="pf-bars">';
            for (var i = 0; i < 7; i++) {
                var v = secs[i], today = i === 6, h = v === 0 ? 4 : Math.max(8, Math.round(96 * v / max));
                var day = new Date(from + i * 86400000 + 3600000).toLocaleDateString(undefined, { weekday: 'narrow' });
                html += '<div class="pf-bar-col"><small>' + (v ? shortT(v) : '') + '</small><i class="' + (today ? 'today' : v ? 'has' : 'none') + '" style="height:' + h + 'px"></i><span class="' + (today ? 'today' : '') + '">' + esc(day) + '</span></div>';
            }
            return html + '</div>';
        },
        month: function () {
            var now = new Date(), shown = new Date(now.getFullYear(), now.getMonth() + Hist.monthOffset, 1);
            var y = shown.getFullYear(), m = shown.getMonth(), days = new Date(y, m + 1, 0).getDate();
            var from = new Date(y, m, 1).getTime(), secs = Hist.daySecs(from, days);
            var isThis = now.getFullYear() === y && now.getMonth() === m, todayD = isThis ? now.getDate() : -1;
            var canFwd = Hist.monthOffset < 0;
            var html = '<div class="pf-mnav"><button type="button" class="pf-mback" aria-label="Previous month">‹</button><b>' + esc(shown.toLocaleDateString(undefined, { month: 'long', year: 'numeric' })) + '</b><button type="button" class="pf-mfwd" aria-label="Next month"' + (canFwd ? '' : ' disabled') + '>›</button></div>';
            // weekday letters, Sunday first (the app follows the phone's first day of the week)
            html += '<div class="pf-cal pf-cal-head">';
            for (var d = 0; d < 7; d++) html += '<span>' + esc(new Date(2023, 0, 1 + d).toLocaleDateString(undefined, { weekday: 'narrow' })) + '</span>';
            html += '</div><div class="pf-cal">';
            var offset = new Date(y, m, 1).getDay(), practiced = 0;
            for (var k = 0; k < offset; k++) html += '<span></span>';
            for (var day = 1; day <= days; day++) {
                var v = secs[day - 1], shade = v <= 0 ? 0 : v < 300 ? 1 : v < 600 ? 2 : 3;
                if (shade) practiced++;
                var fut = Hist.monthOffset > 0 || (isThis && day > todayD);
                html += '<span><i class="s' + shade + (day === todayD ? ' today' : '') + (fut ? ' fut' : '') + '">' + day + '</i></span>';
            }
            html += '</div><p class="pf-pr-count">' + (practiced === 1 ? '1 day practiced this month' : practiced + ' days practiced this month') + '</p>';
            return html;
        },
        renderHistory: function (box) {
            var h = Hist.history();
            if (!h.length) { box.innerHTML = Hist.firstBox(); return; }
            var order = ['eyes-closed', 'eyes-open', 'mirror', 'animated-eyes', 'surprise', 'real-life'];
            var present = order.filter(function (m) { return h.some(function (e) { return e.mode === m; }); });
            if (Hist.filter && present.indexOf(Hist.filter) < 0) Hist.filter = null;
            var html = '';
            if (present.length > 1) {
                html += '<div class="pf-chips">' + [null].concat(present).map(function (m) {
                    return '<button type="button" class="pf-fchip' + (Hist.filter === m ? ' on' : '') + '" data-m="' + (m || '') + '">' + esc(m ? hLabel(m) : 'All') + '</button>';
                }).join('') + '</div>';
            }
            html += '<div class="pf-seg pf-seg-wide">' + ['7 days', '30 days', 'All time'].map(function (l, i) { return '<button type="button" data-r="' + i + '"' + (Hist.range === i ? ' class="on"' : '') + '>' + l + '</button>'; }).join('') + '</div>';
            var days = Hist.range === 0 ? 7 : Hist.range === 1 ? 30 : 0, cut = days ? cutoff(days) : 0;
            var items = h.filter(function (e) { return (!Hist.filter || e.mode === Hist.filter) && (!days || new Date(e.date).getTime() >= cut); });
            if (!items.length) {
                html += '<p class="pf-note">' + (Hist.range === 0 ? 'No trainings in the last 7 days.' : Hist.range === 1 ? 'No trainings in the last 30 days.' : 'No trainings in this mode yet.') + '</p>';
            } else {
                var wk = cutoff(7), train = items.filter(function (e) { return e.mode !== 'real-life'; }), M = Hist.metric;
                if (train.length) {
                    var week = 0, tot = 0;
                    train.forEach(function (e) { tot += e.time || 0; if (new Date(e.date).getTime() >= wk) week++; });
                    var life = (stats && stats.lifetimeSessions) || 0;
                    var shown = (Hist.range === 2 && !Hist.filter && life > train.length) ? life : train.length;
                    html += Hist.row([M('Trainings', String(shown)), M('Last 7 days', String(week)), M('Avg. length', dur(Math.floor(tot / train.length)))]);
                } else {
                    var pauses = 0, wp = 0;
                    items.forEach(function (e) { pauses += e.questions || 0; if (new Date(e.date).getTime() >= wk) wp += e.questions || 0; });
                    html += Hist.row([M('Days', String(items.length)), M('Pauses', String(pauses)), M('Last 7 days', String(wp))]);
                }
                items.forEach(function (e, i) {
                    var ms = new Date(e.date).getTime(), real = e.mode === 'real-life', surprise = e.mode === 'surprise' || real;
                    var q = e.questions || 0, rated = surprise ? 0 : (Extras.data.ratings[e.date] || 0);
                    var sub = real ? '<span>' + q + (q === 1 ? ' pause' : ' pauses') + '</span>'
                        : '<span>' + q + 'Q</span><span class="pf-sep">•</span>' + icon(I.CLOCK, 'pf-ic-xs') + '<span class="pf-tm">' + esc(dur(e.time || 0)) + '</span>' +
                          (e.threeWhy ? '<span class="pf-3y" title="3 Why challenge">3Y</span>' : '');
                    if (rated >= 1 && rated <= 3) sub += '<span class="pf-sep">•</span>' + icon(RATING[rated - 1][1], 'pf-ic-face') ;
                    html += '<button type="button" class="pf-hcard" data-i="' + i + '"><span class="pf-hc-left"><b>' + esc(fmtWhen(ms, surprise)) + '</b><span class="pf-hc-sub">' + sub + '</span></span>' +
                        '<span class="pf-mtag">' + esc(hLabel(e.mode)) + '</span></button>';
                });
            }
            box.innerHTML = html;
            box.querySelectorAll('.pf-fchip').forEach(function (b) { b.addEventListener('click', function () { Hist.filter = b.dataset.m || null; Hist.render(); }); });
            box.querySelectorAll('.pf-seg-wide button').forEach(function (b) { b.addEventListener('click', function () { var r = +b.dataset.r; if (r !== Hist.range) { Hist.range = r; Hist.render(); } }); });
            box.querySelectorAll('.pf-hcard').forEach(function (b) { b.addEventListener('click', function () { Detail.open(items[+b.dataset.i]); }); });
        }
    };
    // the website's tab switch and window open also drive the new panes
    var origSwitch = window.switchProgressTab;
    window.switchProgressTab = function (tab) { var r = origSwitch.apply(this, arguments); Hist.show(tab); return r; };
    var origShowProgress = window.showProgressModal;
    window.showProgressModal = async function () {
        var want = Hist.want || 'stats';
        Hist.want = null;
        var r = await origShowProgress.apply(this, arguments);
        window.switchProgressTab(want);
        return r;
    };

    // ================================================================ history: tap for details, ratings, export (HistoryActivity)
    function historyAll() { var h = []; safe(function () { h = (readLocalHistory(UID) || []).slice(); }); return h.sort(function (a, b) { return new Date(b.date) - new Date(a.date); }); }
    function decorateHistory() { safe(function () { if (document.getElementById('progressModal').style.display === 'flex') Hist.render(); }); return;
        var list = $('sessionHistoryList');
        if (!list) return;
        var all = historyAll();
        list.querySelectorAll('.history-card').forEach(function (card) {
            if (card._pf) return;
            var dateTxt = (card.querySelector('.history-date') || {}).textContent || '';
            var entry = all.filter(function (e) { return safe(function () { return formatHistoryDate(e.date); }) === dateTxt; })[0];
            if (!entry) return;
            card._pf = entry;
            card.setAttribute('role', 'button'); card.tabIndex = 0;
            var rt = Extras.data.ratings[entry.date];
            if (rt) card.querySelector('.history-card-left').insertAdjacentHTML('beforeend', '<span class="pf-felt">' + icon(RATING[rt - 1][1], 'pf-ic-sm') + RATING[rt - 1][0] + '</span>');
            card.addEventListener('click', function () { Detail.open(entry); });
            card.addEventListener('keydown', function (e) { if (e.key === 'Enter') Detail.open(entry); });
        });
    }
    var Detail = {
        open: function (e) {
            var mode = e.mode || '', real = mode === 'real-life', surprise = mode === 'surprise' || real;
            var trail = null;
            if (!surprise) { trail = el('span', 'pf-hicon pf-hicon-static', modeIcon(mode)); trail.setAttribute('aria-label', hLabel(mode)); }
            var pg = subPage('pfDetail', hLabel(mode), { trailing: trail });
            var ms = new Date(e.date).getTime();
            var when = new Date(ms).toLocaleDateString(undefined, { weekday: 'short', day: 'numeric', month: 'short', year: 'numeric' }) +
                (real ? '' : ', ' + new Date(ms).toLocaleTimeString(undefined, { hour: '2-digit', minute: '2-digit', hour12: false }));
            var cells = real ? [['Pauses', String(e.questions || 0)]] : [['Questions', String(e.questions || 0)], ['Time', dur(e.time || 0)]];
            if (!surprise) cells.push(['Deep Pause', e.deepPause ? 'On' : 'Off']);
            if (!surprise && 'threeWhy' in e) cells.push(['3 Why challenge', e.threeWhy ? 'On' : 'Off']);
            var html = '<p class="pf-dt-when">' + esc(when) + '</p><div class="pf-dt-grid">';
            cells.forEach(function (c, i) { html += '<div class="pf-card pf-cell' + (i === cells.length - 1 && cells.length % 2 ? ' pf-cell-full' : '') + '"><span>' + esc(c[0]) + '</span><b>' + esc(c[1]) + '</b></div>'; });
            html += '</div>';
            if (real && Array.isArray(e.at) && e.at.length) {
                html += '<div class="pf-card pf-cell pf-cell-full pf-mt8"><span>Times</span><b>' + e.at.slice().sort().map(function (t) { return esc(new Date(t).toLocaleTimeString(undefined, { hour: 'numeric', minute: '2-digit' })); }).join('<br>') + '</b></div>';
            }
            if (!surprise) {
                var rt = Extras.data.ratings[e.date] || 0;
                html += '<div class="pf-card pf-felt-card"><span>How it felt</span><div class="pf-felt-row">' + RATING.map(function (r, i) {
                    return '<button type="button" class="pf-felt-pill' + (rt === i + 1 ? ' on' : '') + '" data-v="' + (i + 1) + '" aria-pressed="' + (rt === i + 1) + '">' + icon(r[1], 'pf-ic-xs') + '<b>' + r[0] + '</b></button>';
                }).join('') + '</div></div><button type="button" class="pf-pill pf-again" id="pfRedo">Do again</button>';
            }
            pg.body.innerHTML = html;
            pg.body.querySelectorAll('.pf-felt-pill').forEach(function (b) {
                b.addEventListener('click', function () {
                    var v = +b.dataset.v;
                    Extras.change({ t: 'rate', date: e.date, v: v });
                    pg.body.querySelectorAll('.pf-felt-pill').forEach(function (x) { var on = +x.dataset.v === v; x.classList.toggle('on', on); x.setAttribute('aria-pressed', on); });
                    Hist.render();
                });
            });
            if ($('pfRedo')) $('pfRedo').addEventListener('click', function () {
                var webMode = mode === 'animated-eyes' ? 'mirror' : mode;
                if (MODE_NAMES[mode]) {
                    selectTrainingMode(webMode);
                    var camera = mode === 'mirror' || mode === 'animated-eyes';
                    if (!!mirrorMode !== camera) toggleMirrorMode();
                    if (!!mirrorEyesMode !== (mode === 'animated-eyes')) toggleMirrorEyes();
                }
                if (!!deepPauseMode !== !!e.deepPause) toggleDeepPause();
                Modes.sync();
                if (history.state && history.state.pfSub === 'pfDetail') history.back(); else pg.close();
                setTimeout(function () { safe(function () { hideProgressModal(); }); pfGo('training'); }, 30);
            });
        }
    };
    function exportHistory() {
        var range = 7, fmt = 'csv';
        var box = el('div', 'pf-export',
            '<div class="pf-st">Range</div><div class="pf-type-row" data-g="r"><button type="button" class="pf-type on" data-v="7">Last 7 days</button><button type="button" class="pf-type" data-v="30">Last 30 days</button><button type="button" class="pf-type" data-v="0">All time</button></div>' +
            '<div class="pf-st pf-mt">Export as</div><div class="pf-type-row" data-g="f"><button type="button" class="pf-type on" data-v="csv">CSV file</button><button type="button" class="pf-type" data-v="pdf">PDF summary</button></div>');
        box.addEventListener('click', function (e) {
            var b = e.target.closest('.pf-type'); if (!b) return;
            b.parentNode.querySelectorAll('.pf-type').forEach(function (x) { x.classList.toggle('on', x === b); });
            if (b.parentNode.dataset.g === 'r') range = +b.dataset.v; else fmt = b.dataset.v;
        });
        dialog('Export history', null, [{ label: 'Cancel', kind: 'soft' }, { label: 'Export', kind: 'primary', onClick: function () { doExport(range, fmt); } }], box);
    }
    function doExport(days, fmt) {
        var since = days ? Date.now() - days * 86400000 : 0;
        var rows = historyAll().filter(function (e) { return new Date(e.date).getTime() >= since; });
        if (!rows.length) return toast(days ? 'No trainings in the last ' + days + ' days' : 'No history to export yet');
        var label = function (e) { return hLabel(e.mode); };   // same names as the app's export ("Eyes closed")
        var felt = function (e) { var r = Extras.data.ratings[e.date]; return r ? RATING[r - 1][0] : 'Not rated'; };
        if (fmt === 'csv') {
            var csv = 'Date,Mode,Questions,Duration (seconds),Duration,Deep Pause,Felt\n' + rows.map(function (e) {
                return [new Date(e.date).toLocaleString(), label(e), e.questions || 0, e.time || 0, fmtDur(e.time || 0), e.deepPause ? 'Yes' : 'No', felt(e)]
                    .map(function (v) { v = String(v); return /[",\n]/.test(v) ? '"' + v.replace(/"/g, '""') + '"' : v; }).join(',');
            }).join('\n') + '\n';
            var a = el('a'); a.href = URL.createObjectURL(new Blob([csv], { type: 'text/csv' })); a.download = 'pauzfirst-history.csv';
            document.body.appendChild(a); a.click(); setTimeout(function () { URL.revokeObjectURL(a.href); a.remove(); }, 1000);
            toast('Saved to Downloads');
            return;
        }
        var total = rows.reduce(function (s, e) { return s + (e.time || 0); }, 0), qs = rows.reduce(function (s, e) { return s + (e.questions || 0); }, 0);
        var html = '<!DOCTYPE html><html><head><meta charset="utf-8"><title>PauzFirst history</title><style>body{font-family:Poppins,Segoe UI,Arial,sans-serif;color:#0F172A;margin:32px}h1{color:#1E90FF;margin:0}' +
            'p{color:#64748B}table{width:100%;border-collapse:collapse;margin-top:18px;font-size:13px}th,td{text-align:left;padding:8px;border-bottom:1px solid #E2E8F0}th{color:#64748B;font-size:11px;text-transform:uppercase;letter-spacing:1px}' +
            '.k{display:flex;gap:24px;margin-top:16px}.k div{background:#E6F1FF;border-radius:14px;padding:12px 16px}.k b{display:block;font-size:20px}</style></head><body>' +
            '<h1>PauzFirst</h1><p>' + (days ? 'Last ' + days + ' days' : 'All time') + ' · Generated ' + esc(new Date().toLocaleString()) + '</p>' +
            '<div class="k"><div><b>' + rows.length + '</b>Trainings</div><div><b>' + qs + '</b>Questions</div><div><b>' + esc(fmtDur(total)) + '</b>Time trained</div><div><b>' + ((stats && stats.dailyStreak) || 0) + '</b>Current streak</div></div>' +
            '<table><tr><th>Date</th><th>Mode</th><th>Questions</th><th>Duration</th><th>Felt</th></tr>' +
            rows.map(function (e) { return '<tr><td>' + esc(new Date(e.date).toLocaleString()) + '</td><td>' + esc(label(e)) + (e.deepPause ? ' · Deep Pause' : '') + '</td><td>' + (e.questions || 0) + '</td><td>' + esc(fmtDur(e.time || 0)) + '</td><td>' + esc(felt(e)) + '</td></tr>'; }).join('') +
            '</table><script>window.onload=function(){setTimeout(function(){window.print()},300)}<\/script></body></html>';
        var w = window.open('', '_blank');
        if (!w) return toast('Allow pop-ups to create the PDF.');
        w.document.open(); w.document.write(html); w.document.close();
        toast('Choose "Save as PDF" in the print window');
    }

    // ================================================================ profile (ProfileActivity)
    var Profile = {
        open: async function () {
            var pg = subPage('pfProfile', 'Your account', { cls: 'pf-profile' });
            var u = await currentUser();
            var meta = (u && u.user_metadata) || {};
            var name = meta.name || meta.full_name || '', email = (u && u.email) || '', provider = (u && u.app_metadata && u.app_metadata.provider) || 'email';
            var pic = meta.avatar_url || meta.picture || '';
            var hero = el('div', 'pf-profile-hero');
            hero.innerHTML = '<div class="pf-avatar">' + (pic ? '<img alt="" referrerpolicy="no-referrer">' : esc((name || email || '?').trim().charAt(0).toUpperCase())) + '</div>' +
                '<b class="pf-pname"></b><span class="pf-pmail"></span><small>' + (provider === 'google' ? 'Signed in with Google' : 'Signed in with email') + '</small>' +
                '<button type="button" class="pf-chip" id="pfEditName">Edit name</button>';
            if (pic) hero.querySelector('img').src = pic;
            hero.querySelector('.pf-pname').textContent = name || 'Your name';
            hero.querySelector('.pf-pmail').textContent = email;
            pg.top.appendChild(hero);
            pg.body.innerHTML =
                (provider === 'email' ? '<div class="pf-card"><div class="pf-st">Change password</div>' +
                '<input class="pf-field" id="pfPw1" type="password" placeholder="New password" autocomplete="new-password">' +
                '<input class="pf-field" id="pfPw2" type="password" placeholder="Confirm new password" autocomplete="new-password">' +
                '<button type="button" class="pf-pill pf-mt" id="pfPwSave">Change password</button></div>' : '') +
                '<a class="pf-card pf-link-card pf-mt" href="contact.html"><span><b>Contact us</b><small>Questions, feedback or data requests</small></span><i>›</i></a>' +
                '<a class="pf-card pf-link-card pf-mt" href="privacy.html"><span><b>Privacy policy</b><small>What we keep and why</small></span><i>›</i></a>' +
                '<button type="button" class="pf-pill pf-pill-soft pf-mt" id="pfLogout2">Log out</button>' +
                '<button type="button" class="pf-pill pf-pill-red pf-mt" id="pfDelete">Delete account</button>';
            $('pfEditName').addEventListener('click', function () {
                var f = field('Your name', name);
                dialog('Your name', null, [{ label: 'Cancel', kind: 'soft' }, { label: 'Save', kind: 'primary', onClick: function () {
                    var v = f.value.trim();
                    if (!v) { toast('Type your name.'); return true; }
                    sb.auth.updateUser({ data: { name: v } }).then(function (r) {
                        if (r.error) return toast('Could not save. Check your connection and try again.');
                        name = v; hero.querySelector('.pf-pname').textContent = v; toast('Saved');
                    });
                } }], f);
            });
            if ($('pfPwSave')) $('pfPwSave').addEventListener('click', async function () {
                var a = $('pfPw1').value, b = $('pfPw2').value;
                if (a.length < 6) return dialog('Password too short', 'Your password needs at least 6 characters.');
                if (a !== b) return dialog('Passwords do not match', 'Type the same password twice.');
                this.disabled = true;
                var r = await sb.auth.updateUser({ password: a });
                this.disabled = false;
                if (r.error) return dialog('Could not change the password', /fetch|network/i.test(r.error.message) ? 'No internet connection. Please try again.' : 'Please try again in a moment.');
                $('pfPw1').value = ''; $('pfPw2').value = '';
                dialog('Password changed', 'Use your new password next time you log in.');
            });
            $('pfLogout2').addEventListener('click', function () {
                dialog('Logout?', 'This stops any running training and signs you out.', [{ label: 'Cancel', kind: 'soft' }, { label: 'Logout', kind: 'danger', onClick: function () { pg.close(); supabaseSignOut(); } }]);
            });
            $('pfDelete').addEventListener('click', function () {
                dialog('Delete account?', 'This permanently deletes your account and all its data: question lists, presets, history and stats. It can\'t be undone.', [
                    { label: 'Cancel', kind: 'soft' },
                    { label: 'Delete', kind: 'danger', onClick: async function (close, btn) {
                        btn.disabled = true;
                        var r = await sb.rpc('delete_my_account');
                        if (r.error) { btn.disabled = false; close(); dialog('Could not delete account', /fetch|network/i.test(r.error.message) ? 'Check your connection and try again.' : 'Please try again in a moment, or contact us.'); return true; }
                        close(); pg.close();
                        try { localStorage.clear(); } catch (e) {}
                        await supabaseSignOut();
                        return true;
                    } }
                ]);
            });
        }
    };

    // ================================================================ sync: export / import file (DataBackup.kt, same file format)
    var Sync = {
        open: function () {
            dialog('Sync', 'Export saves your presets and question lists in one file you can share. Your history, ratings and used questions are never included. Import adds a file like that: presets and lists you already have stay as they are.', [
                { label: 'Export', kind: 'primary', onClick: Sync.exportFile },
                { label: 'Import', kind: 'soft', onClick: Sync.importFile },
                { label: 'Cancel', kind: 'soft' }
            ]);
        },
        exportFile: function () {
            var lists = Object.keys(savedQuestionLists).filter(function (k) { return k !== '__ai_custom_session__'; })
                .map(function (k) { return { name: k, questions: savedQuestionLists[k] }; });
            var file = { app: 'pauzfirst', version: 3, exportedAt: Date.now(), data: { presets: Presets.all() }, lists: lists };
            var a = el('a'); a.href = URL.createObjectURL(new Blob([JSON.stringify(file, null, 2)], { type: 'application/json' }));
            a.download = 'pauzfirst-backup-' + new Date().toISOString().slice(0, 10) + '.json';
            document.body.appendChild(a); a.click(); setTimeout(function () { URL.revokeObjectURL(a.href); a.remove(); }, 1000);
            toast('Saved to Downloads');
        },
        importFile: function () {
            var inp = el('input'); inp.type = 'file'; inp.accept = 'application/json,.json';
            inp.addEventListener('change', function () {
                var f = inp.files && inp.files[0]; if (!f) return;
                var rd = new FileReader();
                rd.onload = async function () {
                    var j; try { j = JSON.parse(rd.result); } catch (e) { j = null; }
                    if (!j || j.app !== 'pauzfirst' || !j.data) return dialog('Not a PauzFirst file', 'Pick a file made with Export, in the app or on the website.');
                    var addedP = 0, addedL = 0;
                    (j.data.presets || []).forEach(function (p) { if (p && p.name && !Presets.all().some(function (x) { return x.name === p.name; })) { Extras.change({ t: 'addIfMissing', p: p }); addedP++; } });
                    var lists = j.lists || [];
                    for (var i = 0; i < lists.length; i++) {
                        var o = lists[i]; if (!o || !o.name || savedQuestionLists[o.name]) continue;
                        var qs = (o.questions || []).map(qText).filter(Boolean); if (!qs.length) continue;
                        savedQuestionLists[o.name] = qs; await saveList(o.name, qs); addedL++;
                    }
                    displaySavedLists();
                    dialog('Imported', 'Added ' + addedP + ' preset' + (addedP === 1 ? '' : 's') + ' and ' + addedL + ' question list' + (addedL === 1 ? '' : 's') + '.');
                };
                rd.readAsText(f);
            });
            inp.click();
        }
    };

    // ================================================================ login: Login | Sign up, and Google (LoginActivity)
    var Auth = {
        signUp: false,
        init: function () {
            var login = $('authLogin');
            if (!login || $('pfAuthTabs')) return;
            var tabs = el('div', 'pf-toggle pf-auth-tabs', '<button type="button" data-m="0" class="on">Login</button><button type="button" data-m="1">Sign up</button>');
            tabs.id = 'pfAuthTabs';
            login.insertBefore(tabs, login.firstChild);
            var name = field('Your name'); name.id = 'pfSignName'; name.className = 'auth-input pf-name-input'; name.autocomplete = 'name';
            var conf = field('Confirm password', '', 'password'); conf.id = 'pfSignConfirm'; conf.className = 'auth-input'; conf.autocomplete = 'new-password';
            var email = $('loginEmail'), pw = $('loginPassword');
            email.parentNode.insertBefore(name, email);
            var pwWrap = pw.parentNode.classList.contains('auth-input') ? pw : pw.parentNode;
            pwWrap.parentNode.insertBefore(conf, pwWrap.nextSibling);
            var or = el('div', 'pf-or', '<span>or</span>');
            var g = el('button', 'pf-google', '<svg viewBox="0 0 48 48" width="22" height="22" aria-hidden="true"><path fill="#EA4335" d="M24 9.5c3.54 0 6.71 1.22 9.21 3.6l6.85-6.85C35.9 2.38 30.47 0 24 0 14.62 0 6.51 5.38 2.56 13.22l7.98 6.19C12.43 13.72 17.74 9.5 24 9.5z"/><path fill="#4285F4" d="M46.98 24.55c0-1.57-.15-3.09-.38-4.55H24v9.02h12.94c-.58 2.96-2.26 5.48-4.78 7.18l7.73 6c4.51-4.18 7.09-10.36 7.09-17.65z"/><path fill="#FBBC05" d="M10.53 28.59c-.48-1.45-.76-2.99-.76-4.59s.27-3.14.76-4.59l-7.98-6.19C.92 16.46 0 20.12 0 24c0 3.88.92 7.54 2.56 10.78l7.97-6.19z"/><path fill="#34A853" d="M24 48c6.48 0 11.93-2.13 15.89-5.81l-7.73-6c-2.15 1.45-4.92 2.3-8.16 2.3-6.26 0-11.57-4.22-13.47-9.91l-7.98 6.19C6.51 42.62 14.62 48 24 48z"/></svg><span>Login with Google</span>');
            g.type = 'button'; g.id = 'pfGoogleBtn';
            var btn = $('loginBtn'), msg = $('loginMsg');
            msg.parentNode.insertBefore(or, msg.nextSibling);
            or.parentNode.insertBefore(g, or.nextSibling);
            tabs.addEventListener('click', function (e) { var b = e.target.closest('button'); if (b) Auth.mode(b.dataset.m === '1'); });
            g.addEventListener('click', Auth.google);
            // the Login button does Sign up while that tab is on
            btn.addEventListener('click', function (e) { if (Auth.signUp) { e.stopImmediatePropagation(); e.preventDefault(); Auth.doSignUp(); } }, true);
            [pw, conf, name].forEach(function (f) { f.addEventListener('keydown', function (e) { if (Auth.signUp && e.key === 'Enter') { e.stopImmediatePropagation(); e.preventDefault(); Auth.doSignUp(); } }, true); });
            Auth.mode(false);
        },
        mode: function (up) {
            Auth.signUp = up;
            $('pfAuthTabs').querySelectorAll('button').forEach(function (b) { b.classList.toggle('on', (b.dataset.m === '1') === up); });
            $('pfSignName').style.display = up ? '' : 'none';
            $('pfSignConfirm').style.display = up ? '' : 'none';
            $('loginBtn').textContent = up ? 'Create account' : 'Login';
            $('pfGoogleBtn').querySelector('span').textContent = up ? 'Sign up with Google' : 'Login with Google';
            var t = document.querySelector('#authLogin .auth-form-title'), s = document.querySelector('#authLogin .auth-form-sub');
            if (t) t.textContent = up ? 'Create your account' : 'Welcome Back';
            if (s) s.textContent = up ? 'Train to pause and think before you respond' : 'Login to get access to training';
            $('loginPassword').autocomplete = up ? 'new-password' : 'current-password';
            var forgot = document.querySelector('#authLogin .auth-footer-text');
            if (forgot) forgot.style.display = up ? 'none' : '';
            $('loginMsg').textContent = '';
        },
        doSignUp: async function () {
            var msg = $('loginMsg'), btn = $('loginBtn');
            var name = $('pfSignName').value.trim(), email = $('loginEmail').value.trim(), pw = $('loginPassword').value, c = $('pfSignConfirm').value;
            msg.style.color = '';
            if (!name) return (msg.textContent = 'Please enter your name.');
            if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email)) return (msg.textContent = 'Please enter a valid email address.');
            if (pw.length < 6) return (msg.textContent = 'Your password needs at least 6 characters.');
            if (pw !== c) return (msg.textContent = 'Passwords do not match.');
            btn.disabled = true; msg.textContent = 'Creating your account…';
            try {
                var r = await sb.auth.signUp({ email: email, password: pw, options: { data: { name: name, registered: true }, emailRedirectTo: location.origin + location.pathname } });
                btn.disabled = false;
                if (r.error) { msg.textContent = /registered|exists/i.test(r.error.message) ? 'That email already has an account. Log in instead.' : 'Could not create the account. Please try again.'; return; }
                if (r.data && r.data.session) { hideOverlay(); return; }
                msg.style.color = '#16a34a';
                msg.textContent = 'Check your email: we sent a link to confirm your account. Then log in.';
            } catch (e) { btn.disabled = false; msg.textContent = 'Could not connect. Check your internet and try again.'; }
        },
        google: async function () {
            var msg = $('loginMsg');
            try {
                var r = await sb.auth.signInWithOAuth({ provider: 'google', options: { redirectTo: location.origin + location.pathname } });
                if (r.error) msg.textContent = 'Google sign-in is not available right now. Please use email.';
            } catch (e) { msg.textContent = 'Could not connect. Check your internet and try again.'; }
        }
    };

    // ================================================================ start
    function onSignedIn() {
        uid().then(function (id) {
            if (!id) return;
            Extras.loadLocal();
            Extras.emit();
            migrateLocalProfiles();
            Extras.pull().then(function () { Extras.emit(); });
            Bank.refresh(false).catch(function () {});
        });
    }
    Extras.listeners.push(function () {
        if (document.body.classList.contains('pf-page-home')) renderRecent();
        if (ListPage.built && ListPage.tab === 0) ListPage.renderPresets();
    });
    // the bottom bar's History opens Training History, like the app; "View progress" on Home opens Training Stats
    document.addEventListener('click', function (e) {
        if (e.target.closest && e.target.closest('#pfNav .pf-tab[data-page="history"]')) Hist.want = 'history';
    }, true);
    // the bottom bar's List tab opens the app-style List page
    document.addEventListener('click', function (e) {
        var t = e.target.closest && e.target.closest('#pfNav .pf-tab[data-page="list"]');
        if (t) ListPage.build();
    }, true);
    safe(function () {
        // the website's own "My Saved Lists" also opens the app-style page
        var modal = $('listsFullscreenModal');
        var wasOpen = false;
        new MutationObserver(function () {
            var open = modal.style.display === 'flex';
            if (open && !wasOpen) {
                // every way in (List tab, "My Saved Lists" on Training...) starts on the Question list, like the app
                ListPage.build();
                ListPage.show(ListPage.want == null ? 1 : ListPage.want);
                ListPage.want = null;
                ListPage.tagCards();
            }
            wasOpen = open;
        }).observe(modal, { attributes: true, attributeFilter: ['style'] });
        var hist = $('sessionHistoryList');
        if (hist) new MutationObserver(decorateHistory).observe(hist, { childList: true });
        var ph = document.querySelector('#progressModal .pf-header .pf-side:first-child');
        var right = document.querySelector('#progressModal .progress-modal-header > div');
        if (right && !$('pfExportBtn')) {
            var ex = el('button', 'pf-hicon', icon(I.UPLOAD)); ex.type = 'button'; ex.id = 'pfExportBtn'; ex.setAttribute('aria-label', 'Export history');
            ex.addEventListener('click', exportHistory);
            var slot = el('span', 'pf-side pf-right pf-export-slot'); slot.appendChild(ex);
            right.parentNode.appendChild(slot);
        }
    });
    // the start button always reads "Start training", like the app (the website switched it to "Start Again")
    safe(function () {
        var sb2 = $('startBtn');
        var fix = function () { if (/start again|start training/.test(sb2.textContent.toLowerCase()) && sb2.textContent.trim() !== 'Start training') sb2.innerHTML = '<i class="fas fa-play"></i> Start training'; };
        new MutationObserver(fix).observe(sb2, { childList: true, subtree: true, characterData: true });
        fix();
    });
    window.PFX = { renderRecent: renderRecent };
    Theme.apply();
    if (window.matchMedia) safe(function () { matchMedia('(prefers-color-scheme: dark)').addEventListener('change', Theme.apply); });
    Modes.init();
    Cues.init();
    Rec.init();
    initHome();
    Auth.init();
    var ov = $('authOverlay');
    function authState() { if (ov && ov.classList.contains('hidden')) onSignedIn(); else { UID = null; } }
    if (ov) new MutationObserver(authState).observe(ov, { attributes: true, attributeFilter: ['class'] });
    authState();



})();
