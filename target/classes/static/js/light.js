document.addEventListener('DOMContentLoaded', () => {    
    const eventSource = new EventSource('/light/stream');
    function updateLightStatus(light) {
        const lightElement = document.querySelector(`.row[data-id='${'light-' + light.lightId}']`);
        // Nếu không tìm thấy đèn, tạo mới một hàng (row)
        if (!lightElement) {
            console.log('No light exist --> created new');
        }

        if (lightElement) {
            lightElement.querySelector('.cell:nth-child(2)').textContent = light.lightName;
            lightElement.querySelector('.ip').textContent = `IP: ${light.lightIp}`;
            const statusElement = lightElement.querySelector('.status span');
            const actionButton = lightElement.querySelector('.action button');

            if (light.lightStatus === 1) {
                statusElement.textContent = 'ON';
                statusElement.className = 'status-on';
                actionButton.textContent = 'turn off';
                actionButton.className = 'action-button turn-off';
            } else if(light.lightStatus === 0){
                statusElement.textContent = 'OFF';
                statusElement.className = 'status-off';
                actionButton.textContent = 'turn on';
                actionButton.className = 'action-button turn-on';
            } else if (light.lightStatus === null){
                statusElement.textContent = 'Disconnected';
                statusElement.className = 'status-on';
                actionButton.textContent = '⟳';
                actionButton.className = 'action-button refresh';
            }
        }
    }

    // Set up event listeners for updating and deleting lights
    document.querySelector('.table').addEventListener('click', (event) => {
        const target = event.target;

        if (target.classList.contains('action-button') && !target.classList.contains('refresh')) {
            const lightElement = target.closest('.row');
            const checkId = lightElement.getAttribute('data-id').split('-')[0];
            if(checkId === 'light'){
                const lightId = lightElement.getAttribute('data-id').replace('light-', '');
                const lightIp = lightElement.querySelector('.ip').textContent.replace('IP: ', '');
                let lightStatus = target.classList.contains('turn-on') ? 1 : 0;
                sendLightUpdateRequest(lightId, lightStatus, lightIp);
            }
        } else if (target.classList.contains('delete-button')) {
            const lightElement = target.closest('.row');
            const checkId = lightElement.getAttribute('data-id').split('-')[0];
            if(checkId === 'light'){
                const lightId = lightElement.getAttribute('data-id').split('-')[1];
                if (confirm('Are you sure?')) {
                    sendLightDeleteRequest(lightId);
                }
            }

        } 
    });

    eventSource.addEventListener('light-update', function(event) {
        const light = JSON.parse(event.data);
        updateLightStatus(light);
    });
    // nhận xóa đèn thông qua sse
    eventSource.addEventListener('light-delete', function(event){
        const deleteLight = JSON.parse(event.data);
        const lightRow = document.querySelector(`.row[data-id='light-${deleteLight.lightId}']`);
        if (lightRow) {
            lightRow.remove();
        }
    });

    eventSource.onerror = function(error) {
        console.error('EventSource failed:', error);
    };

    // Event handler for device selection
    document.getElementById('deviceSelector').addEventListener('change', function() {
        const selectedDevice = this.value;
        
        if (selectedDevice === "Light") {
            // Add any additional logic needed when "Light" is selected
        }
    });

    // Function to send update request
    function sendLightUpdateRequest(lightId, lightStatus, lightIp) {
        fetch(`light/update/${lightId}`, {
            method: 'PUT',
            headers: {
                'Content-Type': 'application/json',
            },
            body: JSON.stringify({
                lightStatus: lightStatus,
                lightIp: lightIp,
            }),
        })
        .then(response => response.json())
        .then(data => {
            console.log('Update successful:', data);
        })
        .catch(error => {
            // console.error('Error updating light:', error);
        });
    }

    // Function to send delete request
    function sendLightDeleteRequest(lightId) {
        fetch(`light/delete/${lightId}`, {
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
            const lightRow = document.querySelector(`.row[data-id='light-${lightId}']`);
            if (lightRow) {
                lightRow.remove();
            }
        })
        .catch(error => {
            console.error('Error deleting light:', error);
        });
    }
});
