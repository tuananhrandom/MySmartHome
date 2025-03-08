document.addEventListener('DOMContentLoaded', () => {
    const notificationEventSource = new EventSource('/notification/stream');
    const addButton = document.querySelector('.add-button');
    const popup = document.getElementById('popup');
    const cancelButton = document.getElementById('cancelButton');
    const doneButton = document.getElementById('doneButton');

    const notificationBell = document.querySelector(".notification");
    const notificationPopup = document.querySelector(".notification-popup");

    // bật tắt pop up
    function toggleNotificationPopup() {
        if (notificationPopup.style.display === "none" || notificationPopup.style.display === "") {
            notificationPopup.style.display = "block";
        } else {
            notificationPopup.style.display = "none";
        }
    }

    // Add a click event listener to the notification bell to toggle the popup
    notificationBell.addEventListener("click", toggleNotificationPopup);

    // Optional: Close the popup when clicking outside
    document.addEventListener("click", function (event) {
        const isClickInside = notificationBell.contains(event.target) || notificationPopup.contains(event.target);

        if (!isClickInside) {
            notificationPopup.style.display = "none";
        }
    });

    //xóa một notification
    const deleteButtons = document.querySelectorAll(".delete-btn");
    deleteButtons.forEach(button => {
        button.addEventListener("click", function () {
            // Lấy phần tử cha của nút hiện tại (notification-item)
            const notificationItem = this.closest(".notification-item");

            // Lấy id của notification từ data-id
            const notificationId = notificationItem.getAttribute("data-id").split('-')[1];

            // Gửi yêu cầu DELETE tới API
            fetch(`/notification/delete/${notificationId}`, {
                method: 'DELETE',
                headers: {
                    'Content-Type': 'application/json'
                }
            })
                .then(response => {
                    if (response.ok) {
                        // Xóa phần tử notification khỏi DOM nếu thành công
                        notificationItem.remove();
                    } else {
                        console.error('Error deleting notification');
                    }
                })
                .catch(error => {
                    console.error('Network error:', error);
                });
        });
    });

    // xóa tất cả thông báo
    const deleteAllBtn = document.getElementById("notification-delete-all");
    deleteAllBtn.addEventListener("click", function () {
        if (confirm("Are you sure?")) {
            fetch(`/notification/delete/all`, {
                method: 'DELETE',
                headers: {
                    'Content-Type': 'application/json'
                }
            })
                .then(response => {
                    if (response.ok) {
                        // Lấy phần tử chứa danh sách thông báo
                        const notificationList = document.querySelector(".notification-list");

                        // Làm rỗng nội dung của danh sách thông báo
                        notificationList.innerHTML = "";
                    } else {
                        console.error('Error deleting notification');
                    }
                })
                .catch(error => {
                    console.error('Network error:', error);
                });
        }
    })

    // nhận trực tiếp thông báo mới
    notificationEventSource.addEventListener('notification-update', function (event) {
        const notification = JSON.parse(event.data);
        streamNewNotification(notification);
    });
    // nhận xóa thông báo thông qua sse
    notificationEventSource.addEventListener('notification-delete', function (event) {
        console.log("da vao den xoa tung notification")
        const deleteNotification = JSON.parse(event.data);
        const notificationItem = document.querySelector(`.notification-item[data-id='notification-${deleteNotification.notificationId}']`);
        if (notificationItem) {
            notificationItem.remove();
        }
    });
    notificationEventSource.addEventListener('notification-delete-all', function (event) {
        // Lấy phần tử chứa danh sách thông báo
        console.log("da vao den xu ly notification");
        const notificationList = document.querySelector(".notification-list");

        // Làm rỗng nội dung của danh sách thô03ng báo
        notificationList.innerHTML = "";
    });

    function streamNewNotification(notification) {
        // Tạo phần tử HTML mới cho thông báo
        const notificationList = document.querySelector(".notification-list");

        // Tạo nội dung HTML cho thông báo mới
        const newNotificationHTML = `
            <div class="notification-item" data-id="notification-${notification.notificationId}">
                <div class="image-placeholder">
                    <img class="notification-image" src="${notification.notificationImage}" alt="Image">
                </div>
                <div class="notification-content">
                    <p class="notification-title">${notification.notificationTitle}</p>
                    <p class="notification-text">${notification.notificationContent} - ${notification.time}</p>
                </div>
                <button id="notification-delete" class="delete-btn">✖</button>
            </div>
        `;

        // Thêm thông báo mới vào đầu danh sách
        notificationList.insertAdjacentHTML('afterbegin', newNotificationHTML);
        assignDeleteNotificationEvent();
    }


    // gán lại các sự kiện sau khi cập nhật danh sách
    function assignDeleteNotificationEvent() {
        // Tìm tất cả các nút xóa và gán sự kiện `click`
        const deleteButtons = document.querySelectorAll(".delete-btn");
        deleteButtons.forEach(button => {
            button.addEventListener("click", function () {
                // Lấy phần tử cha của nút hiện tại (notification-item)
                const notificationItem = this.closest(".notification-item");

                // Lấy id của notification từ data-id
                const notificationId = notificationItem.getAttribute("data-id").split('-')[1];

                // Gửi yêu cầu DELETE tới API
                fetch(`/notification/delete/${notificationId}`, {
                    method: 'DELETE',
                    headers: {
                        'Content-Type': 'application/json'
                    }
                })
                    .then(response => {
                        if (response.ok) {
                            // Xóa phần tử notification khỏi DOM nếu thành công
                            notificationItem.remove();
                        } else {
                            console.error('Error deleting notification');
                        }
                    })
                    .catch(error => {
                        console.error('Network error:', error);
                    });
            });
        });
    }







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
    function showPopup() {
        document.querySelector('.popup-content').classList.add('popup-show');
        document.querySelector('.popup-overlay').classList.add('popup-overlay-show');
    }
    function hidePopup() {
        document.querySelector('.popup-content').classList.remove('popup-show');
        document.querySelector('.popup-overlay').classList.remove('popup-overlay-show');
    }

    //nút add để thêm mới thiết bị
    document.getElementById("doneButton").addEventListener("click", function () {
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
            const selectedDevice = document.getElementById('deviceSelector').value
            changeFragment(selectedDevice);
        }
    });

    // change HTML table

    document.getElementById('deviceSelector').addEventListener('change', function () {
        const selectedDevice = this.value;
        changeFragment(selectedDevice);
    });
    function changeFragment(selectedDevice) {
        fetch(`/devices/${selectedDevice}`)
            .then(response => response.text())
            .then(html => {
                // Replace the inner content of the .table div
                document.querySelector('.table').innerHTML = html;
            })
            .catch(error => console.error('Error fetching device data:', error));
    }  
});
