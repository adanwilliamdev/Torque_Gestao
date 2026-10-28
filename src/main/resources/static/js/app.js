(function () {
  'use strict';

  // Confirmação antes de ações destrutivas: <form data-confirm="Tem certeza?">
  document.addEventListener('submit', function (event) {
    var message = event.target.getAttribute('data-confirm');
    if (message && !window.confirm(message)) {
      event.preventDefault();
    }
  });

  // Menu lateral no mobile.
  var toggle = document.querySelector('.menu-toggle');
  if (toggle) {
    toggle.addEventListener('click', function () {
      document.body.classList.toggle('nav-open');
    });
  }

  // ---- Dropdown de ações por linha (⋮) ----
  document.querySelectorAll('.row-menu').forEach(function (menu) {
    var btn = menu.querySelector('.menu-btn');
    if (!btn) return;
    btn.addEventListener('click', function (event) {
      event.stopPropagation();
      document.querySelectorAll('.row-menu.open').forEach(function (other) {
        if (other !== menu) other.classList.remove('open');
      });
      menu.classList.toggle('open');
    });
  });
  document.addEventListener('click', function () {
    document.querySelectorAll('.row-menu.open').forEach(function (menu) {
      menu.classList.remove('open');
    });
  });
  document.addEventListener('keydown', function (event) {
    if (event.key === 'Escape') {
      document.querySelectorAll('.row-menu.open').forEach(function (menu) {
        menu.classList.remove('open');
      });
    }
  });

  // ---- Filtra veículos pelo cliente escolhido ----
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

  // ---- Busca + filtro em tabelas com [data-table] ----
  document.querySelectorAll('table[data-table]').forEach(function (table) {
    var name = table.getAttribute('data-table');
    var searchInput = document.querySelector('[data-search-table="' + name + '"]');
    var filterSelect = document.querySelector('[data-filter-table="' + name + '"]');
    if (!searchInput && !filterSelect) return;

    function apply() {
      var q = searchInput ? searchInput.value.toLowerCase().trim() : '';
      var f = filterSelect ? filterSelect.value : '';
      table.querySelectorAll('tbody tr').forEach(function (tr) {
        // Pula a linha de Empty State (que contém a div)
        if (tr.querySelector('.empty-state')) return;

        var textMatch = !q || tr.textContent.toLowerCase().indexOf(q) !== -1;
        var rowFilter = tr.getAttribute('data-filter') || '';
        var filterMatch = !f || rowFilter === f;
        tr.style.display = (textMatch && filterMatch) ? '' : 'none';
      });
    }

    if (searchInput) searchInput.addEventListener('input', apply);
    if (filterSelect) filterSelect.addEventListener('change', apply);
  });
})();