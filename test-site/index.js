/* csp_block_start:type=IMPORTED;name=app.js */
console.log("External App Script Loaded");
/* csp_block_end:type=IMPORTED;name=app.js */

/* csp_block_start:type=INTERNAL;index=1 */
console.log("Internal Script Run");
/* csp_block_end:type=INTERNAL;index=1 */

/* csp_block_start:type=EVENT;selector=#my-btn;event=click */
document.addEventListener('DOMContentLoaded', () => {
    document.querySelectorAll('#my-btn').forEach(el => {
        el.addEventListener('click', function(event) {
            console.log('button clicked', this);
        });
    });
});
/* csp_block_end:type=EVENT;selector=#my-btn;event=click */

/* csp_block_start:type=EVENT;selector=.csp_auto_0001;event=change */
document.addEventListener('DOMContentLoaded', () => {
    document.querySelectorAll('.csp_auto_0001').forEach(el => {
        el.addEventListener('change', function(event) {
            alert('changed')
        });
    });
});
/* csp_block_end:type=EVENT;selector=.csp_auto_0001;event=change */

/* csp_block_start:type=EVENT;selector=#new-div;event=click */
document.addEventListener('DOMContentLoaded', () => {
    document.querySelectorAll('#new-div').forEach(el => {
        el.addEventListener('click', function(event) {
            console.log('new div clicked');
        });
    });
});
/* csp_block_end:type=EVENT;selector=#new-div;event=click */

// === USER CUSTOM CODE ===
function myCustomHelper() {
    console.log("This is a custom helper!");
}
