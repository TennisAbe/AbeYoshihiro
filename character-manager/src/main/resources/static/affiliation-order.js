(() => {
  'use strict';
  document.querySelectorAll('.order-list').forEach(list => {
    function refresh() {
      const rows = [...list.children];
      rows.forEach((row, index) => {
        row.querySelector('[name="positions"]').value = index + 1;
        row.querySelector('[name="positions"]').setCustomValidity('');
        row.querySelector('[data-move="-1"]').disabled = index === 0;
        row.querySelector('[data-move="1"]').disabled = index === rows.length - 1;
      });
    }
    list.addEventListener('click', event => {
      const button = event.target.closest('[data-move]');
      if (!button || button.disabled) return;
      const row = button.closest('.order-row');
      if (button.dataset.move === '-1' && row.previousElementSibling) list.insertBefore(row, row.previousElementSibling);
      else if (button.dataset.move === '1' && row.nextElementSibling) list.insertBefore(row.nextElementSibling, row);
      refresh();
      if (button.disabled) row.querySelector('[name="positions"]').focus(); else button.focus();
    });
    const fields = [...list.querySelectorAll('[name="positions"]')];
    list.closest('form').addEventListener('submit', event => {
      const values = fields.map(field => Number(field.value));
      fields.forEach(field => field.setCustomValidity(''));
      if (new Set(values).size !== values.length) {
        event.preventDefault();
        fields[0].setCustomValidity('表示順は重複しない番号にしてください。');
        fields[0].reportValidity();
      }
    });
    fields.forEach(field => field.addEventListener('input', () => fields.forEach(item => item.setCustomValidity(''))));
    refresh();
  });
})();
