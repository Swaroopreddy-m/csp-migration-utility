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
