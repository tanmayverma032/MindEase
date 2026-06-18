from pymongo import MongoClient
import os
from dotenv import load_dotenv

load_dotenv()

MONGO_URL = os.getenv("MONGO_URL")

if not MONGO_URL:
    raise Exception("MONGO_URL not found")

client = MongoClient(MONGO_URL)

db = client["stress_db"]

users_collection = db["users"]
history_collection = db["history"]
pending_users_collection = db["pending_users"]