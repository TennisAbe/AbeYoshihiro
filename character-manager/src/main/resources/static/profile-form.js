'use strict';
const gender = document.getElementById('gender');
const bustField = document.getElementById('bust-field');
const bustInput = document.getElementById('bustCm');
if (gender && bustField && bustInput) {
  const updateBust = () => {
    const female = gender.value === '女';
    bustField.hidden = !female;
    bustInput.disabled = !female;
  };
  gender.addEventListener('change', updateBust);
  updateBust();
}
