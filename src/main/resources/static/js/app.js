// ===== Modal =====
function openModal() {
    document.getElementById('modal-backdrop').classList.add('open');
    document.getElementById('modal').focus();
}

function closeModal() {
    document.getElementById('modal-backdrop').classList.remove('open');
    document.getElementById('modal-content').innerHTML = '';
}

// Close on Escape key
document.addEventListener('keydown', e => {
    if (e.key === 'Escape') closeModal();
});

// ===== Keyboard navigation for full image viewer =====
document.addEventListener('keydown', e => {
    const viewer = document.getElementById('viewer');
    if (!viewer) return;

    const map = {
        'ArrowLeft':  '[title="Previous"]',
        'ArrowRight': '[title="Next"]',
        'Home':       '[title="First"]',
        'End':        '[title="Last"]'
    };

    const selector = map[e.key];
    if (!selector) return;

    const btn = viewer.querySelector(selector);
    if (btn && !btn.disabled) {
        e.preventDefault();
        btn.click();
    }
});

// ===== Auto-open modal when HTMX swaps content into #modal-content =====
document.getElementById('modal-content').addEventListener('htmx:afterSwap', () => {
    openModal();
});
