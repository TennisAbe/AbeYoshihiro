'use strict';
document.querySelectorAll('.card-color-form').forEach(form => {
  const card = form.closest('.character-card');
  const picker = form.querySelector('.color-picker');
  const fields = ['red', 'green', 'blue'].map(name => form.elements.namedItem(name));
  const status = form.querySelector('.color-status');
  let saved = form.dataset.savedColor;
  const channels = color => [1, 3, 5].map(offset => parseInt(color.slice(offset, offset + 2), 16));
  const preview = color => {
    card.classList.toggle('has-custom-color', Boolean(color));
    if (color) card.style.setProperty('--card-rgb', channels(color).join(', '));
    else card.style.removeProperty('--card-rgb');
  };
  const showInputs = color => {
    picker.value = color || '#33213e';
    channels(picker.value).forEach((value, index) => { fields[index].value = String(value); });
  };
  picker.addEventListener('input', () => { showInputs(picker.value); preview(picker.value); status.textContent = ''; });
  fields.forEach(field => field.addEventListener('input', () => {
    if (!fields.every(input => input.validity.valid)) return;
    picker.value = '#' + fields.map(input => Number(input.value).toString(16).padStart(2, '0')).join('');
    preview(picker.value); status.textContent = '';
  }));
  form.addEventListener('submit', async event => {
    event.preventDefault();
    const reset = event.submitter?.name === 'reset';
    if (!reset && !form.reportValidity()) return;
    const color = reset ? '' : '#' + fields.map(input => Number(input.value).toString(16).padStart(2, '0')).join('');
    const controls = [...form.querySelectorAll('input, button')];
    controls.forEach(control => { control.disabled = true; });
    status.textContent = '';
    try {
      const response = await fetch(form.action, {method: 'PUT', headers: {'Content-Type': 'application/x-www-form-urlencoded'}, body: new URLSearchParams({color})});
      if (!response.ok) throw new Error('save failed');
      const data = await response.json();
      if (typeof data.color !== 'string' || (data.color !== '' && !/^#[0-9a-f]{6}$/.test(data.color))) throw new Error('invalid response');
      saved = data.color; form.dataset.savedColor = saved;
      showInputs(saved); preview(saved);
      status.textContent = reset ? '標準の背景色に戻しました。' : '背景色を保存しました。';
    } catch {
      preview(saved);
      status.textContent = '保存できませんでした。通信を確認して再度保存してください。';
    } finally {
      controls.forEach(control => { control.disabled = false; });
    }
  });
});
