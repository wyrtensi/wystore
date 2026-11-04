export function reportUrl(report) {
  if (typeof report?.sha256 !== 'string' || !/^[a-f0-9]{64}$/.test(report.sha256) || typeof report.report_url !== 'string') {
    throw new Error('Invalid report data');
  }
  const url = new URL(report.report_url);
  const expectedPath = `/gui/file/${report.sha256}`;
  if (url.origin !== 'https://www.virustotal.com' || url.username || url.password ||
      url.pathname !== expectedPath || url.search || url.hash ||
      report.report_url !== `https://www.virustotal.com${expectedPath}`) {
    throw new Error('Invalid VirusTotal report URL');
  }
  return url.href;
}

if (typeof document !== 'undefined') {
  const status = document.getElementById('status');
  const retry = document.getElementById('retry');
  async function openReport() {
    retry.hidden = true;
    status.textContent = 'Открываем отчёт VirusTotal… / Opening the VirusTotal report…';
    try {
      const response = await fetch('https://raw.githubusercontent.com/wyrtensi/wystore/release-reports/virustotal-latest-report.json', { cache: 'no-cache' });
      if (!response.ok) throw new Error(`Report request failed: ${response.status}`);
      window.location.replace(reportUrl(await response.json()));
    } catch (error) {
      console.error('Unable to open the VirusTotal report:', error);
      status.textContent = 'Не удалось открыть отчёт VirusTotal. Попробуйте ещё раз. / Could not open the VirusTotal report. Please try again.';
      retry.hidden = false;
    }
  }
  retry.addEventListener('click', openReport);
  openReport();
}
