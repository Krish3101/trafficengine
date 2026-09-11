document.addEventListener('DOMContentLoaded', () => {
  const readingForm = document.getElementById('reading-form');
  const resultBox = document.getElementById('result-box');
  const resultTitle = document.getElementById('result-title');
  const resultDetails = document.getElementById('result-details');
  const errorBox = document.getElementById('error-box');
  const errorList = document.getElementById('error-list');

  const statTotalViolations = document.getElementById('stat-total-violations');
  const statTotalFines = document.getElementById('stat-total-fines');
  const statCurrencyLabel = document.getElementById('stat-currency-label');
  const zoneSummaryBody = document.getElementById('zone-summary-body');

  const violationsBody = document.getElementById('violations-body');
  const filterZoneInput = document.getElementById('filter-zone');
  const filterBtn = document.getElementById('filter-btn');
  const resetFilterBtn = document.getElementById('reset-filter-btn');

  let currentCurrency = 'INR';

  loadSummary();
  loadViolations();

  readingForm.addEventListener('submit', async (e) => {
    e.preventDefault();
    hideMessages();

    const vehicleId = document.getElementById('vehicleId').value.trim();
    const zone = document.getElementById('zone').value.trim();
    const speedKphVal = document.getElementById('speedKph').value;
    const speedKph = speedKphVal === '' ? null : parseFloat(speedKphVal);
    const emergency = document.getElementById('emergency').checked;

    const payload = { vehicleId, zone, speedKph, emergency };

    try {
      const response = await fetch('/api/readings', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(payload)
      });

      const data = await response.json();

      if (response.ok) {
        showResult(data);
        loadSummary();
        loadViolations();
      } else if (response.status === 400) {
        showErrors(data);
      } else {
        showGenericError(data.detail || 'An unexpected error occurred');
      }
    } catch (err) {
      showGenericError('Failed to communicate with the server.');
    }
  });

  filterBtn.addEventListener('click', () => {
    const zone = filterZoneInput.value.trim();
    loadViolations(zone);
  });

  resetFilterBtn.addEventListener('click', () => {
    filterZoneInput.value = '';
    loadViolations();
  });

  function hideMessages() {
    resultBox.className = 'result-box hidden';
    errorBox.className = 'error-box hidden';
    errorList.innerHTML = '';
  }

  function showResult(data) {
    resultBox.className = `result-box outcome-${data.outcome}`;
    resultTitle.textContent = `Outcome: ${data.outcome}`;

    let html = `
      <p><strong>Vehicle:</strong> ${escapeHtml(data.vehicleId)}</p>
      <p><strong>Zone:</strong> ${escapeHtml(data.zone)}</p>
      <p><strong>Speed:</strong> ${data.speedKph} km/h (Limit: ${data.speedLimitKph} km/h)</p>
      <p><strong>Excess:</strong> ${data.excessKph} km/h</p>
    `;

    if (data.outcome === 'VIOLATION' && data.violation) {
      html += `
        <p><strong>Fine:</strong> ${data.currency} ${data.violation.fineAmount}</p>
        <p><strong>Citation ID:</strong> #${data.violation.id}</p>
      `;
    } else if (data.outcome === 'EXEMPT') {
      html += `<p><em>Vehicle exempt due to emergency status. No citation recorded.</em></p>`;
    } else {
      html += `<p><em>Vehicle within speed limit. No citation recorded.</em></p>`;
    }

    resultDetails.innerHTML = html;
  }

  function showErrors(data) {
    errorBox.classList.remove('hidden');
    errorList.innerHTML = '';

    if (data.errors && Array.isArray(data.errors) && data.errors.length > 0) {
      data.errors.forEach(err => {
        const li = document.createElement('li');
        li.textContent = `${err.field}: ${err.message}`;
        errorList.appendChild(li);
      });
    } else {
      const li = document.createElement('li');
      li.textContent = data.detail || 'Invalid input provided.';
      errorList.appendChild(li);
    }
  }

  function showGenericError(message) {
    errorBox.classList.remove('hidden');
    errorList.innerHTML = `<li>${escapeHtml(message)}</li>`;
  }

  async function loadSummary() {
    try {
      const response = await fetch('/api/analytics/summary');
      if (!response.ok) return;

      const summary = await response.json();
      currentCurrency = summary.currency || 'INR';

      statTotalViolations.textContent = summary.totalViolations.toLocaleString();
      statTotalFines.textContent = `${currentCurrency} ${summary.totalFineAmount.toLocaleString()}`;
      statCurrencyLabel.textContent = `Total Fines (${currentCurrency})`;

      if (!summary.zones || summary.zones.length === 0) {
        zoneSummaryBody.innerHTML = `<tr><td colspan="3" class="empty-state">No violations recorded yet.</td></tr>`;
      } else {
        zoneSummaryBody.innerHTML = summary.zones.map(z => `
          <tr>
            <td><strong>${escapeHtml(z.zone)}</strong></td>
            <td>${z.violations.toLocaleString()}</td>
            <td>${currentCurrency} ${z.fineAmount.toLocaleString()}</td>
          </tr>
        `).join('');
      }
    } catch (err) {
      console.error('Failed to load summary:', err);
    }
  }

  async function loadViolations(zone = '') {
    try {
      let url = '/api/violations?limit=50';
      if (zone) {
        url += `&zone=${encodeURIComponent(zone)}`;
      }

      const response = await fetch(url);
      if (!response.ok) return;

      const violations = await response.json();

      if (!violations || violations.length === 0) {
        violationsBody.innerHTML = `<tr><td colspan="8" class="empty-state">No violations recorded yet.</td></tr>`;
      } else {
        violationsBody.innerHTML = violations.map(v => {
          const excess = Math.max(0, Math.round((v.speedKph - v.speedLimitKph) * 10) / 10);
          return `
            <tr>
              <td>#${v.id}</td>
              <td>${v.recordedAt ? new Date(v.recordedAt).toISOString().replace('T', ' ').substring(0, 19) : ''}</td>
              <td><strong>${escapeHtml(v.vehicleId)}</strong></td>
              <td>${escapeHtml(v.zone)}</td>
              <td>${v.speedKph}</td>
              <td>${v.speedLimitKph}</td>
              <td>${excess}</td>
              <td>${currentCurrency} ${v.fineAmount.toLocaleString()}</td>
            </tr>
          `;
        }).join('');
      }
    } catch (err) {
      console.error('Failed to load violations:', err);
    }
  }

  function escapeHtml(str) {
    if (!str) return '';
    return String(str)
      .replace(/&/g, '&amp;')
      .replace(/</g, '&lt;')
      .replace(/>/g, '&gt;')
      .replace(/"/g, '&quot;')
      .replace(/'/g, '&#039;');
  }
});
