document.addEventListener('DOMContentLoaded', () => {
    const eventSource = new EventSource('/door/stream');

    function updateDoorStatus(door) {
        const doorElement = document.querySelector(`.row[data-id='${'door-' + door.doorId}']`);

        // Nếu không tìm thấy cửa, tạo mới một hàng (row)
        if (!doorElement) {
            console.log('No door exist --> created new');
        }

        if (doorElement) {
            doorElement.querySelector('.cell:nth-child(2)').textContent = door.doorName;
            doorElement.querySelector('.ip').textContent = `IP: ${door.doorIp}`;
            const statusElement = doorElement.querySelector('#door-status span');
            const actionButton = doorElement.querySelector('.action button');
            const warningElement = doorElement.querySelector('#door-warning span');

            // Cập nhật trạng thái cửa
            if (door.doorStatus === 1) {
                statusElement.textContent = 'OPEN';
                statusElement.className = 'status-on';
            } else if (door.doorStatus === null) {
                statusElement.textContent = 'Disconnected';
                statusElement.className = 'status-on';
            } else {
                statusElement.textContent = 'CLOSE';
                statusElement.className = 'status-off';
            }

            // Cập nhật trạng thái khóa cửa
            const lockdownElement = doorElement.querySelector('#door-lockdown span');
            if (door.doorLockDown === 1) {
                lockdownElement.textContent = 'ON';
                lockdownElement.className = 'status-on';
            } else if (door.doorLockDown === 0) {
                lockdownElement.textContent = 'OFF';
                lockdownElement.className = 'status-off';
            } else {
                lockdownElement.textContent = 'NULL';
                lockdownElement.className = 'status-off';
            }

            // Cập nhật nút hành động
            if (door.doorLockDown === 0) {
                actionButton.textContent = 'Alarm On';
                actionButton.className = 'action-button turn-on';
                actionButton.style.display = 'inline-block';
            } else if (door.doorLockDown === 1) {
                actionButton.textContent = 'Alarm Off';
                actionButton.className = 'action-button turn-off';
                actionButton.style.display = 'inline-block';
            }
            else if (door.doorLockDown === null) {
                actionButton.textContent = '⟳'; // Nút refresh
                actionButton.className = 'action-button refresh';
                actionButton.style.display = 'inline-block';
            }

            //Hiển thị và nhấp nháy cảnh báo khi cửa mở và alarm bật
            if (door.doorStatus === 1 && door.doorLockDown === 1) {
                warningElement.parentElement.style.visibility = 'visible'; // Hiển thị icon warning
                warningElement.classList.add('warning-blink'); // Thêm hiệu ứng nhấp nháy
            } else {
                warningElement.parentElement.style.visibility = 'hidden';  // Ẩn icon warning
                warningElement.classList.remove('warning-blink'); // Gỡ bỏ hiệu ứng nhấp nháy
            }
        }
    }

    // Set up event listeners for updating and deleting doors
    document.querySelector('.table').addEventListener('click', (event) => {
        const target = event.target;

        if (target.classList.contains('action-button') && !target.classList.contains('refresh')) {
            const doorElement = target.closest('.row');
            const checkId = doorElement.getAttribute('data-id').split('-')[0];
            if (checkId === 'door') {
                const doorId = doorElement.getAttribute('data-id').replace('door-', '');
                const doorIp = doorElement.querySelector('.ip').textContent.replace('IP: ', '');
                const doorStatusText = doorElement.querySelector('#door-status span').textContent;
                let doorStatus = (doorStatusText === 'OPEN') ? 1 : 0;
                let doorLockDown = target.classList.contains('turn-on') ? 1 : 0;
                sendUpdateRequest(doorId, doorStatus, doorLockDown, doorIp);
            }
        } else if (target.classList.contains('delete-button')) {
            const doorElement = target.closest('.row');
            const checkId = doorElement.getAttribute('data-id').split('-')[0];
            if (checkId === 'door') {
                const doorId = doorElement.getAttribute('data-id').split('-')[1];
                if (confirm('Are you sure?')) {
                    sendDoorDeleteRequest(doorId);
                }
            }
        }
    });

    // Listen for door updates via SSE
    eventSource.addEventListener('door-update', function (event) {
        const door = JSON.parse(event.data);
        updateDoorStatus(door);
    });

    // Handle SSE errors
    eventSource.onerror = function (error) {
        console.error('EventSource failed:', error);
    };

    // Event handler for device selection
    document.getElementById('deviceSelector').addEventListener('change', function () {
        const selectedDevice = this.value;

        if (selectedDevice === "Light") {
            // Add any additional logic needed when "Light" is selected
        }
    });

    // Function to send update request
    function sendUpdateRequest(doorId, doorStatus, doorLockDown, doorIp) {
        fetch(`door/update/${doorId}`, {
            method: 'PUT',
            headers: {
                'Content-Type': 'application/json',
            },
            body: JSON.stringify({
                doorStatus: doorStatus,
                doorLockDown: doorLockDown,
                doorIp: doorIp
            }),
        })
        .then(response => response.json())
        .then(data => {
            console.log('Update successful:', data);
        })
        .catch(error => {
            console.error('Error updating door:', error);
        });
    }

    // Function to send delete request
    function sendDoorDeleteRequest(doorId) {
        fetch(`door/delete/${doorId}`, {
            method: 'DELETE',
            headers: {
                'Content-Type': 'application/json',
            },
        })
        .then(response => {
            if (response.ok) {
                return response.text();
            } else {
                return response.text().then(text => { throw new Error(text) });
            }
        })
        .then(data => {
            console.log('Deleted successfully:', data);
            const doorRow = document.querySelector(`.row[data-id='door-${doorId}']`);
            if (doorRow) {
                doorRow.remove();
            }
        })
        .catch(error => {
            console.error('Error deleting door:', error);
        });
    }
});
