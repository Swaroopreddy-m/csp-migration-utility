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
console.log("Internal Script loaded successfully.");
        function changeColor(element) {
            element.style.background = '#7f00ff';
            Logger.logEvent("Button background color altered locally.");
        }
/* csp_block_end:type=INTERNAL;index=1 */

/* csp_block_start:type=EVENT;selector=.csp_auto_0001;event=load */
document.addEventListener('DOMContentLoaded', () => {
    document.querySelectorAll('.csp_auto_0001').forEach(el => {
        el.addEventListener('load', function(event) {
            console.log('Landing page loaded! ready for interaction.');
        });
    });
});
/* csp_block_end:type=EVENT;selector=.csp_auto_0001;event=load */

/* csp_block_start:type=EVENT;selector=.csp_auto_0002;event=click */
document.addEventListener('DOMContentLoaded', () => {
    document.querySelectorAll('.csp_auto_0002').forEach(el => {
        el.addEventListener('click', function(event) {
            alert('You clicked the info block!');
        });
    });
});
/* csp_block_end:type=EVENT;selector=.csp_auto_0002;event=click */

/* csp_block_start:type=EVENT;selector=.btn-primary;event=click */
document.addEventListener('DOMContentLoaded', () => {
    document.querySelectorAll('.btn-primary').forEach(el => {
        el.addEventListener('click', function(event) {
            changeColor(this);
        });
    });
});
/* csp_block_end:type=EVENT;selector=.btn-primary;event=click */

/* csp_block_start:type=EVENT;selector=.btn-primary;event=mouseover */
document.addEventListener('DOMContentLoaded', () => {
    document.querySelectorAll('.btn-primary').forEach(el => {
        el.addEventListener('mouseover', function(event) {
            this.style.transform='scale(1.05)';
        });
    });
});
/* csp_block_end:type=EVENT;selector=.btn-primary;event=mouseover */

/* csp_block_start:type=EVENT;selector=.btn-primary;event=mouseout */
document.addEventListener('DOMContentLoaded', () => {
    document.querySelectorAll('.btn-primary').forEach(el => {
        el.addEventListener('mouseout', function(event) {
            this.style.transform='scale(1)';
        });
    });
});
/* csp_block_end:type=EVENT;selector=.btn-primary;event=mouseout */

/* csp_block_start:type=EVENT;selector=#btn-secondary;event=click */
document.addEventListener('DOMContentLoaded', () => {
    document.querySelectorAll('#btn-secondary').forEach(el => {
        el.addEventListener('click', function(event) {
            navigateToAbout();
        });
    });
});
/* csp_block_end:type=EVENT;selector=#btn-secondary;event=click */

