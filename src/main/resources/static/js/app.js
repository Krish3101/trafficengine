document.addEventListener('DOMContentLoaded', () => {
  const readingForm = document.getElementById('reading-form');
  const submitBtn = document.getElementById('submit-reading-btn');
  const resultBox = document.getElementById('result-box');
  const resultTitle = document.getElementById('result-title');
  const resultDetails = document.getElementById('result-details');
  const errorBox = document.getElementById('error-box');
  const errorTitle = document.getElementById('error-title');
  const errorList = document.getElementById('error-list');

  const statTotalViolations = document.getElementById('stat-total-violations');
  const statTotalFines = document.getElementById('stat-total-fines');
  const statCurrencyLabel = document.getElementById('stat-currency-label');
  const zoneSummaryBody = document.getElementById('zone-summary-body');

  const violationsBody = document.getElementById('violations-body');
  const filterForm = document.getElementById('filter-form');
  const filterZoneInput = document.getElementById('filter-zone');
  const resetFilterBtn = document.getElementById('reset-filter-btn');

  const OUTCOME_LABELS = {
    WITHIN_LIMIT: 'Within limit',
    VIOLATION: 'Violation',
    EXEMPT: 'Exempt (emergency vehicle)'
  };
  const countFormat = new Intl.NumberFormat('en-IN');

  // en-IN on purpose, so ₹ amounts use lakh grouping (₹1,00,000) whatever the browser locale
  function money(amount, currency) {
    return new Intl.NumberFormat('en-IN', {
      style: 'currency',
      currency: currency || 'INR',
      minimumFractionDigits: 0,
      maximumFractionDigits: 2
    }).format(Number(amount || 0));
  }

  // "2026-09-30 18:29:59"
  function utc(instant) {
    return new Date(instant).toISOString().replace('T', ' ').substring(0, 19);
  }

  refresh();

  readingForm.addEventListener('submit', async (e) => {
    e.preventDefault();
    hideMessages();

    const vehicleId = document.getElementById('vehicleId').value.trim();
    const zone = document.getElementById('zone').value.trim();
    const speedKphVal = document.getElementById('speedKph').value;
    const speedKph = speedKphVal === '' ? null : parseFloat(speedKphVal);
    const emergency = document.getElementById('emergency').checked;

    submitBtn.disabled = true;
    try {
      const response = await fetch('/api/readings', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ vehicleId, zone, speedKph, emergency })
      });
      const data = await response.json();

      if (response.ok) {
        showResult(data);
        refresh();
      } else if (response.status === 400 || response.status === 422) {
        showErrors(data);
      } else {
        showGenericError(data.detail || 'An unexpected error occurred');
      }
    } catch {
      showGenericError('Failed to communicate with the server.');
    } finally {
      submitBtn.disabled = false;
    }
  });

  filterForm.addEventListener('submit', (e) => {
    e.preventDefault();
    loadCitations();
  });

  resetFilterBtn.addEventListener('click', () => {
    filterZoneInput.value = '';
    loadCitations();
  });

  // the "Retry" buttons are rendered inside the tables
  document.addEventListener('click', (e) => {
    if (e.target.classList.contains('retry-btn')) {
      refresh();
    }
  });

  function refresh() {
    loadSummary();
    loadCitations();
  }

  function hideMessages() {
    resultBox.className = 'result-box hidden';
    errorBox.className = 'error-box hidden';
    errorList.innerHTML = '';
    document.querySelectorAll('[aria-invalid]').forEach(el => el.removeAttribute('aria-invalid'));
  }

  function showResult(data) {
    resultBox.className = `result-box outcome-${data.outcome}`;
    resultTitle.textContent = OUTCOME_LABELS[data.outcome] || data.outcome;

    let html = `
      <p><strong>Vehicle:</strong> ${escapeHtml(data.vehicleId)}</p>
      <p><strong>Zone:</strong> ${escapeHtml(data.zone)}</p>
      <p><strong>Speed:</strong> ${data.speedKph} km/h (Limit: ${data.speedLimitKph} km/h)</p>
      <p><strong>Excess:</strong> ${data.excessKph} km/h</p>
    `;

    if (data.reason) {
      html += `<p><strong>Why:</strong> ${escapeHtml(data.reason)}</p>`;
    }

    if (data.outcome === 'VIOLATION' && data.citation) {
      html += `
        <p><strong>Fine:</strong> ${money(data.citation.fineAmount, data.currency)}</p>
        <p><strong>Citation ID:</strong> #${data.citation.id}</p>
      `;
    } else if (data.outcome === 'EXEMPT') {
      html += `<p><em>Vehicle exempt due to emergency status. No citation recorded.</em></p>`;
    } else {
      html += `<p><em>Vehicle within speed limit. No citation recorded.</em></p>`;
    }

    if (data.defaultLimit) {
      html += `<p class="note">Zone not configured: general limit used.</p>`;
    }

    resultDetails.innerHTML = html;
  }

  function showErrors(data) {
    errorTitle.textContent = 'Validation Error';
    errorBox.classList.remove('hidden');
    errorList.innerHTML = '';

    const errors = Array.isArray(data.errors) && data.errors.length > 0
      ? data.errors
      : [{ message: data.detail || 'Invalid input provided.' }];
    errors.forEach(err => {
      const li = document.createElement('li');
      li.textContent = err.message;
      errorList.appendChild(li);
      const input = err.field && document.getElementById(err.field);
      if (input) input.setAttribute('aria-invalid', 'true');
    });
  }

  function showGenericError(message) {
    errorTitle.textContent = 'Something went wrong';
    errorBox.classList.remove('hidden');
    errorList.innerHTML = `<li>${escapeHtml(message)}</li>`;
  }

  function messageRow(colspan, html) {
    return `<tr><td colspan="${colspan}" class="empty-state">${html}</td></tr>`;
  }

  function loadFailedRow(colspan) {
    return messageRow(colspan,
      'Could not load citations. <button type="button" class="link-button retry-btn">Retry.</button>');
  }

  async function loadSummary() {
    try {
      const response = await fetch('/api/analytics/summary');
      if (!response.ok) throw new Error(`HTTP ${response.status}`);
      const summary = await response.json();
      const currency = summary.currency || 'INR';

      statTotalViolations.textContent = countFormat.format(summary.totalCitations || 0);
      statTotalFines.textContent = money(summary.totalFineAmount, currency);
      statCurrencyLabel.textContent = `Total Fines (${currency})`;

      if (!summary.zones || summary.zones.length === 0) {
        zoneSummaryBody.innerHTML = messageRow(3, 'No citations recorded yet.');
      } else {
        zoneSummaryBody.innerHTML = summary.zones.map(z => `
          <tr>
            <td><strong>${escapeHtml(z.zone)}</strong></td>
            <td class="num">${countFormat.format(z.citations || 0)}</td>
            <td class="num">${money(z.fineAmount, currency)}</td>
          </tr>
        `).join('');
      }
    } catch (err) {
      console.error('Failed to load summary:', err);
      statTotalViolations.textContent = '–';
      statTotalFines.textContent = '–';
      zoneSummaryBody.innerHTML = loadFailedRow(3);
    }
  }

  async function loadCitations() {
    const zone = filterZoneInput.value.trim();
    try {
      let url = '/api/citations?limit=50';
      if (zone) {
        url += `&zone=${encodeURIComponent(zone)}`;
      }

      const response = await fetch(url);
      // a 400 here means the filter isn't a valid zone id, so nothing can match it
      if (!response.ok && !(response.status === 400 && zone)) throw new Error(`HTTP ${response.status}`);
      const citations = response.ok ? await response.json() : [];

      if (citations.length === 0) {
        violationsBody.innerHTML = messageRow(9, zone
          ? `No citations match zone ${escapeHtml(zone)}.`
          : 'No citations recorded yet.');
      } else {
        violationsBody.innerHTML = citations.map(c => `
          <tr title="Recorded at ${utc(c.recordedAt)} UTC">
            <td>#${c.id}</td>
            <td class="nowrap">${utc(c.observedAt)}</td>
            <td><strong>${escapeHtml(c.vehicleId)}</strong></td>
            <td>${escapeHtml(c.zone)}</td>
            <td class="nowrap">${escapeHtml(c.ruleSetVersion)}</td>
            <td class="num">${c.speedKph}</td>
            <td class="num">${c.speedLimitKph}</td>
            <td class="num">${c.excessKph}</td>
            <td class="num" title="${escapeHtml(c.reason)}">${money(c.fineAmount, c.currency)}</td>
          </tr>
        `).join('');
      }
    } catch (err) {
      console.error('Failed to load citations:', err);
      violationsBody.innerHTML = loadFailedRow(9);
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
