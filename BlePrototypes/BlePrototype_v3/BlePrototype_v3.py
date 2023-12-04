import asyncio
import bleak
import gspread
from google.oauth2.service_account import Credentials
import threading
from datetime import datetime
import protocols

# Governs if the user connects to a different device or exits the program
proceed = True

# Map of Selected Characteristics to monitor
selected_characteristics = {}

# Map of predefined Test Protocols
test_protocols = {
    "protocol_one" : protocols.protocol_one
}

# Scope and Credentials for Service Account
scope = ["https://spreadsheets.google.com/feeds", "https://www.googleapis.com/auth/drive"]
credentials = Credentials.from_service_account_file("Internship\BlePrototypes\credentials.json", scopes=scope)

# Authenticate the Service Account
print("Authorizing Service Account...")
spreadsheet_client = gspread.authorize(credentials)
print("Service Account Authorized.")
sheet = None

# Setup for monitor_data
stop_event = threading.Event()


# Function designed to ease the process of getting user input. 
# Also has functionality for searching through services and characteristic names if called for
# expected_responses is a list used for the get_input function throughout the Device Interaction Process
# expected_responses is defined uniquely everytime the function is called
def get_input(question, expected_responses, map):
    correct = False
    while(correct is False):

        user_input = input(question)
        for ans in expected_responses:
            if user_input == ans:
                correct = True
                break
        if map is not None:
            if user_input in map:
                correct = True
        if correct is False:
            print("Sorry, your response is not a valid answer to the prompt, please try again")
    return user_input


# Function to relist any part of the Services or Characteristics in either Stage 1 or 2
def relist(map, service):
    # Check if the user is at Stage 2, tells them what Service they are at for convenience
    if service is not None:
        print(service, "is the currently selected service")
    # User can list everything, only services, or only characteristics of a particular service
    user_input = get_input("You can relist everything (e), all services (s), or enter the name of a service to list all characteristics", ['e', 's'], map)
    if user_input == 'e':
        list_all(map)
    elif user_input == 's':
        list_services(map)
    else:
        list_characteristics(map, user_input)

# Function to list all services and characteristics currently discovered
def list_all(map):
    for service_name, service_data in map.items():
        print("Service Name:", service_name, "with UUID:", service_data['uuid'])
        for characteristic_name, characteristic_uuid in service_data['characteristics'].items():
            print("\tCharacteristic Name:", characteristic_name, "with UUID:", characteristic_uuid)

# Function to list all services only
def list_services(map):
    for service_name, service_data in map.items():
        print("Service Name:", service_name, "with UUID:", service_data['uuid'])

# Function to list all characteristics of a given service
def list_characteristics(map, service):
    print("Service:", service)
    for characteristic_name, characteristic_uuid in map[service]['characteristics'].items():
        print("\tCharacteristic Name:", characteristic_name, "with UUID:", characteristic_uuid)


# Stage 0 of the Device Interaction Process, actually connecting to the device
# Parameters:
    # device_address: the device's bluetooth address to connect to
    # disconnect: if the disconnect option is selected in Stage 1, this boolean exits the function while resetting the services and characteristics map.
async def connect_to_device(device_address, disconnect):

    # Hashmap used to store all service and characteristic names and uuids
    service_characteristic_map = {}

    if disconnect is False:
        try:
            
            async with bleak.BleakScanner() as scanner:
                
                deviceFound = False
                # Loop so multiple attempts to connect can occur
                while deviceFound is False:
                    
                    try:
                        # Attempt to connect to device
                        device = await scanner.find_device_by_address(device_address)
                        deviceFound = True
                    
                    except bleak.BleakError as e:
                        
                        # If it fails, inform user and prompt them to try again
                        # Also these two user inputs do not use get_input because the user has freedom to enter anything, because there is only one response that is searched for
                        user_input = input("Connection failed, would you like to try to connect again? Enter y for yes, or anything else to exit the program:")
                        
                        if user_input == 'y':
                            user_input = input("Would you like to reenter the device address in case there was a mistake? Enter y for yes, or anything else for no")
                            
                            if user_input == 'y':
                                device_address = input("Reenter device address: ")
                       
                        else:
                            exit()
                        
            async with bleak.BleakClient(device) as client:
                print("Successfully connected to device:", device_address)

                # Discover services
                services = await client.get_services()

                # Increment counter so Unknown Services or Characteristics don't get overwritten
                unknownService = 1
                unknownCharacteristic = 1

                # Iterate over discovered services, print name, uuid, and add to map
                for service in services:
                    print("Found Service:", service.description, "with UUID:",service.uuid)
                    
                    # If the service name is Unknown, give user opportunity to rename it to something easier to recognize for the current session.
                    if service.description == "Unknown":
                        print("It appears that this service does not have a proper name")
                        user_input = input("Do you know what this services' name is? If so, provide it now, otherwise, enter n, and the service name will be left as Unknown:")
                        
                        # Services just have the name as the key
                        # Then uuid as a data point, and a inner map of a services' characteristics as a second data point.
                        
                        if user_input != 'n':
                            service_characteristic_map[user_input] = {'uuid': service.uuid, 'characteristics': {}}
                            service_description = user_input
                        
                        else: 
                            service_description = "Unknown" + str(unknownService)
                            service_characteristic_map[service_description] = {'uuid': service.uuid, 'characteristics': {}}     
                            unknownService = unknownService+1
                    
                    else:
                        service_characteristic_map[service.description] = {'uuid': service.uuid, 'characteristics': {}}
                        service_description = service.description
                    

                    # Discover characteristics within each service
                    characteristics = service.characteristics

                    # Iterate over discovered characteristics, print name, uuid, and add to map
                    for characteristic in characteristics:
                        print("\tFound Characteristic:", characteristic.description, "with UUID:", characteristic.uuid)
                        # Same Unknown name check as services
                        
                        if service.description == "Unknown":
                            print("It appears that this characteristic does not have a proper name")
                            user_input = input("Do you know what this characteristic's name is? If so, provide it now, otherwise, enter n, and the characteristic name will be left as Unknown:")
                            
                            # Due to frustration with getting properties of characteristics outside of this iteration, 
                            # Forced to add can_read and can_write to the map to keep track of that when it is used later
                            # Of course the uuid is stored as well
                            
                            if user_input != 'n':
                                service_characteristic_map[service_description]['characteristics'][user_input] = {'uuid': characteristic.uuid, "can_read" : True if 'read' in characteristic.properties else False, 'can_write' : True if 'write' in characteristic.properties else False}
                                service_description = user_input
                            
                            else: 
                                service_characteristic_map[service_description]['characteristics']["Unknown" + str(unknownCharacteristic)] = {'uuid': characteristic.uuid, "can_read" : True if 'read' in characteristic.properties else False, 'can_write' : True if 'write' in characteristic.properties else False}    
                                unknownCharacteristic = unknownCharacteristic+1
                        
                        else:
                            service_characteristic_map[service_description]['characteristics'][characteristic.description] = {'uuid': characteristic.uuid, "can_read" : True if 'read' in characteristic.properties else False, 'can_write' : True if 'write' in characteristic.properties else False}
                
                # Next step in Device Interaction
                await spreadsheet_connection(service_characteristic_map, client)

            print("Successfully disconnected from device:", device_address)
        
        except bleak.BleakError as e:
            print("Sorry, there was a Connection error, device is disconnected. Actual Error:", str(e))


# Function for accessing user-specified google spreadsheet, a bridge between Stage 0 and Stage 1 of Device Interaction
async def spreadsheet_connection(map, client):
    
    # Loop so users can try multiple times to connect
    sheetFound = False
    while sheetFound is False:
        # Getting Spreadsheet name
        user_input = input("Please provide the name of your google spreadsheet, you can also enter 'd' to disconnect from the device and connect to a different one:")
        
        if user_input == 'd':
            # If 'd' is given, disconnect from device and go back to usual
            await connect_to_device(device_address, True)
            break
        
        # Getting Specific worksheet name
        user_input2 = input("Also provide the specific sheet to be accessed (Please provide an empty one, otherwise data will be overwritten):")
        
        try:
            try:
                # Attempt to access spreadsheet
                print("Accessing Spreadsheet...")
                global sheet
                sheet = spreadsheet_client.open(user_input).worksheet(user_input2)
                sheetFound = True
                print("Connection Successful, please navigate to the characteristics you wish to monitor")
                
                # Next Step in Device Interaction
                await select_protocol(map, client)

            except gspread.exceptions.WorksheetNotFound as e:
                print("Sorry, the provided worksheet doesn't exist, make sure you entered it correctly, it is case sensitive.")
        
        except gspread.exceptions.SpreadsheetNotFound as e:
            print("Sorry, the spreadsheet provided is not visible to the service account, to fix that, share your google sheet with 'python-program@internship-sheets.iam.gserviceaccount.com' and give editor permissions, then try again.")
            print("Alternatively the spreadsheet simply doesn't exist.")        


# Gives the user the option to use pre-defined protocols to quickly begin monitoring specific characteristics and changing particular data points
async def select_protocol(map, client):

    # Print available test protocols
    print("The currently available test protocols are:")
    
    global test_protocols
    for protocol_name in test_protocols:
        print(protocol_name)
    
    expected_responses = ['c', 'b']
    user_input = get_input("If you wish to use a Test Protocol, please enter the name now. Otherwise, you can continue (c) on to mark characteristics manually, or you can go back (b): ", expected_responses, test_protocols)
    
    if user_input == 'c':
        # Go to Stage 1 and don't use a protocol
        await select_service(map, client)
    
    elif user_input == 'b':
        # Go back to the previous step
        await spreadsheet_connection(map, client)
    
    else:
        # Run the given test protocol and begin monitoring immediately
        global selected_characteristics
        selected_characteristics = await test_protocols[user_input](client)
        await monitor_data(client)


# Stage 1 of Device Interation; Figure out what service the user is accessing
# Parameters:
    # map: A hashmap of all services and characteristics' names and UUIDs established in Stage 0
    # client: The Device itself, is ultimately passed to Stage 3: Writing or Reading the Characteristic's value
async def select_service(map, client):
    
    # Figure out what service the user wants to access, then go to Stage 2, select_characteristics, user is given the option of going back or relisting, and ultimately the option to begin monitoring data, AKA Stage 4
    expected_responses = ['l', 'b', 'done']
    user_input = get_input("Enter the name of the service you want to access, you can list (l) at any time and you can go back (b), finally you can type 'done' to end your search and proceed to monitoring:", expected_responses, map)
    
    if(user_input == 'l'):
        relist(map, None)
        await select_service(map, client)
    
    elif(user_input == 'b'):
        await select_protocol(map, client)
    
    elif(user_input == 'done'):
        await monitor_data(client)
    
    else:
        await select_characteristic(map, client, user_input)
    
        
# Stage 2 of Device Interation; Figure out what characteristic the user is accessing
# Parameters:
    # map: A hashmap of all services and characteristics' names and UUIDs established in Stage 0
    # client: The Device itself, is ultimately passed to Stage 3: Writing or Reading the Characteristic's value
    # service: The Service that the user selected in Stage 1
async def select_characteristic(map, client, service):
    
    # Still gives user option to relist or go back to Stage 1, or begin monitoring data, AKA Stage 4
    expected_responses = ['l', 'b', 'done']
    user_input = get_input("Enter the name of the characteristic you want to access, you can list (l) at any time and you can go back (b), finally you can type 'done' to end your search and proceed to monitoring:", expected_responses, map[service]['characteristics'])
    
    if(user_input == 'l'):
        relist(map, service)
        await select_characteristic(map, client, service)
    
    elif(user_input == 'b'):
        await select_service(map, client)
    
    elif(user_input == 'done'):
        await monitor_data(client)
    
    else:
        await view_characteristic(map, client, service, user_input)


# Stage 3 of Device Interaction, a somewhat in depth look at the characteristic selected by the user
# Allows user to mark the characteristic for monitoring, or manually change the value
# Parameters    
    # map: A hashmap of all services and characteristics' names and UUIDs established in Stage 0
    # client: The Device itself, is used here to actually access the characteristic specified and write the value
    # service: The Service that the user selected in Stage 1
    # characteristic: the Characteristic that the user selected in Stage 2
async def view_characteristic(map, client, service, characteristic):
        
        print("Currently viewed characteristic:",characteristic,"from service:",service)
        # Checks if the characteristic has been marked for monitoring
        added = False

        if characteristic in selected_characteristics:
            added = True

        print("Current Status:", "Marked to Monitor" if added is True else "Not Marked to Monitor")

        # If it is, give user option to remove it
        if added is True:
            expected_responses = ['r', 'b', 'w']
            user_input = get_input("You can remove (r) this characteristic from the Marked Characteristics or you can go back (b), you can also write (w) the value to something different:", expected_responses, None)
            
            if user_input == 'r':
                # Removing the characteristic
                selected_characteristics.pop(characteristic)
                await view_characteristic(map, client, service, characteristic)
            
            elif user_input == 'w':
                # Checks to ensure the characteristic can be read from or written to
                characteristic_data = map[service]['characteristics'][characteristic]
                
                if characteristic_data['can_read'] is False:
                    print("Sorry, ", characteristic," is unreadable, try a different characteristic")
                    await select_characteristic(map, client, service)
                
                elif characteristic_data['can_write'] is False:
                    print("Sorry, ", characteristic,"'s value cannot be modified, try a different characteristic")
                    await select_characteristic(map, client, service)
                
                else:
                    # Reading the value to tell the user
                    value = await client.read_gatt_char(characteristic_data['uuid'])
                    decoded_value = value.decode('utf-8', 'ignore')
                    clean_value = ''.join(char for char in decoded_value if char.isprintable())

                    user_input = input("What would you like to rewrite the value of " + characteristic + " to? Current value is: "+ clean_value + ". You can also go back (b):")
                    
                    if user_input == 'b':
                        await view_characteristic(map, client, service, characteristic)
                   
                    else:
                        # Actually writing the value
                        await client.write_gatt_char(map[service]['characteristics'][characteristic], user_input.encode(), True)
                        print("Characteristic value successfully edited to", user_input)
                        await view_characteristic(map, client, service, characteristic)
            
            else:
                await select_characteristic(map, client, service)

        # If not, give user option to add it
        else:
            expected_responses = ['a', 'b', 'w']
            user_input = get_input("You can add (a) this characteristic from the Marked Characteristics or you can go back (b), you can also write (w) the value to something different:", expected_responses, None)
            
            if user_input == 'a':
                characteristic_data = map[service]['characteristics'][characteristic]['can_read']

                if characteristic_data:
                    selected_characteristics[characteristic] = {'service_UUID': map[service]['uuid'], 'characteristic_UUID': map[service]['characteristics'][characteristic]['uuid']}
                    print("Characteristic successfully marked to monitor")
                    await view_characteristic(map, client, service, characteristic)

                else:
                    print("Invalid: Characteristic's value cannot be read, try selecting a different characteristic")
                    await select_characteristic(map, client, service)

            elif user_input == 'w':
                # Checks to ensure the characteristic can be read from or written to
                characteristic_data = map[service]['characteristics'][characteristic]
                if characteristic_data['can_read'] is False:
                    print("Sorry, ", characteristic," is unreadable, try a different characteristic")
                    await select_characteristic(map, client, service)

                elif characteristic_data['can_write'] is False:
                    print("Sorry, ", characteristic,"'s value cannot be modified, try a different characteristic")
                    await select_characteristic(map, client, service)
                
                else:
                    # Reading the value to tell the user
                    value = await client.read_gatt_char(characteristic_data['uuid'])
                    decoded_value = value.decode('utf-8', 'ignore')
                    clean_value = ''.join(char for char in decoded_value if char.isprintable())

                    user_input = input("What would you like to rewrite the value of " + characteristic + " to? Current value is:", clean_value, "You can also go back (b):")
                    
                    if user_input == 'b':
                        await view_characteristic(map, client, service, characteristic)
                    
                    else:
                        # Actually writing the value
                        await client.write_gatt_char(map[service]['characteristics'][characteristic]['uuid'], user_input.encode(), True)
                        print("Characteristic value successfully edited to", user_input)
                        await view_characteristic(map, client, service, characteristic)
            
            else:
                await select_characteristic(map, client, service)


# Helper Functions to allow for stopping Data Monitoring
def stop_program():
    print("Ending Monitoring, standby...")
    stop_event.set()

def input_listener():
    input("Press Enter to stop monitoring data\n")
    stop_program()


# Stage 4 of Device Interaction 
# Automation of data monitoring where every interval (as specified by user)
# The program reads the values of the marked characteristics and uploads them to the specified google spreadsheet
# Parameters
    # client: The Device itself, is used here to actually access the characteristic specified and write the value
async def monitor_data(client):
    valid_input = False

    while not valid_input:
        user_input = input("Enter the amount of time between data captures in seconds:")
        # Ensures that the user's input is a valid number

        try:
            time = float(user_input)  # Try converting the input to a float
            valid_input = True  # If successful, mark the input as valid

        except ValueError:
            print("Invalid input. Please enter a valid number.")
    
    # Setup for allowing user to stop data monitoring
    input_thread = threading.Thread(target=input_listener)
    input_thread.start()

    # Setup for the Spreadsheet itself, sorted the characteristics to be monitored
    global selected_characteristics
    selected_characteristics = dict(sorted(selected_characteristics.items()))

    # Establishes the top row of the spreadsheet
    curr_row = 1
    data_to_add = [[]]
    data_to_add[0].append("Date & Time")

    for characteristic, char_data in selected_characteristics.items():
        data_to_add[0].append(characteristic)

    sheet.insert_rows(data_to_add, row=curr_row)
    curr_row += 1
    
    # Actual Monitoring Loop
    while not stop_event.is_set():
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
        if not stop_event.is_set():
            sheet.insert_rows(data_to_add, row=curr_row)
            curr_row += 1
            await asyncio.sleep(time)

    print("Monitoring Stopped ")
    input_thread.join()

# Primary Testing Device Address: 08:6B:D7:7C:6D:10

# Create an event loop
loop = asyncio.get_event_loop()

# This section actually starts up the program, proceed is used here in the event of an error
while proceed is True:

    device_address = input("Enter device bluetooth address, or if done enter e to exit: ")

    if device_address == 'e':
        exit()

    if device_address == 'test':
        device_address = '08:6B:D7:7C:6D:10'

    # Resets selected Characteristics
    selected_characteristics = {}
    loop.run_until_complete(connect_to_device(device_address, False))