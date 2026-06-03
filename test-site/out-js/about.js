/* csp_block_start:type=IMPORTED;name=global.js */
// Global JavaScript utilities for verification
console.log("global.js script loaded.");

var Logger = {
    logEvent: function(msg) {
        console.log("[App Log] " + msg);
    }
};

function navigateToAbout() {
    window.location.href = "about.html";
}
/* csp_block_end:type=IMPORTED;name=global.js */

/* csp_block_start:type=INTERNAL;index=1 */
function toggleSecretMessage() {
            var box = document.getElementById('secret-box');
            if (box.style.display === 'none') {
                box.style.display = 'block';
            } else {
                box.style.display = 'none';
            }
        }
/* csp_block_end:type=INTERNAL;index=1 */

/* csp_block_start:type=EVENT;selector=.csp_auto_0001;event=click */
document.addEventListener('DOMContentLoaded', () => {
    document.querySelectorAll('.csp_auto_0001').forEach(el => {
        el.addEventListener('click', function(event) {
            toggleSecretMessage();
        });
    });
});
/* csp_block_end:type=EVENT;selector=.csp_auto_0001;event=click */

/* csp_block_start:type=EVENT;selector=#back-btn;event=click */
document.addEventListener('DOMContentLoaded', () => {
    document.querySelectorAll('#back-btn').forEach(el => {
        el.addEventListener('click', function(event) {
            window.location.href='index.html';
        });
    });
});
/* csp_block_end:type=EVENT;selector=#back-btn;event=click */

