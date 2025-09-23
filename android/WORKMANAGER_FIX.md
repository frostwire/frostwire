# WorkManager JobScheduler Alarm Limit Fix

## Problem Description

FrostWire Android was experiencing crashes due to hitting Android's JobScheduler alarm limit of 500 concurrent alarms:

```
Fatal Exception: java.lang.IllegalStateException
JobScheduler 100 job limit exceeded. In JobScheduler there are 32 jobs from WorkManager. 
There are 6 jobs tracked by WorkManager's database; the Configuration limit is 20.
```

## Root Cause Analysis

1. **Duplicate Daemon Creation**: Both `MainApplication.onCreate()` and `EngineForegroundService` were creating separate `NotificationUpdateDaemon` instances
2. **Exponential Job Creation**: The `NotificationWorker` was creating new `NotificationUpdateDaemon` instances every 15 minutes, leading to exponential growth
3. **No Job Cleanup**: WorkManager jobs were not being properly cancelled when services shut down
4. **No Throttling**: No mechanisms to prevent rapid re-scheduling of the same work
5. **Default WorkManager Limits**: Using default WorkManager scheduler limit of 100 jobs

## Solution Implemented

### 1. WorkManager Configuration (`MainApplication.java`)

```java
@Override
public Configuration getWorkManagerConfiguration() {
    return new Configuration.Builder()
            .setMaxSchedulerLimit(20) // Reduced from default 100
            .setMinimumLoggingLevel(BuildConfig.DEBUG ? android.util.Log.DEBUG : android.util.Log.ERROR)
            .build();
}
```

- Reduced maximum scheduler limit from 100 to 20 jobs
- Added custom WorkManager initialization to enforce the configuration
- Disabled automatic WorkManager initialization via manifest configuration

### 2. Eliminated Duplicate Daemon Creation

**Before:**
- `MainApplication.onCreate()`: Created NotificationUpdateDaemon
- `EngineForegroundService.onCreate()`: Created another NotificationUpdateDaemon  
- `NotificationWorker.doWork()`: Created new NotificationUpdateDaemon every execution

**After:**
- Only `EngineForegroundService` manages the NotificationUpdateDaemon
- `NotificationWorker` updates notifications directly without creating new daemons

### 3. Proper Job Management

```java
// Unique work names to prevent duplicates
WorkManager.getInstance(this).enqueueUniqueWork(
    "TorrentEngineWork",
    androidx.work.ExistingWorkPolicy.KEEP,
    workRequest
);

// Proper cleanup in onDestroy()
WorkManager.getInstance(this).cancelUniqueWork("TorrentEngineWork");
WorkManager.getInstance(this).cancelUniqueWork("NotificationWork");
```

### 4. TaskThrottle Integration

```java
// Prevent rapid re-scheduling
if (!TaskThrottle.isReadyToSubmitTask("TorrentEngineWork", 30000)) {
    LOG.info("TorrentEngineWork throttled - too soon since last execution");
    return;
}
```

### 5. Manifest Configuration

```xml
<!-- Disable automatic WorkManager initialization -->
<provider
    android:name="androidx.startup.InitializationProvider"
    android:authorities="${applicationId}.androidx-startup"
    android:exported="false"
    tools:node="merge">
    <meta-data
        android:name="androidx.work.WorkManagerInitializer"
        android:value="androidx.startup"
        tools:node="remove" />
</provider>
```

## Impact

- **Reduced Job Count**: From potentially 100+ concurrent jobs to maximum 20
- **Eliminated Duplicates**: Single NotificationUpdateDaemon instead of multiple instances  
- **Added Throttling**: Prevents rapid re-scheduling of the same work
- **Proper Cleanup**: Jobs are cancelled when services shut down
- **System Stability**: Prevents hitting Android's 500 alarm limit

## Testing

The fix can be verified by:

1. Monitoring WorkManager job count in logs
2. Checking for "throttled" messages in logs
3. Using `adb shell dumpsys alarm` to verify reduced alarm count
4. Ensuring no JobScheduler limit exceptions occur during extended use

## Files Modified

- `MainApplication.java`: Added WorkManager configuration and removed duplicate daemon
- `EngineForegroundService.java`: Added proper job cleanup and throttling
- `NotificationUpdateDaemon.java`: Added throttling to prevent rapid restarts
- `NotificationWorker.java`: Fixed to update notifications directly instead of creating daemons
- `AndroidManifest.xml`: Disabled automatic WorkManager initialization

## Monitoring

Add this to logs to monitor job scheduling:

```bash
adb logcat | grep -E "(WorkManager|NotificationUpdateDaemon|TorrentEngineWork|throttled)"
```

Check system alarms:

```bash
adb shell dumpsys alarm | grep -A5 -B5 "com.frostwire.android"
```