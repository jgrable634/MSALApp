import gspread
from google.oauth2.service_account import Credentials

# Define the scope and credentials
scope = ["https://spreadsheets.google.com/feeds", "https://www.googleapis.com/auth/drive"]
credentials = Credentials.from_service_account_file("credentials.json", scopes=scope)

# Authenticate and open the Google Sheet
client = gspread.authorize(credentials)
sheet = client.open("Bruh Moment").sheet1

# Example data
data = [
    ["Name", "Age", "City"],
    ["John", 25, "New York"],
    ["Alice", 30, "London"],
    ["Bob", 40, "Paris"]
]

# Upload data to the Google Sheet
sheet.insert_rows(data, row=1)  # Insert data starting from the first row

print("Data uploaded successfully.")