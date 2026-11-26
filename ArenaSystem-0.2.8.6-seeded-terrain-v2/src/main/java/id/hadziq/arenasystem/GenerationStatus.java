package id.hadziq.arenasystem;

/** Persisted lifecycle states for arena creation, regeneration, and recovery. */
enum GenerationStatus {
    COMPLETE,
    RESTORING,
    SCANNING,
    BUILDING,
    RECOVERY_REQUIRED
}
