import asyncio
import bleak

# Governs if the user connects to a different device or exits the program
proceed = True

# Function to at any point in the Device Interaction Process apart from Stage 3 to relist any part of the Services or Characteristics
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


# Function designed to ease the process of getting user input. Also has functionality for searching through services and characteristic names if called for
# expected_responses is a list used for the get_input function throughout the Device Interaction Process, and is defined uniquely everytime the function is called
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
# device_address: the device's bluetooth address to connect to
# disconnect: if the disconnect option is selected in Stage 1, this boolean exits the function while resetting the services and characteristics map.
async def connect_to_device(device_address, disconnect):
    # Hashmap used to store all service and characteristic names and uuids
    service_characteristic_map = {}

    if disconnect is False:
        try:
            deviceFound = False
            async with bleak.BleakScanner() as scanner:
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
                    if service.description == "Unknown":
                        print("It appears that this service does not have a proper name")
                        user_input = input("Do you know what this services' name is? If so, provide it now, otherwise, enter n, and the service name will be left as Unknown:")
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
                        if service.description == "Unknown":
                            print("It appears that this characteristic does not have a proper name")
                            user_input = input("Do you know what this characteristic's name is? If so, provide it now, otherwise, enter n, and the characteristic name will be left as Unknown:")
                            if user_input != 'n':
                                service_characteristic_map[service_description]['characteristics'][user_input] = characteristic.uuid
                                service_description = user_input
                            else: 
                                service_characteristic_map[service_description]['characteristics']["Unknown" + str(unknownCharacteristic)] = characteristic.uuid    
                                unknownCharacteristic = unknownCharacteristic+1
                        else:
                            service_characteristic_map[service_description]['characteristics'][characteristic.description] = characteristic.uuid

                # Stage 1 of the Device Interaction Process
                await select_service(service_characteristic_map, client, None)

            print("Successfully disconnected from device:", device_address)
        except bleak.BleakError as e:
            print("Sorry, there was a Connection error, device is disconnected. Actual Error:", str(e))


# Stage 1 of Device Interation; Figure out what service the user is accessing
# Parameters:
# map: A hashmap of all services and characteristics' names and UUIDs established in Stage 0
# client: The Device itself, is ultimately passed to Stage 3: Writing or Reading the Characteristic's value
# user_action: This parameter is used as a back door after returning from relist() and skipping back to where the user left off
async def select_service(map, client, user_action):
    # This is a check put in place for returning to where you left off after relisting things 
    # Used to skip giving an action you want to do, going to part two of Stage One
    if user_action is None:
        expected_responses = ['r', 'm', 'l', 'd']
        user_action = get_input("Would you like to read (r) or modify (m) a characteristic's value, you can also list (l) the services and characteristics, and disconnect (d) from the device", expected_responses, None)
    if user_action == 'r' or user_action == 'm':
        # Figure out what service the user wants to access, then go to Stage Two, select_characteristics
        # User is given the option of going back or relisting, as per usual
        expected_responses = ['l', 'b']
        user_input = get_input("Enter the name of the service you want to access, you can list (l) at any time and you can go back (b)", expected_responses, map)
        if(user_input == 'l'):
            relist(map, None)
            await select_service(map, client, user_action)
        if(user_input == 'b'):
            await select_service(map, client, None)
        else:
            await select_characteristic(map, client, user_input, user_action)
    elif user_action == 'l':
        relist(map, None)
        await select_service(map, client, None)
    else:
        # If 'd' is given, disconnect from device and go back to usual
        await connect_to_device(device_address, True)

# Stage 2 of Device Interation Process; Figure out what characteristic the user is accessing
# Parameters:
# map: A hashmap of all services and characteristics' names and UUIDs established in Stage 0
# client: The Device itself, is ultimately passed to Stage 3: Writing or Reading the Characteristic's value
# service: The Service that the user selected in Stage 1
# user_action: The action that the user selected in Stage 1, either r for Read, or w for Write
async def select_characteristic(map, client, service, user_action):
    expected_responses = ['l', 'b']
    # Still gives user option to relist or go back to Stage 1
    user_input = get_input("Enter the name of the characteristic you want to access, you can list (l) at any time and you can go back (b)", expected_responses, map[service]['characteristics'])
    if(user_input == 'l'):
        relist(map, service)
        await select_characteristic(map, client, service, user_action)
    if(user_input == 'b'):
        await select_service(map, client, user_action)
    else:
        # Goes to either form of Stage 3, be it Write or Read, based on the action the user selected in Stage 1
        if user_action == 'm':
            await write_characteristic(map, client, service, user_input)
            await select_service(map, client, None)
        else:
            await read_characteristic(map, client, service, user_input)
            await select_service(map, client, None)

# Stage 3 of Device Interaction, performing the action on the characteristic the user selected, in this case its writing the value
# map: A hashmap of all services and characteristics' names and UUIDs established in Stage 0
# client: The Device itself, is used here to actually access the characteristic specified and write the value
# service: The Service that the user selected in Stage 1
# characteristic: the Characteristic that the user selected in Stage 2
async def write_characteristic(map, client, service, characteristic):
        user_input = input("What would you like to rewrite the value of " + characteristic + " to. You can also go back (b):")
        if user_input == 'b':
            await select_characteristic(map, client, service, 'w')
        else:
            # Actually writing the value
            await client.write_gatt_char(map[service]['characteristics'][characteristic], user_input.encode(), True)
            print("Characteristic value successfully edited.")

# Stage 3 of Device Interaction, performing the action on the characteristic the user selected, in this case its writing the value
# map: A hashmap of all services and characteristics' names and UUIDs established in Stage 0
# client: The Device itself, is used here to actually access the characteristic specified and write the value
# service: The Service that the user selected in Stage 1
# characteristic: the Characteristic that the user selected in Stage 2
async def read_characteristic(map, client, service, characteristic):
    # async with client:
        # Actually Reading the value
        value = await client.read_gatt_char(map[service]['characteristics'][characteristic])
        print("Characteristic's Value is:",value.decode())

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
    loop.run_until_complete(connect_to_device(device_address, False))

# Used for Testing, scans all nearby devices and prints their address
'''
async def scan_devices():
    try:
        devices = await bleak.discover()
        for device in devices:
            print("Device found:", device.address)

    except bleak.BleakError as e:
        print("Scanning error:", str(e))

# Create an event loop
loop = asyncio.get_event_loop()

# Call the function to scan for devices
loop.run_until_complete(scan_devices())
'''