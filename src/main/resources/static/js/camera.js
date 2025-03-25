const canvas = document.getElementById('videoCanvas');
const ctx = canvas.getContext('2d');

let ws = null;
let reconnectAttempts = 0;
const MAX_RECONNECT_ATTEMPTS = 5;

function connectWebSocket() {
    const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:';
    const wsUrl = `${protocol}//${window.location.host}/ws/camera/livecamera`;
    
    ws = new WebSocket(wsUrl);
    ws.binaryType = 'arraybuffer';

    ws.onopen = function() {
        console.log("WebSocket connection opened");
        reconnectAttempts = 0;
    };

    ws.onmessage = function(event) {
        if (event.data instanceof ArrayBuffer) {
            const blob = new Blob([event.data], { type: 'image/jpeg' });
            const url = URL.createObjectURL(blob);
            const img = new Image();
            
            img.onload = function() {
                canvas.width = img.width;
                canvas.height = img.height;
                ctx.drawImage(img, 0, 0, img.width, img.height);
                URL.revokeObjectURL(url);
            };

            img.onerror = function() {
                console.error("Error loading image");
                URL.revokeObjectURL(url);
            };

            img.src = url;
        }
    };

    ws.onclose = function() {
        console.log("WebSocket connection closed");
        if (reconnectAttempts < MAX_RECONNECT_ATTEMPTS) {
            reconnectAttempts++;
            console.log(`Attempting to reconnect (${reconnectAttempts}/${MAX_RECONNECT_ATTEMPTS})...`);
            setTimeout(connectWebSocket, 5000);
        } else {
            console.error("Max reconnection attempts reached");
        }
    };

    ws.onerror = function(error) {
        console.error("WebSocket error:", error);
    };
}

// Khởi tạo kết nối
connectWebSocket();