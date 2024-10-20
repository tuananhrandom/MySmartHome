document.addEventListener('DOMContentLoaded', () => {
    const eventSource = new EventSource('/door/stream');

    function updateDoorStatus(door) {
        const doorElement = document.querySelector(`.row[data-id='${'door-' + door.doorId}']`);
    
        if (!doorElement) {
            console.log('No door exists --> created new');
            return;
        }
    
        if (doorElement) {
            doorElement.querySelector('.cell:nth-child(2)').textContent = door.doorName;
            doorElement.querySelector('.ip').textContent = `IP: ${door.doorIp}`;
            const statusElement = doorElement.querySelector('#door-status span');
            const actionButton = doorElement.querySelector('.action button');
            const warningElement = doorElement.querySelector('#door-warning span');
            const checkButtonElement = doorElement.querySelector('#check-btn');
            const doorAlertStatus = door.doorAlert;
    
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
    
            // Cập nhật trạng thái lockdown
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
    
            // Cập nhật nút action
            if (door.doorLockDown === 0) {
                actionButton.textContent = 'Alert On';
                actionButton.className = 'action-button turn-on';
                actionButton.style.display = 'inline-block';
            } else if (door.doorLockDown === 1) {
                actionButton.textContent = 'Alert Off';
                actionButton.className = 'action-button turn-off';
                actionButton.style.display = 'inline-block';
            } else if (door.doorLockDown === null) {
                actionButton.textContent = '⟳';
                actionButton.className = 'action-button refresh';
                actionButton.style.display = 'inline-block';
            }
    
            // Hiển thị và nhấp nháy warning nếu doorAlert = 1
            if (doorAlertStatus === 1) {
                if (warningElement && warningElement.parentElement) {
                    warningElement.parentElement.style.visibility = 'visible';
                    warningElement.classList.add('warning-blink');
                    checkButtonElement.style.visibility = 'visible';
                }
            } else {
                if (warningElement && warningElement.parentElement) {
                    warningElement.classList.remove('warning-blink');
                    warningElement.parentElement.style.visibility = 'hidden';
                    checkButtonElement.style.visibility = 'hidden';
                }
            }
    
            // Kiểm tra trạng thái cửa và lockdown để cập nhật doorAlert
            if (door.doorStatus === 1 && door.doorLockDown === 1) {
                door.doorAlert = 1;
                sendAlertNotification(door.doorId, door.doorName);
                if (warningElement && warningElement.parentElement) {
                    warningElement.parentElement.style.visibility = 'visible';
                    warningElement.classList.add('warning-blink');
                    checkButtonElement.style.visibility = 'visible';
                }
            }
    
            // Lắng nghe sự kiện nhấn nút Check để tắt cảnh báo
            checkButtonElement.addEventListener('click', function () {
                warningElement.classList.remove('warning-blink');
                warningElement.parentElement.style.visibility = 'hidden';
                checkButtonElement.style.visibility = 'hidden';
                // Cập nhật doorAlert về 0 khi người dùng nhấn nút Check
                door.doorAlert = 0;
                sendUpdateRequest(door.doorId,door.door.doorStatus,door.doorLockDown, door.doorAlert,door.doorIp); // Gửi cập nhật doorAlert về server
            });
        }
    }

    document.querySelector('.table').addEventListener('click', (event) => {
        const target = event.target;

        if (target.classList.contains('action-button') && !target.classList.contains('refresh') && !target.classList.contains('check')) {
            const doorElement = target.closest('.row');
            const checkId = doorElement.getAttribute('data-id').split('-')[0];
            if (checkId === 'door') {
                const doorId = doorElement.getAttribute('data-id').replace('door-', '');
                const doorIp = doorElement.querySelector('.ip').textContent.replace('IP: ', '');
                const doorStatusText = doorElement.querySelector('#door-status span').textContent;
                const doorAlert = 0;
                let doorStatus = (doorStatusText === 'OPEN') ? 1 : 0;
                let doorLockDown = target.classList.contains('turn-on') ? 1 : 0;
                if(doorStatus === 1 && doorAlert === 1){
                    doorAlert =1;
                }
                sendUpdateRequest(doorId, doorStatus, doorLockDown,doorAlert, doorIp);
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

    eventSource.addEventListener('door-update', function (event) {
        const door = JSON.parse(event.data);
        updateDoorStatus(door);
    });

    eventSource.addEventListener('door-delete', function (event) {
        const deleteDoor = JSON.parse(event.data);
        const doorRow = document.querySelector(`.row[data-id='door-${deleteDoor.doorId}']`);
        if (doorRow) {
            doorRow.remove();
        }
    });

    eventSource.onerror = function (error) {
        console.error('EventSource failed:', error);
    };

    document.getElementById('deviceSelector').addEventListener('change', function () {
        const selectedDevice = this.value;

        if (selectedDevice === "Light") {
            // Add any additional logic needed when "Light" is selected
        }
    });

    function sendUpdateRequest(doorId, doorStatus, doorLockDown, doorAlert, doorIp) {
        fetch(`door/update/${doorId}`, {
            method: 'PUT',
            headers: {
                'Content-Type': 'application/json',
            },
            body: JSON.stringify({
                doorStatus: doorStatus,
                doorLockDown: doorLockDown,
                doorAlert: doorAlert,
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

    function sendAlertNotification(doorId, doorName) {
        const currentTime = new Date();
        const localTime = new Date(currentTime.getTime() + (7 * 60 * 60 * 1000)).toISOString();
        fetch(`notification/new`, {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json',
            },
            body: JSON.stringify({
                notificationImage: "/picture/door.png",
                notificationTitle: doorName + " " + doorId + " Alert",
                notificationContent: "Door Opened At: ",
                time: localTime
            }),
        })
            .then(response => response.json())
            .then(data => {
                console.log('Sent Notification successful:', data);
            })
            .catch(error => {
                console.error('Error', error);
            });
    }
});
