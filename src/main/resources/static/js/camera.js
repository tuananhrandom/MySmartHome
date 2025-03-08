const canvas = document.getElementById('videoCanvas');
        const ctx = canvas.getContext('2d');
        const ws = new WebSocket("ws://192.168.1.20:8080/ws/camera/livecamera");

        ws.binaryType = 'arraybuffer';

        ws.onopen = function() {
            console.log("WebSocket connection opened");
        };

        ws.onmessage = function(event) {
            if (event.data instanceof ArrayBuffer) {
                const blob = new Blob([event.data], { type: 'image/jpeg' });
                const url = URL.createObjectURL(blob);
                const img = new Image();
                
                img.onload = function() {
                    // Điều chỉnh kích thước canvas dựa trên kích thước ảnh
                    canvas.width = img.width;
                    canvas.height = img.height;

                    // Vẽ ảnh lên canvas
                    ctx.drawImage(img, 0, 0, img.width, img.height);
                    URL.revokeObjectURL(url);
                };

                img.src = url;
            }
        };

        ws.onclose = function() {
            console.log("WebSocket connection closed");
        };

        ws.onerror = function(error) {
            console.error("WebSocket error:", error);
        };