# Offline-first local storage with sync-ready schema

The app operates 100% offline initially, but a custom backend authenticated via an API key is in development for future sync. We will store all data locally in Room with a sync-ready schema from day one: client-generated UUID primary keys, `createdAt`/`updatedAt` timestamps, `deletedAt` soft-delete tombstones, and a `syncStatus` (`PENDING`/`SYNCED`) flag, accessed through a repository boundary. This avoids a breaking primary-key and history migration when the backend API arrives, while supporting immediate local JSON export/import.
