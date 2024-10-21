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
                console.log("doorAlertStatus = " + door.doorAlert);
                if (warningElement && warningElement.parentElement) {
                    warningElement.parentElement.style.visibility = 'visible';
                    warningElement.classList.add('warning-blink');
                    checkButtonElement.style.visibility = 'visible';
                    console.log("bật cảnh báo nhé");
                }
                console.log("chạy đến hết if");
            } else {
                if (warningElement && warningElement.parentElement) {
                    warningElement.classList.remove('warning-blink');
                    warningElement.parentElement.style.visibility = 'hidden';
                    checkButtonElement.style.visibility = 'hidden';
                    console.log("tắt cảnh báo nhé");
                }
                console.log("đến hết else số 2");
            }
            // Lắng nghe sự kiện nhấn nút Check để tắt cảnh báo
            checkButtonElement.addEventListener('click', function () {
                warningElement.classList.remove('warning-blink');
                warningElement.parentElement.style.visibility = 'hidden';
                checkButtonElement.style.visibility = 'hidden';
                // Cập nhật doorAlert về 0 khi người dùng nhấn nút Check
                door.doorAlert = 0;
                sendUpdateAlert(door.doorId, door.doorAlert); // Gửi cập nhật doorAlert về server
            });
        }
    }

    document.querySelector('.table').addEventListener('click', (event) => {
        const target = event.target;
        reUpdateAllDoors();
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
                if (doorStatus === 1 && doorAlert === 1) {
                    doorAlert = 1;
                }
                sendUpdateRequest(doorId, doorStatus, doorLockDown, doorAlert, doorIp);


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
        //thêm cập nhật trạng thái của cảnh báo và nút check
        // Cập nhật hiệu ứng cảnh báo sau khi trạng thái thay đổi
        // const warningElement = doorElement.querySelector('#door-warning span');
        // const checkButtonElement = doorElement.querySelector('#check-btn');
        // if (doorAlert === 1) {
        //     warningElement.parentElement.style.visibility = 'visible';
        //     warningElement.classList.add('warning-blink');
        //     checkButtonElement.style.visibility = 'visible';
        // } else {
        //     warningElement.classList.remove('warning-blink');
        //     warningElement.parentElement.style.visibility = 'hidden';
        //     checkButtonElement.style.visibility = 'hidden';
        // }

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
    function sendUpdateAlert(doorId, doorAlert) {
        fetch(`door/alert/${doorId}`, {
            method: 'PUT',
            headers: {
                'Content-Type': 'application/json',
            },
            body: JSON.stringify({
                doorAlert: doorAlert,
            }),
        })
            .then(response => response.json())
            .then(data => {
                console.log('Update Alert successful:', data);
            })
            .catch(error => {
                console.error('Error updating Door-Alert:', error);
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

    // gán lại các sự kiện khi chuyển tab
    function reUpdateAllDoors(){
        const doorRows = document.querySelectorAll('.row[data-id^="door-"]');
        doorRows.forEach(row => {
            const doorId = row.getAttribute('data-id').replace('door-', ''); // Lấy doorId
            const doorIp = row.querySelector('.ip').textContent.replace('IP: ', ''); // Lấy doorIp
            const doorStatusText = row.querySelector('#door-status span').textContent; // Lấy trạng thái cửa (OPEN/CLOSE/Disconnected)
            const doorLockDownText = row.querySelector('#door-lockdown span').textContent; // Lấy trạng thái cảnh báo (ON/OFF)
            const doorAlert = row.getAttribute('doorAlert') // lấy doorAlert
            console.log("đang gửi lại dữ liệu update")
            // Chuyển trạng thái cửa thành giá trị số
            let doorStatus;
            if (doorStatusText === 'OPEN') {
                doorStatus = 1;
            } else if (doorStatusText === 'CLOSE') {
                doorStatus = 0;
            } else {
                doorStatus = null; // Nếu Disconnected
            }
    
            // Chuyển trạng thái lockdown thành giá trị số
            let doorLockDown;
            if (doorLockDownText === 'ON') {
                doorLockDown = 1;
            } else if (doorLockDownText === 'OFF') {
                doorLockDown = 0;
            } else {
                doorLockDown = null; // Nếu Null
            }
    
            // Nếu cần thêm các trường khác, có thể tiếp tục thu thập ở đây
    
            // Gửi yêu cầu cập nhật cho từng cửa
            sendUpdateRequest(doorId, doorStatus, doorLockDown, doorAlert, doorIp);
        });
    }

});
