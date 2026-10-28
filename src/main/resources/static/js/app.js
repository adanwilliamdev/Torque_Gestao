(function () {
  'use strict';

  var SEARCH_SVG = '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round"><circle cx="11" cy="11" r="8"/><path d="m21 21-4.35-4.35"/></svg>';

  // Confirmação antes de ações destrutivas: <form data-confirm="Tem certeza?">
  document.addEventListener('submit', function (event) {
    var message = event.target.getAttribute && event.target.getAttribute('data-confirm');
    if (message && !window.confirm(message)) {
      event.preventDefault();
    }
  });

  // Menu lateral no mobile.
  document.addEventListener('click', function (event) {
    if (event.target.closest('.menu-toggle')) {
      document.body.classList.toggle('nav-open');
    }
  });

  // ---- Dropdown de ações por linha (⋮) — delegação de eventos ----
  document.addEventListener('click', function (event) {
    var btn = event.target.closest('.menu-btn');
    var menu = btn && btn.closest('.row-menu');
    document.querySelectorAll('.row-menu.open').forEach(function (other) {
      if (other !== menu) other.classList.remove('open');
    });
    if (menu) {
      event.stopPropagation();
      menu.classList.toggle('open');
    }
  });
  document.addEventListener('keydown', function (event) {
    if (event.key === 'Escape') {
      document.querySelectorAll('.row-menu.open').forEach(function (menu) {
        menu.classList.remove('open');
      });
    }
  });


  // ---- Tema claro/escuro ----
  document.addEventListener('click', function (event) {
    if (!event.target.closest('.theme-toggle')) return;
    var next = document.documentElement.getAttribute('data-theme') === 'dark' ? 'light' : 'dark';
    document.documentElement.setAttribute('data-theme', next);
    try { localStorage.setItem('torque-theme', next); } catch (e) { /* ignora */ }
  });

  // ---- Mostrar/ocultar senha ----
  document.addEventListener('click', function (event) {
    var peek = event.target.closest('[data-peek]');
    if (!peek) return;
    var input = document.getElementById(peek.getAttribute('data-peek'));
    if (input) input.type = input.type === 'password' ? 'text' : 'password';
  });

  // ---- Toasts: transforma os alertas de sucesso/erro do topo em notificações ----
  var CHECK = '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="3" stroke-linecap="round" stroke-linejoin="round"><path d="M20 6 9 17l-5-5"/></svg>';
  var CROSS = '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="3" stroke-linecap="round"><path d="M18 6 6 18M6 6l12 12"/></svg>';

  function toast(kind, text) {
    var box = document.querySelector('.toasts');
    if (!box) {
      box = document.createElement('div');
      box.className = 'toasts';
      box.setAttribute('aria-live', 'polite');
      document.body.appendChild(box);
    }
    var el = document.createElement('div');
    el.className = 'toast ' + kind;
    el.setAttribute('role', kind === 'error' ? 'alert' : 'status');
    el.innerHTML = '<span class="t-icon">' + (kind === 'error' ? CROSS : CHECK) + '</span><span class="t-text"></span><span class="t-bar"></span>';
    el.querySelector('.t-text').textContent = text;
    box.appendChild(el);
    var close = function () {
      el.classList.add('leaving');
      setTimeout(function () { el.remove(); }, 300);
    };
    el.addEventListener('click', close);
    setTimeout(close, 5000);
  }

  // ---- Contagem animada dos números do dashboard ----
  function countUp(el) {
    var target = parseInt(el.textContent, 10);
    if (isNaN(target) || target === 0 || window.matchMedia('(prefers-reduced-motion: reduce)').matches) return;
    var start = null, dur = 900;
    function step(ts) {
      if (start === null) start = ts;
      var p = Math.min((ts - start) / dur, 1);
      el.textContent = Math.round(target * (1 - Math.pow(1 - p, 3)));
      if (p < 1) requestAnimationFrame(step);
    }
    el.textContent = '0';
    requestAnimationFrame(step);
  }

  // ---- Inicializações que dependem do conteúdo da página (reexecutadas após o modal) ----
  function init() {
    // Alertas de feedback viram toasts (sem JS continuam visíveis na página)
    document.querySelectorAll('.content > .alert.success, .content > .alert.error').forEach(function (alert) {
      toast(alert.classList.contains('error') ? 'error' : 'success', alert.textContent.trim());
      alert.remove();
    });

    // Dashboard: saudação, data e contadores
    var greeting = document.querySelector('[data-greeting]');
    if (greeting) {
      var h = new Date().getHours();
      greeting.textContent = h < 12 ? 'Bom dia' : (h < 18 ? 'Boa tarde' : 'Boa noite');
    }
    var today = document.querySelector('[data-today]');
    if (today) {
      today.textContent = new Date().toLocaleDateString('pt-BR', { weekday: 'long', day: 'numeric', month: 'long' });
    }
    document.querySelectorAll('[data-count]').forEach(countUp);

    // Filtra veículos pelo cliente escolhido
    var client = document.getElementById('clientId');
    var vehicle = document.querySelector('[data-vehicle-select]');
    if (client && vehicle) {
      var options = Array.prototype.slice.call(vehicle.querySelectorAll('option[data-client]'));
      var apply = function (reset) {
        var selected = client.value;
        options.forEach(function (option) {
          var visible = selected !== '' && option.getAttribute('data-client') === selected;
          option.hidden = !visible;
          option.disabled = !visible;
        });
        var current = vehicle.options[vehicle.selectedIndex];
        if (reset || !current || current.disabled) vehicle.value = '';
        vehicle.disabled = selected === '';
      };
      client.addEventListener('change', function () { apply(true); });
      apply(false);
    }

    // Busca + filtro em tabelas com [data-table]
    document.querySelectorAll('table[data-table]').forEach(function (table) {
      var name = table.getAttribute('data-table');
      var searchInput = document.querySelector('[data-search-table="' + name + '"]');
      var filterSelect = document.querySelector('[data-filter-table="' + name + '"]');
      if (!searchInput && !filterSelect) return;

      var tbody = table.tBodies[0];
      var cols = table.querySelectorAll('thead th').length;
      var noResults = document.createElement('tr');
      noResults.className = 'no-results';
      noResults.hidden = true;
      noResults.innerHTML = '<td colspan="' + cols + '" style="padding:0;border:none">' +
        '<div class="empty-state">' + SEARCH_SVG +
        '<h3>Nenhum resultado encontrado</h3>' +
        '<p>Ajuste a busca ou o filtro para encontrar o que procura.</p>' +
        '<button type="button" class="btn secondary sm" data-clear>Limpar filtros</button>' +
        '</div></td>';
      tbody.appendChild(noResults);

      function applyFilters() {
        var q = searchInput ? searchInput.value.toLowerCase().trim() : '';
        var f = filterSelect ? filterSelect.value : '';
        var rows = 0, shown = 0;
        tbody.querySelectorAll('tr').forEach(function (tr) {
          if (tr === noResults || tr.querySelector('.empty-state')) return;
          rows++;
          var textMatch = !q || tr.textContent.toLowerCase().indexOf(q) !== -1;
          var filterMatch = !f || (tr.getAttribute('data-filter') || '') === f;
          var visible = textMatch && filterMatch;
          tr.style.display = visible ? '' : 'none';
          if (visible) shown++;
        });
        noResults.hidden = !(rows > 0 && shown === 0);
      }

      noResults.querySelector('[data-clear]').addEventListener('click', function () {
        if (searchInput) searchInput.value = '';
        if (filterSelect) filterSelect.value = '';
        applyFilters();
        if (searchInput) searchInput.focus();
      });
      if (searchInput) searchInput.addEventListener('input', applyFilters);
      if (filterSelect) filterSelect.addEventListener('change', applyFilters);
    });
  }
  init();

  // ============================================================
  // Modal para formulários curtos: <a data-modal href="/mechanics/new">
  // Sem JS (ou em caso de erro) o link abre a página normalmente.
  // ============================================================
  var dialog = null;

  function ensureDialog() {
    if (dialog) return dialog;
    dialog = document.createElement('dialog');
    dialog.className = 'modal';
    dialog.setAttribute('aria-labelledby', 'modal-title');
    dialog.addEventListener('click', function (e) { if (e.target === dialog) dialog.close(); });
    document.body.appendChild(dialog);
    return dialog;
  }

  function parse(html) {
    return new DOMParser().parseFromString(html, 'text/html');
  }

  function renderForm(doc, fallbackUrl) {
    var form = doc.querySelector('main form.form-card');
    if (!form) return false;
    var title = doc.querySelector('main .page-header h1');
    var d = ensureDialog();
    d.innerHTML = '';
    var head = document.createElement('div');
    head.className = 'modal-head';
    head.innerHTML = '<div><h2 id="modal-title"></h2><div class="modal-sub"></div></div><button type="button" class="modal-close" aria-label="Fechar">&times;</button>';
    head.querySelector('h2').textContent = title ? title.textContent : '';
    var sub = doc.querySelector('main .page-header p');
    var subBox = head.querySelector('.modal-sub');
    if (sub) { subBox.appendChild(document.importNode(sub, true)); } else { subBox.remove(); }
    head.querySelector('.modal-close').addEventListener('click', function () { d.close(); });
    d.appendChild(head);
    d.appendChild(document.importNode(form, true));
    var cancel = d.querySelector('.form-actions a.btn.secondary');
    if (cancel) cancel.addEventListener('click', function (e) { e.preventDefault(); d.close(); });
    var first = d.querySelector('input:not([type=hidden]), select, textarea');
    if (first) first.focus();
    d.querySelector('form').addEventListener('submit', function (e) {
      if (e.defaultPrevented) return;
      e.preventDefault();
      submitModal(e.target, fallbackUrl);
    });
    return true;
  }

  function openModal(url) {
    var d = ensureDialog();
    d.innerHTML = '<div class="loading">Carregando…</div>';
    if (!d.open) d.showModal();
    fetch(url, { credentials: 'same-origin', headers: { 'X-Requested-With': 'fetch' } })
      .then(function (r) { if (!r.ok) throw new Error(r.status); return r.text(); })
      .then(function (html) {
        if (!renderForm(parse(html), url)) throw new Error('sem formulário');
      })
      .catch(function () { d.close(); window.location.assign(url); });
  }

  function submitModal(form, fallbackUrl) {
    var btn = form.querySelector('button[type=submit]');
    if (btn) btn.disabled = true;
    fetch(form.action, {
      method: 'POST',
      credentials: 'same-origin',
      headers: { 'Content-Type': 'application/x-www-form-urlencoded', 'X-Requested-With': 'fetch' },
      body: new URLSearchParams(new FormData(form)).toString()
    })
      .then(function (r) { if (!r.ok) throw new Error(r.status); return r.text().then(function (t) { return { url: r.url, html: t }; }); })
      .then(function (res) {
        var doc = parse(res.html);
        if (renderForm(doc, fallbackUrl)) return; // erro de validação: mantém o modal aberto
        // Sucesso: o redirect já foi seguido; troca o conteúdo da página sem recarregar.
        var content = doc.querySelector('.content');
        var current = document.querySelector('.content');
        if (!content || !current) { window.location.assign(res.url); return; }
        current.innerHTML = content.innerHTML;
        document.title = doc.title;
        history.pushState({}, '', res.url);
        dialog.close();
        init();
      })
      .catch(function () { dialog.close(); window.location.assign(fallbackUrl); });
  }

  document.addEventListener('click', function (event) {
    var link = event.target.closest('a[data-modal]');
    if (!link || event.metaKey || event.ctrlKey || event.shiftKey || event.button !== 0) return;
    event.preventDefault();
    openModal(link.href);
  });

  window.addEventListener('popstate', function () { window.location.reload(); });
})();
