import asyncio
import bleak
import gspread
from google.oauth2.service_account import Credentials
import threading
from datetime import datetime
import os
import sys

# Get the directory path of the current script
current_dir = os.path.dirname(os.path.abspath(__file__))
print(current_dir)
package_dir = os.path.dirname(os.path.abspath(current_dir))
print(package_dir)

# Add the directory containing protocols.py to sys.path
protocols_dir = os.path.join(package_dir, "protocols.py")
print(protocols_dir)
sys.path.append(protocols_dir)
import protocols
import json

# Map of Selected Characteristics to monitor
selected_characteristics = {}

# Map of predefined Test Protocols
test_protocols = {
    "protocol_one" : protocols.protocol_one
}

# Scope and Credentials for Service Account
scope = ["https://spreadsheets.google.com/feeds", "https://www.googleapis.com/auth/drive"]
credentials = Credentials.from_service_account_file("BlePrototypes\credentials.json", scopes=scope)

# Authenticate the Service Account
print("Authorizing Service Account...")
spreadsheet_client = gspread.authorize(credentials)
print("Service Account Authorized.")
sheet = None

# Setup for monitor_data
stop_event = threading.Event()

# Stage 0 of the Device Interaction Process, actually connecting to the device
# Parameters:
    # device_address: the device's bluetooth address to connect to
    # disconnect: if the disconnect option is selected in Stage 1, this boolean exits the function while resetting the services and characteristics map.
async def connect_to_device(device_address):

    # Hashmap used to store all service and characteristic names and uuids
    service_characteristic_map = {}
    try:
            
            async with bleak.BleakScanner() as scanner:
                # Attempt to connect to device
                device = await scanner.find_device_by_address(device_address)
                deviceFound = True
                response = {
                    'status' : 'Success',
                    'message' : 'Successfully connected to device: {}'.format(device_address),
                    'client' : bleak.BleakClient(device)
                }

            async with bleak.BleakClient(device) as client:
                # Discover services
                services = await client.get_services()

                # Increment counter so Unknown Services or Characteristics don't get overwritten
                unknownService = 1
                unknownCharacteristic = 1

                # Iterate over discovered services, print name, uuid, and add to map
                for service in services:
                    
                    # If the service name is Unknown, give user opportunity to rename it to something easier to recognize for the current session.
                    if service.description == "Unknown":
                        
                        # Services just have the name as the key
                        # Then uuid as a data point, and a inner map of a services' characteristics as a second data point.
                        service_description = "Unknown" + str(unknownService)
                        service_characteristic_map[service_description] = {'uuid': service.uuid, 'characteristics': {}}     
                        unknownService += 1
                    
                    else:
                        service_characteristic_map[service.description] = {'uuid': service.uuid, 'characteristics': {}}
                        service_description = service.description
                    

                    # Discover characteristics within each service
                    characteristics = service.characteristics

                    # Iterate over discovered characteristics, print name, uuid, and add to map
                    for characteristic in characteristics:
                        # Same Unknown name check as services
                        
                        if service.description == "Unknown":
                            service_characteristic_map[service_description]['characteristics']["Unknown" + str(unknownCharacteristic)] = {'uuid': characteristic.uuid, "can_read" : True if 'read' in characteristic.properties else False, 'can_write' : True if 'write' in characteristic.properties else False}    
                            unknownCharacteristic = unknownCharacteristic+1
                        
                        else:
                            service_characteristic_map[service_description]['characteristics'][characteristic.description] = {'uuid': characteristic.uuid, "can_read" : True if 'read' in characteristic.properties else False, 'can_write' : True if 'write' in characteristic.properties else False}
        
    except bleak.BleakError as e:
        response = {
                'status': 'error',
                'message': 'Connection error: {}'.format(str(e))
        }
    
    # Convert response dictionary to JSON string
    response_json = json.dumps(response)
    return response_json

# Function for accessing user-specified google spreadsheet, a bridge between Stage 0 and Stage 1 of Device Interaction
async def spreadsheet_connection(spreadsheet, worksheet):
    
    try:
        try:
            # Attempt to access spreadsheet
            sheet = spreadsheet_client.open(spreadsheet).worksheet(worksheet)
            response = {
                'status': 'Success',
                'message': 'Connection successful, navigate to the characteristics you wish to monitor',
                'worksheet' : sheet
            }
        except gspread.exceptions.WorksheetNotFound as e:
            response = {
                'status': 'Success',
                'message': 'Connection error: Worksheet does not exist'
            }
    except gspread.exceptions.SpreadsheetNotFound as e:
        response = {
                'status': 'Success',
                'message': 'Connection error: Spreadsheet does not exist, or is not visible to the service account'
        }

    response_json = json.dumps(response)
    return response_json
    
# Stage 3 of Device Interaction, performing the action on the characteristic the user selected, in this case its writing the value
# map: A hashmap of all services and characteristics' names and UUIDs established in Stage 0
# client: The Device itself, is used here to actually access the characteristic specified and write the value
# service: The Service that the user selected in Stage 1
# characteristic: the Characteristic that the user selected in Stage 2
async def write_characteristic(map, client, service, characteristic, value):
        try:
            # Actually writing the value
            await client.write_gatt_char(map[service]['characteristics'][characteristic], value.encode(), True)
            response = {
                    'status': 'Success',
                    'message': 'Characteristic successfully edited to {}'.format(str(value))
            }
        except bleak.BleakError as e:
            response = {
                'status': 'error',
                'message': 'Connection error: {}'.format(str(e))
            }
        response_json = json.dumps(response)
        return response_json
        
        

# Stage 3 of Device Interaction, performing the action on the characteristic the user selected, in this case its writing the value
# map: A hashmap of all services and characteristics' names and UUIDs established in Stage 0
# client: The Device itself, is used here to actually access the characteristic specified and write the value
# service: The Service that the user selected in Stage 1
# characteristic: the Characteristic that the user selected in Stage 2
async def read_characteristic(map, client, service, characteristic):
    # Actually Reading the value
    value = await client.read_gatt_char(map[service]['characteristics'][characteristic])
    response = {
                    'status': 'Success',
                    'message': '{}'.format(str(value))
    }
    response_json = json.dumps(response)
    return response_json

# Stage 4 of Device Interaction 
# Automation of data monitoring where every interval (as specified by user)
# The program reads the values of the marked characteristics and uploads them to the specified google spreadsheet
# Parameters
    # client: The Device itself, is used here to actually access the characteristic specified and write the value
async def monitor_data(client, time, monitor_map, worksheet):
    
    # Setup for the Spreadsheet itself, sorted the characteristics to be monitored
    selected_characteristics = dict(sorted(monitor_map.items()))

    # Establishes the top row of the spreadsheet
    curr_row = 1
    data_to_add = [[]]
    data_to_add[0].append("Date & Time")

    for characteristic, char_data in selected_characteristics.items():
        data_to_add[0].append(characteristic)

    worksheet.insert_rows(data_to_add, row=curr_row)
    curr_row += 1
    
    # Actual Monitoring Loop
    while True:
        # Start of new row of data
        data_to_add = [[]]
    
        # Entering the date and time into the first column, always
        current_datetime = datetime.now()
        formatted_datetime = current_datetime.strftime("%m/%d/%y, %I:%M:%S %p")
        data_to_add[0].append(formatted_datetime)
        
        # Loops through the marked characteristics, gets their values, and adds them to the new row
        for characteristic, char_data in selected_characteristics.items():

            value = await client.read_gatt_char(char_data['characteristic_UUID'])

            # Ensuring the read value doesn't contain invalid characters
            decoded_value = value.decode('utf-8', 'ignore')
            clean_value = ''.join(char for char in decoded_value if char.isprintable())

            data_to_add[0].append(clean_value)

        # Check to ensure that the program does not enter the next row in after recieving the command to stop
        sheet.insert_rows(data_to_add, row=curr_row)
        curr_row += 1
        await asyncio.sleep(time)

# Primary Testing Device Address: 08:6B:D7:7C:6D:10

# Create an event loop
# loop = asyncio.get_event_loop()

# Resets selected Characteristics
selected_characteristics = {}
# loop.run_until_complete(connect_to_device(device_address, False))