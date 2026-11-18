/*
 * PauzFirst website: app layout.
 *
 * Gives the website the same layout as the Android app (blue curved header, stat tiles, bottom bar with
 * Home / Training / List / History) WITHOUT changing how the website works:
 *  - no element ids are renamed and no existing function is replaced;
 *  - existing elements are only moved into new containers or restyled (see app-skin.css);
 *  - every button here just calls the website's own functions (showListsModal, showProgressModal, ...).
 * If anything in here fails, the page keeps working with its old layout.
 */
(function () {
    'use strict';

    // Same icon shapes as the app (Icons.kt), drawn the same way (24x24, round 2.2 stroke)
    var I = {
        HOME: 'M3 11l9-8 9 8 M5 10v10h14V10',
        TRAIN: 'M5 5h3v14H5z M13 5l7 7-7 7z',
        LIST: 'M8 6h13 M8 12h13 M8 18h13 M3 6h.01 M3 12h.01 M3 18h.01',
        CHART: 'M4 20V10 M10 20V4 M16 20v-7 M22 20H2',
        LOGOUT: 'M9 21H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h4 M16 17l5-5-5-5 M21 12H9',
        SAVE: 'M19 21H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h11l5 5v11a2 2 0 0 1-2 2z M17 21v-8H7v8 M7 3v5h8',
        CLOCK: 'M12 2a10 10 0 1 0 0 20 10 10 0 0 0 0-20z M12 6v6l4 2',
        PLUS: 'M12 5v14 M5 12h14',
        DOWNLOAD: 'M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4 M7 10l5 5 5-5 M12 15V3',
        SHARE: 'M18 8a3 3 0 1 0 0-6 3 3 0 0 0 0 6z M6 15a3 3 0 1 0 0-6 3 3 0 0 0 0 6z M18 22a3 3 0 1 0 0-6 3 3 0 0 0 0 6z M8.6 13.5l6.8 4 M15.4 6.5l-6.8 4',
        BACK: 'M15 18l-6-6 6-6'
    };
    function icon(path, cls) {
        return '<svg class="pf-ic ' + (cls || '') + '" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2" ' +
            'stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="' + path + '"/></svg>';
    }
    function el(tag, cls, html) {
        var e = document.createElement(tag);
        if (cls) e.className = cls;
        if (html != null) e.innerHTML = html;
        return e;
    }
    function $(id) { return document.getElementById(id); }
    function txt(s) {
        return String(s == null ? '' : s).replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/"/g, '&quot;');
    }
    function safe(fn) {
        try { return fn(); } catch (e) { if (window.console) console.warn('[app layout]', e); }
    }

    var body = document.body;
    var app = $('appContainer');
    if (!app || !$('configPanel')) return;   // not the page we expect: leave it alone

    body.classList.add('pf-app');

    // ---------------------------------------------------------------- pages
    var page = 'home';          // home | training | list | history
    var mainPage = 'home';      // last of home / training (where List and History return to)
    var fitters = [];           // resize the stepper numbers (see stepper)

    function isTraining() { return app.classList.contains('training-active'); }
    function isOpen(id) { var m = $(id); return !!m && m.style.display === 'flex'; }

    function setPage(p) {
        page = p;
        if (p === 'home' || p === 'training') mainPage = p;
        body.classList.remove('pf-page-home', 'pf-page-training', 'pf-page-list', 'pf-page-history');
        body.classList.add('pf-page-' + p);
        var tabs = document.querySelectorAll('#pfNav .pf-tab');
        for (var i = 0; i < tabs.length; i++) {
            var on = tabs[i].getAttribute('data-page') === p;
            tabs[i].classList.toggle('on', on);
            if (on) tabs[i].setAttribute('aria-current', 'page'); else tabs[i].removeAttribute('aria-current');
        }
        if (p === 'home') safe(renderHome);
        if (p === 'training') fitters.forEach(function (f) { safe(f); });
    }

    /** Open a tab, like tapping it in the app's bottom bar. */
    function go(p) {
        if (isTraining()) return;
        if (p === page && p !== 'list' && p !== 'history') {
            if (p === 'home') safe(renderHome);      // tapping Home again refreshes it
            return;
        }
        if (p === 'home' || p === 'training') {
            if (isOpen('listsFullscreenModal')) safe(function () { hideListsModal(); });
            if (isOpen('progressModal')) safe(function () { hideProgressModal(); });
            setPage(p);
            window.scrollTo(0, 0);
        } else if (p === 'list') {
            if (isOpen('progressModal')) safe(function () { hideProgressModal(); });
            setPage('list');
            if (!isOpen('listsFullscreenModal')) safe(function () { showListsModal(); });
        } else if (p === 'history') {
            if (isOpen('listsFullscreenModal')) safe(function () { hideListsModal(); });
            setPage('history');
            if (!isOpen('progressModal')) safe(function () { showProgressModal(); });
        }
    }
    window.pfGo = go;

    // List and History are the website's own full-screen windows. Whenever one opens or closes (from the bottom
    // bar, the "My Saved Lists" card, the phone's back button...), the bottom bar follows.
    function syncFromModals() {
        if (isOpen('progressModal')) { if (page !== 'history') setPage('history'); return; }
        if (isOpen('listsFullscreenModal')) { if (page !== 'list') setPage('list'); return; }
        if (page === 'list' || page === 'history') setPage(mainPage);
    }
    ['listsFullscreenModal', 'progressModal'].forEach(function (id) {
        var m = $(id);
        if (m) new MutationObserver(syncFromModals).observe(m, { attributes: true, attributeFilter: ['style'] });
    });
    // The progress window is moved to the end of the page when it opens; keep watching it there too.
    new MutationObserver(function () { body.classList.toggle('pf-running', isTraining()); })
        .observe(app, { attributes: true, attributeFilter: ['class'] });

    // Phone back button on Training goes Home, like the app
    window.addEventListener('popstate', function () {
        setTimeout(function () {
            if (page === 'training' && !isTraining() && !isOpen('listsFullscreenModal') && !isOpen('progressModal')) setPage('home');
        }, 0);
    });

    // ---------------------------------------------------------------- bottom bar
    var nav = el('nav', 'pf-nav');
    nav.id = 'pfNav';
    nav.setAttribute('aria-label', 'Main');
    [['home', 'Home', I.HOME], ['training', 'Training', I.TRAIN], ['list', 'List', I.LIST], ['history', 'History', I.CHART]]
        .forEach(function (t) {
            var b = el('button', 'pf-tab', icon(t[2]) + '<span>' + t[1] + '</span>');
            b.type = 'button';
            b.setAttribute('data-page', t[0]);
            b.addEventListener('click', function () {
                if (t[0] === 'training' && page !== 'training') history.pushState({ pf: 'training' }, '');
                go(t[0]);
            });
            nav.appendChild(b);
        });
    body.appendChild(nav);

    // ---------------------------------------------------------------- Home: blue hero with stat tiles
    var home = el('section', 'pf-home');
    home.id = 'pfHome';
    home.innerHTML =
        '<div class="pf-hero">' +
        '  <div class="pf-hero-top">' +
        '    <div class="pf-brand"><img src="logo-badge.png" alt="" width="34" height="30"><span>PAUZFIRST</span></div>' +
        '    <button type="button" class="pf-hicon pf-logout" id="pfLogoutBtn" aria-label="Logout">' + icon(I.LOGOUT) + '</button>' +
        '  </div>' +
        '  <div class="pf-tiles">' +
        '    <div class="pf-tile"><b id="pfTileSessions">0</b><span>Trainings</span></div>' +
        '    <div class="pf-tile"><b id="pfTileQuestions">0</b><span>Questions</span></div>' +
        '    <div class="pf-tile"><b id="pfTileStreak">0</b><span>Day streak</span></div>' +
        '  </div>' +
        '  <div class="pf-hero-extra" id="pfHeroExtra"></div>' +
        '  <button type="button" class="pf-chip" id="pfProgressChip">View progress&nbsp;&nbsp;›</button>' +
        '</div>' +
        '<div class="pf-card pf-first" id="pfFirstRun" hidden><b>Start your first training</b>' +
        '<p>Open Training, pick a mode and press Start training. Your numbers, streak and history will show up here after your first one.</p></div>' +
        '<div class="pf-goal-slot" id="pfGoalSlot"></div>' +
        '<div class="pf-recent-head"><span class="pf-st">' + icon(I.CLOCK, 'pf-st-ic') + 'Recent</span>' +
        '  <button type="button" class="pf-mini-pill" id="pfPresetsPill">Presets&nbsp;&nbsp;›</button></div>' +
        '<div class="pf-recent" id="pfRecent"></div>';
    app.insertBefore(home, app.firstChild);

    var goal = $('goalCard');
    if (goal) $('pfGoalSlot').appendChild(goal);

    $('pfProgressChip').addEventListener('click', function () { go('history'); });
    $('pfPresetsPill').addEventListener('click', function () { var b = $('settingsListBtn'); if (b) b.click(); });
    $('pfLogoutBtn').addEventListener('click', function () {
        dialog('Logout?', 'This stops any running training and signs you out.', 'Logout', function () {
            safe(function () { supabaseSignOut(); });
        });
    });

    // Stat tiles copy the website's own numbers (filled by updateProgressDisplay)
    function copyStats() {
        var map = { pfTileSessions: 'lifetimeSessions', pfTileQuestions: 'lifetimeQuestions', pfTileStreak: 'dailyStreak' };
        Object.keys(map).forEach(function (k) {
            var src = $(map[k]);
            if (src && $(k)) $(k).textContent = src.textContent.trim() || '0';
        });
        var t = $('lifetimeTime'), s = $('lifetimeStreak');
        $('pfHeroExtra').textContent = (t ? t.textContent.trim() + ' trained' : '') +
            (s && s.textContent.trim() !== '0' ? '  ·  best streak ' + s.textContent.trim() : '');
        var none = !$('lifetimeSessions') || ($('lifetimeSessions').textContent.trim() === '0');
        $('pfFirstRun').hidden = !none;
    }
    ['lifetimeSessions', 'lifetimeQuestions', 'dailyStreak', 'lifetimeTime', 'lifetimeStreak'].forEach(function (id) {
        var e = $(id);
        if (e) new MutationObserver(copyStats).observe(e, { childList: true, characterData: true, subtree: true });
    });

    // Recent presets = the website's saved settings, newest first. Tap one: load it and open Training.
    function renderHome() {
        copyStats();
        var box = $('pfRecent');
        // presets are kept on the account (app-features.js), the same ones the app shows
        if (window.PFX && window.PFX.renderRecent) { window.PFX.renderRecent(box); return; }
        var list = [];
        safe(function () { list = loadProfiles() || []; });
        box.innerHTML = '';
        if (!list.length) {
            box.appendChild(el('p', 'pf-note', 'No presets yet. Set things up on Training, then tap the save icon.'));
            return;
        }
        list.slice(0, 3).forEach(function (p) {
            var meta = '';
            safe(function () { meta = profileMeta(p.settings || {}); });
            var c = el('button', 'pf-card pf-preset',
                '<span class="pf-preset-top"><b>' + txt(p.name) + '</b><i>›</i></span><span class="pf-preset-meta">' + txt(meta) + '</span>');
            c.type = 'button';
            c.addEventListener('click', function () {
                safe(function () { applySettingsProfile(p.settings); });
                history.pushState({ pf: 'training' }, '');
                go('training');
            });
            box.appendChild(c);
        });
    }
    var plist = $('settingsListScroll');
    if (plist) new MutationObserver(function () { if (page === 'home') safe(renderHome); }).observe(plist, { childList: true });

    // ---------------------------------------------------------------- Training page
    var trainHead = el('div', 'pf-header');
    trainHead.id = 'pfTrainHeader';
    trainHead.innerHTML = '<span class="pf-side"></span><h2>Training</h2>' +
        '<span class="pf-side pf-right"><button type="button" class="pf-hicon" id="pfSaveBtn" aria-label="Save settings as a preset">' +
        icon(I.SAVE) + '</button></span>';
    var panel = $('configPanel');
    panel.parentNode.insertBefore(trainHead, panel);
    $('pfSaveBtn').addEventListener('click', function () { var b = $('setDefaultBtn'); if (b) b.click(); });

    safe(function () {
        var groups = panel.querySelectorAll(':scope > .config-group');
        var modeGroup = groups[0];
        var sourceGroup = $('sourceCardsGrid') ? $('sourceCardsGrid').closest('.config-group') : null;
        var numGroup = $('numQuestionsInput') ? $('numQuestionsInput').closest('.config-group') : null;
        var timesGroup = $('trainingTimesGroup');

        // "Questions from" first, as in the app
        if (sourceGroup) {
            sourceGroup.classList.add('pf-source-group');
            var st = sourceGroup.querySelector('.config-title');
            if (st) st.textContent = 'Questions from';
            panel.insertBefore(sourceGroup, panel.firstChild);
            var pres = $('settingsListBtn');
            if (pres && $('sourceCardsGrid')) {
                pres.classList.add('pf-presets-btn');
                pres.innerHTML = 'Presets';
                $('sourceCardsGrid').parentNode.insertBefore(pres, $('sourceCardsGrid'));
            }
        }
        if (modeGroup) modeGroup.classList.add('pf-mode-group');

        // Timing card: Questions, Pause and Respond as  "Label   (−) value (+)"  rows
        if (numGroup && timesGroup) {
            var label = el('div', 'pf-group-label pf-timing-label', 'Timing');
            var card = el('div', 'config-group pf-timing');
            card.id = 'pfTimingCard';
            panel.insertBefore(label, modeGroup ? modeGroup.nextSibling : panel.firstChild);
            panel.insertBefore(card, label.nextSibling);
            card.appendChild(stepper('Questions', $('numQuestionsInput'), 1, 50, ''));
            var times = el('div', 'pf-times');
            times.appendChild(stepper('Pause', $('pauseTimeInput'), 1, 60, 's'));
            times.appendChild(stepper('Respond', $('answerTimeInput'), 1, 60, 's'));
            timesGroup.classList.add('pf-times-group');
            timesGroup.appendChild(times);         // still inside #trainingTimesGroup, so Deep Pause hides it as before
            card.appendChild(timesGroup);
            numGroup.classList.add('pf-hidden-group');
        }

        var est = $('trainingTimeEstimate');
        if (est) { var l = est.querySelector('.label'); if (l) l.textContent = 'Training time'; }
    });

    function stepper(name, input, min, max, unit) {
        var row = el('div', 'pf-stepper');
        row.innerHTML = '<span class="pf-step-name">' + name + '</span>' +
            '<button type="button" class="pf-step-btn" aria-label="Less">−</button>' +
            '<span class="pf-step-val"></span>' +
            '<button type="button" class="pf-step-btn" aria-label="More">+</button>';
        var val = row.querySelector('.pf-step-val');
        var btns = row.querySelectorAll('.pf-step-btn');
        input.classList.add('pf-step-input');
        val.appendChild(input);                     // the real input stays, so saving and presets work unchanged
        if (unit) val.appendChild(el('span', 'pf-unit', unit));
        // keep the number snug against its "s", whatever its length (also when a preset fills it in)
        function fit() { input.style.width = Math.max(1, String(input.value).length) + 'ch'; }
        input.addEventListener('input', fit);
        input.addEventListener('change', fit);
        fitters.push(fit);
        fit();
        function change(d) {
            var v = parseInt(input.value, 10);
            if (isNaN(v)) v = min;
            v = Math.max(min, Math.min(max, v + d));
            input.value = v;
            input.dispatchEvent(new Event('input', { bubbles: true }));
            input.dispatchEvent(new Event('change', { bubbles: true }));
            fit();
        }
        btns[0].addEventListener('click', function () { change(-1); });
        btns[1].addEventListener('click', function () { change(1); });
        return row;
    }

    // Sticky "Start training" bar above the bottom bar
    var startBtn = $('startBtn');
    if (startBtn) {
        var bar = el('div', 'pf-startbar');
        bar.id = 'pfStartBar';
        app.appendChild(bar);
        bar.appendChild(startBtn);
    }

    // The save and presets pop-ups live inside the Training settings; move them to the page itself so they
    // also open from Home (they are fixed pop-ups, so nothing else changes)
    ['savePromptOverlay', 'settingsListOverlay'].forEach(function (id) { var o = $(id); if (o) body.appendChild(o); });

    // ---------------------------------------------------------------- List and History as app pages
    safe(function () {
        var lh = document.querySelector('#listsFullscreenModal .lists-fullscreen-header');
        if (lh) {
            lh.classList.add('pf-header');
            lh.innerHTML = '<span class="pf-side"><button type="button" class="pf-hicon" id="pfNewListBtn" aria-label="Create new list">' + icon(I.PLUS) + '</button></span>' +
                '<h2 class="lists-fullscreen-title">My questions</h2>' +
                '<span class="pf-side pf-right"><button type="button" class="pf-hicon" id="pfImportBtn" aria-label="Import list by code">' + icon(I.DOWNLOAD) + '</button></span>';
            $('pfNewListBtn').addEventListener('click', function () { var r = $('createListToggleModal'); if (r) r.click(); });
            $('pfImportBtn').addEventListener('click', function () { var r = $('importListToggle'); if (r) r.click(); });
        }
        var ph = document.querySelector('#progressModal .progress-modal-header');
        if (ph) {
            ph.classList.add('pf-header');
            var t = ph.querySelector('.progress-modal-title');
            if (t) t.textContent = 'Your progress';
            ph.insertBefore(el('span', 'pf-side'), ph.firstChild);
        }
    });

    // Tabs and sort buttons get their colours from inline styles set by the website; mirror them into a class
    function mirrorOn(id, isOn) {
        var b = $(id);
        if (!b) return;
        var f = function () { b.classList.toggle('pf-on', isOn(b)); };
        new MutationObserver(f).observe(b, { attributes: true, attributeFilter: ['style'] });
        f();
    }
    function white(b) { var c = (b.style.color || '').replace(/\s/g, ''); return c === 'white' || c === '#fff' || c === 'rgb(255,255,255)'; }
    function blue(b) { var c = (b.style.color || '').replace(/\s/g, '').toLowerCase(); return c === '#1e90ff' || c === 'rgb(30,144,255)'; }
    mirrorOn('tabStatsBtn', white);
    mirrorOn('tabHistoryBtn', white);
    mirrorOn('sortRecentBtn', function (b) { return blue(b) || !b.style.color; });
    mirrorOn('sortAlphaBtn', blue);

    // ---------------------------------------------------------------- Login screen like the app
    safe(function () {
        var ov = $('authOverlay');
        var form = ov && ov.querySelector('.auth-form-center');
        if (!form) return;
        var hero = el('div', 'pf-auth-hero',
            '<img src="app-icon.png" alt="" width="60" height="60"><div class="pf-auth-brand">PAUZFIRST</div>' +
            '<div class="pf-auth-tag">Pause and think before you respond</div>');
        ov.insertBefore(hero, ov.firstChild);
        form.classList.add('pf-auth-card');
        var login = $('authLogin');
        if (login) {
            var legal = el('p', 'pf-legal', '<a href="privacy.html">Privacy policy</a>&nbsp;&nbsp;·&nbsp;&nbsp;<a href="contact.html">Contact us</a>');
            login.appendChild(legal);
        }
    });
    function authWatch() { body.classList.toggle('pf-authed', $('authOverlay') && $('authOverlay').classList.contains('hidden')); }
    if ($('authOverlay')) new MutationObserver(authWatch).observe($('authOverlay'), { attributes: true, attributeFilter: ['class'] });
    authWatch();
    // After signing in or out, start on Home like the app
    if ($('authOverlay')) new MutationObserver(function () { if (!body.classList.contains('pf-authed')) setPage('home'); })
        .observe($('authOverlay'), { attributes: true, attributeFilter: ['class'] });

    // ---------------------------------------------------------------- small app-style dialog
    function dialog(title, message, okLabel, onOk) {
        var o = el('div', 'pf-dialog-back');
        o.innerHTML = '<div class="pf-dialog" role="dialog" aria-modal="true"><h3></h3><p></p>' +
            '<div class="pf-dialog-btns"><button type="button" class="pf-btn-soft">Cancel</button>' +
            '<button type="button" class="pf-btn-danger"></button></div></div>';
        o.querySelector('h3').textContent = title;
        o.querySelector('p').textContent = message;
        var ok = o.querySelector('.pf-btn-danger');
        ok.textContent = okLabel;
        o.querySelector('.pf-btn-soft').addEventListener('click', function () { o.remove(); });
        o.addEventListener('click', function (e) { if (e.target === o) o.remove(); });
        ok.addEventListener('click', function () { o.remove(); onOk(); });
        body.appendChild(o);
        ok.focus();
    }

    setPage('home');
})();
