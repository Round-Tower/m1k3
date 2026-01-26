#!/usr/bin/env python3
"""Convert conversational_enhancement.json to CuratedKnowledgeBase format."""

import json
from pathlib import Path

# Input/output paths
input_file = Path("composeApp/src/commonMain/composeResources/files/conversational_enhancement.json")
output_file = Path("composeApp/src/commonMain/composeResources/files/conversational_enhancement.json")
backup_file = Path("composeApp/src/commonMain/composeResources/files/conversational_enhancement.json.bak")

# Load old format
with open(input_file) as f:
    old_data = json.load(f)

# Backup original
with open(backup_file, 'w') as f:
    json.dump(old_data, f, indent=2)

# Transform to new format
new_data = {
    "category": "conversational",
    "description": "Conversational patterns and engagement strategies for natural interactions",
    "version": old_data["version"],
    "lastUpdated": old_data["metadata"]["created_at"],
    "facts": []
}

# Convert each document to a fact
for doc in old_data["documents"]:
    fact = {
        "id": doc["id"],
        "question": doc["title"],
        "answer": doc["content"],
        "importance": 0.8,  # Conversational content has good importance
        "tags": doc["metadata"]["tags"]
    }
    new_data["facts"].append(fact)

# Write transformed data
with open(output_file, 'w') as f:
    json.dump(new_data, f, indent=2)

print(f"✓ Converted {len(new_data['facts'])} documents")
print(f"✓ Backup saved to {backup_file}")
print(f"✓ New format written to {output_file}")
