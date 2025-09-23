#!/bin/bash

# WorkManager Fix Validation Script
# This script helps validate that the JobScheduler alarm limit fix is working

echo "=== FrostWire WorkManager Fix Validation ==="
echo ""

# Check if device is connected
adb devices | grep -q "device$"
if [ $? -ne 0 ]; then
    echo "❌ No Android device connected. Please connect a device and enable USB debugging."
    exit 1
fi

echo "✅ Android device connected"

# Check if FrostWire is installed
PACKAGE="com.frostwire.android"
adb shell pm list packages | grep -q "$PACKAGE"
if [ $? -ne 0 ]; then
    echo "❌ FrostWire not installed on device"
    exit 1
fi

echo "✅ FrostWire is installed"

echo ""
echo "=== Monitoring WorkManager Activity ==="
echo "Monitoring logs for 30 seconds. Please start FrostWire if not already running..."

# Create a temporary file for logs
LOGFILE="/tmp/frostwire_workmanager.log"

# Monitor WorkManager related logs for 30 seconds
timeout 30s adb logcat -s "MainApplication:*" "EngineForegroundService:*" "NotificationUpdateDaemon:*" "TorrentEngineWorker:*" "NotificationWorker:*" > "$LOGFILE" &
LOGPID=$!

sleep 30
kill $LOGPID 2>/dev/null

echo ""
echo "=== Log Analysis ==="

# Check for WorkManager configuration
if grep -q "WorkManager initialized with reduced scheduler limit" "$LOGFILE"; then
    echo "✅ WorkManager properly configured with reduced scheduler limit"
else
    echo "❌ WorkManager configuration not found in logs"
fi

# Check for throttling
THROTTLE_COUNT=$(grep -c "throttled" "$LOGFILE")
if [ $THROTTLE_COUNT -gt 0 ]; then
    echo "✅ Throttling is working ($THROTTLE_COUNT throttle events detected)"
else
    echo "⚠️  No throttling events detected (this may be normal if services weren't restarted frequently)"
fi

# Check for duplicate daemon prevention
if grep -q "NotificationUpdateDaemon start throttled" "$LOGFILE"; then
    echo "✅ Duplicate daemon creation prevented"
else
    echo "⚠️  No duplicate daemon prevention detected (this may be normal)"
fi

# Check for job cleanup
CLEANUP_COUNT=$(grep -c "Cancelled WorkManager jobs" "$LOGFILE")
if [ $CLEANUP_COUNT -gt 0 ]; then
    echo "✅ Job cleanup is working ($CLEANUP_COUNT cleanup events detected)"
else
    echo "⚠️  No job cleanup detected (this may be normal if services didn't shut down)"
fi

echo ""
echo "=== Alarm Count Check ==="
echo "Checking system alarms for FrostWire..."

ALARM_COUNT=$(adb shell dumpsys alarm | grep -c "$PACKAGE")
echo "📊 Current alarms scheduled by FrostWire: $ALARM_COUNT"

if [ $ALARM_COUNT -lt 50 ]; then
    echo "✅ Alarm count looks reasonable (< 50)"
elif [ $ALARM_COUNT -lt 100 ]; then
    echo "⚠️  Alarm count is elevated but acceptable ($ALARM_COUNT)"
else
    echo "❌ Alarm count is high ($ALARM_COUNT) - may indicate the fix isn't fully working"
fi

echo ""
echo "=== WorkManager Job Count ==="
echo "Note: This requires a debug build to see WorkManager internal state"

# Try to get WorkManager job count from logs
JOB_COUNT=$(adb shell dumpsys jobscheduler | grep -A5 -B5 "$PACKAGE" | grep -c "JobStatus")
if [ $JOB_COUNT -gt 0 ]; then
    echo "📊 Current JobScheduler jobs for FrostWire: $JOB_COUNT"
    if [ $JOB_COUNT -lt 20 ]; then
        echo "✅ Job count is within our configured limit (< 20)"
    else
        echo "⚠️  Job count exceeds our configured limit of 20"
    fi
else
    echo "⚠️  Could not determine JobScheduler job count"
fi

echo ""
echo "=== Summary ==="
echo "Log file saved to: $LOGFILE"
echo ""
echo "Manual verification steps:"
echo "1. Leave FrostWire running for extended periods"
echo "2. Monitor for JobScheduler limit exceptions"
echo "3. Use 'adb shell dumpsys alarm' to check alarm counts periodically"
echo "4. Check logs for 'WorkManager initialized with reduced scheduler limit'"
echo ""
echo "If you see JobScheduler limit exceptions, please check:"
echo "- The WorkManager configuration is properly applied"
echo "- No other code is creating additional WorkManager jobs"
echo "- The manifest provider configuration is correctly set"

# Cleanup
rm -f "$LOGFILE"