document.addEventListener('DOMContentLoaded', () => {
    const addButton = document.querySelector('.add-button');
    const popup = document.getElementById('popup');
    const cancelButton = document.getElementById('cancelButton');
    const doneButton = document.getElementById('doneButton');

    // Show the popup when the "Add" button is clicked
    if (addButton && popup) {
        addButton.addEventListener('click', () => {
            // popup.style.display = 'block';
            showPopup();
            
        });
    }

    // Hide the popup when the "Cancel" button is clicked
    if (cancelButton && popup) {
        cancelButton.addEventListener('click', () => {
            // popup.style.display = 'none';
            hidePopup();
        });
    }

    // Handle the "Done" button
    if (doneButton && popup) {
        doneButton.addEventListener('click', () => {
            // Perform your actions with the input values here

            // Hide the popup
            // popup.style.display = 'none';
            hidePopup();
        });
    }
    function showPopup(){
        document.querySelector('.popup-content').classList.add('popup-show');
        document.querySelector('.popup-overlay').classList.add('popup-overlay-show');        
    }
    function hidePopup(){
        document.querySelector('.popup-content').classList.remove('popup-show');
        document.querySelector('.popup-overlay').classList.remove('popup-overlay-show');
    }

    //nút add để thêm mới thiết bị
    document.getElementById("doneButton").addEventListener("click", function() {
        const deviceType = document.getElementById("deviceType").value;
        const deviceId = document.getElementById("deviceId").value;
        const deviceName = document.getElementById("deviceName").value;
    
        // Kiểm tra xem người dùng đã chọn thiết bị và nhập các thông tin cần thiết chưa
        if (deviceType === "Light") {
            data = {
                lightId: deviceId,
                lightName: deviceName
            };
            fetch(`light/new${deviceType}`, {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/json'
                },
                body: JSON.stringify(data)
            })
            .then(response => {
                if (response.ok) {
                    alert(`${deviceType} added successfully!`);
                    changeFragment(deviceType);
                } else {
                    alert(`${deviceType} already exists or an error occurred.`);
                }
            })
            .catch(error => {
                console.error('Error:', error);
            });
        } else if (deviceType === "Door") {
            data = {
                doorId: deviceId,
                doorName: deviceName
            };
            fetch(`door/new${deviceType}`, {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/json'
                },
                body: JSON.stringify(data)
            })
            .then(response => {
                if (response.ok) {
                    alert(`${deviceType} added successfully!`);
                    changeFragment(deviceType);
                } else {
                    alert(`${deviceType} already exists or an error occurred.`);
                }
            })
            .catch(error => {
                console.error('Error:', error);
            });
        } else if (deviceType === "Camera") {
            data = {
                cameraId: deviceId,
                cameraName: deviceName
            };
            fetch(`camera/new${deviceType}`, {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/json'
                },
                body: JSON.stringify(data)
            })
            .then(response => {
                if (response.ok) {
                    alert(`${deviceType} added successfully!`);
                    changeFragment(deviceType);
                } else {
                    alert(`${deviceType} already exists or an error occurred.`);
                }
            })
            .catch(error => {
                console.error('Error:', error);
            });
        } else {
            alert("Invalid device type selected.");
        }
        
    });
    //inside row refresh button
    document.querySelector('.table').addEventListener('click', (event) => {
        const target = event.target;
        if (target.classList.contains('refresh')) {
            const selectedDevice=document.getElementById('deviceSelector').value
            changeFragment(selectedDevice);
        } 
    });

    // change HTML table

    document.getElementById('deviceSelector').addEventListener('change', function() {
        const selectedDevice = this.value;
        changeFragment(selectedDevice);
    });
    function changeFragment(selectedDevice){
        fetch(`/devices/${selectedDevice}`)
        .then(response => response.text())
        .then(html => {
            // Replace the inner content of the .table div
            document.querySelector('.table').innerHTML = html;
            })
        .catch(error => console.error('Error fetching device data:', error));
    }

});
