/* Petites aides de formulaire, communes à toutes les pages (aucune dépendance). */
(function () {
  'use strict';

  // Confirmation de mot de passe : tout champ portant data-confirm="idDuChamp" doit lui être identique.
  function bindConfirm(input) {
    var other = document.getElementById(input.getAttribute('data-confirm'));
    if (!other) return;
    var check = function () {
      input.setCustomValidity(input.value && input.value !== other.value ? 'Les mots de passe ne correspondent pas.' : '');
    };
    input.addEventListener('input', check);
    other.addEventListener('input', check);
  }
  var p2 = document.getElementById('password2');
  if (p2 && !p2.hasAttribute('data-confirm')) p2.setAttribute('data-confirm', 'password');
  Array.prototype.forEach.call(document.querySelectorAll('[data-confirm]'), bindConfirm);

  // Filtre instantané d'un tableau : <input data-filter="#idTableau">
  Array.prototype.forEach.call(document.querySelectorAll('[data-filter]'), function (box) {
    var table = document.querySelector(box.getAttribute('data-filter'));
    if (!table) return;
    var empty = document.querySelector(box.getAttribute('data-filter') + '-noresult');
    box.addEventListener('input', function () {
      var q = box.value.trim().toLowerCase();
      var shown = 0;
      Array.prototype.forEach.call(table.tBodies[0].rows, function (row) {
        var match = row.textContent.toLowerCase().indexOf(q) !== -1;
        row.hidden = !match;
        if (match) shown++;
      });
      if (empty) empty.hidden = shown !== 0;
    });
  });

  // Dates de rendez-vous : pas de date passée.
  Array.prototype.forEach.call(document.querySelectorAll('input[type="date"][data-min-today]'), function (input) {
    var d = new Date();
    d.setMinutes(d.getMinutes() - d.getTimezoneOffset());
    input.min = d.toISOString().slice(0, 10);
  });

  // Empêche le double envoi d'un formulaire (double clic).
  Array.prototype.forEach.call(document.querySelectorAll('form'), function (form) {
    form.addEventListener('submit', function () {
      var btn = form.querySelector('button[type="submit"]');
      if (btn && form.checkValidity()) setTimeout(function () { btn.disabled = true; }, 0);
    });
  });
})();
