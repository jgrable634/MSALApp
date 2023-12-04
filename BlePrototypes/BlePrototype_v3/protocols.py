def tag_group(tag):
    def decorator(func):
        setattr(func, 'tag', tag)
        return func
    return decorator

# Instructions for creating protocols:
    # first, insert @tag_group('test_protocols')
    # Pass in client as the only parameter
    # Create an empty map to store the characteristics to be monitored, return that at end of protocol
    # Add characteristics to map in the following format: map[characteristic_name] = {'service_uuid' : service_uuid, 'characteristic_uuid' : characteristic_uuid}
    # use client.write_gatt_char(characteristic_uuid, text_to_enter.encode(), True) to change characteristic's values

@tag_group('test_protocols')
async def protocol_one(client):
    selected_characteristics = {}
    await client.write_gatt_char("d1afa2e2-4421-43a3-93bd-3cfb6c6c19ba", "BetterTest".encode('utf-8'), True)
    selected_characteristics["Data"] = {'service_UUID' : 'e150f046-8ae5-4b6b-a367-276d7e9212e8', 'characteristic_UUID' : 'd1afa2e2-4421-43a3-93bd-3cfb6c6c19ba'}
    selected_characteristics["Device Name"] = {'service_UUID' : '00001800-0000-1000-8000-00805f9b34fb', 'characteristic_UUID' : '00002a00-0000-1000-8000-00805f9b34fb'}
    return selected_characteristics